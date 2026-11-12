package com.arcadiaeditions.catalog.products.events

import com.arcadiaeditions.catalog.products.EventPublisher
import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onProductPublished(event: ProductPublished) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onProductRetired(event: ProductRetired) {
        applicationEventPublisher.publishEvent(event)
    }
}
