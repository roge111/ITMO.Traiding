package com.trading.users

import com.trading.database.DataBaseManager
import org.mindrot.jbcrypt.BCrypt


class Authorization {
    private val db: DataBaseManager = DataBaseManager()

    init {
        db.connect()
    }

    fun authorization(login: String, password: String): Boolean {
        return checkUser(login, password)
    }

    private fun checkUser(username: String, password: String): Boolean {
        return try {
            db.getConnection().prepareStatement(
                "SELECT password_hash FROM users WHERE username = ? LIMIT 1"
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { result ->
                    result.next() && BCrypt.checkpw(password, result.getString("password_hash"))
                }
            }
        } catch (e: Exception) {
            println("Ошибка при проверке пользователя: ${e.message}")
            false
        }
    }
}
