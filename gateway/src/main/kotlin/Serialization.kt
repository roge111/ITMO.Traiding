package com.trading

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}