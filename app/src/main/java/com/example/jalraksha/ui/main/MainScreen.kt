package com.example.jalraksha.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jalraksha.ui.components.dropletShape
import com.example.jalraksha.ui.dashboard.DashboardScreen
import com.example.jalraksha.ui.profile.ProfileScreen
import com.example.jalraksha.ui.report.ReportScreen
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType
import com.example.jalraksha.ui.trends.TrendsScreen

/**
 * The four tabbed screens (04–07) behind one persistent bottom bar.
 *
 * The design draws the same floating bar on every one of them, so it lives here rather than being
 * repeated per screen — and switching tabs keeps each screen's ViewModel alive, so returning to
 * Trends does not re-fetch the window you were already looking at.
 */
@Composable
fun MainScreen(
    onNoVillage: () -> Unit,
    onSignedOut: () -> Unit,
    onEditLanguage: () -> Unit,
    onEditVillage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.Home) }

    Box(modifier.fillMaxSize().background(JrColor.SurfaceMuted)) {
        when (selected) {
            MainTab.Home -> DashboardScreen(onNoVillage = onNoVillage)
            MainTab.Trends -> TrendsScreen()
            MainTab.Report -> ReportScreen(onBack = { selected = MainTab.Home })
            MainTab.Profile -> ProfileScreen(
                onSignedOut = onSignedOut,
                onEditLanguage = onEditLanguage,
                onEditVillage = onEditVillage,
            )
        }

        BottomNav(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

/** Floating 4-item nav bar. Icons are geometry, matching the design's CSS-only shapes. */
@Composable
private fun BottomNav(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(JrColor.Surface)
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) JrColor.ChipFill else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NavIcon(tab = tab, selected = isSelected)
                Spacer(Modifier.height(7.dp))
                Text(
                    // Translated labels run longer than "Home" — one line, shrunk to fit rather
                    // than wrapped, so the bar keeps the design's height in every language.
                    stringResource(tab.labelRes),
                    style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.91f),
                    color = if (isSelected) JrColor.Primary else JrColor.Faint,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun NavIcon(tab: MainTab, selected: Boolean) {
    if (selected) {
        // The active tab is always the filled brand droplet.
        Box(Modifier.size(17.dp).rotate(45f).background(JrColor.Primary, dropletShape(2.dp)))
        return
    }
    // Inactive tabs are outlines the corner radius distinguishes: Home an outlined droplet,
    // Trends a square-ish box, Report a circle, Profile a softly rounded card.
    if (tab == MainTab.Home) {
        Box(Modifier.size(16.dp).rotate(45f).border(2.dp, JrColor.Faint, dropletShape(2.dp)))
        return
    }
    val shape = when (tab) {
        MainTab.Trends -> RoundedCornerShape(4.dp)
        MainTab.Report -> RoundedCornerShape(50)
        else -> RoundedCornerShape(6.dp)
    }
    Box(Modifier.size(16.dp).border(2.dp, JrColor.Faint, shape))
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainPreview() {
    JalrakshaTheme {
        MainScreen(
            onNoVillage = {},
            onSignedOut = {},
            onEditLanguage = {},
            onEditVillage = {},
        )
    }
}
