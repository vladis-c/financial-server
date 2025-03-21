package com.vladisc.financial.server.data

import com.typesafe.config.ConfigFactory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.vladisc.financial.server.models.Users
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.config.*

object DatabaseFactory {
    fun init() {
        val dotenv = dotenv()
        val config = HoconApplicationConfig(ConfigFactory.load())

        val url = "jdbc:postgresql://localhost:5050/postgres"
        val driver = "org.postgresql.Driver"
        val user = dotenv["DB_USER"] ?: config.property("ktor.database.user").getString()
        val password = dotenv["DB_PASSWORD"] ?: config.property("ktor.database.password").getString()

        Database.connect(url, driver, user, password)

        transaction {
            SchemaUtils.create(Users)
        }
    }
}
