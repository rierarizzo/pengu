package com.misgus.pengu.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.action_try_again
import com.misgus.pengu.app.shared.generated.resources.profiles_empty
import com.misgus.pengu.app.shared.generated.resources.profiles_pin
import com.misgus.pengu.app.shared.generated.resources.profiles_subtitle
import com.misgus.pengu.app.shared.generated.resources.profiles_title
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.ui.components.ActionButton
import com.misgus.pengu.ui.components.ActionButtonStyle
import com.misgus.pengu.ui.components.ProfileAvatar
import com.misgus.pengu.ui.components.clickableSurface
import com.misgus.pengu.ui.components.focusRing
import com.misgus.pengu.ui.components.interactionHighlight
import com.misgus.pengu.ui.components.rememberInteraction
import com.misgus.pengu.ui.components.sectionHeading
import com.misgus.pengu.ui.components.toUserMessage
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.theme.AppSizes
import com.misgus.pengu.ui.theme.AppSpacing
import com.misgus.pengu.ui.theme.AppType
import com.misgus.pengu.ui.theme.LocalWindowSize
import com.misgus.pengu.ui.theme.screenPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Asks which profile to work with.
 *
 * Shown as a gate: the library, the watch progress and every write belong to a
 * profile, so there is no safe default. Choosing one is mandatory.
 */
@Composable
fun ProfilePicker(modifier: Modifier = Modifier) {
    val account = LocalAppServices.current.account
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = AppSizes.contentMaxWidth).fillMaxWidth().padding(LocalWindowSize.current.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.profiles_title), style = AppType.screenTitle(), modifier = Modifier.sectionHeading())
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                stringResource(Res.string.profiles_subtitle),
                style = AppType.bodySecondary(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.xl))

            val isFirstLoad = state.isLoadingProfiles && state.profiles.isEmpty()
            val profilesError = state.profilesError
            when {
                isFirstLoad -> CircularProgressIndicator()
                profilesError != null ->
                    Problem(
                        message = stringResource(profilesError.toUserMessage()),
                        onRetry = { scope.launch { account.loadProfiles() } },
                    )
                state.profiles.isEmpty() ->
                    Problem(
                        message = stringResource(Res.string.profiles_empty),
                        onRetry = { scope.launch { account.loadProfiles() } },
                    )
                else ->
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    ) {
                        state.profiles.forEach { profile ->
                            ProfileTile(profile) { scope.launch { account.selectProfile(profile) } }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: BackendProfile,
    onTap: () -> Unit,
) {
    val interaction = rememberInteraction()
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .focusRing(interaction)
                .background(interactionHighlight(interaction))
                .clickableSurface(interaction, onTap)
                .padding(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(102.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                profile = profile,
                size = 96.dp,
                initialStyle = MaterialTheme.typography.headlineLarge,
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Text(profile.name, style = AppType.itemTitle())
        if (profile.pinEnabled) {
            Text(stringResource(Res.string.profiles_pin), style = AppType.bodySecondary())
        }
    }
}

@Composable
private fun Problem(
    message: String,
    onRetry: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            MaterialIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(message, textAlign = TextAlign.Center, style = AppType.bodySecondary())
        Spacer(Modifier.height(AppSpacing.md))
        ActionButton(
            text = stringResource(Res.string.action_try_again),
            onClick = onRetry,
            icon = MaterialIcons.Refresh,
            style = ActionButtonStyle.Tonal,
        )
    }
}
