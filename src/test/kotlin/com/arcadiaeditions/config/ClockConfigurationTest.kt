package com.arcadiaeditions.config

import java.time.Clock
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClockConfigurationTest {

    @Test
    fun clockBeanIsTheSystemUtcClock() {
        assertEquals(Clock.systemUTC(), ClockConfiguration().clock())
        assertEquals(ZoneOffset.UTC, ClockConfiguration().clock().zone)
    }
}
