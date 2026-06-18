package com.duelmasters.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection

object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = ServerConfig.databaseUrl
            username = ServerConfig.databaseUser
            password = ServerConfig.databasePassword
            maximumPoolSize = 10
        }
        dataSource = HikariDataSource(config)
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    fun connection(): Connection = dataSource.connection
}
