package com.example.jalraksha.ui.village

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.data.SampleData
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.locale.LocalAppLocale
import com.example.jalraksha.ui.components.Droplet
import com.example.jalraksha.ui.components.JrBackButton
import com.example.jalraksha.ui.components.JrChip
import com.example.jalraksha.ui.components.JrPrimaryButton
import com.example.jalraksha.ui.components.JrStepDots
import com.example.jalraksha.ui.components.softShadow
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 03 — bind the account to a village. */
@Composable
fun VillageScreen(
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VillageViewModel = viewModel(factory = VillageViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VillageContent(
        state = state,
        onSelect = viewModel::select,
        onConfirm = { viewModel.confirm(onConfirmed) },
        onBack = onBack,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun VillageContent(
    state: VillageUiState,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLocale.current.language

    Column(
        modifier
            .fillMaxSize()
            .background(JrColor.Surface)
            .systemBarsPadding()
            .padding(horizontal = 30.dp)
            .padding(top = 14.dp, bottom = 30.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JrBackButton(onClick = onBack)
            JrStepDots(completed = 3, total = 3)
            // Balances the back button so the dots stay optically centred.
            Spacer(Modifier.width(42.dp))
        }

        Spacer(Modifier.height(28.dp))

        Text(stringResource(R.string.village_title), style = JrType.Title, color = JrColor.Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.village_subtitle),
            style = JrType.Body,
            color = JrColor.Muted,
        )

        Spacer(Modifier.height(22.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = JrColor.Primary, strokeWidth = 3.dp)
                }

                state.failed -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.error_villages_load),
                        style = JrType.BodySmall,
                        color = JrColor.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.action_retry),
                        style = JrType.Label,
                        color = JrColor.Primary,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                }

                else -> state.villages.forEach { village ->
                    VillageCard(
                        village = village,
                        language = language,
                        selected = village.id == state.selectedId,
                        onClick = { onSelect(village.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.village_reassign_note),
            style = JrType.BodySmall.copy(fontSize = JrType.Label.fontSize),
            color = JrColor.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        JrPrimaryButton(
            text = state.selected
                ?.let { stringResource(R.string.village_confirm_named, it.displayName(language)) }
                ?: stringResource(R.string.action_confirm),
            onClick = onConfirm,
            enabled = state.selectedId != null,
        )
    }
}

@Composable
private fun VillageCard(
    village: Village,
    language: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) JrColor.ChipFill else JrColor.SurfaceMuted,
        label = "villageCardBackground",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.softShadow(
                        radius = 24.dp,
                        color = JrColor.Primary.copy(alpha = 0.45f),
                        elevation = 16.dp,
                    )
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(
                1.5.dp,
                if (selected) JrColor.Primary else JrColor.BorderSoft,
                RoundedCornerShape(24.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            if (selected) JrColor.Primary else JrColor.Border,
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Droplet(
                        size = 19.dp,
                        color = if (selected) Color.White else JrColor.Deep,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        village.displayName(language),
                        style = JrType.CardTitle,
                        color = JrColor.Ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        village.secondaryLine(language),
                        style = JrType.BodySmall,
                        color = JrColor.Muted,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            SelectionRadio(selected = selected)
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JrChip(
                text = pluralStringResource(
                    R.plurals.village_households,
                    village.households,
                    formatCount(village.households),
                ),
                background = JrColor.Surface,
                contentColor = JrColor.Slate,
                border = JrColor.BorderSoft,
            )
            JrChip(
                text = pluralStringResource(
                    R.plurals.village_sensors,
                    village.sensors,
                    formatCount(village.sensors),
                ),
                background = JrColor.Surface,
                contentColor = JrColor.Slate,
                border = JrColor.BorderSoft,
            )
            // A score of 85+ is called out in solid navy; anything lower stays a quiet outline.
            val scoreText = stringResource(R.string.village_score, formatCount(village.score))
            if (village.score >= HIGH_SCORE_THRESHOLD) {
                JrChip(
                    text = scoreText,
                    background = JrColor.Deep,
                    contentColor = Color.White,
                )
            } else {
                JrChip(
                    text = scoreText,
                    background = JrColor.Surface,
                    contentColor = JrColor.Slate,
                    border = JrColor.BorderSoft,
                )
            }
        }
    }
}

@Composable
private fun SelectionRadio(selected: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .background(
                if (selected) JrColor.Primary else JrColor.Surface,
                RoundedCornerShape(50),
            )
            .border(
                2.dp,
                if (selected) JrColor.Primary else JrColor.RadioBorder,
                RoundedCornerShape(50),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(9.dp).background(Color.White, RoundedCornerShape(50)))
        }
    }
}

private const val HIGH_SCORE_THRESHOLD = 85

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "मराठी", widthDp = 390, heightDp = 844, showBackground = true, locale = "mr")
@Preview(name = "বাংলা", widthDp = 390, heightDp = 844, showBackground = true, locale = "bn")
@Composable
private fun VillagePreview() {
    JalrakshaTheme {
        VillageContent(
            state = VillageUiState(
                villages = SampleData.villages,
                selectedId = "rampur",
                loading = false,
            ),
            onSelect = {},
            onConfirm = {},
            onBack = {},
            onRetry = {},
        )
    }
}
