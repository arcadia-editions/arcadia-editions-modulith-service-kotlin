package com.arcadiaeditions.payments.processing.application.service

import com.arcadiaeditions.payments.processing.adapter.driven.jpa.inmemory.InMemoryJpaRepository
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.domain.Payment
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus

/**
 * Payments stored the way JPA keeps them (T-80): committed rows with an `@Version`, one persistence context per
 * transaction (REQUIRES_NEW suspends the caller's), `save` of a detached copy is a merge that fails on a version
 * mismatch, and a dirty entity is flushed at commit with its version checked and incremented. The in-memory repository
 * shares one instance everywhere, so it cannot show a stale merge; this store can.
 */
class VersionedPaymentStore {

    /** One persistence context: the managed instances and their state when loaded. */
    class PersistenceContext {
        val managed: MutableMap<Long, Payment> = LinkedHashMap()
        val loadedState: MutableMap<Long, List<Any?>> = HashMap()
        val inserted: MutableSet<Long> = HashSet()
    }

    private val rows: MutableMap<Long, Payment> = LinkedHashMap()
    private var nextId = 1L

    @Volatile private var current: PersistenceContext? = null

    val transactionManager = TransactionManager()
    val repository = Repository()

    /** The committed row of [orderId] (a copy), as the database holds it. */
    fun committed(orderId: String): Payment? = rows.values.firstOrNull { it.orderId == orderId }?.copy()

    /** Inserts committed rows, as data.sql does. */
    fun load(payments: List<Payment>) {
        payments.forEach { p ->
            val id = p.id ?: nextId++
            nextId = maxOf(nextId, id + 1)
            rows[id] = p.copy(id = id, version = p.version ?: 0)
        }
    }

    private fun state(p: Payment): List<Any?> =
        Payment::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "version" }
            .map {
                it.isAccessible = true
                it.get(p)
            }

    private fun copyState(from: Payment, to: Payment) {
        Payment::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "version" }
            .forEach {
                it.isAccessible = true
                it.set(to, it.get(from))
            }
    }

    private fun context(): PersistenceContext =
        current ?: throw IllegalStateException("no transaction: JPA repositories run inside one")

    private fun manage(ctx: PersistenceContext, row: Payment): Payment {
        val managed = row.copy()
        ctx.managed[managed.id!!] = managed
        ctx.loadedState[managed.id!!] = state(managed)
        return managed
    }

    private fun flush(ctx: PersistenceContext) {
        ctx.managed.values.forEach { m ->
            val id = m.id!!
            if (id in ctx.inserted) {
                rows[id] = m.copy()
            } else if (state(m) != ctx.loadedState[id]) {
                val row = rows[id] ?: throw ObjectOptimisticLockingFailureException(Payment::class.java, id)
                if (row.version != m.version) throw ObjectOptimisticLockingFailureException(Payment::class.java, id)
                m.version = (m.version ?: 0) + 1
                rows[id] = m.copy()
            }
        }
    }

    inner class Repository : InMemoryJpaRepository<Payment, Long>(), PaymentRepository {

        override fun findByOrderId(orderId: String): Payment? {
            val ctx = context()
            ctx.managed.values.firstOrNull { it.orderId == orderId }?.let { return it }
            val row = rows.values.firstOrNull { it.orderId == orderId } ?: return null
            return manage(ctx, row)
        }

        override fun <S : Payment> save(entity: S): S {
            val ctx = context()
            val id = entity.id
            if (id == null || entity.version == null) {
                // persist: the instance becomes managed, version 0, inserted at flush
                entity.id = id ?: nextId++
                entity.version = 0
                ctx.managed[entity.id!!] = entity
                ctx.inserted += entity.id!!
                return entity
            }
            val managed = ctx.managed[id]
            if (managed === entity) return entity
            // merge of a detached copy: its version must be the one this context (or the database) holds
            val target = managed ?: rows[id]?.let { manage(ctx, it) } ?: throw ObjectOptimisticLockingFailureException(Payment::class.java, id)
            if (target.version != entity.version) throw ObjectOptimisticLockingFailureException(Payment::class.java, id)
            copyState(entity, target)
            @Suppress("UNCHECKED_CAST")
            return target as S
        }

        override fun findAll(): MutableList<Payment> = rows.values.map { it.copy() }.toMutableList()
    }

    /** One persistence context per transaction; REQUIRES_NEW suspends the caller's and resumes it after. */
    inner class TransactionManager : AbstractPlatformTransactionManager() {

        private inner class Tx(var ctx: PersistenceContext?)

        override fun doGetTransaction(): Any = Tx(current)

        override fun isExistingTransaction(transaction: Any): Boolean = (transaction as Tx).ctx != null

        override fun doBegin(transaction: Any, definition: TransactionDefinition) {
            val ctx = PersistenceContext()
            (transaction as Tx).ctx = ctx
            current = ctx
        }

        override fun doSuspend(transaction: Any): Any {
            val suspended = current!!
            (transaction as Tx).ctx = null
            current = null
            return suspended
        }

        override fun doResume(transaction: Any?, suspendedResources: Any) {
            current = suspendedResources as PersistenceContext
        }

        override fun doCommit(status: DefaultTransactionStatus) {
            flush((status.transaction as Tx).ctx!!)
        }

        override fun doRollback(status: DefaultTransactionStatus) {}

        override fun doCleanupAfterCompletion(transaction: Any) {
            (transaction as Tx).ctx = null
            current = null
        }
    }
}
