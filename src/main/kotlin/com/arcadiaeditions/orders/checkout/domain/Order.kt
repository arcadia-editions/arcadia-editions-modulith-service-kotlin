package com.arcadiaeditions.orders.checkout.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy

/**  */
@Entity
@Table(name = "checkout_order")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "order_id", nullable = false, unique = true) var orderId: String? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.CREATED,
    @NotNull @Column(name = "items", nullable = false) var items: MutableList<String> = mutableListOf(),
    @Column(name = "created_at") var createdAt: Instant? = null,
    @Column(name = "confirmed_at") var confirmedAt: Instant? = null,
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    override fun toString(): String {
        return this::class.java.name + "#" + id
    }

    /* https://vladmihalcea.com/the-best-way-to-implement-equals-hashcode-and-tostring-with-jpa-and-hibernate/ */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
