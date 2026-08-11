package com.example.sportapp.feature.quotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.SheetAction
import com.example.sportapp.designsystem.common_components.StyledSearchField
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.redMedium

@Composable
fun QuotesScreen(
    onBack: () -> Unit,
    viewModel: QuotesViewModel = hiltViewModel()
) {
    val quotes by viewModel.quotes.collectAsState()

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var showAddDialog by remember { mutableStateOf(false) }
    var quoteToEdit by remember { mutableStateOf<Quote?>(null) }
    var quoteToDelete by remember { mutableStateOf<Quote?>(null) }
    var quoteForOptions by remember { mutableStateOf<Quote?>(null) }

    // Filtre local : la recherche matche le texte OU l'auteur (insensible casse).
    val query = searchQuery.text.trim()
    val filtered = if (query.isBlank()) quotes
        else quotes.filter {
            it.text.contains(query, ignoreCase = true) ||
                (it.author ?: "").contains(query, ignoreCase = true)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        // Header : back (gauche) + titre centré.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(appColors.bgSurface),
        ) {
            ActionIconButton(
                iconRes = R.drawable.ic_arrow_left_alt,
                hasBackground = false,
                tint = appColors.textPrimary,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            )
            Text(
                text = stringResource(R.string.quotes_title),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section "Actions" : recherche (filtre) + ajout.
            TitledDivider(stringResource(R.string.quotes_section_actions))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledSearchField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholderText = stringResource(R.string.quotes_search_hint),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ActionIconButton(
                    iconRes = R.drawable.ic_add,
                    tint = appColors.textOnSelected,
                    onClick = { showAddDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section liste : compteur « N citations » (reflète le filtre).
            TitledDivider(
                pluralStringResource(R.plurals.quotes_count, filtered.size, filtered.size)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyListRow(
                            text = stringResource(
                                if (query.isBlank()) R.string.quotes_empty
                                else R.string.quotes_no_results
                            ),
                            iconRes = R.drawable.ic_rounded_book,
                        )
                    }
                } else {
                    items(filtered, key = { it.uuid }) { quote ->
                        QuoteRow(
                            quote = quote,
                            onMoreClick = { quoteForOptions = quote }
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet d'options (Edit / Delete) déclenchée par le ⋮ d'une row.
    quoteForOptions?.let { quote ->
        OptionsBottomSheet(
            title = stringResource(R.string.quotes_options_title),
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.common_edit),
                    iconRes = R.drawable.ic_rounded_edit,
                    color = blueMedium,
                    onClick = {
                        quoteForOptions = null
                        quoteToEdit = quote
                    }
                ),
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        quoteForOptions = null
                        quoteToDelete = quote
                    }
                ),
            ),
            onDismissRequest = { quoteForOptions = null }
        )
    }

    if (showAddDialog) {
        QuoteFormDialog(
            title = stringResource(R.string.quotes_add_title),
            confirmText = stringResource(R.string.common_add),
            onConfirm = { text, author ->
                viewModel.addQuote(text, author)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    quoteToEdit?.let { quote ->
        QuoteFormDialog(
            title = stringResource(R.string.quotes_edit_title),
            confirmText = stringResource(R.string.common_save),
            initialText = quote.text,
            initialAuthor = quote.author ?: "",
            onConfirm = { text, author ->
                viewModel.updateQuote(quote, text, author)
                quoteToEdit = null
            },
            onDismiss = { quoteToEdit = null }
        )
    }

    quoteToDelete?.let { quote ->
        ConfirmationDialog(
            title = stringResource(R.string.quotes_delete_title),
            message = stringResource(R.string.quotes_delete_message),
            confirmButtonText = stringResource(R.string.common_delete),
            dismissButtonText = stringResource(R.string.common_cancel),
            onConfirm = {
                viewModel.deleteQuote(quote)
                quoteToDelete = null
            },
            onDismiss = { quoteToDelete = null }
        )
    }
}

@Composable
private fun QuoteRow(
    quote: Quote,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bgRecessed, shape = MaterialTheme.shapes.small)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "\"${quote.text}\"",
                color = appColors.textPrimary,
                fontSize = 15.sp,
            )
            if (!quote.author.isNullOrBlank()) {
                Text(
                    text = "— ${quote.author}",
                    color = appColors.primaryAction,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        ActionIconButton(
            iconRes = R.drawable.ic_rounded_more_vert,
            tint = appColors.textPrimary,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun QuoteFormDialog(
    title: String,
    confirmText: String,
    onConfirm: (text: String, author: String?) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
    initialAuthor: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    var author by remember { mutableStateOf(initialAuthor) }

    FormDialog(
        title = title,
        confirmText = confirmText,
        confirmEnabled = text.isNotBlank(),
        disabledReason = if (text.isBlank()) stringResource(R.string.quotes_error_text_required) else null,
        onConfirm = { onConfirm(text, author) },
        onDismiss = onDismiss,
    ) {
        CustomTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.quotes_field_text),
            placeholder = stringResource(R.string.quotes_field_text_hint),
            singleLine = false,
        )
        CustomTextField(
            value = author,
            onValueChange = { author = it },
            label = stringResource(R.string.quotes_field_author),
            placeholder = stringResource(R.string.quotes_field_author_hint),
        )
    }
}
