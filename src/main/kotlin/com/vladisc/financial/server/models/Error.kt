package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable

enum class ErrorStatus {
    PARAMETER_MISSING, GENERIC_ERROR
}

@Serializable
data class Error (
    val errorStatus: ErrorStatus,
    val errorMessage: String?
)