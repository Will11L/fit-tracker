package com.example.sportapp.feature.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.conversations.viewmodel.ConversationsViewModel

/**
 * Écran Conversations = agent IA conversationnel in-app (Cas C Phase 2 MCP).
 *
 * Fil de bulles (LazyColumn) + champ de saisie + bouton d'envoi. Réponse non
 * streamée au MVP : un indicateur de chargement s'affiche pendant que le
 * backend orchestre la boucle tool-use. Historique en mémoire (VM).
 */
@Composable
fun ConversationsScreen(
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll vers le bas à chaque nouveau message ou pendant l'envoi.
    LaunchedEffect(messages.size, isSending) {
        val lastIndex = messages.size - if (isSending) 0 else 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex.coerceAtLeast(0))
    }

    val send: () -> Unit = {
        val text = input
        if (text.isNotBlank() && !isSending) {
            viewModel.sendMessage(text)
            input = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.conversations_title),
                color = appColors.textPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_refresh,
                tint = appColors.textSecondary,
                clickable = messages.isNotEmpty() && !isSending,
                onClick = { viewModel.clearConversation() },
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !isSending) {
                Text(
                    text = stringResource(R.string.agent_empty_hint),
                    color = appColors.textTertiary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(messages) { _, bubble ->
                        MessageBubble(bubble)
                    }
                    if (isSending) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = stringResource(R.string.agent_input_placeholder),
                singleLine = false,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            ActionIconButton(
                iconRes = R.drawable.ic_arrow_upward_alt,
                tint = appColors.textOnSelected,
                customBackgroundColor = appColors.primaryAction,
                clickable = input.isNotBlank() && !isSending,
                onClick = send,
            )
        }
    }
}

@Composable
private fun MessageBubble(bubble: ConversationsViewModel.ChatBubble) {
    val isUser = bubble.role == ConversationsViewModel.ChatBubble.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) appColors.selectedFill else appColors.bgSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = bubble.text,
                color = if (isUser) appColors.textOnSelected else appColors.textPrimary,
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(appColors.bgSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = appColors.primaryAction,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.agent_thinking),
                    color = appColors.textTertiary,
                )
            }
        }
    }
}
