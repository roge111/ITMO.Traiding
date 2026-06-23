package com.trading.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TradingApp() } }
    }
}

private sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data class Quotes(val username: String) : Screen
    data class Details(val username: String, val quote: Quote) : Screen
}

@Composable
private fun TradingApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    when (val current = screen) {
        Screen.Login -> CredentialsScreen(
            title = "Вход",
            action = "Войти",
            switchText = "Нет аккаунта? Зарегистрироваться",
            onSubmit = ApiClient::login,
            onSuccess = { screen = Screen.Quotes(it) },
            onSwitch = { screen = Screen.Register }
        )
        Screen.Register -> CredentialsScreen(
            title = "Регистрация",
            action = "Создать аккаунт",
            switchText = "Уже есть аккаунт? Войти",
            onSubmit = ApiClient::register,
            onSuccess = { screen = Screen.Quotes(it) },
            onSwitch = { screen = Screen.Login }
        )
        is Screen.Quotes -> QuotesScreen(
            username = current.username,
            onQuoteClick = { screen = Screen.Details(current.username, it) },
            onLogout = { screen = Screen.Login }
        )
        is Screen.Details -> QuoteDetailsScreen(
            username = current.username,
            quote = current.quote,
            onBack = { screen = Screen.Quotes(current.username) }
        )
    }
}

