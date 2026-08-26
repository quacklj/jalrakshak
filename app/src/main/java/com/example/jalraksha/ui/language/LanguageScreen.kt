package com.example.jalraksha.ui.language

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.data.model.Language
import com.example.jalraksha.ui.components.Droplet
import com.example.jalraksha.ui.components.JrBackButton
import com.example.jalraksha.ui.components.JrPrimaryButton
import com.example.jalraksha.ui.components.JrStepDots
import com.example.jalraksha.ui.components.softShadow
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/**
 * Screen 02 — pick the language every alert and advisory will arrive in.
 *
 * Tapping a tile writes the choice straight away, so the screen you are looking at translates
 * itself under your finger. That is the point of the screen: you should be able to confirm you
 * picked right by reading the button underneath.
 */
@Composable
fun LanguageScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = viewModel(factory = LanguageViewModel.Factory),
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    LanguageContent(
        selected = selected,
        onSelect = viewModel::select,
        onContinue = { viewModel.commit(onContinue) },
        onSkip = { viewModel.commit(onContinue) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun LanguageContent(
    selected: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            JrStepDots(completed = 2, total = 3)
            Text(
                stringResource(R.string.action_skip),
                style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.93f),
                color = JrColor.Muted,
                modifier = Modifier.clickable(onClick = onSkip),
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(stringResource(R.string.language_title), style = JrType.Title, color = JrColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.language_subtitle),
            style = JrType.Body.copy(
                fontSize = JrType.Body.fontSize * 0.96f,
                lineHeight = JrType.Body.lineHeight * 0.93f,
            ),
            color = JrColor.Muted,
        )

        Spacer(Modifier.height(20.dp))

        // Eight fixed tiles in two columns. Laid out directly rather than with a lazy grid so the
        // whole set is always on screen — the design shows every language at once, and a nested
        // scroller here would hide half of them behind the footer.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Language.supported.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { language ->
                        LanguageTile(
                            language = language,
                            selected = language.code == selected,
                            onClick = { onSelect(language.code) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        LanguageFootnote(stringResource(Language.byCode(selected).nameRes))

        Spacer(Modifier.height(14.dp))
        JrPrimaryButton(text = stringResource(R.string.action_continue), onClick = onContinue)
    }
}

/**
 * The language's own name on top, its name in the language currently being read underneath — so
 * a Bengali reader looking for Tamil finds "தமிழ்" over "তামিল" rather than over an English word.
 */
@Composable
private fun LanguageTile(
    language: Language,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) JrColor.Primary else JrColor.SurfaceMuted,
        label = "languageTileBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else JrColor.Ink,
        label = "languageTileContent",
    )

    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.softShadow(
                        radius = 18.dp,
                        color = JrColor.Primary.copy(alpha = 0.5f),
                        elevation = 12.dp,
                    )
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(
                1.5.dp,
                if (selected) JrColor.Primary else JrColor.BorderSoft,
                RoundedCornerShape(18.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Text(
            language.native,
            style = JrType.Section.copy(fontSize = JrType.Section.fontSize * 1.13f),
            color = contentColor,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(language.nameRes),
            style = JrType.Label.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor.copy(alpha = 0.62f),
        )
    }
}

/** The tinted reassurance panel above the Continue button. */
@Composable
private fun LanguageFootnote(languageName: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.SurfaceMuted, RoundedCornerShape(18.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(JrColor.Border, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Droplet(size = 15.dp, color = JrColor.Primary)
        }
        Spacer(Modifier.width(12.dp))
        // The sentence is one resource with the language name substituted in, so translators can
        // move the name to wherever their grammar puts it.
        val sentence = stringResource(R.string.language_footnote, languageName)
        val nameStart = sentence.indexOf(languageName)
        Text(
            buildAnnotatedString {
                if (nameStart < 0) {
                    append(sentence)
                } else {
                    append(sentence.substring(0, nameStart))
                    withStyle(
                        SpanStyle(color = JrColor.Primary, fontWeight = FontWeight.ExtraBold),
                    ) {
                        append(languageName)
                    }
                    append(sentence.substring(nameStart + languageName.length))
                }
            },
            style = JrType.BodySmall,
            color = JrColor.Slate,
        )
    }
}

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Preview(name = "ಕನ್ನಡ", widthDp = 390, heightDp = 844, showBackground = true, locale = "kn")
@Composable
private fun LanguagePreview() {
    JalrakshaTheme {
        LanguageContent(
            selected = "en",
            onSelect = {},
            onContinue = {},
            onSkip = {},
            onBack = {},
        )
    }
}
