package com.arcadiaeditions.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The single source of "now" for services: inject [Clock] instead of calling Instant.now(). */
@Configuration
open class ClockConfiguration {

    @Bean open fun clock(): Clock = Clock.systemUTC()
}
