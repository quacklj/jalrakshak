package com.example.jalraksha.ui.profile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.BuildConfig
import com.example.jalraksha.R
import com.example.jalraksha.data.SampleAccount
import com.example.jalraksha.data.SampleData
import com.example.jalraksha.data.model.Language
import com.example.jalraksha.data.model.Profile
import com.example.jalraksha.ui.components.Droplet
import com.example.jalraksha.ui.components.JrSecondaryButton
import com.example.jalraksha.ui.components.drawLeftBottom
import com.example.jalraksha.ui.text.WaterStrings
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 07 — the account, its settings, and the way out. */
@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onEditLanguage: () -> Unit,
    onEditVillage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.signedOut) {
        LaunchedEffect(Unit) { onSignedOut() }
    }

    ProfileContent(
        state = state,
        onAlertsChange = viewModel::setUnsafeAlerts,
        onSignOut = viewModel::signOut,
        onEditLanguage = onEditLanguage,
        onEditVillage = onEditVillage,
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onAlertsChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onEditLanguage: () -> Unit,
    onEditVillage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile

    Box(modifier.fillMaxSize().background(JrColor.SurfaceMuted)) {
        if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JrColor.Primary, strokeWidth = 3.dp)
            }
            return@Box
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ProfileHeader(
                profile = profile,
                villageName = state.village?.displayName(state.languageCode).orEmpty(),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 20.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsCard(
                    profile = profile,
                    languageName = stringResource(Language.byCode(state.languageCode).nameRes),
                    villageName = state.village?.displayName(state.languageCode).orEmpty(),
                    onEditLanguage = onEditLanguage,
                    onEditVillage = onEditVillage,
                )

                AlertsCard(enabled = profile.unsafeAlerts, onChange = onAlertsChange)

                HelpCard()

                JrSecondaryButton(
                    text = stringResource(R.string.action_sign_out),
                    onClick = onSignOut,
                    contentColor = JrColor.Danger,
                )

                Text(
                    stringResource(R.string.profile_footer, BuildConfig.VERSION_NAME),
                    style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
                    color = JrColor.Faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Clearance for the floating bottom nav.
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

/** Navy panel: avatar, name, verification, and the three headline numbers. */
@Composable
private fun ProfileHeader(profile: Profile, villageName: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Deep, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 26.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.profile_eyebrow),
                style = JrType.Label.copy(letterSpacing = 0.16.em),
                color = JrColor.OnDeepMuted,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.action_edit), style = JrType.Caption, color = Color.White)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(66.dp).background(Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.initials,
                    style = JrType.CardTitle.copy(fontSize = JrType.CardTitle.fontSize * 1.21f),
                    color = JrColor.Deep,
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = JrType.CardTitle.copy(fontSize = JrType.CardTitle.fontSize * 1.11f),
                    color = Color.White,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatPhoneForDisplay(profile.phone),
                    style = JrType.BodySmall,
                    color = JrColor.OnDeepMuted,
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            villageName,
                            style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (profile.verified) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(JrColor.Blue)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 5.dp, height = 3.dp)
                                        .rotate(-45f)
                                        .drawLeftBottom(JrColor.Blue, strokeWidth = 1.5.dp),
                                )
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                stringResource(R.string.profile_verified),
                                style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = formatCount(profile.reportsFiled),
                label = stringResource(R.string.profile_stat_reports),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = formatCount(profile.villageScore),
                label = stringResource(R.string.profile_stat_score),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stringResource(
                    R.string.profile_months_short,
                    formatCount(profile.memberSinceMonths),
                ),
                label = stringResource(R.string.profile_stat_member_since),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            value,
            style = JrType.CardTitle.copy(fontSize = JrType.CardTitle.fontSize),
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.91f),
            color = JrColor.OnDeepMuted,
        )
    }
}

