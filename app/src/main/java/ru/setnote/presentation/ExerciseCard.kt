@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.setnote.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import ru.setnote.domain.*

@Composable
internal fun ExerciseCard(
    ex: Exercise,
    last: Entry?,
    draft: EntryDraft?,
    busy: Boolean,
    hasDraft: Boolean,
    vm: NotebookViewModel,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDiscard: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable(enabled = !hasDraft, onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            ex.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            last?.date?.format(dateFormat) ?: "Пока без записей",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(enabled = !hasDraft, onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, "Действия с упражнением")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Изменить") },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Поднять выше") },
                            onClick = {
                                menu = false
                                vm.move(ex.id, -1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Опустить ниже") },
                            onClick = {
                                menu = false
                                vm.move(ex.id, 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Убрать из программы") },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (ex.photo != null) {
                AsyncImage(
                    model = File(ex.photo),
                    contentDescription = "Фото ${ex.name}",
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !hasDraft, onClick = onOpen),
                    contentScale = ContentScale.Fit,
                )
            }
            if (ex.note.isNotBlank()) {
                Text(
                    text = ex.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (last != null) {
                Text(
                    "Подходов: ${last.sets.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    last.sets.joinToString("  ·  ") { it.summary(ex.type) },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (ex.perDumbbell && ex.type == ExerciseType.WEIGHT)
                Text(
                    "Вес одной гантели",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            if (draft != null) SetEditor(ex, draft, busy, vm, onDiscard)
            else
                OutlinedButton(
                    onClick = { vm.begin(ex) },
                    enabled = !hasDraft && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Записать подходы")
                }
        }
    }
}
