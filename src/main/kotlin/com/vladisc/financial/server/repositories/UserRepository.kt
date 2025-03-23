package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.SignupUser
import com.vladisc.financial.server.models.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate

class UserRepository {
    fun addUser(user: SignupUser): Boolean {
        return transaction {
            val inserted = UsersTable.insertIgnore {
                it[email] = user.email
                it[password] = BCrypt.hashpw(user.password, BCrypt.gensalt())
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[dateOfBirth] = LocalDate.parse(user.dateOfBirth)
            }
            inserted.insertedCount > 0
            // TODO: create a table
        }
    }

    fun findUser(email: String): ResultRow? {
        val usersList = transaction {
            UsersTable.selectAll().where { UsersTable.email eq email }.toList()
        }
        return if (usersList.isEmpty()) {
            null
        } else {
            usersList[0]
        }
    }

    fun findUserById(id: Int): ResultRow? {
        val usersList = transaction {
            UsersTable.selectAll().where { UsersTable.id eq id }.toList()
        }
        return if (usersList.isEmpty()) {
            null
        } else {
            usersList[0]
        }
    }

    fun updateUser(userId: Int, updates: UsersTable.(UpdateStatement) -> Unit): Boolean {
        try {
            return transaction {
                val updateStatement = UsersTable.update({ UsersTable.id eq userId }) {
                    updates(it) // Apply user-provided updates
                }
                return@transaction updateStatement != 0 // No fields were modified, return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteUser(userId: Int): Boolean {
        return try {
            transaction {
                val deletedRows = UsersTable.deleteWhere { id eq userId }
                deletedRows > 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
