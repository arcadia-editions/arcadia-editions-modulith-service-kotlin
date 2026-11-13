package com.arcadiaeditions.config

import io.zenwave360.modulith.events.scs.config.EnableSpringCloudStreamEventExternalization
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

@Configuration
@EnableSpringCloudStreamEventExternalization
@EnableScheduling
class ExternalizationConfiguration(
    private val incompleteEventPublications: IncompleteEventPublications,
    @Value("\${app.events.resubmit-incomplete-publications-older-than:PT5M}")
    private val resubmitOlderThan: Duration
) {

    @Scheduled(fixedRate = 30000)
    fun resubmitIncompletePublications() {
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(resubmitOlderThan)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExternalizationConfiguration::class.java)
    }
}
