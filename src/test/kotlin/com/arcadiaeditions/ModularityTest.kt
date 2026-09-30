package com.arcadiaeditions

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verifies the Spring Modulith module structure: no cycles between application modules and no
 * access to another module's internal (non-exposed) types.
 *
 * Known to fail today (M-14 in docs/zenwave-gap-report.md): the PlaceOrderFlow choreography makes
 * the modules listen to each other's events (a cycle at the default module granularity), and
 * shared types such as `common.mappers.BaseMapper` and each `*Transitions.InvalidStateTransitionException`
 * are not exposed as named interfaces.
 */
class ModularityTest {

    private val modules = ApplicationModules.of(Application::class.java)

    @Test
    fun `application modules are verified`() {
        modules.forEach { println(it) }
        modules.verify()
    }
}
