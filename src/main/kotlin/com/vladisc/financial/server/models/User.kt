package com.vladisc.financial.server.models

import org.jetbrains.exposed.sql.*

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)

    override val primaryKey = PrimaryKey(id)
}