// formatter:off
package com.arcadiaeditions

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import org.jmolecules.archunit.JMoleculesDddRules
import org.jmolecules.archunit.JMoleculesArchitectureRules
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth

/**
 * Verifies the jMolecules stereotypes ZenWave generated.
 *
 * Unlike `ArchitectureTest`, which matches package names, these rules read the annotations
 * themselves - so they keep working whatever project layout you choose, and they fail when an
 * annotation stops matching the code it sits on.
 *
 * Requires `org.jmolecules.integrations:jmolecules-archunit` on the test classpath.
 */
@AnalyzeClasses(packages = ["com.arcadiaeditions"], importOptions = [DoNotIncludeTests::class])
internal class JMoleculesArchitectureTest {

    companion object {

        /** Every type annotated `@Entity` or `@AggregateRoot` must declare an `@Identity`. */
        @ArchTest
        @JvmField
        val entitiesHaveIdentities: ArchRule =
            JMoleculesDddRules.annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier()

        /** A `@ValueObject` must not reference an entity or an aggregate. */
        @ArchTest
        @JvmField
        val valueObjectsDoNotReferenceEntities: ArchRule =
            JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables()

        /** An `@Entity` must not be shared between two aggregates. */
        @ArchTest
        @JvmField
        val entitiesBelongToTheirAggregate: ArchRule =
            JMoleculesDddRules.entitiesShouldBeDeclaredForUseInSameAggregate()

        /**
         * Nothing may hold a direct reference to an `@AggregateRoot` - only an identifier or an
         * `Association`.
         *
         * Note that ZenWave maps a bidirectional relationship to a plain object reference:
         * `Customer{addresses} to Address{customer}` generates a `customer: Customer` property,
         * which this rule rejects even though both types live in the same aggregate. To opt out,
         * comment out the `@ArchTest` below - this file is generated once and never overwritten, so
         * the edit survives regeneration.
         */
        @ArchTest
        @JvmField
        val aggregateReferencesUseIdsOrAssociations: ArchRule =
            JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation()

        /**
         * Ports and adapters may only talk to each other through ports.
         *
         * `LENIENT` lets adapters and ports reach non-port application code, and lets primary
         * adapters use secondary ports directly. `SEMI_STRICT` withdraws that from primary
         * adapters. `STRICT` withdraws it from every adapter.
         */
        @ArchTest
        @JvmField
        val respectsHexagonalArchitecture: ArchRule =
            JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT)
    }
}
