package com.arcadiaeditions

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * The modulith mixes four package layouts, so no single hexagonal/onion rule fits all modules.
 * Each module is checked against its own layout, looking only at dependencies inside the module
 * (module-to-module and `common` dependencies are the job of [ApplicationModules.verify]):
 *
 * - `orders.checkout`: CleanHexagonal (`core.{domain,inbound,outbound,application}`, `adapters`, `infrastructure`)
 * - `payments.processing`: Hexagonal (`domain`, `application.{port,service}`, `adapter.{driving,driven}`)
 * - `catalog.inventory`, `notifications.consumer`: Layered (`domain`, `repository`, `service`, `events`)
 * - `catalog.products`, `fulfillment.shipping`: SimpleDomain (flat module, `domain` depends on nothing else)
 *
 * The jMolecules DDD rules are in [JMoleculesArchitectureTest].
 */
@AnalyzeClasses(packages = ["com.arcadiaeditions"], importOptions = [DoNotIncludeTests::class])
class ArchitectureTest {

    /** Module boundaries, `allowedDependencies`, cycles and internal-package access (Spring Modulith). */
    @Test
    fun applicationModulesAreValid() {
        val violations = ApplicationModules.of(Application::class.java).detectViolations()
        val unexpected = violations.messages.filterNot { isKnown(it) }
        assertThat(unexpected).describedAs("Spring Modulith violations").isEmpty()
    }

    /** The tolerance is bounded: an unlisted cycle, BaseMapper user or jMolecules message is not known. */
    @Test
    fun toleranceRejectsAnythingNotListed() {
        assertThat(isKnown(cycle("orders", "fulfillment", "orders"))).isFalse()
        assertThat(isKnown(cycle("orders", "payments", "orders"))).isTrue()
        assertThat(isKnown("Module 'orders' depends on non-exposed type com.arcadiaeditions.common.other.Foo within module 'common'!")).isFalse()
        // repository shape: tolerated only in catalog.inventory / notifications.consumer, same-module domain types only
        assertThat(isKnown(
            "Method <$NOT.repository.jpa.NotificationRepository.findByOrderIdAndType(java.lang.String, $NOT.domain.NotificationType)> " +
                "has return type <$NOT.domain.Notification> in (NotificationRepository.kt:0)")).isTrue()
        assertThat(isKnown(
            "Method <$INV.repository.jpa.InventoryPositionRepository.findAllBySkuIn(java.util.List)> " +
                "has generic return type <java.util.List<$INV.domain.InventoryPosition>> with type argument depending on <$INV.domain.InventoryPosition>")).isTrue()
        assertThat(isKnown(
            "Method <com.arcadiaeditions.orders.checkout.infrastructure.jpa.OrderRepository.findByOrderId(java.lang.String)> " +
                "has return type <com.arcadiaeditions.orders.checkout.core.domain.Order> in (OrderRepository.kt:0)")).isFalse()
        assertThat(isKnown(
            "Class <com.arcadiaeditions.orders.checkout.repository.jpa.OrderRepository> has generic interface " +
                "<org.springframework.data.jpa.repository.JpaRepository<com.arcadiaeditions.orders.checkout.domain.Order, java.lang.Long>> " +
                "with type argument depending on <com.arcadiaeditions.orders.checkout.domain.Order>")).isFalse()
        assertThat(isKnown(
            "Method <$INV.repository.jpa.InventoryPositionRepository.findByOrder(java.lang.String)> " +
                "has return type <com.arcadiaeditions.orders.checkout.core.domain.Order> in (InventoryPositionRepository.kt:0)")).isFalse()
        assertThat(isKnown(
            "Method <$INV.repository.jpa.InventoryPositionRepository.findBySku($NOT.domain.Notification)> " +
                "has parameter of type <$NOT.domain.Notification> in (InventoryPositionRepository.kt:0)")).isFalse()
        assertThat(isKnown(
            "Method <$INV.repository.jpa.InventoryPositionRepository.findBySku(java.lang.String)> " +
                "has return type <$INV.service.dtos.InventoryPositionDto> in (InventoryPositionRepository.kt:0)")).isFalse()
        assertThat(isKnown(
            "Method <$INV.service.impl.InventoryServiceImpl.x()> has return type <$INV.domain.InventoryPosition> in (InventoryServiceImpl.kt:0)")).isFalse()
        // event publishers: exact list
        assertThat(isKnown(
            "Method <$INV.events.DefaultEventPublisher.onStockReleased($INV.domain.events.StockReleased)> has parameter of type <$INV.domain.events.StockReleased> in (DefaultEventPublisher.kt:0)")).isTrue()
        assertThat(isKnown(
            "Method <$INV.events.DefaultEventPublisher.onStockReserved($INV.domain.events.StockReserved)> has parameter of type <$INV.domain.events.StockReserved> in (DefaultEventPublisher.kt:0)")).isFalse()
    }

