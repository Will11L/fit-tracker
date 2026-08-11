package com.example.sportapp.designsystem.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightBlue
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium

/**
 * Footer du Drawer : barre de statut en bas de [DrawerContent].
 *
 * Affiche : texte "Sync: X" a gauche (flexible : il cede la place aux icones,
 * jamais l'inverse), puis groupees a droite :
 * - icone signal reseau (vert si connecte, rouge sinon)
 * - icone/badge sync (BadgedBox + [totalPending]) -> tap declenche [onSyncClick]
 * - icone WebSocket (vert si connecte, orange sinon) -> tap (uniquement si
 *   deconnecte) declenche [onWsRestartClick]
 */
@Composable
fun DrawerFooter(
    lastSyncText: String,
    isConnected: Boolean,
    hasUnsynced: Boolean,
    totalPending: Int,
    isWsConnected: Boolean,
    onSyncClick: () -> Unit,
    onWsRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.drawer_last_sync, lastSyncText),
            color = appColors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        ActionIconButton(
            iconRes = if (isConnected) R.drawable.ic_baseline_signal_cellular_alt else R.drawable.ic_rounded_signal_cellular_off,
            tint = if (isConnected) mediumGreen else redMedium,
            hasBackground = false,
            clickable = false
        )

        // T4.2 Phase 4.3 : badge avec compteur des rows en attente de sync.
        // Le badge n'apparait que si totalPending > 0 (cf. BadgedBox). Chip pill
        // (bords gauche/droit pleinement ronds) fond clair / chiffre fonce :
        // lisible sur le drawer sombre, sans pousser l'icone WebSocket.
        BadgedBox(
            badge = {
                if (totalPending > 0) {
                    Text(
                        text = totalPending.toString(),
                        color = secondBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(lightBlue)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        ) {
            ActionIconButton(
                iconRes = if (hasUnsynced) R.drawable.ic_rounded_cloud_upload else R.drawable.ic_cloud_done,
                tint = if (hasUnsynced) yellowMedium else appColors.primaryAction,
                hasBackground = false,
                clickable = true,
                onClick = onSyncClick
            )
        }

        ActionIconButton(
            iconRes = if (isWsConnected) R.drawable.ic_rounded_router else R.drawable.ic_rounded_router_off,
            tint = if (isWsConnected) mediumGreen else orangeMedium,
            hasBackground = false,
            clickable = !isWsConnected,
            onClick = onWsRestartClick
        )
    }
}
