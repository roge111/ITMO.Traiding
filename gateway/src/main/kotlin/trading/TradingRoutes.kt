package com.trading.trading

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.tradingRoutes() {
    routing {
        get("/api/account") {
            val login = call.request.queryParameters["login"]
            if (login.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing login")
                return@get
            }
            call.respond(runCatching { TradingRepository.account(login) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Account error")
                return@get
            })
        }

        get("/api/portfolio") {
            val login = call.request.queryParameters["login"]
            if (login.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing login")
                return@get
            }
            call.respond(runCatching { TradingRepository.portfolio(login) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Portfolio error")
                return@get
            })
        }

        get("/api/quotes/{name}/history") {
            val name = call.parameters["name"]
            if (name.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing quote name")
                return@get
            }
            call.respond(runCatching { TradingRepository.history(name) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "History error")
                return@get
            })
        }

        get("/api/quotes/{name}/candles") {
            val name = call.parameters["name"]
            if (name.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing quote name")
                return@get
            }
            call.respond(runCatching { TradingRepository.candles(name) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Candles error")
                return@get
            })
        }

        post("/api/trade") {
            val params = call.receiveParameters()
            val login = params["login"]
            val quoteName = params["quoteName"]
            val quantity = params["quantity"]?.toIntOrNull()
            val side = params["side"]
            if (login.isNullOrBlank() || quoteName.isNullOrBlank() || quantity == null || side.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing trade parameters")
                return@post
            }
            call.respond(runCatching {
                TradingRepository.trade(login, quoteName, quantity, side)
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Trade error")
                return@post
            })
        }

        post("/api/market/tick") {
            val params = call.receiveParameters()
            val quoteName = params["quoteName"]?.takeIf { it.isNotBlank() }
            call.respond(runCatching {
                TradingRepository.marketTick(quoteName)
                mapOf("status" to "ok")
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Market tick error")
                return@post
            })
        }
    }
}
