package com.example.jalraksha.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.jalraksha.R
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/**
 * The design's input shell: 56dp tall, 18dp radius, pale fill, hairline border that picks up the
 * brand blue on focus. [leading] and [trailing] slot in the "+91" prefix and the "Show" toggle.
 */
@Composable
private fun FieldShell(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) JrColor.Primary else JrColor.BorderSoft,
        label = "fieldBorder",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(JrColor.SurfaceMuted, RoundedCornerShape(18.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Field label — the 12sp muted caption that sits 8dp above every input. */
@Composable
fun JrFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = JrType.Label, color = JrColor.Muted, modifier = modifier)
}

/**
 * Mobile-number input with the fixed `+91` country prefix and a hairline divider, exactly as drawn.
 * Digits only; the caller receives the raw digits with no spacing.
 */
@Composable
fun JrPhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    var focused by remember { mutableStateOf(false) }
    FieldShell(
        modifier = modifier,
        focused = focused,
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+91", style = JrType.Input.copy(fontSize = JrType.Input.fontSize * 0.94f), color = JrColor.Ink)
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .width(1.dp)
                        .height(22.dp)
                        .background(JrColor.Border),
                )
            }
        },
    ) {
        JrInnerTextField(
            value = value,
            // Strip anything the keyboard or a paste might sneak in; store 10 raw digits.
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(10)) },
            placeholder = stringResource(R.string.field_mobile_placeholder),
            keyboardType = KeyboardType.Phone,
            imeAction = imeAction,
            visualTransformation = PhoneVisualTransformation,
            onFocusChanged = { focused = it },
        )
    }
}

/** Bare numeric input in the same shell — used for the 6-digit OTP. */
@Composable
fun JrCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    maxLength: Int = 6,
    onImeAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    FieldShell(modifier = modifier, focused = focused) {
        JrInnerTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
            placeholder = placeholder,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            onImeAction = onImeAction,
            visualTransformation = VisualTransformation.None,
            onFocusChanged = { focused = it },
        )
    }
}

/** Password input with the inline Show/Hide toggle from the design. */
@Composable
fun JrPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    FieldShell(
        modifier = modifier,
        focused = focused,
        trailing = {
            Text(
                text = stringResource(if (visible) R.string.action_hide else R.string.action_show),
                style = JrType.Label,
                color = JrColor.Primary,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { visible = !visible }
                    .padding(4.dp),
            )
        },
    ) {
        JrInnerTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.field_password_placeholder),
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            onImeAction = onImeAction,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            onFocusChanged = { focused = it },
        )
    }
}

/** Shared [BasicTextField] body so both fields agree on caret colour, style and placeholder. */
@Composable
private fun JrInnerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    visualTransformation: VisualTransformation,
    onFocusChanged: (Boolean) -> Unit,
    onImeAction: () -> Unit = {},
) {
    val selectionColors = TextSelectionColors(
        handleColor = JrColor.Primary,
        backgroundColor = JrColor.ChipFill,
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = JrType.Input.copy(color = JrColor.Ink),
                cursorBrush = SolidColor(JrColor.Primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction() },
                    onGo = { onImeAction() },
                ),
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
            )
            if (value.isEmpty()) {
                Text(placeholder, style = JrType.Input, color = JrColor.Faint)
            }
        }
    }
}

/**
 * Renders `9876543210` as `98765 43210` without changing the stored value.
 *
 * The offset map has to account for the single space we insert after the 5th digit, otherwise the
 * caret drifts once the number is long enough to be split.
 */
private val PhoneVisualTransformation = VisualTransformation { text ->
    val digits = text.text
    val formatted = if (digits.length > 5) {
        digits.substring(0, 5) + " " + digits.substring(5)
    } else {
        digits
    }
    TransformedText(
        AnnotatedString(formatted),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > 5) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > 5) offset - 1 else offset
        },
    )
}

/** 20dp rounded checkbox, filled blue with a white tick when [checked]. */
@Composable
fun JrCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(
                if (checked) JrColor.Primary else JrColor.Surface,
                RoundedCornerShape(6.dp),
            )
            .border(
                1.5.dp,
                if (checked) JrColor.Primary else JrColor.RadioBorder,
                RoundedCornerShape(6.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            // The tick only — the rounded square above is already the checkbox's fill, so drawing
            // a JrCheck here would stamp a second, circular background inside it.
            Box(
                Modifier
                    .size(width = 9.dp, height = 5.dp)
                    .rotate(-45f)
                    .drawLeftBottom(Color.White),
            )
        }
    }
}
