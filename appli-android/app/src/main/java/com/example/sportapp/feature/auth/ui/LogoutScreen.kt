package com.example.sportapp.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.feature.auth.viewmodel.LogoutScreenViewModel
import kotlinx.coroutines.delay

@Composable
fun LogoutScreen(
    onLoggedOut: () -> Unit
) {
    val vm: LogoutScreenViewModel = hiltViewModel()
    val quote by vm.motivationalQuote

    // évite de rejouer si recomposition
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!done) {
            done = true
            vm.logout()

            // optionnel : petite pause pour laisser voir le feedback (sinon instant)
            delay(2000)

            onLoggedOut()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = appColors.primaryAction
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.auth_logout_progress),
                style = MaterialTheme.typography.titleMedium,
                color = appColors.primaryAction
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.auth_logout_bye),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.bgBottomNav
            )

            quote?.let { q ->
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "\"${q.text}\"",
                    fontSize = 14.sp,
                    color = appColors.textPrimary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(horizontal = 8.dp)
                )
                q.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        fontSize = 13.sp,
                        color = appColors.primaryAction,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
