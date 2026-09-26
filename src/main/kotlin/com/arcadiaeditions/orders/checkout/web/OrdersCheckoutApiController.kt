package com.arcadiaeditions.orders.checkout.web

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.web.*
import com.arcadiaeditions.orders.checkout.web.dtos.*
import com.arcadiaeditions.common.*
import com.arcadiaeditions.orders.checkout.web.mappers.*

import java.net.URI
import java.net.URISyntaxException
import java.math.*
import java.time.*
import java.util.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.mapstruct.factory.Mappers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.context.request.NativeWebRequest

/**
 * REST controller for OrdersCheckoutApi.
 */
@RestController
@RequestMapping("/api")
open class OrdersCheckoutApiController(
    private val ordersCheckoutService: OrdersCheckoutService
) : OrdersCheckoutApi {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var request: NativeWebRequest

    private val mapper = OrdersCheckoutDTOsMapper.INSTANCE



    override fun startOrderCheckout(reqBody: StartOrderCheckoutInputDTO): ResponseEntity<OrderDTO> {
        log.debug("REST request to startOrderCheckout: {}", reqBody)
        val input = mapper.asStartOrderCheckoutInput(reqBody)
        val order =  ordersCheckoutService.startOrderCheckout(input)
        val responseDTO = mapper.asOrderDTO(order)
        return ResponseEntity.status(201).body(responseDTO)
    }


    protected fun pageOf(page: Int?, limit: Int?, sort: List<String>?): Pageable {
        val sortOrder = sort?.let {
            Sort.by(it.map { sortParam ->
                val parts = sortParam.split(":")
                val property = parts[0]
                val direction = if (parts.size > 1) Sort.Direction.fromString(parts[1]) else Sort.Direction.ASC
                Sort.Order(direction, property)
            })
        } ?: Sort.unsorted()
        return PageRequest.of(page ?: 0, limit ?: 10, sortOrder)
    }
}
