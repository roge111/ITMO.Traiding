package com.trading.android

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class QuotesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quotes)

        val rvQuotes = findViewById<RecyclerView>(R.id.rvQuotes)
        val tvError = findViewById<TextView>(R.id.tvError)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        rvQuotes.layoutManager = LinearLayoutManager(this)

        loadQuotes(rvQuotes, tvError, progressBar)
    }

    private fun loadQuotes(
        rvQuotes: RecyclerView,
        tvError: TextView,
        progressBar: ProgressBar
    ) {
        progressBar.visibility = android.view.View.VISIBLE
        tvError.visibility = android.view.View.GONE

        ApiClient.getQuotes { quotes, error ->
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                if (error != null) {
                    tvError.text = error
                    tvError.visibility = android.view.View.VISIBLE
                    Snackbar.make(rvQuotes, error, Snackbar.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (quotes.isNullOrEmpty()) {
                    tvError.text = getString(R.string.no_quotes)
                    tvError.visibility = android.view.View.VISIBLE
                    return@runOnUiThread
                }
                rvQuotes.adapter = QuotesAdapter(quotes)
            }
        }
    }
}