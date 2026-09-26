package com.arcadiaeditions.notifications.consumer.domain

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
@Table(name = "notification")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "notification_id", nullable = false, unique = true) var notificationId: String? = null,
    @NotNull @Column(name = "customer_id", nullable = false) var customerId: String? = null,
    @Column(name = "order_id") var orderId: String? = null,
    @Column(name = "sku") var sku: String? = null,
    @NotNull @Column(name = "type", nullable = false) @Enumerated(EnumType.STRING) var type: NotificationType? = null,
    @NotNull
    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    var channel: NotificationChannel? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: NotificationStatus = NotificationStatus.PENDING,
    @NotNull @Column(name = "subject", nullable = false) var subject: String? = null,
    @NotNull @Column(name = "body", nullable = false) var body: String? = null,
    @NotNull @Column(name = "recipient", nullable = false) var recipient: String? = null,
    @NotNull @Column(name = "created_at", nullable = false) var createdAt: Instant? = null,
    @Column(name = "sent_at") var sentAt: Instant? = null,
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
        if (other !is Notification) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
