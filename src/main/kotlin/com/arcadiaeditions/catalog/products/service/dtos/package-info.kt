/** Inbound DTOs. */
@file:JvmName("PackageInfo")

package com.arcadiaeditions.catalog.products.service.dtos

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Keep {
    // keeps this package even when it's empty,
    // allowing wildcard import "com.arcadiaeditions.core.inbound.dtos.*;"
}
