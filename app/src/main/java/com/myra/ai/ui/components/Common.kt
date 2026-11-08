package com.myra.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.ai.ui.theme.MyraCard

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(MyraCard, RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun Title(text: String) = Text(text, fontSize = 18.sp, color = Color.White)

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF)
    Box(
        Modifier.clickable(onClick = onClick).background(bg, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = Color.White, fontSize = 13.sp) }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White)
            if (sub != null) Text(sub, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", color = Color.White, fontSize = 13.sp)
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun Pick(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $value", maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}
