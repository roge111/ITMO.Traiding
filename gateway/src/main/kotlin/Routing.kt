package com.trading

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType

import com.trading.quotes.quotesAsTable 
import com.trading.quotes.Quote
import com.trading.quotes.QuoteRepository

fun Application.configureRouting() {
    routing {
        get("/quotes") {
            val quotes = QuoteRepository.getQuotesDB()
            call.respondText(
                contentType = ContentType.Text.Html,
                text = quotes.quotesAsTable()
            )
        }

        

    }
}