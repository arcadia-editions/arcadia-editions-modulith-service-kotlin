package com.arcadiaeditions.payments.processing.domain

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
@Table(name = "payment")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "payment_id", nullable = false, unique = true) var paymentId: String? = null,
    @NotNull @Column(name = "order_id", nullable = false, unique = true) var orderId: String? = null,
    @NotNull @Column(name = "customer_id", nullable = false) var customerId: String? = null,
    @NotNull @Column(name = "amount", nullable = false) var amount: BigDecimal? = null,
    @NotNull @Column(name = "currency", nullable = false) var currency: String? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = PaymentStatus.AUTHORIZATION_REQUESTED,
    @Column(name = "provider_reference") var providerReference: String? = null,
    @NotNull @Column(name = "attempt_count", nullable = false) var attemptCount: Integer? = null,
    @Column(name = "authorized_at") var authorizedAt: Instant? = null,
    @Column(name = "declined_at") var declinedAt: Instant? = null,
    @Column(name = "failed_at") var failedAt: Instant? = null,
    @Column(name = "captured_at") var capturedAt: Instant? = null,
    @Column(name = "voided_at") var voidedAt: Instant? = null,
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
        if (other !is Payment) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
