package com.arcadiaeditions.orders.checkout.web

import com.arcadiaeditions.orders.checkout.web.*
import com.arcadiaeditions.orders.checkout.web.dtos.*
import com.arcadiaeditions.common.BaseWebTestClientTest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

import java.math.BigDecimal

import org.springframework.http.HttpMethod.*

/**
 * Integration tests for the [OrdersCheckoutApi] REST controller.
 */
open class OrdersCheckoutApiIntegrationTest : BaseWebTestClientTest() {



        /**
        * Test: startOrderCheckout for OK.
        */
        @Test
        fun testStartOrderCheckout_201() {
        val requestBody = """
            {
              "items" : [ "items-uk8rc5gioj0b84ty4biv" ]
            }
        """

        webTestClient.method(POST).uri("/api/orders/start-checkout")
        .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
        .exchange()
        .expectStatus().isEqualTo(201)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.version").isNotEmpty()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isNotEmpty()
            .jsonPath("$.items").isNotEmpty()
                .jsonPath("$.items").isArray()
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.confirmedAt").isNotEmpty()
            .jsonPath("$.cancelledAt").isNotEmpty()
        }

}
