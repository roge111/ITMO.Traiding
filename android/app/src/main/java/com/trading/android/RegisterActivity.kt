package com.trading.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please fill all fields"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnRegister.isEnabled = false
            btnRegister.text = "Loading..."

            ApiClient.register(username, password) { success, message ->
                runOnUiThread {
                    btnRegister.isEnabled = true
                    btnRegister.text = getString(R.string.register_btn)
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

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}