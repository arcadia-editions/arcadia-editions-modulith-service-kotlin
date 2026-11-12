package com.arcadiaeditions.catalog.inventory.domain

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
@Table(name = "stock_reservation")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class StockReservation(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "reservation_id", nullable = false, unique = true) var reservationId: String? = null,

    /** One reservation per order line: an order has as many reservations as items. */
    @NotNull @Column(name = "order_id", nullable = false) var orderId: String? = null,
    @NotNull @Column(name = "sku", nullable = false) var sku: String? = null,
    @NotNull @Column(name = "quantity", nullable = false) var quantity: Integer? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ReservationStatus = ReservationStatus.RESERVED,
    @NotNull @Column(name = "reserved_at", nullable = false) var reservedAt: Instant? = null,
    @NotNull @Column(name = "expires_at", nullable = false) var expiresAt: Instant? = null,
    @Column(name = "confirmed_at") var confirmedAt: Instant? = null,
    @Column(name = "released_at") var releasedAt: Instant? = null,
    @Column(name = "release_reason") @Enumerated(EnumType.STRING) var releaseReason: StockReleaseReason? = null,
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
        if (other !is StockReservation) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
