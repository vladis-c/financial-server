package com.vladisc.financial.server.classes

import kotlinx.serialization.Serializable

@Serializable
data class User (
    val name: String,
    val id: Int?
)