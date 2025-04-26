package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val password = varchar("password", 255).check { it.charLength() greaterEq 4 }
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
    val dateOfBirth = date("date_of_birth")
    val company = varchar("company_name", 255)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class User(
    val uid: String? = null,
    val email: String? = null,
    val password: String? = null,
    val newPassword: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
    val company: String? = null,
)


@Serializable
data class UserCredentials(val email: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String?)