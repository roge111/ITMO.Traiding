package com.trading.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please fill all fields"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnLogin.isEnabled = false
            btnLogin.text = "Loading..."

            ApiClient.login(username, password) { success, message ->
                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.login_btn)
                    if (success) {
                        startActivity(Intent(this, QuotesActivity::class.java))
                        finish()
                    } else {
                        tvError.text = message
                        tvError.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}