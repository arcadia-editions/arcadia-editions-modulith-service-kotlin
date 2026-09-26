package com.arcadiaeditions.catalog.products.domain

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
@Table(name = "product")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.jmolecules.ddd.annotation.AggregateRoot
@org.jmolecules.architecture.layered.DomainLayer
data class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @org.jmolecules.ddd.annotation.Identity
    var id: Long? = null,
    @Version var version: Int? = null,
    @NotNull @Column(name = "sku", nullable = false, unique = true) var sku: String? = null,
    @NotNull @Column(name = "name", nullable = false) var name: String? = null,
    @Column(name = "description") var description: String? = null,
    @NotNull @Column(name = "price", nullable = false) var price: BigDecimal? = null,
    @NotNull @Column(name = "currency", nullable = false) var currency: String? = null,
    @NotNull @Column(name = "edition_size", nullable = false) var editionSize: Integer? = null,
    @NotNull
    @Column(name = "tracking_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    var trackingMode: InventoryTrackingMode? = null,
    @Column(name = "launch_at") var launchAt: Instant? = null,
    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ProductStatus = ProductStatus.DRAFT,
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
        if (other !is Product) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
