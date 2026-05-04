package com.trading.users  // ← исправлено: packege → package

import org.mindrot.jbcrypt.BCrypt  // ← исправлено: jBCrypt → jbcrypt (всё маленькими)
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
        
        val connection = db.getConnection()
        db.execute(
            "INSERT INTO users (username, password_hash, balance) VALUES (?, ?, ?)",
            login, hashedPassword, 0
        )

        return "User registered successfully."
    }

    fun checkUser(login: String): Boolean {
        return try {
            val connection = db.getConnection()
            val stmt = connection.prepareStatement("SELECT 1 FROM users WHERE username = ? LIMIT 1")
            stmt.setString(1, login)
            val rs = stmt.executeQuery()
            val exists = rs.next()
            rs.close()
            stmt.close()
            exists
        } catch (e: Exception) {
            println("Ошибка при проверке пользователя: ${e.message}")
            false
        }
    }
}