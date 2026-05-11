package com.trading.users

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.trading.users.*

fun Application.registerRoutes() {
    val register = Register()
    val auth = Authorization()
    
    routing {
        get("/register") {
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Регистрация</title>
            <style>
                body { font-family: Arial; margin: 50px; }
                input { margin: 5px; padding: 8px; width: 200px; }
                button { padding: 8px 20px; margin-top: 10px; }
                .error { color: red; }
            </style>
        </head>
        <body>
            <h2>Регистрация</h2>
            <form id="registerForm">
                <input type="text" id="login" placeholder="Логин" required><br>
                <input type="password" id="password" placeholder="Пароль" required><br>
                <button type="submit">Зарегистрироваться</button>
            </form>
            <div id="message"></div>
            
            <script>
                const form = document.getElementById('registerForm');
                form.onsubmit = async (event) => {
                    event.preventDefault();  // ← Отменяем стандартную отправку
                    
                    const login = document.getElementById('login').value;
                    const password = document.getElementById('password').value;
                    const msgDiv = document.getElementById('message');
                    
                    const response = await fetch('/api/register', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: 'login=' + encodeURIComponent(login) + '&password=' + encodeURIComponent(password)
                    });
                    
                    const message = await response.text();
                    
                    if (response.ok) {
                        console.log('Перенаправление на /authorization');
                        window.location.href = '/authorization';
                    } else {
                        msgDiv.className = 'error';
                        msgDiv.innerHTML = message;
                    }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
    call.respondText(html, ContentType.Text.Html)
}
        
        post("/api/register") {
            val params = call.receiveParameters()
            val login = params["login"]
            val password = params["password"]
            
            if (login.isNullOrBlank() || password.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing login or password")
                return@post
            }
            
            val result = register.register(login, password)
            
            if (result == "User registered successfully.") {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.BadRequest, result)
            }
        }
        
        get("/authorization") {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Авторизация</title>
                    <style>
                        body { font-family: Arial; margin: 50px; }
                        input { margin: 5px; padding: 8px; width: 200px; }
                        button { padding: 8px 20px; margin-top: 10px; }
                        .error { color: red; }
                    </style>
                </head>
                <body>
                    <h2>Авторизация</h2>
                    <form id="loginForm">
                        <input type="text" id="login" placeholder="Логин" required><br>
                        <input type="password" id="password" placeholder="Пароль" required><br>
                        <button type="submit">Войти</button>
                    </form>
                    <div id="message"></div>
                    
                    <script>
                        document.getElementById('loginForm').onsubmit = async (e) => {
                            e.preventDefault();
                            const login = document.getElementById('login').value;
                            const password = document.getElementById('password').value;
                            const msgDiv = document.getElementById('message');
                            
                            const response = await fetch('/api/login', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: 'login=' + encodeURIComponent(login) + '&password=' + encodeURIComponent(password)
                            });
                            
                            const message = await response.text();
                            
                            if (response.ok) {
                                window.location.href = '/quotes';
                            } else {
                                msgDiv.className = 'error';
                                msgDiv.innerHTML = message;
                            }
                        };
                    </script>
                </body>
                </html>
            """.trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }
        
        post("/api/login") {
            val params = call.receiveParameters()
            val login = params["login"]
            val password = params["password"]
            
            if (login.isNullOrBlank() || password.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing login or password")
                return@post
            }
            val result = auth.authorization(login, password)
            
            // Временная заглушка для проверки
            if (result) {
                println("Login successful")
                call.respond(HttpStatusCode.OK, "Login successful")
            } else {
                println("Invalid login or password")
                call.respond(HttpStatusCode.Unauthorized, "Invalid login or password")
            }
        }
    }
}

fun Application.module() {
    registerRoutes()
}