package com.arcadiaeditions.payments.processing.config

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus

/** Resourceless transaction manager for the in-memory services: records the transactions begun and committed. */
class InMemoryTransactionManager : AbstractPlatformTransactionManager() {

    /** Propagation behavior of every transaction begun, in order. */
    val begun: MutableList<Int> = mutableListOf()

    @Volatile var commits: Int = 0

    @Volatile var rollbacks: Int = 0

    fun reset() {
        begun.clear()
        commits = 0
        rollbacks = 0
    }

    override fun doGetTransaction(): Any = Any()

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        begun.add(definition.propagationBehavior)
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        commits++
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        rollbacks++
    }
}
