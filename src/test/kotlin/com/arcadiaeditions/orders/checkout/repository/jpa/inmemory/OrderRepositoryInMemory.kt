package com.arcadiaeditions.orders.checkout.repository.jpa.inmemory

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.repository.jpa.OrderRepository
import java.math.*
import java.time.*
import java.util.*

class OrderRepositoryInMemory : InMemoryJpaRepository<Order, Long>(), OrderRepository {
    override fun findByOrderId(orderId: String): Order? = findByUniqueField("orderId", orderId)
}
