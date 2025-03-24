package com.vladisc.financial.server.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.vladisc.financial.server.models.UsersTable
import io.github.cdimascio.dotenv.dotenv

object DatabaseFactory {
    fun init() {
        val dotenv = dotenv()

        val url = "jdbc:postgresql://localhost:5050/postgres"
        val driver = "org.postgresql.Driver"
        val user = dotenv["DB_USER"]
        val password = dotenv["DB_PASSWORD"]

        Database.connect(url, driver, user, password)

        transaction {
            SchemaUtils.create(UsersTable)
        }
    }
}
