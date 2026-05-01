package com.trading.quotes

data class Quote(
    val name: String,
    val price: Double,
    val percentageChange: Double,
    val minCost: Long,
    val maxCost: Long
)

fun Quote.quoteAsRow(): String {
    return """
        <tr>
            <td>${this.name}</td>
            <td>${this.price}</td>
            <td>${this.percentageChange}%</td>
            <td>${this.minCost}</td>
            <td>${this.maxCost}</td>
        </tr>
    """.trimIndent()
}

fun List<Quote>.quotesAsTable(): String {
    if (this.isEmpty()) return "<p>Нет данных</p>"
    
    val header = """
        <tr>
            <th>Название</th>
            <th>Цена</th>
            <th>Изменение %</th>
            <th>Мин.</th>
            <th>Макс.</th>
        </tr>
    """.trimIndent()
    
    val rows = this.joinToString("\n") { quote ->
        """
        <tr>
            <td>${quote.name}</td>
            <td>${String.format("%.2f", quote.price)}</td>
            <td style='color: ${if (quote.percentageChange >= 0) "green" else "red"}'>${String.format("%.2f", quote.percentageChange)}%</td>
            <td>${quote.minCost}</td>
            <td>${quote.maxCost}</td>
        <table>
        """.trimIndent()
    }
    
    return "<table border='1'>$header$rows</table>"
}