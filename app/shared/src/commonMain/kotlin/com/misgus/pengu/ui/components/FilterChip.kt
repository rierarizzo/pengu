package com.misgus.pengu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.misgus.pengu.ui.icons.MaterialIcons

/** One entry of a [FilterChip]'s menu. */
data class FilterOption(
    val key: String,
    val label: String,
)

/**
 * A chip that opens a list of choices, for a picker with more options than a row
 * of chips can hold (a run of genres, a catalog list).
 *
 * The label of the chip is the current choice, so the row always says what is
 * applied without being opened.
 */
@Composable
fun FilterChip(
    label: String,
    options: List<FilterOption>,
    selectedKey: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppChip(
            text = label,
            onClick = { expanded = true },
            enabled = enabled,
            trailingIcon = MaterialIcons.ChevronDown,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        if (option.key != selectedKey) onSelected(option.key)
                    },
                )
            }
        }
    }
}
