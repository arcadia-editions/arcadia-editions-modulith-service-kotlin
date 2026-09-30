package com.arcadiaeditions.orders.checkout.repository.jpa

import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.*
import java.util.List
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class OrderRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var orderRepository: OrderRepository

    @Test
    fun findAllTest() {
        val results = orderRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val order = orderRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(order.id)
        Assertions.assertNotNull(order.version)
    }

    @Test
    fun saveTest() {
        val order = Order()
        order.orderId = ""
        order.status = OrderStatus.values()[0]
        order.customerId = ""
        order.customerEmail = ""
        order.paymentMethodId = ""
        order.items = List.of(OrderItem())
        order.shippingAddress = ShippingAddress()
        order.totalAmount = BigDecimal.valueOf(0)
        order.currency = ""
        order.createdAt = Instant.now()
        order.confirmedAt = Instant.now()
        order.cancelledAt = Instant.now()
        order.cancellationReason = ""

        // Persist aggregate root
        val created = orderRepository.save(order)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val order = orderRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        order.orderId = ""
        order.status = OrderStatus.values()[0]
        order.customerId = ""
        order.customerEmail = ""
        order.paymentMethodId = ""
        order.items = List.of(OrderItem())
        order.shippingAddress = ShippingAddress()
        order.totalAmount = BigDecimal.valueOf(0)
        order.currency = ""
        order.createdAt = Instant.now()
        order.confirmedAt = Instant.now()
        order.cancelledAt = Instant.now()
        order.cancellationReason = ""

        val updated = orderRepository.save(order)
        Assertions.assertEquals("", updated.orderId)
        Assertions.assertEquals(OrderStatus.values()[0], updated.status)
        Assertions.assertEquals("", updated.customerId)
        Assertions.assertEquals("", updated.customerEmail)
        Assertions.assertEquals("", updated.paymentMethodId)
        Assertions.assertEquals(List.of(OrderItem()), updated.items)
        Assertions.assertEquals(ShippingAddress(), updated.shippingAddress)
        Assertions.assertEquals(BigDecimal.valueOf(0), updated.totalAmount)
        Assertions.assertEquals("", updated.currency)
        // Phase 6 / baseline fix: see StockReservationRepositoryIntegrationTest - comparing against
        // a second independent Instant.now() call is flaky by construction.
        Assertions.assertNotNull(updated.createdAt)
        Assertions.assertNotNull(updated.confirmedAt)
        Assertions.assertNotNull(updated.cancelledAt)
        Assertions.assertEquals("", updated.cancellationReason)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        orderRepository.deleteById(id)
        val notFound = orderRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
