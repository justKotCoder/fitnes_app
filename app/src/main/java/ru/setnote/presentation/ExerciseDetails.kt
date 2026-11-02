@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.setnote.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import ru.setnote.domain.*

@Composable
internal fun ExerciseDetails(
    ex: Exercise,
    entries: List<Entry>,
    draft: EntryDraft?,
    busy: Boolean,
    vm: NotebookViewModel,
    onDiscard: () -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onDeleteExercise: () -> Unit,
) {
    var history by rememberSaveable(ex.id) { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (ex.photo != null)
            AsyncImage(
                File(ex.photo),
                "Фото ${ex.name}",
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp)).clickable {
                    fullPhoto = true
                },
                contentScale = ContentScale.Crop,
            )
        Text(
            ex.type.title +
                if (ex.perDumbbell && ex.type == ExerciseType.WEIGHT) " · вес одной гантели"
                else "",
            color = MaterialTheme.colorScheme.primary,
        )
        if (ex.note.isNotBlank()) Text(ex.note, style = MaterialTheme.typography.bodyLarge)
        else
            Text(
                "Добавь свою подсказку через значок карандаша.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        val last = entries.firstOrNull()
        if (last != null)
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Последняя запись · ${last.date.format(dateFormat)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(last.sets.joinToString("  ·  ") { it.summary(ex.type) })
                }
            }
        if (draft?.exerciseId == ex.id) SetEditor(ex, draft, busy, vm, onDiscard)
        else
            Button(
                enabled = draft == null && !busy,
                onClick = { vm.begin(ex) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Записать подходы")
            }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        TextButton(onClick = { history = !history }, modifier = Modifier.fillMaxWidth()) {
            Text("История · ${entries.size}", modifier = Modifier.weight(1f))
            Icon(
                if (history) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null,
            )
        }
        if (history) {
            OutlinedButton(
                enabled = draft == null && !busy,
                onClick = { vm.begin(ex, newDate = true) },
            ) {
                Text("Добавить запись за другую дату")
            }
            if (entries.isEmpty())
                Text(
                    "Здесь появятся твои записи по датам.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            entries.forEach { entry ->
                Card(
                    colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            entry.date.format(dateFormat),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(entry.sets.joinToString("  ·  ") { it.summary(ex.type) })
                        Row {
                            TextButton(
                                enabled = draft == null && !busy,
                                onClick = { vm.begin(ex, entry) },
                            ) {
                                Text("Изменить")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                enabled = draft == null && !busy,
                                onClick = { onDeleteEntry(entry) },
                            ) {
                                Icon(Icons.Rounded.Delete, "Удалить запись")
                            }
                        }
                    }
                }
            }
        }
        TextButton(enabled = draft == null && !busy, onClick = onDeleteExercise) {
            Text("Убрать из программы", color = MaterialTheme.colorScheme.error)
        }
    }
    if (fullPhoto && ex.photo != null)
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullPhoto = false }) {
            Card {
                Column {
                    AsyncImage(
                        File(ex.photo),
                        "Фото упражнения",
                        Modifier.fillMaxWidth().heightIn(max = 550.dp),
                        contentScale = ContentScale.Fit,
                    )
                    TextButton(onClick = { fullPhoto = false }) { Text("Закрыть") }
                }
            }
        }
}
