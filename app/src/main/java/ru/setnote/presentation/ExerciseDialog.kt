package ru.setnote.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import ru.setnote.domain.*

@Composable
internal fun ExerciseDialog(
    ex: Exercise,
    exercises: List<Exercise>,
    hasHistory: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Exercise, Uri?) -> Unit,
) {
    var name by rememberSaveable(ex.id) { mutableStateOf(ex.name) }
    var note by rememberSaveable(ex.id) { mutableStateOf(ex.note) }
    var type by rememberSaveable(ex.id) { mutableStateOf(ex.type) }
    var perDumbbell by rememberSaveable(ex.id) { mutableStateOf(ex.perDumbbell) }
    var selectedUri by rememberSaveable(ex.id) { mutableStateOf<String?>(null) }
    var photo by rememberSaveable(ex.id) { mutableStateOf(ex.photo) }
    val existing =
        if (ex.id == 0L)
            exercises.find {
                it.identityKey() ==
                    ex.copy(name = name, type = type, perDumbbell = perDumbbell).identityKey()
            }
        else null
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) selectedUri = uri.toString()
        }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (ex.id == 0L) "Новое упражнение" else "Упражнение") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text("Название") },
                    singleLine = true,
                    enabled = !busy,
                )
                if (ex.id == 0L) {
                    Text("Сохранённые упражнения", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Добавь существующее: вес, фото, заметка и история будут общими во всех программах.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    exercises
                        .filter { exerciseNameKey(it.name).contains(exerciseNameKey(name)) }
                        .take(8)
                        .forEach { suggestion ->
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    onSave(suggestion.copy(id = 0, programId = ex.programId), null)
                                },
                            ) {
                                Text(
                                    "Добавить: ${suggestion.name} · ${suggestion.type.title}" +
                                        if (suggestion.perDumbbell) " · одна гантель" else ""
                                )
                            }
                        }
                } else {
                    Text(
                        "Изменения сохранятся во всех программах с этим упражнением.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Что записывать", style = MaterialTheme.typography.labelLarge)
                ExerciseType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        enabled = !busy && !hasHistory,
                        label = { Text(t.title) },
                    )
                }
                if (hasHistory)
                    Text(
                        "Способ учёта закреплён за историей. Для другого типа или веса создай новое упражнение.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (type == ExerciseType.WEIGHT)
                    Row {
                        Checkbox(perDumbbell, { perDumbbell = it }, enabled = !busy && !hasHistory)
                        Text(
                            "Указывать вес одной гантели",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                if (existing != null) {
                    Text(
                        "Это упражнение уже сохранено. Будет добавлена общая карточка с его текущими данными.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text("Моя подсказка") },
                        placeholder = { Text("Например, сиденье на делении 4") },
                        minLines = 2,
                        maxLines = 5,
                        enabled = !busy,
                    )
                    if (selectedUri != null || photo != null) {
                        AsyncImage(
                            selectedUri ?: photo?.let { File(it) },
                            "Фото упражнения",
                            Modifier.fillMaxWidth().height(150.dp),
                            contentScale = ContentScale.Crop,
                        )
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                selectedUri = null
                                photo = null
                            },
                        ) {
                            Text("Убрать фото")
                        }
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    ) {
                        Text(
                            if (selectedUri != null || photo != null) "Заменить фото"
                            else "Добавить фото"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    if (existing != null) {
                        onSave(existing.copy(id = 0, programId = ex.programId), null)
                    } else
                        onSave(
                            ex.copy(
                                name = name,
                                type = type,
                                note = note,
                                photo = photo,
                                perDumbbell = perDumbbell,
                            ),
                            selectedUri?.let(Uri::parse),
                        )
                },
            ) {
                Text(
                    if (busy) "Сохраняем…"
                    else if (existing != null) "Добавить в программу" else "Сохранить"
                )
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}
