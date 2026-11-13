package com.arcadiaeditions.common

import com.arcadiaeditions.fulfillment.shipping.FakeWarehouse
import com.arcadiaeditions.notifications.consumer.EmailMessage
import com.arcadiaeditions.notifications.consumer.FakeEmailSender
import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan

class FakeAdaptersGatingTest {

    @ComponentScan(basePackageClasses = [FakeWarehouse::class, FakeEmailSender::class, FakePaymentProcessor::class],
        useDefaultFilters = false,
        includeFilters = [ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = [FakeWarehouse::class, FakeEmailSender::class, FakePaymentProcessor::class])])
    class Scan

    private val runner = ApplicationContextRunner().withUserConfiguration(Scan::class.java)

    @Test
    fun `fakes are absent without the property`() {
        runner.run {
            assertEquals(0, it.getBeansOfType(FakeWarehouse::class.java).size)
            assertEquals(0, it.getBeansOfType(FakeEmailSender::class.java).size)
            assertEquals(0, it.getBeansOfType(FakePaymentProcessor::class.java).size)
        }
    }

    @Test
    fun `fakes are present when enabled`() {
        runner.withPropertyValues("arcadia.fake-adapters.enabled=true").run {
            assertEquals(1, it.getBeansOfType(FakeWarehouse::class.java).size)
            assertEquals(1, it.getBeansOfType(FakeEmailSender::class.java).size)
            assertEquals(1, it.getBeansOfType(FakePaymentProcessor::class.java).size)
        }
    }

    @Test
    fun `sent list is bounded`() {
        val sender = FakeEmailSender()
        repeat(FakeEmailSender.MAX_SENT + 5) { sender.send(EmailMessage("a@b.c", "s$it", "b")) }
        assertEquals(FakeEmailSender.MAX_SENT, sender.sent.size)
        assertEquals("s${FakeEmailSender.MAX_SENT + 4}", sender.sent.last().subject)
    }
}
