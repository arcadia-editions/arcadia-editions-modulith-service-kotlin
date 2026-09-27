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
              "customerId" : "customerId-kh5nk5bzthzqf91",
              "customerEmail" : "alisha.muller@gmail.com",
              "paymentMethodId" : "paymentMethodId-gp5zsgzeku",
              "currency" : "currency-ved4s2a",
              "items" : [ {
                "sku" : "sku-v0urwnlikr1mcs55pq",
                "quantity" : 67,
                "unitPrice" : 28
              } ],
              "shippingAddress" : {
                "name" : "name-z0evug11h57379kt82vz",
                "line1" : "line1-vbtagjy9op5l6zm5",
                "line2" : "line2-ayzxkxm3y54t80xy",
                "city" : "Lynchbury",
                "region" : "region-zmk6gg",
                "postalCode" : "postalCode-r",
                "country" : "Guernsey"
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

}
