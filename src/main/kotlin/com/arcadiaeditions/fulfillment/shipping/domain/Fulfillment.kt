package com.arcadiaeditions.fulfillment.shipping.domain

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
@Table(name = "fulfillment")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class Fulfillment(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "fulfillment_id", nullable = false, unique = true) var fulfillmentId: String? = null,
    @NotNull @Column(name = "order_id", nullable = false, unique = true) var orderId: String? = null,
    @NotNull @Column(name = "customer_id", nullable = false) var customerId: String? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: FulfillmentStatus = FulfillmentStatus.SCHEDULED,
    @NotNull
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "shipping_address")
    var shippingAddress: ShippingAddress? = null,
    @NotNull
    @Size(min = 1)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "items")
    var items: MutableList<FulfillmentItem> = mutableListOf(),
    @Column(name = "carrier_code") var carrierCode: String? = null,
    @Column(name = "tracking_number") var trackingNumber: String? = null,
    @Column(name = "scheduled_at") var scheduledAt: Instant? = null,
    @Column(name = "ready_at") var readyAt: Instant? = null,
    @Column(name = "failed_at") var failedAt: Instant? = null,
    @Column(name = "failure_reason") var failureReason: String? = null,
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
        if (other !is Fulfillment) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
