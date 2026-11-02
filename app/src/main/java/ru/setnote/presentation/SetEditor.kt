package ru.setnote.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import ru.setnote.domain.*

@Composable
internal fun SetEditor(
    ex: Exercise,
    draft: EntryDraft,
    busy: Boolean,
    vm: NotebookViewModel,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val fieldsPerRow = if (ex.type == ExerciseType.WEIGHT) 2 else 1
    val requesters =
        remember(draft.rows.size, fieldsPerRow) {
            List(draft.rows.size * fieldsPerRow) { FocusRequester() }
        }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        TextButton(
            enabled = !busy,
            onClick = {
                DatePickerDialog(
                        context,
                        { _, y, m, d -> vm.date(LocalDate.of(y, m + 1, d)) },
                        draft.date.year,
                        draft.date.monthValue - 1,
                        draft.date.dayOfMonth,
                    )
                    .apply { datePicker.maxDate = System.currentTimeMillis() }
                    .show()
            },
        ) {
            Icon(Icons.Rounded.DateRange, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(draft.date.format(dateFormat))
        }
        Text(
            if (draft.entryId == 0L)
                "Новая запись. Добавляй подходы по мере выполнения; прошлые значения служат подсказкой."
            else "Редактирование сохранённой записи",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        draft.rows.forEachIndexed { index, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${index+1}",
                    modifier = Modifier.width(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ex.type == ExerciseType.WEIGHT)
                    NumberField(
                        row.weight,
                        { vm.updateRow(index, row.copy(weight = it)) },
                        "кг",
                        true,
                        busy,
                        Modifier.weight(1f).focusRequester(requesters[index * fieldsPerRow]),
                        ImeAction.Next,
                    ) {
                        requesters[index * fieldsPerRow + 1].requestFocus()
                    }
                val last = index == draft.rows.lastIndex
                NumberField(
                    if (ex.type == ExerciseType.TIME) row.seconds else row.reps,
                    {
                        if (ex.type == ExerciseType.TIME)
                            vm.updateRow(index, row.copy(seconds = it))
                        else vm.updateRow(index, row.copy(reps = it))
                    },
                    if (ex.type == ExerciseType.TIME) "секунды" else "повторы",
                    false,
                    busy,
                    Modifier.weight(1f)
                        .focusRequester(requesters[index * fieldsPerRow + fieldsPerRow - 1]),
                    if (last) ImeAction.Done else ImeAction.Next,
                ) {
                    if (last) {
                        keyboard?.hide()
                        focus.clearFocus()
                        vm.saveDraft()
                    } else requesters[(index + 1) * fieldsPerRow].requestFocus()
                }
                IconButton(
                    enabled = !busy && draft.rows.size > 1,
                    onClick = { vm.removeRow(index) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "Удалить подход ${index+1}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(enabled = !busy, onClick = vm::addRow) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text("Ещё подход")
        }
        Button(
            enabled = !busy && draft.rows.isNotEmpty(),
            onClick = {
                keyboard?.hide()
                focus.clearFocus()
                vm.saveDraft()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Сохраняем…" else "Сохранить подходы")
        }
        TextButton(enabled = !busy, onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
            Text("Отмена")
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    decimal: Boolean,
    disabled: Boolean,
    modifier: Modifier,
    action: ImeAction,
    onAction: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            if (
                next.text.length <= 10 &&
                    next.text.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }
            ) {
                field = next
                onChange(next.text)
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = !disabled,
        modifier =
            modifier.onFocusChanged {
                if (it.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = action,
            ),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }),
    )
}
