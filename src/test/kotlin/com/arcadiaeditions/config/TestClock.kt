package com.arcadiaeditions.config

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Fixed test "now" (see the header of data.sql). */
object TestClock {
    val NOW: Instant = Instant.parse("2026-10-01T12:00:00Z")
    val FIXED: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
}
