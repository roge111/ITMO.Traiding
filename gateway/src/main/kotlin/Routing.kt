package com.trading

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType

import com.trading.quotes.quotesAsTable 
import com.trading.quotes.QuoteRepository
import com.trading.trading.tradingRoutes

fun Application.configureRouting() {
    tradingRoutes()
    routing {
        get("/quotes") {
            val quotes = QuoteRepository.getQuotesDB()
            call.respondText(
                contentType = ContentType.Text.Html,
                text = quotes.quotesAsTable()
            )
        }

        get("/api/quotes") {
            val quotes = QuoteRepository.getQuotesDB()
            call.respond(quotes)
        }
    }
}
