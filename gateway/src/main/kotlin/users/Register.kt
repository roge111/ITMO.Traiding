package com.trading.users

import org.mindrot.jbcrypt.BCrypt
import com.trading.database.DataBaseManager

class Register {

    private val db = DataBaseManager()

    init {
        db.connect()
    }

    fun register(login: String, password: String): String {
        if (login.length !in 3..50) {
            return "Login must contain from 3 to 50 characters."
        }
        if (password.length < 6) {
            return "Password must contain at least 6 characters."
        }
        if (checkUser(login)) {
            return "The user is already registered."
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        
        db.execute(
            "INSERT INTO users (username, password_hash) VALUES (?, ?)",
            login, hashedPassword
        )

        return "User registered successfully."
    }

    fun checkUser(username: String): Boolean {
        return try {
            db.getConnection().prepareStatement(
                "SELECT 1 FROM users WHERE username = ? LIMIT 1"
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { result -> result.next() }
            }
        } catch (e: Exception) {
            println("Ошибка при проверке пользователя: ${e.message}")
            false
        }
    }
}
