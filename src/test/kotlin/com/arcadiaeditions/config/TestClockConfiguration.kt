package com.arcadiaeditions.config

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** A [Clock] tests can set and advance (timers, reservation expiry). Starts at [TestClock.NOW]. */
class MutableClock(@Volatile private var now: Instant = TestClock.NOW, private val zone: ZoneId = ZoneOffset.UTC) :
    Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun set(instant: Instant) {
        now = instant
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    fun reset() = set(TestClock.NOW)
}

/**
 * Replaces the application's `Clock.systemUTC()` with a [MutableClock] at [TestClock.NOW] (data.sql header). Import it
 * in a Spring test (`@Import(TestClockConfiguration::class)`) and inject [MutableClock] to move time.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    @Bean @Primary fun testClock(): MutableClock = MutableClock()
}
