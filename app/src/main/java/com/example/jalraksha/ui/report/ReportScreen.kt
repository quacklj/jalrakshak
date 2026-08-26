package com.example.jalraksha.ui.report

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.data.SampleAccount
import com.example.jalraksha.data.SampleData
import com.example.jalraksha.data.model.PastReport
import com.example.jalraksha.data.model.ReportSeverity
import com.example.jalraksha.data.model.WaterIssue
import com.example.jalraksha.locale.LocalAppLocale
import com.example.jalraksha.ui.components.Droplet
import com.example.jalraksha.ui.components.JrBackButton
import com.example.jalraksha.ui.components.JrChip
import com.example.jalraksha.ui.components.JrPrimaryButton
import com.example.jalraksha.ui.text.WaterStrings
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.text.formatShortDate
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 06 — tell the gram panchayat something is wrong with the water. */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = viewModel(factory = ReportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportContent(
        state = state,
        onBack = onBack,
        onIssueSelected = viewModel::selectIssue,
        onSeveritySelected = viewModel::selectSeverity,
        onNoteChange = viewModel::onNoteChange,
        onTogglePhoto = viewModel::togglePhoto,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun ReportContent(
    state: ReportUiState,
    onBack: () -> Unit,
    onIssueSelected: (WaterIssue) -> Unit,
    onSeveritySelected: (ReportSeverity) -> Unit,
    onNoteChange: (String) -> Unit,
    onTogglePhoto: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLocale.current.language

    Column(
        modifier
            .fillMaxSize()
            .background(JrColor.SurfaceMuted)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ReportHeader(
            villageName = state.village?.displayName(language).orEmpty(),
            sourceKey = state.sourceKey,
            sourceNumber = state.sourceNumber,
            onBack = onBack,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 20.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.report_question_issue)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WaterIssue.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { issue ->
                                IssueTile(
                                    issue = issue,
                                    selected = issue == state.issue,
                                    onClick = { onIssueSelected(issue) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Section(stringResource(R.string.report_question_severity)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportSeverity.entries.forEach { severity ->
                        SeverityTile(
                            severity = severity,
                            selected = severity == state.severity,
                            onClick = { onSeveritySelected(severity) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Section(stringResource(R.string.report_note_label)) {
                NoteField(note = state.note, onNoteChange = onNoteChange)
            }

            PhotoRow(hasPhoto = state.hasPhoto, onToggle = onTogglePhoto)

            JrPrimaryButton(
                text = stringResource(
                    if (state.submitting) R.string.report_sending else R.string.report_submit,
                ),
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.submitting,
            )

            when (state.outcome) {
                SubmitOutcome.Sent -> OutcomeText(R.string.report_sent, JrColor.Primary)
                SubmitOutcome.Failed -> OutcomeText(R.string.report_failed, JrColor.Danger)
                SubmitOutcome.None -> Unit
            }

            Section(stringResource(R.string.report_recent)) {
                if (state.recent.isEmpty()) {
                    Text(
                        stringResource(R.string.report_none_yet),
                        style = JrType.BodySmall,
                        color = JrColor.Muted,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.recent.forEach { PastReportRow(it) }
                    }
                }
            }

            // Clearance for the floating bottom nav.
            Spacer(Modifier.height(60.dp))
        }
    }
}

/** White panel: who is reporting, about which source, and what happens next. */
@Composable
private fun ReportHeader(
    villageName: String,
    sourceKey: String,
    sourceNumber: Int,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JrBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.report_title), style = JrType.ScreenTitle, color = JrColor.Ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.report_source_line,
                        villageName,
                        WaterStrings.source(sourceKey, sourceNumber),
                    ),
                    style = JrType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = JrColor.Muted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(JrColor.ChipFill, RoundedCornerShape(20.dp))
                .border(1.dp, JrColor.ChipBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).background(JrColor.Primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Droplet(size = 15.dp, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            // The window is substituted rather than baked into the sentence, so translators can
            // put "24 hours" wherever their grammar wants it.
            val window = stringResource(R.string.report_tester_window)
            val sentence = stringResource(R.string.report_tester_notice, window)
            val start = sentence.indexOf(window)
            Text(
                buildAnnotatedString {
                    if (start < 0) {
                        append(sentence)
                    } else {
                        append(sentence.substring(0, start))
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(window) }
                        append(sentence.substring(start + window.length))
                    }
                },
                style = JrType.BodySmall,
                color = JrColor.Deep,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
            color = JrColor.Ink,
        )
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun IssueTile(
    issue: WaterIssue,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) JrColor.Primary else JrColor.Surface,
        label = "issueBackground",
    )
    Row(
        modifier
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
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Droplet(size = 15.dp, color = if (selected) Color.White else JrColor.GridLine)
        Spacer(Modifier.width(10.dp))
        Text(
            WaterStrings.issue(issue),
            style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.08f),
            color = if (selected) Color.White else JrColor.Ink,
        )
    }
}

@Composable
private fun SeverityTile(
    severity: ReportSeverity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) JrColor.Deep else JrColor.Surface,
        label = "severityBackground",
    )
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                1.5.dp,
                if (selected) JrColor.Deep else JrColor.BorderSoft,
                RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            WaterStrings.severity(severity),
            style = JrType.Label.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
            color = if (selected) Color.White else JrColor.Muted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoteField(note: String, onNoteChange: (String) -> Unit) {
    val selectionColors = TextSelectionColors(
        handleColor = JrColor.Primary,
        backgroundColor = JrColor.ChipFill,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(20.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                BasicTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    textStyle = JrType.BodySmall.copy(
                        color = JrColor.Ink,
                        fontSize = JrType.BodySmall.fontSize * 1.08f,
                        lineHeight = JrType.BodySmall.lineHeight * 1.12f,
                    ),
                    cursorBrush = SolidColor(JrColor.Primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (note.isEmpty()) {
                    Text(
                        stringResource(R.string.report_note_placeholder),
                        style = JrType.BodySmall.copy(
                            fontSize = JrType.BodySmall.fontSize * 1.08f,
                            lineHeight = JrType.BodySmall.lineHeight * 1.12f,
                        ),
                        color = JrColor.Faint,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                R.string.report_note_counter,
                formatCount(note.length),
                formatCount(ReportViewModel.NOTE_MAX_LENGTH),
            ),
            style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
            color = JrColor.Faint,
        )
    }
}

/** The add-photo tile, and the attachment once there is one. */
@Composable
private fun PhotoRow(hasPhoto: Boolean, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .weight(1f)
                .height(92.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JrColor.Surface)
                .dashedBorder(JrColor.GridLine, RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !hasPhoto,
                    onClick = onToggle,
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(26.dp).background(JrColor.ChipFill, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = JrType.Section, color = JrColor.Primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.report_add_photo),
                style = JrType.Caption,
                color = JrColor.Muted,
            )
        }

        if (hasPhoto) {
            Box(
                Modifier
                    .weight(1f)
                    .height(92.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(JrColor.SurfaceMuted)
                    .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    )
                    .padding(10.dp),
            ) {
                Text(
                    stringResource(R.string.report_photo_attached),
                    style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.87f),
                    color = JrColor.Slate,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                Text(
                    stringResource(R.string.report_remove_photo),
                    style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.87f),
                    color = JrColor.Primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PastReportRow(report: PastReport) {
    val locale = LocalAppLocale.current
    val resolved = report.statusKey == "resolved"
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(20.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(20.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // The reporter's own words when they wrote any; otherwise the issue they tapped.
                report.note.ifBlank { WaterStrings.issue(report.issueKey) },
                style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
                color = JrColor.Ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(
                    R.string.report_source_line,
                    formatShortDate(report.createdAt, locale),
                    WaterStrings.source(report.sourceKey, report.sourceNumber),
                ),
                style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = JrColor.Muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        JrChip(
            text = WaterStrings.reportStatus(report.statusKey),
            background = if (resolved) JrColor.ChipFill else JrColor.TrackQuiet,
            contentColor = if (resolved) JrColor.Primary else JrColor.Muted,
        )
    }
}

@Composable
private fun OutcomeText(resId: Int, color: Color) {
    Text(
        stringResource(resId),
        style = JrType.BodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A dashed outline, which Compose has no first-class border for. */
private fun Modifier.dashedBorder(
    color: Color,
    shape: RoundedCornerShape,
) = drawBehind {
    val stroke = Stroke(
        width = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
    )
    val radius = shape.topStart.toPx(size, this)
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke.width / 2, stroke.width / 2),
        size = androidx.compose.ui.geometry.Size(
            size.width - stroke.width,
            size.height - stroke.width,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = stroke,
    )
}

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Composable
private fun ReportPreview() {
    JalrakshaTheme {
        ReportContent(
            state = ReportUiState(
                village = SampleData.villages.first(),
                sourceKey = "handpump",
                sourceNumber = 4,
                note = "Water from handpump 4 tastes salty since Tuesday morning.",
                recent = SampleAccount.pastReports,
            ),
            onBack = {},
            onIssueSelected = {},
            onSeveritySelected = {},
            onNoteChange = {},
            onTogglePhoto = {},
            onSubmit = {},
        )
    }
}
