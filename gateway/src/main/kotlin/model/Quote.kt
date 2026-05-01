package com.trading.model

data class Quote(
    val name: String,
    val price: Double
)

fun Quote.quoteAsRow() = """
    <tr>
        <td>${name}</td>
        <td>${price}</td>
        <td>${procent}</td>
        <td>${min_price}</td>
        <td>${max_price}</td>
    </table>
""".trimIndent()

fun List<Quote>.quotesAsTable() = this.joinToString(
    prefix = "<table border='1'>",
    postfix = "</table>",
    separator = "\n",
    transform = Quote::quoteAsRow
)