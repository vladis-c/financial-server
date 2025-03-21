package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

//    fun findUser(username: String): Pair<Int, String>? {
//        return transaction {
//            Users.select { Users.username eq username }
//                .map { it[Users.id] to it[Users.passwordHash] }
//                .singleOrNull()
//        }
//    }
}
