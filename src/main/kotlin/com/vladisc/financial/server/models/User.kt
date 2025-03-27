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

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class SignupUser(
    val email: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val password: String
)

@Serializable
data class User(
    val email: String,
    val id: Int?,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val password: String?
    )

@Serializable
data class PartialUser(
    val email: String? = null,
    val id: Int? = null,
    val newPassword: String? = null,
    val oldPassword: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
)


@Serializable
data class UserCredentials(val email: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String?)