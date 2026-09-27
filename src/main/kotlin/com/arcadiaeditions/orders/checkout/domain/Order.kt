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

    /** Customer snapshot taken at checkout (there is no customers bounded context yet). */
    @NotNull @Column(name = "customer_id", nullable = false) var customerId: String? = null,
    @NotNull
    @Size(max = 254)
    @Column(name = "customer_email", nullable = false, length = 254)
    var customerEmail: String? = null,
    @NotNull @Column(name = "payment_method_id", nullable = false) var paymentMethodId: String? = null,
    @NotNull
    @Size(min = 1)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "items")
    var items: MutableList<OrderItem> = mutableListOf(),
    @NotNull
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "shipping_address")
    var shippingAddress: ShippingAddress? = null,
    @NotNull @Column(name = "total_amount", nullable = false) var totalAmount: BigDecimal? = null,
    @NotNull @Size(max = 3) @Column(name = "currency", nullable = false, length = 3) var currency: String? = null,
    @Column(name = "created_at") var createdAt: Instant? = null,
    @Column(name = "confirmed_at") var confirmedAt: Instant? = null,
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null,
    @Column(name = "cancellation_reason") var cancellationReason: String? = null,
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
