@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifeticker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeticker.data.Entry
import com.lifeticker.data.MAX_VALUE
import com.lifeticker.data.MIN_VALUE
import com.lifeticker.data.Stock
import com.lifeticker.data.StockList
import com.lifeticker.data.formatValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun moodLabel(v: Float): String = when {
    v >= 8 -> "Amazing"
    v >= 4 -> "Good"
    v >= 1 -> "Slightly up"
    v > -1 -> "Steady"
    v > -4 -> "Slightly down"
    v > -8 -> "Rough"
    else -> "Terrible"
}

// ---------------------------------------------------------------------------------------
// Create / edit a stock
// ---------------------------------------------------------------------------------------

@Composable
fun StockDialog(
    initial: Stock?,
    lists: List<StockList>,
    initialListIds: Set<String>,
    existingTickers: Set<String>,
    onDismiss: () -> Unit,
    onSave: (ticker: String, name: String, listIds: Set<String>) -> Unit,
    onCreateList: (String) -> String,
) {
    var ticker by remember { mutableStateOf(initial?.ticker ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selected by remember { mutableStateOf(initialListIds) }
    var newList by remember { mutableStateOf("") }

    val cleanTicker = ticker.trim().uppercase()
    val duplicate = cleanTicker.isNotEmpty() && cleanTicker != initial?.ticker && cleanTicker in existingTickers
    val valid = cleanTicker.isNotEmpty() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New stock" else "Edit ${initial.ticker}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { v -> ticker = v.filter { it.isLetterOrDigit() }.take(6).uppercase() },
                    label = { Text("Ticker (e.g. NINA)") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("You already have a stock called $cleanTicker") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Full name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(6.dp))
                Text("Lists", style = MaterialTheme.typography.titleSmall)
                if (lists.isEmpty()) {
                    Text(
                        "No lists yet. Create one below, e.g. Romance.",
                        color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
                lists.forEach { l ->
                    val checked = l.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - l.id else selected + l.id },
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected = if (it) selected + l.id else selected - l.id },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Up, checkmarkColor = androidx.compose.ui.graphics.Color.Black),
                        )
                        Text(l.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newList,
                        onValueChange = { newList = it.take(30) },
                        label = { Text("New list") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newList.isNotBlank(),
                        onClick = {
                            val id = onCreateList(newList.trim())
                            selected = selected + id
                            newList = ""
                        },
                    ) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(cleanTicker, name.trim(), selected) }) {
                Text(if (initial == null) "Create" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------
// Log / edit an entry
// ---------------------------------------------------------------------------------------

@Composable
fun EntryDialog(
    ticker: String,
    initial: Entry?,
    defaultValue: Float,
    onDismiss: () -> Unit,
    onSave: (timestamp: Long, value: Float, note: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = ZoneId.systemDefault()
    var value by remember { mutableFloatStateOf(initial?.value ?: defaultValue) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var dateTime by remember {
        mutableStateOf(
            initial?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), zone) }
                ?: LocalDateTime.now(zone).withSecond(0).withNano(0)
        )
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val color = trendColor(value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log $ticker" else "Edit entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        (if (value > 0) "+" else "") + formatValue(value),
                        color = color, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(" / 10", color = Palette.TextDim, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text(moodLabel(value), color = color, modifier = Modifier.padding(bottom = 10.dp))
                }
                Slider(
                    value = value,
                    onValueChange = { value = it.roundToInt().toFloat() },
                    valueRange = MIN_VALUE..MAX_VALUE,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = Palette.SurfaceHigh,
                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("−10", color = Palette.Down, style = MaterialTheme.typography.labelSmall)
                    Text("0", color = Palette.TextDim, style = MaterialTheme.typography.labelSmall)
                    Text("+10", color = Palette.Up, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.padding(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1.4f)) {
                        Text(dateTime.format(dateFmt), maxLines = 1)
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                        Text(dateTime.format(timeFmt), maxLines = 1)
                    }
                }
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    label = { Text("What happened? (optional)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                )
                if (onDelete != null) {
                    Spacer(Modifier.padding(4.dp))
                    HorizontalDivider()
                    TextButton(onClick = onDelete) { Text("Delete entry", color = Palette.Down) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(dateTime.atZone(zone).toInstant().toEpochMilli(), value, note)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDate) {
        // The Material date picker works in UTC midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        dateTime = dateTime.with(d)
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(dateTime.hour, dateTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    dateTime = dateTime.withHour(state.hour).withMinute(state.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Small reusable dialogs
// ---------------------------------------------------------------------------------------

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(30) },
                label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = Palette.Down) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ListMembersDialog(
    list: StockList,
    stocks: List<Stock>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(list.stockIds.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stocks in ${list.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (stocks.isEmpty()) Text("Create a stock first.", color = Palette.TextDim)
                stocks.forEach { s ->
                    val checked = s.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - s.id else selected + s.id },
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected = if (it) selected + s.id else selected - s.id },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Up, checkmarkColor = androidx.compose.ui.graphics.Color.Black),
                        )
                        Text(s.ticker, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(s.name, color = Palette.TextDim)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
