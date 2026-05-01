package com.trading.database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

// Класс предназначен для управления соединением к БД
class DataBaseManager {
    private lateinit var connection: Connection
    
    fun connect() {
        val url = "jdbc:postgresql://localhost:5432/itmo_traiding_system"
        val user = "postgres"
        val password = "Gb%v5oVA"
        
        connection = DriverManager.getConnection(url, user, password)
        println("Подключение к БД установлено")
    }

    fun disconnect() {
        connection.close()
        println("Подключение к БД закрыто")
    }

    fun getConnection(): Connection = connection


    fun query(sql: String, vararg params: Any): ResultSet {
        val stmt: PreparedStatement = connection.prepareStatement(sql)
        params.forEachIndexed { index, param ->
            stmt.setObject(index + 1, param)
        }
        return stmt.executeQuery()
    }
    

}