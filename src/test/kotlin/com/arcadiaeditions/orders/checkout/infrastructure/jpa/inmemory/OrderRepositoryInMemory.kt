package com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory

import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import java.math.*
import java.time.*
import java.util.*

class OrderRepositoryInMemory : InMemoryJpaRepository<Order, Long>(), OrderRepository {}
