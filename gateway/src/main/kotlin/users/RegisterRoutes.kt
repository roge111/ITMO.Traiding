package com.trading

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*  // ← добавить этот импорт
import io.ktor.server.routing.*
import io.ktor.http.*
import com.trading.users.Register

fun Application.registerRoutes() {
    val register = Register()
    
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
                        .success { color: green; }
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
                        document.getElementById('registerForm').onsubmit = async (e) => {
                            e.preventDefault();
                            const login = document.getElementById('login').value;
                            const password = document.getElementById('password').value;
                            
                            const response = await fetch('/api/register', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: 'login=' + encodeURIComponent(login) + '&password=' + encodeURIComponent(password)
                            });
                            
                            const message = await response.text();
                            const msgDiv = document.getElementById('message');
                            if (response.ok) {
                                msgDiv.className = 'success';
                                msgDiv.innerHTML = '✓ ' + message;
                                document.getElementById('registerForm').reset();
                            } else {
                                msgDiv.className = 'error';
                                msgDiv.innerHTML = '✗ ' + message;
                            }
                        };
                    </script>
                </body>
                </html>
            """.trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }
        
        post("/api/register") {
            // Используем receiveParameters() вместо parameters
            val params = call.receiveParameters()
            val login = params["login"]
            val password = params["password"]
            
            if (login.isNullOrBlank() || password.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing login or password")
                return@post
            }
            
            val result = register.register(login, password)
            
            if (result.contains("successfully")) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.BadRequest, result)
            }
        }
    }
}

fun Application.module() {
    registerRoutes()
}