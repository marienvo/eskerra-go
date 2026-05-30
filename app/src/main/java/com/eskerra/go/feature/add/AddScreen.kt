package com.eskerra.go.feature.add

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stateless "new note" form. Form values and change callbacks are passed in; the
 * screen holds no state of its own. Pressing save simply invokes [onSave].
 * Nothing is persisted in this step.
 */
@Composable
fun AddScreen(
    title: String,
    body: String,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: (title: String, body: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Add note",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            label = { Text("Body") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Button(
            onClick = { onSave(title, body) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Save (not persisted yet)")
        }
    }
}
