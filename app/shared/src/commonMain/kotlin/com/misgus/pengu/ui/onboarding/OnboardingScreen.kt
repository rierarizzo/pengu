package com.misgus.pengu.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.app_name
import com.misgus.pengu.app.shared.generated.resources.onboarding_no_sign_in
import com.misgus.pengu.app.shared.generated.resources.onboarding_tagline
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.screenPadding
import org.jetbrains.compose.resources.stringResource

/** Max width of the onboarding column, so it stays readable on a wide window. */
private val ONBOARDING_MAX_WIDTH = AppSizes.formMaxWidth

/**
 * First-run screen: what the app is, and how to sign in.
 *
 * Shown before the shell while there is no session, so a new user does not have
 * to find the sign-in card inside Settings. The form it renders depends on what
 * the backend offers through `signInMethods`.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val account = LocalAppServices.current.account

    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = ONBOARDING_MAX_WIDTH).padding(LocalWindowSize.current.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.app_name), style = AppType.screenTitle(), modifier = Modifier.sectionHeading())
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                stringResource(Res.string.onboarding_tagline),
                style = AppType.body(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.xl))
            if (SignInMethod.EmailPassword in account.signInMethods) {
                SignInForm()
            } else {
                Text(
                    stringResource(Res.string.onboarding_no_sign_in),
                    style = AppType.bodySecondary(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
