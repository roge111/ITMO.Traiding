package com.trading

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    val applicationLog = log
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            applicationLog.error("Unhandled request error", cause)
            call.respondText(
                text = "Internal server error",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}
