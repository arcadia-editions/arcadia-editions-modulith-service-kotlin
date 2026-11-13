package com.arcadiaeditions.orders.checkout.adapters.web

import com.arcadiaeditions.orders.checkout.adapters.web.*
import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*
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
              "customerId" : "CUST-001",
              "customerEmail" : "customer01@example.com",
              "paymentMethodId" : "pm_card_visa",
              "currency" : "EUR",
              "items" : [ { "sku" : "SKU-001", "quantity" : 1, "unitPrice" : 49.99 } ],
              "shippingAddress" : {
                "name" : "Alex Example", "line1" : "Calle Ficticia 1", "line2" : "2B", "city" : "Madrid",
                "region" : "Madrid", "postalCode" : "28001", "country" : "ES"
              }
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
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.customerId").isEqualTo("CUST-001")
            .jsonPath("$.items[0].sku").isEqualTo("SKU-001")
            .jsonPath("$.totalAmount").isEqualTo(49.99)
            .jsonPath("$.currency").isEqualTo("EUR")
            .jsonPath("$.createdAt").isNotEmpty()
        }

        /**
        * Test: startOrderCheckout for Invalid request.
        */
        @Test
        fun testStartOrderCheckout_400() {
        val requestBody = """
            {
              "customerId" : "customerId-xb",
              "customerEmail" : "dylan.batz@yahoo.com",
              "paymentMethodId" : "paymentMethodId-8hwjogyyxq",
              "currency" : "currency-vy1v26p60l8xc77fj",
              "items" : [ {
                "sku" : "sku-f4puykvwxu",
                "quantity" : 73,
                "unitPrice" : 15
              } ],
              "shippingAddress" : {
                "name" : "name-ykl97uupvmlt42v",
                "line1" : "line1-rz3dnw7plscbfdb",
                "line2" : "line2-cjm276l9kqyo4dpj4niv",
                "city" : "Donovanstad",
                "region" : "region-ytt",
                "postalCode" : "postalCode-7chalctbnx7czid",
                "country" : "Japan"
              }
            }
        """

        webTestClient.method(POST).uri("/api/orders/start-checkout")
        .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
        .exchange()
        .expectStatus().isEqualTo(400)
        }

        /**
        * Test: startOrderCheckout for stock unavailable (C-07, SKU-003) answers 409 with StockUnavailable body (P-03 A).
        */
        @Test
        fun testStartOrderCheckout_409() {
        val requestBody = """
            {
              "customerId" : "CUST-007",
              "customerEmail" : "customer07@example.com",
              "paymentMethodId" : "pm_card_visa",
              "currency" : "EUR",
              "items" : [ { "sku" : "SKU-003", "quantity" : 1, "unitPrice" : 75.00 } ],
              "shippingAddress" : {
                "name" : "Alex Example", "line1" : "Calle Ficticia 1", "city" : "Madrid",
                "postalCode" : "28001", "country" : "ES"
              }
            }
        """

        webTestClient.method(POST).uri("/api/orders/start-checkout")
        .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
        .exchange()
        .expectStatus().isEqualTo(409)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.unavailableItems[0].sku").isEqualTo("SKU-003")
            .jsonPath("$.unavailableItems[0].requestedQuantity").isEqualTo(1)
            .jsonPath("$.unavailableItems[0].availableQuantity").isEqualTo(0)
        }

        /**
        * Test: getOrder for OK.
        */
        @Test
        fun testGetOrder_200() {
        webTestClient.method(GET).uri("/api/orders/{id}", 1)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(200)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-001")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.customerId").isEqualTo("CUST-001")
            .jsonPath("$.totalAmount").isEqualTo(49.99)
        }

        /**
        * Test: getOrder for an unknown id answers 404.
        */
        @Test
        fun testGetOrder_404() {
        webTestClient.method(GET).uri("/api/orders/{id}", 999999)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(404)
        }

}
