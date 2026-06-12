package com.eskerra.go.core.model

sealed class VaultSettingsError(val message: String) {
    class ParseError(message: String) : VaultSettingsError(message)
    class NotFound(message: String) : VaultSettingsError(message)
    class ValidationError(message: String) : VaultSettingsError(message)
    class IoError(message: String) : VaultSettingsError(message)
}

class VaultSettingsException(val error: VaultSettingsError) : Exception(error.message)
