package com.trading.android

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object ApiClient {
    private val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun login(username: String, password: String): ApiResult<Unit> {
        return submitCredentials("/api/login", username, password)
    }

    suspend fun register(username: String, password: String): ApiResult<Unit> {
        return submitCredentials("/api/register", username, password)
    }

    private suspend fun submitCredentials(
        path: String,
        username: String,
        password: String
    ): ApiResult<Unit> {
        val formBody = FormBody.Builder()
            .add("login", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(formBody)
            .build()
        return execute(request) { response ->
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
        }
    }

    suspend fun getQuotes(): ApiResult<List<Quote>> {
        val request = Request.Builder()
            .url("$baseUrl/api/quotes")
            .get()
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error("HTTP ${response.code}")
            } else {
                try {
                    val json = JSONArray(response.body?.string().orEmpty())
                    val quotes = buildList {
                        for (index in 0 until json.length()) {
                            val item = json.getJSONObject(index)
                            add(
                            Quote(
                                name = item.getString("name"),
                                price = item.getDouble("price"),
                                percentageChange = item.getDouble("percentageChange"),
                                minCost = item.getLong("minCost"),
                                maxCost = item.getLong("maxCost")
                            )
                            )
                        }
                    }
                    ApiResult.Success(quotes)
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный ответ сервера: ${e.message}")
                }
            }
        }
    }

    suspend fun getAccount(username: String): ApiResult<AccountSummary> {
        val request = Request.Builder()
            .url("$baseUrl/api/account?login=${username.urlPart()}")
            .get()
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
            } else {
                try {
                    val item = JSONObject(response.body?.string().orEmpty())
                    ApiResult.Success(
                        AccountSummary(
                            username = item.getString("username"),
                            balance = item.getDouble("balance"),
                            portfolioValue = item.getDouble("portfolioValue"),
                            totalAssets = item.getDouble("totalAssets")
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный account: ${e.message}")
                }
            }
        }
    }

    suspend fun getPortfolio(username: String): ApiResult<List<Holding>> {
        val request = Request.Builder()
            .url("$baseUrl/api/portfolio?login=${username.urlPart()}")
            .get()
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
            } else {
                try {
                    val json = JSONArray(response.body?.string().orEmpty())
                    ApiResult.Success(buildList {
                        for (index in 0 until json.length()) add(json.getJSONObject(index).toHolding())
                    })
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный portfolio: ${e.message}")
                }
            }
        }
    }

    suspend fun getHistory(quoteName: String): ApiResult<List<PricePoint>> {
        val request = Request.Builder()
            .url("$baseUrl/api/quotes/${quoteName.urlPart()}/history")
            .get()
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
            } else {
                try {
                    val json = JSONArray(response.body?.string().orEmpty())
                    ApiResult.Success(buildList {
                        for (index in 0 until json.length()) {
                            val item = json.getJSONObject(index)
                            add(PricePoint(item.getDouble("price"), item.getString("timestamp")))
                        }
                    })
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный history: ${e.message}")
                }
            }
        }
    }

    suspend fun getCandles(quoteName: String): ApiResult<List<Candle>> {
        val request = Request.Builder()
            .url("$baseUrl/api/quotes/${quoteName.urlPart()}/candles")
            .get()
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
            } else {
                try {
                    val json = JSONArray(response.body?.string().orEmpty())
                    ApiResult.Success(buildList {
                        for (index in 0 until json.length()) {
                            val item = json.getJSONObject(index)
                            add(
                                Candle(
                                    open = item.getDouble("open"),
                                    high = item.getDouble("high"),
                                    low = item.getDouble("low"),
                                    close = item.getDouble("close"),
                                    timestamp = item.getString("timestamp")
                                )
                            )
                        }
                    })
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный candles: ${e.message}")
                }
            }
        }
    }

    suspend fun tickMarket(quoteName: String? = null): ApiResult<Unit> {
        val form = FormBody.Builder().apply {
            if (!quoteName.isNullOrBlank()) add("quoteName", quoteName)
        }.build()
        val request = Request.Builder()
            .url("$baseUrl/api/market/tick")
            .post(form)
            .build()
        return execute(request) { response ->
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
        }
    }

    suspend fun trade(
        username: String,
        quoteName: String,
        quantity: Int,
        side: String
    ): ApiResult<TradeResult> {
        val formBody = FormBody.Builder()
            .add("login", username)
            .add("quoteName", quoteName)
            .add("quantity", quantity.toString())
            .add("side", side)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/api/trade")
            .post(formBody)
            .build()
        return execute(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Error(response.body?.string().orEmpty().ifBlank { "HTTP ${response.code}" })
            } else {
                try {
                    val item = JSONObject(response.body?.string().orEmpty())
                    ApiResult.Success(
                        TradeResult(
                            message = item.getString("message"),
                            balance = item.getDouble("balance"),
                            holding = item.optJSONObject("holding")?.toHolding()
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Error("Некорректный trade: ${e.message}")
                }
            }
        }
    }

    private suspend fun <T> execute(
        request: Request,
        transform: (Response) -> ApiResult<T>
    ): ApiResult<T> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(ApiResult.Error("Ошибка сети: ${e.message}"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (continuation.isActive) continuation.resume(transform(it))
                }
            }
        })
    }
}

private fun JSONObject.toHolding(): Holding {
    return Holding(
        quoteName = getString("quoteName"),
        quantity = getInt("quantity"),
        avgPrice = getDouble("avgPrice"),
        currentPrice = getDouble("currentPrice"),
        marketValue = getDouble("marketValue"),
        profit = getDouble("profit")
    )
}

private fun String.urlPart(): String = HttpUrl.Builder()
    .scheme("http")
    .host("localhost")
    .addPathSegment(this)
    .build()
    .encodedPath
    .removePrefix("/")

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}