@Composable
private fun SettingsCard(
    profile: Profile,
    languageName: String,
    villageName: String,
    onEditLanguage: () -> Unit,
    onEditVillage: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(24.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(24.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        SettingRow(
            icon = SettingIcon.Circle,
            label = stringResource(R.string.setting_language),
            subtitle = stringResource(R.string.setting_language_sub),
            value = languageName,
            onClick = onEditLanguage,
        )
        SettingRow(
            icon = SettingIcon.Droplet,
            label = stringResource(R.string.setting_village),
            subtitle = stringResource(R.string.setting_village_sub),
            value = villageName,
            onClick = onEditVillage,
        )
        SettingRow(
            icon = SettingIcon.Square,
            label = stringResource(R.string.setting_household),
            subtitle = stringResource(R.string.setting_household_sub),
            value = formatCount(profile.householdSize),
        )
        SettingRow(
            icon = SettingIcon.Tank,
            label = stringResource(R.string.setting_source),
            subtitle = stringResource(R.string.setting_source_sub),
            value = WaterStrings.source(profile.sourceKey, profile.sourceNumber),
        )
    }
}

/** The four glyphs the design distinguishes settings rows with, drawn as geometry. */
private enum class SettingIcon { Circle, Droplet, Square, Tank }

@Composable
private fun SettingRow(
    icon: SettingIcon,
    label: String,
    subtitle: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(JrColor.ChipFill, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (icon) {
                SettingIcon.Circle ->
                    Box(Modifier.size(15.dp).border(2.dp, JrColor.Primary, RoundedCornerShape(50)))
                SettingIcon.Droplet -> Droplet(size = 15.dp, color = JrColor.Primary)
                SettingIcon.Square ->
                    Box(Modifier.size(14.dp).border(2.dp, JrColor.Primary, RoundedCornerShape(4.dp)))
                SettingIcon.Tank -> Box(
                    Modifier.size(14.dp).border(
                        2.dp,
                        JrColor.Primary,
                        RoundedCornerShape(
                            topStart = 3.dp,
                            topEnd = 3.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp,
                        ),
                    ),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.08f),
                color = JrColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = JrColor.Muted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = JrType.Label.copy(fontSize = JrType.BodySmall.fontSize),
            color = JrColor.Primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlertsCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(24.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(24.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.profile_alerts_title),
                style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.08f),
                color = JrColor.Ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.profile_alerts_subtitle),
                style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = JrColor.Muted,
            )
        }
        Spacer(Modifier.width(14.dp))
        JrSwitch(checked = enabled, onCheckedChange = onChange)
    }
}

/** 56×32 pill switch, matching the design rather than Material's. */
@Composable
private fun JrSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(
        targetValue = if (checked) JrColor.Primary else JrColor.Border,
        label = "switchTrack",
    )
    val knobOffset: Dp by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "switchKnob",
    )
    Box(
        Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .size(26.dp)
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun HelpCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(24.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(24.dp))
            .padding(4.dp),
    ) {
        listOf(
            R.string.help_guide,
            R.string.help_contact,
            R.string.help_privacy,
        ).forEach { resId ->
            Row(
                Modifier.fillMaxWidth().padding(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(resId),
                    style = JrType.Label.copy(fontSize = JrType.BodySmall.fontSize * 1.08f),
                    color = JrColor.Ink,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                // A right-pointing chevron: the same two-stroke trick as the back button.
                Box(
                    Modifier
                        .size(8.dp)
                        .rotate(-135f)
                        .drawLeftBottom(JrColor.ChevronQuiet),
                )
            }
        }
    }
}

/** `+919876543210` reads as `+91 98765 43210` — the way it is written on a form. */
private fun formatPhoneForDisplay(phone: String): String {
    val digits = phone.filter(Char::isDigit)
    val local = digits.takeLast(10)
    if (local.length < 10) return phone
    return "+91 ${local.substring(0, 5)} ${local.substring(5)}"
}

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Composable
private fun ProfilePreview() {
    JalrakshaTheme {
        ProfileContent(
            state = ProfileUiState(
                profile = SampleAccount.profile,
                village = SampleData.villages.first(),
                loading = false,
            ),
            onAlertsChange = {},
            onSignOut = {},
            onEditLanguage = {},
            onEditVillage = {},
        )
    }
}