    private fun cycle(vararg slices: String) =
        "Cycle detected: " + slices.joinToString(" -> \n") { "Slice $it" } + "\n  1. Dependencies of Slice ${slices[0]}"

    private fun isKnown(message: String): Boolean {
        if (KNOWN_MAPPER_VIOLATION.containsMatchIn(message)) return true
        if (message.startsWith("Cycle detected")) {
            val chain = SLICE.findAll(message.substringBefore("1. Dependencies")).map { it.groupValues[1] }.joinToString(">")
            return chain in KNOWN_CYCLES
        }
        val bare = LOCATION.replace(message, "").trim()
        return bare in KNOWN_PUBLISHER_MESSAGES || isSameModuleRepositoryMessage(bare)
    }

    /** jMolecules layered message from a `<module>.repository.jpa.*Repository` that references only `<module>.domain` types. */
    private fun isSameModuleRepositoryMessage(message: String): Boolean {
        val match = REPOSITORY_MESSAGE.matchEntire(message) ?: return false
        val source = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val module = LAYERED_MODULES.firstOrNull {
            val name = source.removePrefix("com.arcadiaeditions.$it.repository.jpa.")
            name != source && '.' !in name && name.endsWith("Repository")
        } ?: return false
        val referenced = PROJECT_TYPE.findAll(message).map { it.value }.filterNot { it == source || it.startsWith("$source.") }.toList()
        return referenced.isNotEmpty() && referenced.all { it.startsWith("com.arcadiaeditions.$module.domain.") }
    }

    private companion object {
        val SLICE = Regex("""Slice (\w+)""")
        val LOCATION = Regex("""\s+in \(\w+\.kt:\d+\)$""")
        const val INV = "com.arcadiaeditions.catalog.inventory"
        const val NOT = "com.arcadiaeditions.notifications.consumer"

        /**
         * Known and reported (P-04), not fixed here. Cause: the code layout (each producer module owns its
         * event classes, each consumer's listener imports them), not the models. Anything else fails.
         *
         * 1. Module cycles, exactly these 4 (the flow's event choreography through event types).
         */
        val KNOWN_CYCLES = setOf(
            "catalog>payments>fulfillment>orders>catalog",
            "catalog>payments>orders>catalog",
            "fulfillment>orders>payments>fulfillment",
            "orders>payments>orders",
        )

        /**
         * 2. `common.mappers.BaseMapper` is not exposed by `common`; every module's MapStruct mapper extends it.
         */
        val KNOWN_MAPPER_VIOLATION =
            Regex("""Module '\w+' depends on non-exposed type com\.arcadiaeditions\.common\.mappers\.BaseMapper within module 'common'!""")

        /**
         * 3. jMolecules layered-architecture messages (jmolecules-archunit runs inside Modulith): `@InfrastructureLayer`
         * types depend on `@DomainLayer` types of the same module, in catalog.inventory and notifications.consumer only.
         *
         * 3a. Repositories, by shape (a new finder must not break the build; T-36 splits the modules, P-04 A):
         * the source is a `<module>.repository.jpa.*Repository` of one of [LAYERED_MODULES], the message is its
         * `JpaRepository<Entity, ..>` supertype or a method parameter/return type, and every `com.arcadiaeditions`
         * type it references is in `<module>.domain`. Today: 3 `JpaRepository` supertypes (InventoryPosition,
         * StockReservation, Notification) + 3 finders (findBySku, findByOrderId, findByOrderIdAndType).
         */
        val LAYERED_MODULES = listOf("catalog.inventory", "notifications.consumer")
        val PROJECT_TYPE = Regex("""com\.arcadiaeditions\.[\w.$]+""")
        val REPOSITORY_MESSAGE = Regex(
            """(?:Class <([\w.$]+)> has generic interface <org\.springframework\.data\.jpa\.repository\.JpaRepository<.+>>""" +
                """|Method <([\w.$]+)\.\w+\([^)]*\)> has (?:return type|generic return type|parameter of type|generic parameter type) <.+>)""" +
                """(?: with type argument depending on <[\w.$]+>)?""",
        )

        /** 3b. Event publishers take domain events: exactly these 8 (4 inventory + 4 notifications). */
        val KNOWN_PUBLISHER_MESSAGES = setOf(
            *listOf("StockAdjusted", "StockReceived", "StockReleased", "StockReservationConfirmed").map {
                "Method <$INV.events.DefaultEventPublisher.on$it($INV.domain.events.$it)> has parameter of type <$INV.domain.events.$it>"
            }.toTypedArray(),
            *listOf("NotificationDeliveryFailed", "OrderCancelledNotificationSent", "OrderConfirmationSent", "StockUnavailableNotificationSent").map {
                "Method <$NOT.events.DefaultEventPublisher.on$it($NOT.domain.events.$it)> has parameter of type <$NOT.domain.events.$it>"
            }.toTypedArray(),
        )
    }