@Composable
private fun CredentialsScreen(
    title: String,
    action: String,
    switchText: String,
    onSubmit: suspend (String, String) -> ApiResult<Unit>,
    onSuccess: (String) -> Unit,
    onSwitch: () -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(login, { login = it }, label = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !loading,
            onClick = {
                val username = login.trim()
                if (username.isBlank() || password.isBlank()) {
                    error = "Заполните логин и пароль"
                    return@Button
                }
                scope.launch {
                    loading = true
                    error = null
                    when (val result = onSubmit(username, password)) {
                        is ApiResult.Success -> onSuccess(username)
                        is ApiResult.Error -> error = result.message
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Загрузка..." else action) }
        TextButton(onClick = onSwitch, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(switchText)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuotesScreen(
    username: String,
    onQuoteClick: (Quote) -> Unit,
    onLogout: () -> Unit
) {
    var quotes by remember { mutableStateOf<List<Quote>>(emptyList()) }
    var account by remember { mutableStateOf<AccountSummary?>(null) }
    var holdings by remember { mutableStateOf<List<Holding>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun loadData(showLoader: Boolean) {
        if (showLoader) loading = true
        error = null
        ApiClient.tickMarket()
        when (val result = ApiClient.getQuotes()) {
            is ApiResult.Success -> quotes = result.value
            is ApiResult.Error -> error = result.message
        }
        when (val result = ApiClient.getAccount(username)) {
            is ApiResult.Success -> account = result.value
            is ApiResult.Error -> error = result.message
        }
        when (val result = ApiClient.getPortfolio(username)) {
            is ApiResult.Success -> holdings = result.value
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }

    fun load(showLoader: Boolean = false) {
        scope.launch { loadData(showLoader) }
    }

    LaunchedEffect(username) {
        loadData(showLoader = true)
        while (isActive) {
            delay(1_000)
            loadData(showLoader = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ITMO Trading") },
                actions = {
                    TextButton(onClick = { load(showLoader = false) }) { Text("Обновить") }
                    TextButton(onClick = onLogout) { Text("Выйти") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            AccountCard(username, account, holdings)
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (holdings.isNotEmpty()) {
                        item { Text("Портфель", style = MaterialTheme.typography.titleMedium) }
                        items(holdings, key = { "holding-${it.quoteName}" }) { holding ->
                            val quote = quotes.firstOrNull { it.name == holding.quoteName } ?: holding.asQuote()
                            HoldingCard(
                                holding = holding,
                                modifier = Modifier.clickable { onQuoteClick(quote) }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Котировки", style = MaterialTheme.typography.titleMedium)
                        Text("Обновление каждую секунду", style = MaterialTheme.typography.bodySmall)
                    }
                    items(quotes, key = { "quote-${it.name}" }) { quote ->
                        QuoteCard(quote, modifier = Modifier.clickable { onQuoteClick(quote) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(username: String, account: AccountSummary?, holdings: List<Holding>) {
    val portfolioProfit = holdings.sumOf { it.profit }
    val portfolioInvested = holdings.sumOf { it.avgPrice * it.quantity }
    val profitPercent = if (portfolioInvested == 0.0) 0.0 else portfolioProfit / portfolioInvested * 100.0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Пользователь: $username")
            Spacer(Modifier.height(6.dp))
            Text("Свободные деньги: ${money(account?.balance ?: 0.0)}")
            Text("Стоимость портфеля: ${money(account?.portfolioValue ?: 0.0)}")
            Text(
                text = "P/L портфеля: ${signedMoney(portfolioProfit)} (${signedPercent(profitPercent)})",
                color = profitColor(portfolioProfit)
            )
        }
    }
}

@Composable
private fun HoldingCard(holding: Holding, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${holding.quoteName}: ${holding.quantity} шт.")
            Text("Средняя: ${money(holding.avgPrice)} · Текущая: ${money(holding.currentPrice)}")
            Text("Вложено: ${money(holding.avgPrice * holding.quantity)} · Сейчас: ${money(holding.marketValue)}")
            Text(
                text = "P/L: ${signedMoney(holding.profit)} (${signedPercent(holdingProfitPercent(holding))})",
                color = profitColor(holding.profit)
            )
            Text("Нажмите, чтобы открыть график", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuoteCard(quote: Quote, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(quote.name, style = MaterialTheme.typography.titleMedium)
                Text(money(quote.price))
            }
            Text(
                text = "%+.2f%%".format(quote.percentageChange),
                color = profitColor(quote.percentageChange)
            )
            Text("Минимум: ${money(quote.minCost.toDouble())} · Максимум: ${money(quote.maxCost.toDouble())}")
            Text("Нажмите, чтобы открыть график и торговлю", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuoteDetailsScreen(username: String, quote: Quote, onBack: () -> Unit) {
    var currentQuote by remember(quote.name) { mutableStateOf(quote) }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var holding by remember(quote.name) { mutableStateOf<Holding?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadDetails() {
        ApiClient.tickMarket(quote.name)
        when (val result = ApiClient.getQuotes()) {
            is ApiResult.Success -> currentQuote = result.value.firstOrNull { it.name == quote.name } ?: currentQuote
            is ApiResult.Error -> message = result.message
        }
        when (val result = ApiClient.getPortfolio(username)) {
            is ApiResult.Success -> holding = result.value.firstOrNull { it.quoteName == quote.name }
            is ApiResult.Error -> message = result.message
        }
        when (val result = ApiClient.getCandles(quote.name)) {
            is ApiResult.Success -> candles = result.value
            is ApiResult.Error -> message = result.message
        }
    }

    fun loadHistory() {
        scope.launch { loadDetails() }
    }

    fun trade(side: String) {
        val amount = quantity.toIntOrNull()
        if (amount == null || amount <= 0) {
            message = "Введите положительное количество"
            return
        }
        scope.launch {
            loading = true
            when (val result = ApiClient.trade(username, quote.name, amount, side)) {
                is ApiResult.Success -> {
                    message = "${result.value.message}. Баланс: ${money(result.value.balance)}"
                    loadDetails()
                }
                is ApiResult.Error -> message = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(quote.name) {
        while (isActive) {
            loadDetails()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quote.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { QuoteCard(currentQuote) }
            item { PositionCard(holding) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("График цены", style = MaterialTheme.typography.titleMedium)
                        CandleChart(candles)
                        Text("Автообновление каждую секунду", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Торговля", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter(Char::isDigit) },
                            label = { Text("Количество") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !loading, onClick = { trade("BUY") }, modifier = Modifier.weight(1f)) {
                                Text("Купить")
                            }
                            OutlinedButton(enabled = !loading, onClick = { trade("SELL") }, modifier = Modifier.weight(1f)) {
                                Text("Продать")
                            }
                        }
                        message?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandleChart(candles: List<Candle>) {
    if (candles.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("Недостаточно данных для графика")
        }
        return
    }
    val minPrice = candles.minOf { it.low }
    val maxPrice = candles.maxOf { it.high }
    val padding = max(1.0, (maxPrice - minPrice) * 0.08)
    val chartMin = minPrice - padding
    val chartMax = maxPrice + padding
    val range = max(1.0, chartMax - chartMin)
    val growingColor = Color(0xFF16813D)
    val fallingColor = MaterialTheme.colorScheme.error
    val closeLineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(vertical = 12.dp)) {
        val slot = size.width / candles.size
        val bodyWidth = min(slot * 0.55f, 18f).coerceAtLeast(5f)

        fun y(price: Double): Float {
            return size.height - ((price - chartMin) / range * size.height).toFloat()
        }

        repeat(4) { line ->
            val y = size.height * line / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        val closePoints = mutableListOf<Offset>()
        candles.forEachIndexed { index, candle ->
            val x = slot * index + slot / 2f
            val openY = y(candle.open)
            val closeY = y(candle.close)
            val highY = y(candle.high)
            val lowY = y(candle.low)
            val color = if (candle.close >= candle.open) growingColor else fallingColor
            val top = min(openY, closeY)
            val height = max(3f, kotlin.math.abs(closeY - openY))

            drawLine(color, Offset(x, highY), Offset(x, lowY), strokeWidth = 3f)
            drawRect(
                color = color,
                topLeft = Offset(x - bodyWidth / 2f, top),
                size = Size(bodyWidth, height)
            )
            closePoints.add(Offset(x, closeY))
        }

        for (index in 0 until closePoints.lastIndex) {
            drawLine(closeLineColor, closePoints[index], closePoints[index + 1], strokeWidth = 2.5f)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("min ${money(minPrice)}", style = MaterialTheme.typography.bodySmall)
        Text("max ${money(maxPrice)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PositionCard(holding: Holding?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Моя позиция", style = MaterialTheme.typography.titleMedium)
            if (holding == null) {
                Text("Акций нет")
                Text("После покупки здесь появятся количество и P/L", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Количество: ${holding.quantity} шт.")
                Text("Средняя цена: ${money(holding.avgPrice)}")
                Text("Текущая стоимость: ${money(holding.marketValue)}")
                Text(
                    text = "P/L: ${signedMoney(holding.profit)} (${signedPercent(holdingProfitPercent(holding))})",
                    color = profitColor(holding.profit)
                )
            }
        }
    }
}

private fun money(value: Double): String = "%,.2f ₽".format(value)

private fun signedMoney(value: Double): String = "%+,.2f ₽".format(value)

private fun signedPercent(value: Double): String = "%+.2f%%".format(value)

private fun holdingProfitPercent(holding: Holding): Double {
    val invested = holding.avgPrice * holding.quantity
    return if (invested == 0.0) 0.0 else holding.profit / invested * 100.0
}

private fun Holding.asQuote(): Quote {
    val price = currentPrice
    return Quote(
        name = quoteName,
        price = price,
        percentageChange = holdingProfitPercent(this),
        minCost = price.toLong(),
        maxCost = price.toLong()
    )
}

@Composable
private fun profitColor(value: Double): Color {
    return if (value >= 0) Color(0xFF16813D) else MaterialTheme.colorScheme.error
}
