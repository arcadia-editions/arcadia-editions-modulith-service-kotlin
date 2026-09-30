package com.arcadiaeditions.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** X-06: a single injectable [Clock] so every timestamp (`Instant.now(clock)`) is testable. */
@Configuration
class ClockConfiguration {

    @Bean fun clock(): Clock = Clock.systemUTC()
}