    @ArchTest
    val ordersCheckoutIsCleanHexagonal: ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.arcadiaeditions.orders.checkout..")
        .layer("Domain").definedBy("..orders.checkout.core.domain..")
        .layer("Inbound").definedBy("..orders.checkout.core.inbound..")
        .layer("Outbound").definedBy("..orders.checkout.core.outbound..")
        .layer("Application").definedBy("..orders.checkout.core.application..")
        .layer("Adapters").definedBy("..orders.checkout.adapters..")
        .layer("Infrastructure").definedBy("..orders.checkout.infrastructure..")
        .whereLayer("Domain").mayNotAccessAnyLayer()
        .whereLayer("Inbound").mayOnlyAccessLayers("Domain")
        .whereLayer("Outbound").mayOnlyAccessLayers("Domain")
        .whereLayer("Application").mayNotBeAccessedByAnyLayer()
        .whereLayer("Adapters").mayNotBeAccessedByAnyLayer()
        .whereLayer("Adapters").mayOnlyAccessLayers("Inbound", "Domain")
        .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
        .whereLayer("Infrastructure").mayOnlyAccessLayers("Outbound", "Domain")

    @ArchTest
    val paymentsProcessingIsHexagonal: ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.arcadiaeditions.payments.processing..")
        .layer("Domain").definedBy("..payments.processing.domain..")
        .layer("Ports").definedBy("..payments.processing.application.port..")
        .layer("Service").definedBy("..payments.processing.application.service..")
        .layer("Adapters").definedBy("..payments.processing.adapter..")
        .whereLayer("Domain").mayNotAccessAnyLayer()
        .whereLayer("Ports").mayOnlyAccessLayers("Domain")
        .whereLayer("Service").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyAccessLayers("Ports", "Domain")
        .whereLayer("Adapters").mayNotBeAccessedByAnyLayer()
        .whereLayer("Adapters").mayOnlyAccessLayers("Ports", "Domain")

    @ArchTest
    val catalogInventoryIsLayered: ArchRule = layered("catalog.inventory")

    @ArchTest
    val notificationsConsumerIsLayered: ArchRule = layered("notifications.consumer")

    @ArchTest
    val catalogProductsIsSimpleDomain: ArchRule = simpleDomain("catalog.products")

    @ArchTest
    val fulfillmentShippingIsSimpleDomain: ArchRule = simpleDomain("fulfillment.shipping")

    private fun layered(module: String): ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.arcadiaeditions.$module..")
        .layer("Domain").definedBy("..$module.domain..")
        .layer("Repository").definedBy("..$module.repository..")
        .layer("Service").definedBy("..$module.service..")
        .layer("Events").definedBy("..$module.events..")
        .whereLayer("Domain").mayNotAccessAnyLayer()
        .whereLayer("Repository").mayOnlyAccessLayers("Domain")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Events")
        .whereLayer("Events").mayOnlyBeAccessedByLayers("Service")

    private fun simpleDomain(module: String): ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.arcadiaeditions.$module..")
        .layer("Domain").definedBy("..$module.domain..")
        .layer("Rest").definedBy("..$module..")
        .whereLayer("Domain").mayNotAccessAnyLayer()
}
