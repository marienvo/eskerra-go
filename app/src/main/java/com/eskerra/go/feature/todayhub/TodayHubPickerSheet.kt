package com.eskerra.go.feature.todayhub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.ui.markdown.VaultMarkdownTokens
import com.eskerra.go.ui.theme.EskerraChromeTokens

/**
 * Bottom sheet that lets the user pick the active Today hub when more than one `Today.md` exists
 * (spec §11.1). Lists each hub's folder label and vault-relative path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayHubPickerSheet(
    hubs: List<TodayHubRef>,
    activeHubId: NoteId,
    onPickHub: (NoteId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EskerraChromeTokens.ModalBackground
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Choose a hub",
                color = EskerraChromeTokens.ModalTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            hubs.forEachIndexed { index, hub ->
                if (index > 0) {
                    HorizontalDivider(
                        color = EskerraChromeTokens.ListDivider,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                val isActive = hub.noteId == activeHubId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickHub(hub.noteId) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = hub.folderLabel,
                        color = if (isActive) {
                            VaultMarkdownTokens.ExternalSite
                        } else {
                            EskerraChromeTokens.ModalTitle
                        },
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        text = hub.noteId.value,
                        color = EskerraChromeTokens.Subtitle,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
