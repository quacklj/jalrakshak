package com.example.jalraksha.ui.signin

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.ui.components.JalrakshaMark
import com.example.jalraksha.ui.components.JrCheckbox
import com.example.jalraksha.ui.components.JrCodeField
import com.example.jalraksha.ui.components.JrFieldLabel
import com.example.jalraksha.ui.components.JrPasswordField
import com.example.jalraksha.ui.components.JrPhoneField
import com.example.jalraksha.ui.components.JrPrimaryButton
import com.example.jalraksha.ui.components.JrSecondaryButton
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 01 — sign in with a mobile number and password, or fall back to an OTP. */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = viewModel(factory = SignInViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Phone verification needs a real Activity for the reCAPTCHA fallback.
    val activity = LocalActivity.current

    if (state.signedIn) {
        // Handing control to the navigator during composition would fight the current frame.
        LaunchedEffect(Unit) { onSignedIn() }
    }

    SignInContent(
        state = state,
        onPhoneChange = viewModel::onPhoneChange,
        onPasswordChange = viewModel::onPasswordChange,
        onKeepSignedInChange = viewModel::onKeepSignedInChange,
        onSignIn = viewModel::signIn,
        onRegister = viewModel::register,
        onForgot = viewModel::forgotPassword,
        onRequestOtp = { activity?.let(viewModel::requestOtp) },
        modifier = modifier,
    )

    if (state.otpVerificationId != null) {
        OtpDialog(
            phone = state.phone,
            code = state.otpCode,
            loading = state.loading,
            message = state.message,
            onCodeChange = viewModel::onOtpCodeChange,
            onVerify = viewModel::verifyOtp,
            onDismiss = viewModel::dismissOtp,
        )
    }
}

@Composable
private fun SignInContent(
    state: SignInUiState,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onKeepSignedInChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onRequestOtp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(JrColor.Surface)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp)
            .padding(bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        JalrakshaMark(size = 88.dp)

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.brand_wordmark), style = JrType.Eyebrow, color = JrColor.Primary)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.signin_title),
            style = JrType.Title,
            color = JrColor.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.signin_subtitle),
            style = JrType.Body,
            color = JrColor.Muted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        JrFieldLabel(stringResource(R.string.field_mobile_number), Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        JrPhoneField(value = state.phone, onValueChange = onPhoneChange)

        Spacer(Modifier.height(16.dp))

        JrFieldLabel(stringResource(R.string.field_password), Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        JrPasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            onImeAction = onSignIn,
        )

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                JrCheckbox(checked = state.keepSignedIn, onCheckedChange = onKeepSignedInChange)
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.signin_keep_signed_in),
                    style = JrType.BodySmall.copy(fontSize = JrType.Body.fontSize * 0.93f),
                    color = JrColor.Ink,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onKeepSignedInChange(!state.keepSignedIn) },
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.signin_forgot),
                style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.93f),
                color = JrColor.Primary,
                modifier = Modifier.clickable(onClick = onForgot),
            )
        }

        Spacer(Modifier.height(20.dp))

        JrPrimaryButton(
            text = stringResource(R.string.action_sign_in),
            onClick = onSignIn,
            enabled = state.canSubmit,
            loading = state.loading && state.otpVerificationId == null,
        )
        Spacer(Modifier.height(12.dp))
        JrSecondaryButton(
            text = stringResource(R.string.signin_use_otp),
            onClick = onRequestOtp,
            enabled = state.canRequestOtp && !state.loading,
        )

        // The design keeps a single inline slot for feedback rather than a floating snackbar, so
        // the message stays anchored to the form it belongs to.
        state.message?.let { message ->
            Spacer(Modifier.height(14.dp))
            Text(
                messageText(message, state.phone),
                style = JrType.BodySmall,
                color = if (message.isError) ErrorRed else JrColor.Muted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            buildAnnotatedString {
                append(stringResource(R.string.signin_new_here))
                append(" ")
                withStyle(SpanStyle(color = JrColor.Primary, fontWeight = FontWeight.ExtraBold)) {
                    append(stringResource(R.string.action_register))
                }
            },
            style = JrType.BodySmall.copy(fontSize = JrType.Body.fontSize * 0.93f),
            color = JrColor.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(enabled = state.canSubmit, onClick = onRegister),
        )
    }
}

/** The one message with an argument is the OTP confirmation; the rest are plain sentences. */
@Composable
private fun messageText(message: UiMessage, phone: String): String =
    if (message.id == R.string.otp_code_sent) {
        stringResource(message.id, phone)
    } else {
        stringResource(message.id)
    }

/**
 * Not part of the design file — but "Get an OTP instead" has to land somewhere, and a full OTP
 * screen was never drawn. Styled from the same tokens so it does not read as a stock dialog.
 */
@Composable
private fun OtpDialog(
    phone: String,
    code: String,
    loading: Boolean,
    message: UiMessage?,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(JrColor.Surface, RoundedCornerShape(26.dp))
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.otp_title), style = JrType.Section, color = JrColor.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.otp_sent_to, phone),
                style = JrType.BodySmall,
                color = JrColor.Muted,
            )
            Spacer(Modifier.height(18.dp))
            JrCodeField(
                value = code,
                onValueChange = onCodeChange,
                placeholder = stringResource(R.string.otp_placeholder),
                onImeAction = onVerify,
            )
            if (message?.isError == true) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(message.id), style = JrType.BodySmall, color = ErrorRed)
            }
            Spacer(Modifier.height(18.dp))
            JrPrimaryButton(
                text = stringResource(R.string.action_verify),
                onClick = onVerify,
                enabled = code.length == 6,
                loading = loading,
            )
            Spacer(Modifier.height(10.dp))
            JrSecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        }
    }
}

/**
 * The design has no error state, so it has no red. This is the smallest departure that still reads
 * as "something is wrong" next to the blue palette.
 */
private val ErrorRed = Color(0xFFC2410C)

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Preview(name = "தமிழ்", widthDp = 390, heightDp = 844, showBackground = true, locale = "ta")
@Composable
private fun SignInPreview() {
    JalrakshaTheme {
        SignInContent(
            state = SignInUiState(phone = "9876543210", password = "jalraksha"),
            onPhoneChange = {},
            onPasswordChange = {},
            onKeepSignedInChange = {},
            onSignIn = {},
            onRegister = {},
            onForgot = {},
            onRequestOtp = {},
        )
    }
}
