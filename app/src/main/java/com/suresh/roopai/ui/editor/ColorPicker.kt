package com.suresh.roopai.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.suresh.roopai.core.imaging.Colors

/** Preset swatches followed by a custom-colour button. */
@Composable
fun PaletteRow(palette: List<Int>, selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var custom by remember { mutableStateOf(false) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items(palette) { c ->
            Swatch(Color(c), selected == c, "Colour #%06X".format(c and 0xFFFFFF)) { onPick(c) }
        }
        item {
            val isCustom = selected != null && selected !in palette
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCustom) Color(selected!!) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { custom = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Colorize, contentDescription = "Custom colour", modifier = Modifier.size(18.dp)) }
        }
    }
    if (custom) {
        ColorPickerDialog(initial = selected ?: palette.first(), onDismiss = { custom = false }, onPick = { custom = false; onPick(it) })
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** Hue / saturation / brightness picker. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { Colors.rgbToHsv(initial) }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Colors.hsvToRgb(h, s, v)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(color)))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        Brush.horizontalGradient((0..6).map { Color(Colors.hsvToRgb(it * 60f, 1f, 1f)) }),
                    ),
                )
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..359f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = s, onValueChange = { s = it })
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = v, onValueChange = { v = it })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
