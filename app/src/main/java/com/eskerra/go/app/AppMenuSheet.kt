package com.eskerra.go.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.eskerra.go.feature.menu.MenuEntry
import com.eskerra.go.feature.menu.MenuScreen
import kotlinx.coroutines.launch

/**
 * Hamburger menu rendered as a [ModalBottomSheet] overlay. It dismisses on scrim tap and Back
 * (handled by [ModalBottomSheet]); selecting an item animates the sheet closed, then navigates.
 * Opening or closing it never touches the NavHost, so the route underneath is preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppMenuSheet(
    items: List<MenuEntry>,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        MenuScreen(
            items = items,
            onItemClick = { item ->
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                    onItemClick(item)
                }
            }
        )
    }
}
