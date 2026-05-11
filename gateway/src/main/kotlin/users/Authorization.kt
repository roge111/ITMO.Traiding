package com.trading.users

import com.trading.database.DataBaseManager
import org.mindrot.jbcrypt.BCrypt


class Authorization {
    private val db: DataBaseManager = DataBaseManager()

    init {
        db.connect()
    }

    fun authorization (login: String, password: String): Boolean {
         val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

        if (checkUser(login, hashedPassword)) {
            return true
        } else {
            return false
        }
         
    }

        fun checkUser(username: String, hashedPassword: String): Boolean {
        return try {
            val rs = db.query("SELECT 1 FROM users WHERE username = ? AND password_hash = ? LIMIT 1", username, hashedPassword)
            val exists = rs.next()
            println("Пользователь $username найден")
            rs.close()
            exists
            true
        } catch (e: Exception) {
            println("Ошибка при проверке пользователя: ${e.message}")
            false
        }
    }
}