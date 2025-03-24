package com.vladisc.financial.server.plugins

import kotlinx.serialization.Serializable

enum class ErrorRoutingStatus {
    PARAMETER_MISSING, GENERIC_ERROR, CONFLICT, NOT_FOUND, UNAUTHORIZED
}

@Serializable
data class ErrorRouting(
    val errorStatus: ErrorRoutingStatus,
    val errorMessage: String?
)