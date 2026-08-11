package com.example.sportapp.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.*
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.UiShowcaseCardBackground
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.lightGrayBlue
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.health.ui.components.HealthBarChart
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.yellowMedium
import kotlin.math.roundToInt

/**
 * UI Showcase — galerie visuelle des atoms (et molecules/organisms a venir)
 * pour comparatif Figma <-> appli reelle. Cache du drawer sauf si user admin.
 *
 * Pattern par atom :
 * - Card avec titre code (ex. "D5 · CustomSwitch") + brief description
 * - "Interactive" : 1 instance live avec son state local (remember)
 * - "All states" : Row d'instances statiques montrant les variants
 *
 * Code interne : helpers _SectionCard, _StateLabel pour DRY.
 */
@Composable
fun UiShowcaseScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        // Header avec back + titre
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(appColors.bgSurface),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textPrimary
                )
            }
            Text(
                text = stringResource(R.string.ui_showcase_title),
                color = appColors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ===== TAB BAR =====
        var selectedTab by remember { mutableIntStateOf(1) } // default = Common Components
        // Onglets = structure de dossiers du projet : Foundations (designsystem/theme) + Common
        // Components (designsystem/common_components). Aligné sur le showcase web.
        _ShowcaseTabs(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            tabs = listOf(
                stringResource(R.string.ui_showcase_tab_foundations),
                stringResource(R.string.ui_showcase_tab_components),
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Foundations (designsystem/theme)
                    item { Section_FoundationsColors() }
                }
                1 -> {
                    // Common Components (designsystem/common_components) — ordre alphabétique (listing de dossier).
                    item { Section_ActionIconButton() }
                    item { Section_CustomRadioButton() }
                    item { Section_CustomSelect() }
                    item { Section_CustomSpacer() }
                    item { Section_CustomSwitch() }
                    item { Section_CustomTextField() }
                    item { Section_DetailRow() }
                    item { Section_DualTabMenu() }
                    item { Section_FilterDropdown() }
                    item { Section_GenericEntityCard() }
                    item { Section_HmsWheelPicker() }
                    item { Section_HorizontalNumberPicker() }
                    item { Section_LabeledProgressBar() }
                    item { Section_ProgressRing() }
                    item { Section_MultiSelectDropdown() }
                    item { Section_OptionRow() }
                    item { Section_SetRow() }
                    item { Section_SingleSelectDropdown() }
                    item { Section_StatusIcon() }
                    item { Section_StyledSearchField() }
                    item { Section_SummaryItem() }
                    item { Section_TimeRangePickerBar() }
                    item { Section_TitledDivider() }
                    item { Section_TrendLineChart() }
                    item { Section_WheelPicker() }
                    item { Section_HealthBarChart() }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

@Composable
private fun _SectionCard(
    code: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = UiShowcaseCardBackground,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = code,
                color = appColors.primaryAction,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = appColors.textTertiary,
                fontSize = 12.sp
            )
            HorizontalDivider(color = appColors.divider.copy(alpha = 0.3f), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun _Label(text: String) {
    Text(
        text = text,
        color = appColors.textTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
}

/** Cadre thirdBlue (comme les cadres charts du hub Santé) pour présenter un chart « nu »
 *  (fond transparent) bien visible sur la card du Showcase. */
@Composable
private fun _ChartFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(appColors.bgRecessed)
            .padding(12.dp),
    ) { content() }
}

/** Tab bar 4 tabs Foundations / Atoms / Molecules / Organisms. */
@Composable
private fun _ShowcaseTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    tabs: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(appColors.bgBottomNav),
    ) {
        tabs.forEachIndexed { idx, label ->
            val isSelected = idx == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (isSelected) appColors.selectedFill else appColors.bgBottomNav)
                    .clickable { onSelect(idx) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) appColors.textOnSelected else appColors.textTertiary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Foundations tab content : grid des 24 AppColors tokens avec swatches + nom + rôle.
 * Mirror simplifié de la page Figma `1 · Foundations`.
 */
@Composable
private fun Section_FoundationsColors() {
    val tokens = listOf(
        Triple("bg/screen", appColors.bgScreen, "fond global d'un screen"),
        Triple("bg/surface", appColors.bgSurface, "fond cartes/boîtes/dialogs"),
        Triple("bg/recessed", appColors.bgRecessed, "fond \"enfoncé\" (rows alternées)"),
        Triple("bg/bottom-nav", appColors.bgBottomNav, "BottomNavBar + onglets non actifs"),
        Triple("bg/button", appColors.bgButton, "fond boutons d'action carrés"),
        Triple("selected/fill", appColors.selectedFill, "élément sélectionné (tab actif)"),
        Triple("primary/action", appColors.primaryAction, "boutons primaires/FAB/switches"),
        Triple("text/primary", appColors.textPrimary, "titres, body, valeurs"),
        Triple("text/secondary", appColors.textSecondary, "sous-titres, headers, captions"),
        Triple("text/tertiary", appColors.textTertiary, "placeholders, disabled, hints"),
        Triple("text/on-selected", appColors.textOnSelected, "texte sur selected/fill"),
        Triple("text/accent", appColors.accentText, "titres drawer, liens"),
        Triple("divider/default", appColors.divider, "Divider standard"),
        Triple("divider/strong", appColors.dividerStrong, "séparateur section"),
        Triple("priority/high", appColors.priorityHigh, "badge priorité HIGH"),
        Triple("priority/medium", appColors.priorityMedium, "badge priorité MEDIUM"),
        Triple("priority/low", appColors.priorityLow, "badge priorité LOW"),
        Triple("task/row-green-bg", appColors.taskRowGreenBg, "fond row task verte"),
        Triple("task/row-green-name", appColors.taskRowGreenNameBox, "sous-box nom row verte"),
        Triple("task/row-orange-bg", appColors.taskRowOrangeBg, "fond row task orange"),
        Triple("task/row-orange-name", appColors.taskRowOrangeNameBox, "sous-box nom row orange"),
        Triple("snackbar/success", appColors.snackbarSuccess, "accent SUCCESS"),
        Triple("snackbar/warning", appColors.snackbarWarning, "accent WARNING"),
        Triple("snackbar/error", appColors.snackbarError, "accent ERROR"),
    )
    _SectionCard(
        code = "AppColors — 24 semantic tokens",
        description = "Source de vérité : AppColors.kt. Chaque token = swatch + nom + rôle. Switch entre Dark/Light via le système (Settings tel)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tokens.forEach { (name, color, role) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .border(1.dp, appColors.divider.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = name,
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(160.dp)
                    )
                    Text(
                        text = role,
                        color = appColors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Section divider entre Atoms / Molecules / Organisms — texte primary/action + ligne. */
@Composable
private fun _SectionDivider(text: String) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
            text = text.uppercase(),
            color = appColors.primaryAction,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.5.dp,
            color = appColors.primaryAction.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun _MiniCaption(text: String) {
    Text(
        text = text,
        color = appColors.textTertiary.copy(alpha = 0.7f),
        fontSize = 9.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/**
 * Wrapper Surface bg/recessed (thirdBlue) pour mettre en évidence les atoms
 * qui n'ont pas de background propre (RadioButton, Divider, StatusIcon...)
 * ou qui utilisent bg/button (== bg/surface en Dark → invisibles sur la Card).
 * NE PAS utiliser pour les atoms qui consomment déjà bg/recessed (TextField,
 * Dropdowns, WheelPicker cells...) car le wrap blend avec l'atome.
 */
@Composable
private fun _DemoBox(content: @Composable () -> Unit) {
    Surface(
        color = appColors.bgRecessed,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

// ============================================================================
// Atom sections
// ============================================================================

@Composable
private fun Section_CustomSwitch() {
    var live by remember { mutableStateOf(true) }
    _SectionCard(
        code = "D5 · CustomSwitch",
        description = "Switch stylé (M3 Switch wrapper). Track primary/action quand checked, blueMedium border quand unchecked."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        _DemoBox {
            CustomSwitch(checked = live, onCheckedChange = { live = it })
        }

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomSwitch(checked = true, onCheckedChange = {}, enabled = true)
                    _MiniCaption("On\nenabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomSwitch(checked = true, onCheckedChange = {}, enabled = false)
                    _MiniCaption("On\ndisabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomSwitch(checked = false, onCheckedChange = {}, enabled = true)
                    _MiniCaption("Off\nenabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomSwitch(checked = false, onCheckedChange = {}, enabled = false)
                    _MiniCaption("Off\ndisabled")
                }
            }
        }
    }
}

@Composable
private fun Section_CustomRadioButton() {
    var live by remember { mutableStateOf(true) }
    _SectionCard(
        code = "D5b · CustomRadioButton",
        description = "RadioButton stylé. Ring primary/action si selected, blueMedium sinon."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        _DemoBox {
            CustomRadioButton(selected = live, onClick = { live = !live })
        }

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomRadioButton(selected = true, onClick = {}, enabled = true)
                    _MiniCaption("Selected\nenabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomRadioButton(selected = true, onClick = {}, enabled = false)
                    _MiniCaption("Selected\ndisabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomRadioButton(selected = false, onClick = {}, enabled = true)
                    _MiniCaption("Unselected\nenabled")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomRadioButton(selected = false, onClick = {}, enabled = false)
                    _MiniCaption("Unselected\ndisabled")
                }
            }
        }
    }
}

@Composable
private fun Section_CustomTextField() {
    var liveValue by remember { mutableStateOf("") }
    _SectionCard(
        code = "E1 · CustomTextField",
        description = "TextField M3 stylé. Container bg/recessed, label flotte si Filled OU Focused, indicator bottom si Focused."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        CustomTextField(
            value = liveValue,
            onValueChange = { liveValue = it },
            placeholder = "Placeholder",
            label = "Label"
        )

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            _MiniCaption("Empty")
            CustomTextField(value = "", onValueChange = {}, placeholder = "Placeholder", label = "Label")
            _MiniCaption("Filled")
            CustomTextField(value = "Sample value", onValueChange = {}, placeholder = "Placeholder", label = "Label")
        }
    }
}

@Composable
private fun Section_StyledSearchField() {
    var liveValue by remember { mutableStateOf(TextFieldValue("")) }
    _SectionCard(
        code = "E2 · StyledSearchField",
        description = "Champ de recherche : texte toujours primary/action, container bg/recessed."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        StyledSearchField(value = liveValue, onValueChange = { liveValue = it }, placeholderText = "Search")

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            _MiniCaption("Empty")
            StyledSearchField(value = TextFieldValue(""), onValueChange = {}, placeholderText = "Search")
            _MiniCaption("Filled")
            StyledSearchField(value = TextFieldValue("Sample query"), onValueChange = {}, placeholderText = "Search")
        }
    }
}

@Composable
private fun Section_CustomSelect() {
    var liveSelected by remember { mutableStateOf("Option B") }
    _SectionCard(
        code = "E3 · CustomSelect",
        description = "Dropdown custom (Surface + Compose DropdownMenu). Selected text primary/action."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        CustomSelect(
            selected = liveSelected,
            options = listOf("Option A", "Option B", "Option C"),
            onSelect = { liveSelected = it }
        )
    }
}

@Composable
private fun Section_SingleSelectDropdown() {
    var liveSelected by remember { mutableStateOf("Option B") }
    _SectionCard(
        code = "E4 · SingleSelectDropdown",
        description = "M3 ExposedDropdownMenuBox. Check icon sur option sélectionnée, disabledOptions suffixées."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        SingleSelectDropdown(
            label = "Label",
            selected = liveSelected,
            options = listOf("Option A", "Option B", "Option C", "Option D"),
            onSelect = { liveSelected = it },
            disabledOptions = setOf("Option C"),
            disabledSuffix = " (current)"
        )
    }
}

@Composable
private fun Section_MultiSelectDropdown() {
    var liveSelected by remember { mutableStateOf(listOf("Chest", "Back")) }
    _SectionCard(
        code = "E5 · MultiSelectDropdown",
        description = "Multi-select. Check icon par item sélectionné. Display = comma-separated."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        MultiSelectDropdown(
            label = "Tags",
            options = listOf("Chest", "Back", "Shoulders", "Legs"),
            selectedItems = liveSelected,
            onSelectionChange = { liveSelected = it }
        )
    }
}

@Composable
private fun Section_FilterDropdown() {
    var liveSelected by remember { mutableStateOf<String?>("All") }
    _SectionCard(
        code = "E6 · FilterDropdown",
        description = "Variante simple de dropdown (pas de disabled, pas de check)."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        FilterDropdown(
            label = "Filter",
            options = listOf("All", "Recent", "Favorites"),
            selected = liveSelected,
            onSelect = { liveSelected = it }
        )
    }
}

@Composable
private fun Section_DualTabMenu() {
    var topIdx by remember { mutableIntStateOf(1) }
    var subIdx by remember { mutableIntStateOf(0) }
    _SectionCard(
        code = "F1 · DualTabMenu",
        description = "2 rangées de tabs. Top row + optional sub-row. Tab actif = selected/fill."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        _DemoBox {
            DualTabMenu(
                topTabs = listOf("Tab 1", "Tab 2", "Tab 3"),
                subTabsMap = mapOf(
                    "Tab 1" to emptyList(),
                    "Tab 2" to listOf("Sub A", "Sub B", "Sub C"),
                    "Tab 3" to listOf("Sub X", "Sub Y")
                ),
                selectedTopIndex = topIdx,
                selectedSubIndex = subIdx,
                onTopTabSelected = { topIdx = it; subIdx = 0 },
                onSubTabSelected = { subIdx = it }
            )
        }
    }
}

@Composable
private fun Section_TitledDivider() {
    _SectionCard(
        code = "C1 · TitledDivider",
        description = "Divider horizontal avec titre centré. Couleur paramétrable (divider / dividerStrong)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                _MiniCaption("Color = divider (default)")
                TitledDivider(title = "Session Completion")
                _MiniCaption("Color = dividerStrong")
                TitledDivider(title = "Session Completion", color = appColors.dividerStrong)
            }
        }
    }
}

@Composable
private fun Section_CustomSpacer() {
    _SectionCard(
        code = "C2 · CustomSpacer",
        description = "Spacer vertical transparent de largeur paramétrable (default 6dp), fillMaxHeight. Renommé depuis CustomVerticalDivider (qui n'était pas un divider mais un spacer)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                _MiniCaption("Default (6dp) — gap entre 2 cells")
                Row(modifier = Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = appColors.primaryAction, modifier = Modifier.size(40.dp)) {}
                    CustomSpacer()
                    Surface(color = appColors.snackbarSuccess, modifier = Modifier.size(40.dp)) {}
                }
                _MiniCaption("Custom 24dp — gap plus large")
                Row(modifier = Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = appColors.primaryAction, modifier = Modifier.size(40.dp)) {}
                    CustomSpacer(width = 24.dp)
                    Surface(color = appColors.snackbarSuccess, modifier = Modifier.size(40.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun Section_ActionIconButton() {
    _SectionCard(
        code = "D4 · ActionIconButton family",
        description = "3 composables : ActionIconButton (canonique, ~145 usages), ActionIconWithTextButton, ActionTextButton."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Labels forcés sur 2 lignes pour uniformiser la hauteur des colonnes
                // (sinon "ActionIconWithTextButton" wrap en 2 lignes et déséquilibre la Row).
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionIconButton(iconRes = R.drawable.ic_settings)
                    _MiniCaption("Action\nIconButton")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionIconWithTextButton(iconRes = R.drawable.ic_calendar_month, text = "View")
                    _MiniCaption("ActionIcon\nWithTextButton")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // ActionTextButton utilise fillMaxSize() en interne → wrap dans Box sized.
                    // (clickable default = true depuis l'alignement avec ActionIconButton)
                    Box(modifier = Modifier.size(width = 100.dp, height = 40.dp)) {
                        ActionTextButton(text = "View", hasBackground = true, onClick = {})
                    }
                    _MiniCaption("Action\nTextButton")
                }
            }
        }
    }
}

@Composable
private fun Section_StatusIcon() {
    _SectionCard(
        code = "I1 · StatusIcon",
        description = "Container 16dp avec icône tint customizable. Démo avec 4 tints sémantiques."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusIcon(iconRes = R.drawable.ic_rounded_check_circle, tint = appColors.snackbarSuccess, size = 24.dp)
                    _MiniCaption("Success")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusIcon(iconRes = R.drawable.ic_rounded_warning, tint = appColors.snackbarWarning, size = 24.dp)
                    _MiniCaption("Warning")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusIcon(iconRes = R.drawable.ic_rounded_cancel, tint = appColors.snackbarError, size = 24.dp)
                    _MiniCaption("Error")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusIcon(iconRes = R.drawable.ic_rounded_info, tint = appColors.accentText, size = 24.dp)
                    _MiniCaption("Info")
                }
            }
        }
    }
}

@Composable
private fun Section_WheelPicker() {
    var liveValue by remember { mutableIntStateOf(12) }
    _SectionCard(
        code = "E7 · WheelPicker",
        description = "Wheel scroll vertical, snap au centre. Range + selected callback."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        _DemoBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelPicker(
                    range = 0..23,
                    selected = liveValue,
                    onSelected = { liveValue = it },
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = "selected = $liveValue",
                    color = appColors.textPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun Section_HorizontalNumberPicker() {
    var liveValue by remember { mutableIntStateOf(12) }
    _SectionCard(
        code = "E8 · HorizontalNumberPicker",
        description = "LazyRow de cells 40dp. Selected = primary/action. Optional targetRange (cells hors range = redMedium α0.5)."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        HorizontalNumberPicker(
            range = 8..20,
            selected = liveValue,
            label = "Sample picker",
            onValueChange = { liveValue = it }
        )

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        _MiniCaption("With targetRange = 10..13 (cells hors range en rouge)")
        HorizontalNumberPicker(
            range = 8..15,
            selected = 12,
            targetRange = 10..13,
            label = "Target: 10-13",
            onValueChange = {}
        )
    }
}

@Composable
private fun Section_LabeledProgressBar() {
    var liveProgress by remember { mutableFloatStateOf(0.6f) }
    _SectionCard(
        code = "I2 · LabeledProgressBar",
        description = "Barre progress + % à droite. Couleur par threshold : <20 red, <50 orange, <75 lightGreen, <100 mediumGreen, =100 primary."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                LabeledProgressBar(progress = liveProgress)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.1f, 0.3f, 0.6f, 0.8f, 1f).forEach { p ->
                Surface(
                    onClick = { liveProgress = p },
                    color = appColors.bgRecessed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${(p * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = appColors.textPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.1f, 0.3f, 0.6f, 0.8f, 1f).forEach { p ->
                LabeledProgressBar(progress = p)
            }
        }
    }
}

@Composable
private fun Section_ProgressRing() {
    _SectionCard(
        code = "I3 · ProgressRing",
        description = "Anneau de progression (miroir du ProgressRing web) : piste + arc à bouts arrondis, valeur au centre + sous-titre optionnel (cible). Utilisé par le bandeau nutrition (mode anneaux : 5 anneaux, un par macro)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            ProgressRing(progress = 0.66f, color = appColors.primaryAction, label = "1450", sublabel = "/ 2200")
            ProgressRing(progress = 0.85f, color = lightGreen, label = "187", sublabel = "/ 220 g")
            ProgressRing(progress = 0.45f, color = orangeMedium, label = "32", sublabel = "/ 70 g")
            ProgressRing(progress = 1f, color = blueMedium, label = "130", sublabel = "/ 130 g")
            ProgressRing(progress = 0f, color = lightGrayBlue, label = "0", sublabel = "g")
        }
    }
}

@Composable
private fun Section_HealthBarChart() {
    _SectionCard(
        code = "H1 · HealthBarChart",
        description = "Barres génériques à coins arrondis. Intraday = 48 barres (tranches de 30 min) + axe grayBlue (ligne + ticks 0/6/12/18/24 h) ; 7 jours = quantième seul (sans axe) + ligne pointillée de moyenne (jours renseignés) avec sa valeur au-dessus de la ligne, calée à droite. Le hub l'encadre en thirdBlue compact (fond/piste/padding surchargeables). Pas / FC / sommeil."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        // Variante intraday : 48 barres (tranches de 30 min), axe grayBlue avec repères
        // aux quarts (ticks 0/6/12/18/24 h).
        HealthBarChart(
            values = List(48) { slot -> if (slot in 14..43) (slot - 13) * 45f else 0f },
            axisLabels = listOf("0h", "6h", "12h", "18h", "24h"),
            axisColor = GrayBlue,
            // Tooltip au tap (cadre façon web) : suffixe d'unité de démonstration.
            valueSuffix = " bpm",
            tooltipLabel = { slot -> "%02d:%02d".format(slot / 2, (slot % 2) * 30) },
        )

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Variante 7 jours : 7 barres, quantième du jour seul (axisColor = null → pas
            // d'axe) + ligne pointillée de moyenne des jours renseignés (44800/6 ≈ 7467).
            // Le 3e jour est vide (0) → son slot reste réservé (positions alignées).
            HealthBarChart(
                values = listOf(6200f, 8100f, 0f, 9500f, 7000f, 5200f, 8800f),
                axisLabels = listOf("28", "29", "30", "1", "2", "3", "4"),
                axisColor = null,
                averageLine = 7467f,
                valueSuffix = " pas",
            )
            // Variante EMPILÉE (ex. phases de sommeil 7 j) : segments colorés du bas
            // vers le haut [profond, léger, paradoxal, éveillé], jour 3 vide réservé.
            HealthBarChart(
                values = List(7) { 0f }, // ignoré en mode empilé (totaux dérivés)
                axisLabels = listOf("28", "29", "30", "1", "2", "3", "4"),
                axisColor = null,
                stackedValues = listOf(
                    listOf(90f, 210f, 70f, 20f),
                    listOf(120f, 180f, 90f, 10f),
                    listOf(0f, 0f, 0f, 0f),
                    listOf(70f, 250f, 60f, 30f),
                    listOf(100f, 200f, 80f, 15f),
                    listOf(80f, 190f, 100f, 25f),
                    listOf(110f, 220f, 75f, 10f),
                ),
                stackColors = listOf(blueMedium, lightGrayBlue, lightGreen, orangeMedium),
            )
            // Vide (piste seule + axe grayBlue) : 48 tranches, repères épars.
            HealthBarChart(
                values = List(48) { 0f },
                axisLabels = listOf("0h", "12h", "24h"),
                axisColor = GrayBlue,
            )
        }
    }
}

@Composable
private fun Section_TrendLineChart() {
    _SectionCard(
        code = "H2 · TrendLineChart",
        description = "Courbe de tendance lissée (Catmull-Rom) + aire dégradée sous la courbe, transposition Android du multi-line chart web (Stats). 1 point par slot daté, null = slot vide (aucune interpolation). Graduations Y « rondes » (pas nice-number, sans ligne d'axe verticale) : chaque niveau a son label rattaché à gauche (min, max et médiane en clair, autres intermédiaires en GrayBlue plus foncé) + une gridline pointillée légère qui traverse le chart → l'œil raccroche la courbe aux valeurs. Échelle Y resserrée (faibles variations lisibles). 2 modes de labels X (1 par slot / repères épars). averageLine optionnel = ligne pointillée de moyenne + valeur en haut à droite (pas/FC 7 j ; Poids sans). Canonique du design system (tendance de poids + pas/FC 7 j / 30 j)."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        // 7 jours (1 label par slot) : le jour 3 est vide (null) → la courbe le saute.
        // Cadre thirdBlue (comme le hub) → l'aire dégradée sous la courbe fond vers le fond du cadre.
        _ChartFrame {
            TrendLineChart(
                values = listOf(74.8f, 74.5f, null, 74.9f, 74.2f, 74.6f, 74.1f),
                axisLabels = listOf("3", "4", "5", "6", "7", "8", "9"),
                lineColor = yellowMedium,
                // Tooltip au tap (cadre façon web) : suffixe d'unité de démonstration.
                valueSuffix = " kg",
            )
        }

        Spacer(Modifier.height(8.dp))
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 30 jours (repères épars) : nombreux slots, quelques pesées manquantes.
            _ChartFrame {
                TrendLineChart(
                    values = List(30) { i ->
                        if (i % 5 == 2) null else 75f + (i % 7 - 3) * 0.3f
                    },
                    axisLabels = listOf("10", "17", "24", "1", "8"),
                    lineColor = yellowMedium,
                )
            }
            // Point unique (une seule pesée) : un point centré, min = max affiché une fois.
            _ChartFrame {
                TrendLineChart(
                    values = listOf(null, null, null, 80.4f, null, null, null),
                    axisLabels = listOf("3", "4", "5", "6", "7", "8", "9"),
                    lineColor = appColors.primaryAction,
                )
            }
            // 7 jours avec ligne de moyenne (ex. pas) : couleur domaine + averageLine + format entier.
            _ChartFrame {
                TrendLineChart(
                    values = listOf(6200f, 8100f, null, 9500f, 7000f, 5200f, 8800f),
                    axisLabels = listOf("28", "29", "30", "1", "2", "3", "4"),
                    lineColor = lightGreen,
                    averageLine = 7467f,
                    valueFormat = { "%,d".format(it.roundToInt()) },
                    valueSuffix = " pas",
                )
            }
        }
    }
}

// ============================================================================
// Molecules sections
// ============================================================================

@Composable
private fun Section_HmsWheelPicker() {
    var hours by remember { mutableIntStateOf(8) }
    var minutes by remember { mutableIntStateOf(30) }
    var seconds by remember { mutableIntStateOf(45) }
    _SectionCard(
        code = "M8 · HmsWheelPicker",
        description = "Picker H:M:S avec preview box en haut, 3 WheelPickers (HH 0..23 / MM 0..59 / SS 0..59) séparés par ':' + hint. Compose une heure complète via 3 atoms WheelPicker."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        _DemoBox {
            HmsWheelPicker(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                onHoursChange = { hours = it },
                onMinutesChange = { minutes = it },
                onSecondsChange = { seconds = it }
            )
        }
    }
}

@Composable
private fun Section_TimeRangePickerBar() {
    var start by remember { mutableIntStateOf(480) } // 08:00
    var end by remember { mutableIntStateOf(1080) }   // 18:00
    _SectionCard(
        code = "M7 · TimeRangePickerBar",
        description = "Range picker time : Column label primary/action + Row [< button] [M3 RangeSlider + labels HH:MM au-dessus des thumbs] [> button]. Snap au step (5min par défaut)."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        com.example.sportapp.feature.routines.ui.components.routineTasksScreen.TimeRangePickerBar(
            minMinutes = 0,
            maxMinutes = 1439,
            stepMinutes = 5,
            startMinutes = start,
            endMinutes = end,
            onChange = { newStart, newEnd ->
                start = newStart
                end = newEnd
            },
            label = "Time"
        )
    }
}

@Composable
private fun Section_GenericEntityCard() {
    _SectionCard(
        code = "M6 · GenericEntityCard",
        description = "Card rounded 16dp slot-based : header (icon entity + title centré + slot headerTrailing optionnel + chevron expand) + body expandable (slot detailsContent) + footer actions (slot actions). Toggle en cliquant le header. Base canonique de ExerciseCard / MuscleCard / vue debug SyncSettings."
    ) {
        _Label(stringResource(R.string.ui_showcase_interactive))
        GenericEntityCard(
            title = "Squat Heavy",
            iconRes = R.drawable.ic_exercise,
            detailsContent = {
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    label = "uuid",
                    value = "demo-1",
                    valueColor = appColors.textTertiary
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_format_list_numbered,
                    label = "sets",
                    value = "4",
                    valueColor = appColors.textTertiary
                )
                DetailRow(
                    iconRes = R.drawable.ic_cloud_off,
                    label = "synced",
                    value = "false",
                    valueColor = appColors.textTertiary
                )
            },
            actions = {
                ActionIconButton(
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    onClick = {},
                    customBackgroundColor = appColors.primaryAction,
                    tint = appColors.textPrimary
                )
                ActionIconButton(
                    iconRes = R.drawable.ic_cloud_off,
                    onClick = {},
                    iconSize = 30.dp,
                    customBackgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                    tint = yellowMedium
                )
            }
        )
    }
}

@Composable
private fun Section_SetRow() {
    // Fake ActualWorkoutSet data for demo
    val doneSet = com.example.sportapp.core.data.model.ActualWorkoutSet(
        uuid = "demo-1",
        actualWorkoutExerciseUUID = "demo-ex",
        setOrder = 1,
        reps = 12,
        weight = 60f,
        status = "DONE"
    )
    val dropSet = doneSet.copy(uuid = "demo-2", isDropset = true)

    _SectionCard(
        code = "M5 · SetRow",
        description = "Row workout set (35dp) avec 7 cells weighted (1.6/2/2/1.6/1.6/1.6/1.6) + 4 CustomSpacer 6dp. Cells : order, reps, weight, trend, status, delete, note. 2 variants : Done (normal) et Dropset (subdir arrow + bg α0.5)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetRow(
                set = doneSet,
                targetRepsRange = 8..12,
                onIndexClick = {},
                onEditRepsClick = {},
                onEditWeightClick = {},
                onDeleteClick = {},
                onAddNoteClick = {}
            )
            SetRow(
                set = dropSet,
                targetRepsRange = 8..12,
                onIndexClick = {},
                onEditRepsClick = {},
                onEditWeightClick = {},
                onDeleteClick = {},
                onAddNoteClick = {}
            )
        }
    }
}

