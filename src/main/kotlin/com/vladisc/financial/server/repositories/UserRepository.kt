package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

class UserRepository {
    fun addUser(username: String, password: String): Boolean {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

        return transaction {
            val inserted = Users.insertIgnore {
                it[Users.username] = username
                it[passwordHash] = hashedPassword
            }
            inserted.insertedCount > 0
        }
    }

    fun findUser(username: String): ResultRow? {
        return transaction {
            Users.selectAll()
                .toList()
                .find { it[Users.username] == username }
        }
    }
}
