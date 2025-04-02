package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.User
import com.vladisc.financial.server.models.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate

class UserRepository {
    fun addUser(user: User): Boolean {
        return transaction {
            val inserted = UsersTable.insertIgnore {
                if (!user.email.isNullOrBlank()) {
                    it[email] = user.email
                }
                it[password] = BCrypt.hashpw(user.password, BCrypt.gensalt())
                if (!user.firstName.isNullOrBlank()) {
                    it[firstName] = user.firstName
                }
                if (!user.lastName.isNullOrBlank()) {
                    it[lastName] = user.lastName
                }
                if (!user.dateOfBirth.isNullOrBlank()) {
                    it[dateOfBirth] = LocalDate.parse(user.dateOfBirth)
                }
                if(!user.company.isNullOrBlank()) {
                    it[company] = user.company
                }

            }
            inserted.insertedCount > 0
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

    fun updateUser(userId: Int, user: User, currentPassword: String?): Boolean {
        try {
            return transaction {
                val updateStatement = UsersTable.update({ UsersTable.id eq userId }) {
                    if (user.email != null) {
                        it[email] = user.email
                    }
                    if (user.newPassword != null && user.password != null && currentPassword != null) {
                        if (BCrypt.checkpw(user.password, currentPassword)) {
                            it[password] = BCrypt.hashpw(user.newPassword, BCrypt.gensalt())
                        }
                    }
                    if (user.firstName != null) {
                        it[firstName] = user.firstName
                    }
                    if (user.lastName != null) {
                        it[lastName] = user.lastName
                    }
                    if (user.dateOfBirth != null) {
                        it[dateOfBirth] = LocalDate.parse(user.dateOfBirth)
                    }
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
