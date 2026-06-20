package com.trading.android

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8080"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun login(username: String, password: String, callback: (Boolean, String) -> Unit) {
        val formBody = FormBody.Builder()
            .add("login", username)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/login")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                callback(response.isSuccessful, body)
            }
        })
    }

    fun register(username: String, password: String, callback: (Boolean, String) -> Unit) {
        val formBody = FormBody.Builder()
            .add("login", username)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/register")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                callback(response.isSuccessful, body)
            }
        })
    }

    fun getQuotes(callback: (List<Quote>?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/quotes")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(null, "HTTP ${response.code}")
                    return
                }
                val body = response.body?.string() ?: ""
                try {
                    val arr = JSONArray(body)
                    val quotes = mutableListOf<Quote>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        quotes.add(
                            Quote(
                                name = obj.getString("name"),
                                price = obj.getDouble("price"),
                                percentageChange = obj.getDouble("percentageChange"),
                                minCost = obj.getLong("minCost"),
                                maxCost = obj.getLong("maxCost")
                            )
                        )
                    }
                    callback(quotes, null)
                } catch (e: Exception) {
                    callback(null, "Parse error: ${e.message}")
                }
            }
        })
    }
}