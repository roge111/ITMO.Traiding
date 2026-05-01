package com.trading

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import com.trading.model.QuoteRepository
import com.trading.model.quotesAsTable 

fun Application.configureRouting() {
    routing {
        get("/quotes") {
            val quotes = QuoteRepository.getAll()
            call.respondText(
                contentType = ContentType.Text.Html,
                text = quotes.quotesAsTable()
            )
        }
    }
}