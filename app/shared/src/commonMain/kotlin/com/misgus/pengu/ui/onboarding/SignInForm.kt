package com.misgus.pengu.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_sign_in
import com.misgus.pengu.app.shared.generated.resources.field_email
import com.misgus.pengu.app.shared.generated.resources.field_password
import com.misgus.pengu.app.shared.generated.resources.field_password_hide
import com.misgus.pengu.app.shared.generated.resources.field_password_show
import com.misgus.pengu.core.repository.SignInRequest
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.AppIconButton
import com.misgus.pengu.ui.components.AppTextField
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.FluentIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Email + password form, shared by the onboarding screen and the Settings card.
 *
 * It owns its input, its busy flag and its error text, and signs in through the
 * account repository; callers only decide where it appears.
 */
@Composable
fun SignInForm(modifier: Modifier = Modifier) {
    val account = LocalAppServices.current.account
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }

    val submit: () -> Unit = {
        if (!busy && email.isNotBlank() && password.isNotEmpty()) {
            busy = true
            error = null
            scope.launch {
                try {
                    account.signIn(SignInRequest.EmailPassword(email.trim(), password))
                    password = ""
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    error = failure.toUserMessage()
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        AppTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(Res.string.field_email),
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        AppTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.field_password),
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                AppIconButton(
                    icon = if (passwordVisible) FluentIcons.VisibilityOff else FluentIcons.Visibility,
                    contentDescription =
                        stringResource(
                            if (passwordVisible) Res.string.field_password_hide else Res.string.field_password_show,
                        ),
                    onClick = { passwordVisible = !passwordVisible },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        ActionButton(
            text = stringResource(Res.string.action_sign_in),
            onClick = submit,
            icon = FluentIcons.Login,
            enabled = email.isNotBlank() && password.isNotEmpty(),
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
