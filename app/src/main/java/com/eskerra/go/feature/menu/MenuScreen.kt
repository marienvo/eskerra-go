package com.eskerra.go.feature.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stateless menu content for the hamburger overlay. Receives entries and reports taps through
 * [onItemClick]. It wraps its height (no [fillMaxSize]) so it sits naturally inside a bottom sheet.
 */
@Composable
fun MenuScreen(items: List<String>, onItemClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                text = "Menu",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
            )
        }
    }
}
