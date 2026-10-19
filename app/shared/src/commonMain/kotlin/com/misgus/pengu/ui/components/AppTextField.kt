package com.misgus.pengu.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import com.misgus.pengu.ui.icons.MaterialIcons
import com.misgus.pengu.ui.theme.AppIconSize
import com.misgus.pengu.ui.theme.AppSizes

/*
 * The app's text field.
 *
 * Material's stock `OutlinedTextField` reads as a default M3 control: a 2dp
 * corner and a grey outline that ignores the design language. This wrapper pins
 * it to the same shape, surface, focus colour and typography as the rest of the
 * app, so a field is never styled at the call site and every form stays
 * consistent.
 */

/**
 * A themed single-field form control.
 *
 * The base is an outlined field on the low surface tier: a subtle border at
 * rest, the amber [MaterialTheme.colorScheme.primary] on focus, and the error
 * role on validation. The label floats only when [label] is set; search-style
 * fields use [placeholder] instead.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = AppSizes.fieldHeight),
        enabled = enabled,
        label = label?.let { text -> { Text(text) } },
        placeholder = placeholder?.let { text -> { Text(text) } },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(AppIconSize.md)) } },
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = appTextFieldColors(),
    )
}

/**
 * Search field: no floating label, a leading magnifier and, when non-empty, a
 * clear action. Shared by Search and the streams dialog so both match.
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = MaterialIcons.Search,
        trailingIcon =
            if (value.isNotEmpty()) {
                {
                    AppIconButton(
                        icon = MaterialIcons.Close,
                        contentDescription = clearDescription,
                        onClick = { onValueChange("") },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
    )
}

/**
 * Colours for [AppTextField].
 *
 * All values come from the scheme: the field carries no fill of its own, so it
 * reads the surface it is placed on like every other flat control, and the
 * border is `outlineVariant` at rest and `primary` on focus. Every icon, label
 * and placeholder reads an on-surface role instead of Material's untouched
 * defaults.
 */
@Composable
private fun appTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        errorBorderColor = MaterialTheme.colorScheme.error,
        cursorColor = MaterialTheme.colorScheme.primary,
        errorCursorColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorLabelColor = MaterialTheme.colorScheme.error,
        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
