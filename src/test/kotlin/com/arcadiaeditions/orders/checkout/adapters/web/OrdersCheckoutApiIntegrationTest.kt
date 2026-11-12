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
              "customerId" : "customerId-fz",
              "customerEmail" : "ricardo.tremblay@hotmail.c",
              "paymentMethodId" : "paymentMethodId-sygwuu1k28",
              "currency" : "currency-zoesrbh8tagdogzbp",
              "items" : [ {
                "sku" : "sku-j6dpsnojidxr",
                "quantity" : 85,
                "unitPrice" : 64
              } ],
              "shippingAddress" : {
                "name" : "name-gh",
                "line1" : "line1-eykvcxb4sf14fs6yts4z",
                "line2" : "line2-did5wykahu",
                "city" : "Lake Katina",
                "region" : "region-al5i2rwx3cjwyu",
                "postalCode" : "postalCode-s",
                "country" : "Maldives"
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
            .jsonPath("$.version").isNotEmpty()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isNotEmpty()
            .jsonPath("$.customerId").isNotEmpty()
            .jsonPath("$.customerEmail").isNotEmpty()
            .jsonPath("$.paymentMethodId").isNotEmpty()
            .jsonPath("$.items").isNotEmpty()
                .jsonPath("$.items").isArray()
                    .jsonPath("$.items[0].sku").isNotEmpty()
                    .jsonPath("$.items[0].quantity").isNotEmpty()
                    .jsonPath("$.items[0].unitPrice").isNotEmpty()
            .jsonPath("$.shippingAddress").isNotEmpty()
                    .jsonPath("$.shippingAddress.name").isNotEmpty()
                    .jsonPath("$.shippingAddress.line1").isNotEmpty()
                    .jsonPath("$.shippingAddress.line2").isNotEmpty()
                    .jsonPath("$.shippingAddress.city").isNotEmpty()
                    .jsonPath("$.shippingAddress.region").isNotEmpty()
                    .jsonPath("$.shippingAddress.postalCode").isNotEmpty()
                    .jsonPath("$.shippingAddress.country").isNotEmpty()
            .jsonPath("$.totalAmount").isNotEmpty()
            .jsonPath("$.currency").isNotEmpty()
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.confirmedAt").isNotEmpty()
            .jsonPath("$.cancelledAt").isNotEmpty()
            .jsonPath("$.cancellationReason").isNotEmpty()
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
        * Test: getOrder for OK.
        */
        @Test
        fun testGetOrder_200() {
        val id = ""

        webTestClient.method(GET).uri("/api/orders/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(200)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.version").isNotEmpty()
            .jsonPath("$.orderId").isNotEmpty()
            .jsonPath("$.status").isNotEmpty()
            .jsonPath("$.customerId").isNotEmpty()
            .jsonPath("$.customerEmail").isNotEmpty()
            .jsonPath("$.paymentMethodId").isNotEmpty()
            .jsonPath("$.items").isNotEmpty()
                .jsonPath("$.items").isArray()
                    .jsonPath("$.items[0].sku").isNotEmpty()
                    .jsonPath("$.items[0].quantity").isNotEmpty()
                    .jsonPath("$.items[0].unitPrice").isNotEmpty()
            .jsonPath("$.shippingAddress").isNotEmpty()
                    .jsonPath("$.shippingAddress.name").isNotEmpty()
                    .jsonPath("$.shippingAddress.line1").isNotEmpty()
                    .jsonPath("$.shippingAddress.line2").isNotEmpty()
                    .jsonPath("$.shippingAddress.city").isNotEmpty()
                    .jsonPath("$.shippingAddress.region").isNotEmpty()
                    .jsonPath("$.shippingAddress.postalCode").isNotEmpty()
                    .jsonPath("$.shippingAddress.country").isNotEmpty()
            .jsonPath("$.totalAmount").isNotEmpty()
            .jsonPath("$.currency").isNotEmpty()
            .jsonPath("$.createdAt").isNotEmpty()
            .jsonPath("$.confirmedAt").isNotEmpty()
            .jsonPath("$.cancelledAt").isNotEmpty()
            .jsonPath("$.cancellationReason").isNotEmpty()
        }

        /**
        * Test: getOrder for Invalid request.
        */
        @Test
        fun testGetOrder_400() {
        val id = ""

        webTestClient.method(GET).uri("/api/orders/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(400)
        }

}
