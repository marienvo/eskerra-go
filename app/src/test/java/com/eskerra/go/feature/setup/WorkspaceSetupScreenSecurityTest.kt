package com.eskerra.go.feature.setup

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceSetupScreenSecurityTest {

    @Test
    fun credentialField_usesPasswordStyleInput() {
        assertEquals(
            PasswordVisualTransformation::class,
            WorkspaceSetupInputOptions.credentialVisualTransformation::class
        )
        assertEquals(
            KeyboardType.Password,
            WorkspaceSetupInputOptions.credentialKeyboardOptions.keyboardType
        )
        assertEquals(
            false,
            WorkspaceSetupInputOptions.credentialKeyboardOptions.autoCorrectEnabled
        )
    }
}
