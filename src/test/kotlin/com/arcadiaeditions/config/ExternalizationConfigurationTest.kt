package com.arcadiaeditions.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.modulith.events.IncompleteEventPublications
import java.time.Duration

class ExternalizationConfigurationTest {

    private val publications = mock(IncompleteEventPublications::class.java)

    @Test
    fun resubmitsOnlyPublicationsOlderThanConfiguredAge() {
        val age = Duration.ofMinutes(5)

        ExternalizationConfiguration(publications, age).resubmitIncompletePublications()

        verify(publications).resubmitIncompletePublicationsOlderThan(age)
        verifyNoMoreInteractions(publications)
    }
}
