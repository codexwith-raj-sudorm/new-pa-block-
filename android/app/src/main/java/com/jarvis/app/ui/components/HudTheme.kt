package com.jarvis.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Shared HUD field palette: deep-navy fill, gold focus, transparent rest underline. */
@Composable
fun hudFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF0E1930),
    unfocusedContainerColor = Color(0xFF0E1930),
    disabledContainerColor = Color(0xFF0E1930),
    focusedIndicatorColor = HudGold,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = HudInk,
    unfocusedTextColor = HudInk,
    cursorColor = HudGold,
    focusedPlaceholderColor = Color(0xFF8B949E),
    unfocusedPlaceholderColor = Color(0xFF8B949E)
)

/** App-wide text field: same params as Material's, HUD colors by default. */
@Composable
fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: TextFieldColors = hudFieldColors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors = colors
    )
}

/** App-wide dialog: same slots as Material's, HUD surface by default. */
@Composable
fun HudDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF0B1322),
        titleContentColor = HudGold,
        textContentColor = HudInk,
        tonalElevation = 0.dp
    )
}
