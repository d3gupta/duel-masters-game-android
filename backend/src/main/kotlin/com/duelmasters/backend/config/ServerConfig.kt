package com.duelmasters.backend.config

object ServerConfig {
    val host: String = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val port: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080

    val databaseUrl: String =
        System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/duel_masters"

    val databaseUser: String = System.getenv("DATABASE_USER") ?: "postgres"
    val databasePassword: String = System.getenv("DATABASE_PASSWORD") ?: "postgres"
}
