package com.trading.users

import org.mindrot.jbcrypt.BCrypt
import com.trading.database.DataBaseManager

class Register {

    private val db = DataBaseManager()

    init {
        db.connect()
    }

    fun register(login: String, password: String): String {
        if (checkUser(login)) {
            return "The user is already registered."
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        
        db.execute(
            "INSERT INTO users (username, password_hash, balance) VALUES (?, ?, ?)",
            login, hashedPassword, 0
        )

        return "User registered successfully."
    }

    fun checkUser(username: String): Boolean {
        return try {
            val rs = db.query("SELECT 1 FROM users WHERE username = ? LIMIT 1", username)
            val exists = rs.next()
            rs.close()
            exists
        } catch (e: Exception) {
            println("Ошибка при проверке пользователя: ${e.message}")
            false
        }
    }
}