@Composable
private fun Section_SummaryItem() {
    _SectionCard(
        code = "M4 · SummaryItem",
        description = "Cellule de résumé : Row bg/recessed + icône tintée + Column (value SemiBold + label tertiary). Variante compacte (compact = true) montrée ici : icône 24dp, texte 13sp, 1 ligne + ellipsis. Variante standard : icône 36dp, texte 14sp."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryItem(
                icon = R.drawable.ic_rounded_check_circle,
                value = "12",
                label = "Sessions",
                iconTint = appColors.snackbarSuccess,
                compact = true
            )
            SummaryItem(
                icon = R.drawable.ic_calendar_month,
                value = "Mar 17",
                label = "Next workout",
                iconTint = appColors.accentText,
                compact = true
            )
        }
    }
}

@Composable
private fun Section_OptionRow() {
    _SectionCard(
        code = "M3 · OptionRow",
        description = "Row fillMaxWidth bg/recessed rounded 8dp (padding 12h/10v) + label text (weight 1f) + ActionIconButton trailing (toujours hasBackground=true). 3 variants par couleur du bouton via customColor : Default (bg/button), Primary (primary/action), Danger (redMedium)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionRow(
                label = "Set as default",
                iconRes = R.drawable.ic_settings,
                onClick = {},
                customColor = appColors.bgButton
            )
            OptionRow(
                label = "Confirm action",
                iconRes = R.drawable.ic_settings,
                onClick = {},
                customColor = appColors.primaryAction
            )
            OptionRow(
                label = "Delete account",
                iconRes = R.drawable.ic_settings,
                onClick = {},
                customColor = com.example.sportapp.designsystem.theme.redMedium
            )
        }
    }
}

@Composable
private fun Section_DetailRow() {
    _SectionCard(
        code = "M1 · DetailRow",
        description = "Row horizontale : icon 16dp + label + value (Medium). 2 variants : Inline (single-line, center aligned) et Indented (multi-line, top aligned, AnnotatedString)."
    ) {
        _Label(stringResource(R.string.ui_showcase_all_states))
        _DemoBox {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                _MiniCaption("Inline (DetailRow)")
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    label = "Sets",
                    value = "4"
                )
                _MiniCaption("Indented (DetailRowWithIndentation) — value wraps multiline")
                DetailRowWithIndentation(
                    iconRes = R.drawable.ic_rounded_info,
                    label = "Description",
                    value = "Long descriptive text that wraps over multiple lines to demonstrate the indentation behavior — the icon stays at top while text flows below."
                )
            }
        }
    }
}
