package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
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
        val usersList = transaction {
            Users.selectAll().where { Users.username eq username }.toList()
        }
        return if (usersList.isEmpty()) {
            null
        } else {
            usersList[0]
        }
    }

    fun findUserById(id: Int): ResultRow? {
        val usersList = transaction {
            Users.selectAll().where { Users.id eq id }.toList()
        }
        return if (usersList.isEmpty()) {
            null
        } else {
            usersList[0]
        }
    }

    fun updateUser(userId: Int, updates: Users.(UpdateStatement) -> Unit): Boolean {
        try {
            return transaction {
                val updateStatement = Users.update({ Users.id eq userId }) {
                    updates(it) // Apply user-provided updates
                }
                return@transaction updateStatement != 0 // No fields were modified, return false
            }
        } catch (e: Exception) {
            return false
        }

    }
}
