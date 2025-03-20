package com.vladisc.financial.server.classes

import kotlinx.serialization.Serializable

enum class ErrorStatus {
    PARAMETER_MISSING, UNKNOWN_ERROR
}

@Serializable
data class Error (
    val errorStatus: ErrorStatus,
    val errorMessage: String?
)