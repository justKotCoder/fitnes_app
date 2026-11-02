@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.setnote.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.setnote.domain.*

internal val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

@Composable
fun NotebookApp(vm: NotebookViewModel) {
    val notebook by vm.notebook.collectAsStateWithLifecycle()
    val pid by vm.programId.collectAsStateWithLifecycle()
    val eid by vm.exerciseId.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val program = notebook.programs.find { it.id == pid }
    val exercise = notebook.exercises.find { it.id == eid && it.programId == pid }
    var programDialogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exerciseDialogId by rememberSaveable { mutableStateOf<Long?>(null) }
    val programDialog =
        programDialogId?.let { id ->
            if (id == 0L) Program(name = "") else notebook.programs.find { it.id == id }
        }
    val exerciseDialog =
        exerciseDialogId?.let { id ->
            if (id == 0L && pid != null)
                Exercise(programId = pid!!, name = "", type = ExerciseType.WEIGHT)
            else notebook.exercises.find { it.id == id && it.programId == pid }
        }
    var deletion by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var discardDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    BackHandler(pid != null || draft != null) {
        if (draft != null) discardDialog = true else vm.back()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        exercise?.name ?: program?.name ?: "Подход",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (pid != null)
                        IconButton(
                            onClick = { if (draft != null) discardDialog = true else vm.back() }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                        }
                },
                actions = {
                    if (exercise != null)
                        IconButton(
                            enabled = draft == null,
                            onClick = { exerciseDialogId = exercise.id },
                        ) {
                            Icon(Icons.Rounded.Edit, "Изменить упражнение")
                        }
                    else if (program != null) {
                        IconButton(
                            enabled = draft == null,
                            onClick = { programDialogId = program.id },
                        ) {
                            Icon(Icons.Rounded.Edit, "Переименовать программу")
                        }
                        IconButton(
                            enabled = draft == null,
                            onClick = {
                                deletion =
                                    "Удалить программу? Упражнения и их история останутся в сохранённых." to
                                        {
                                            vm.deleteProgram(program.id)
                                        }
                            },
                        ) {
                            Icon(Icons.Rounded.Delete, "Удалить программу")
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
            )
        },
        floatingActionButton = {
            if (exercise == null && draft == null && ready)
                FloatingActionButton(
                    onClick = {
                        if (program == null) programDialogId = 0L else exerciseDialogId = 0L
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        if (program == null) "Добавить программу" else "Добавить упражнение",
                    )
                }
        },
    ) { padding ->
        if (!ready)
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (busy) CircularProgressIndicator()
                else Button(onClick = vm::retry) { Text("Повторить загрузку") }
            }
        else
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentPadding =
                    PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (program == null) {
                    item {
                        Text(
                            "ТВОЙ ТЕМП. ТВОИ РЕЗУЛЬТАТЫ.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    item {
                        Text(
                            "Мои программы",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (notebook.programs.isEmpty())
                        item {
                            EmptyCard(
                                "Начни со своей программы",
                                "Например, «Верх» или «Ноги». Добавь упражнения в том порядке, в котором тебе удобно заниматься.",
                            )
                        }
                    items(notebook.programs, key = { "p${it.id}" }) { p ->
                        Card(
                            onClick = { vm.openProgram(p.id) },
                            shape = RoundedCornerShape(22.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(22.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.List,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "Упражнений: ${notebook.exercises.count { it.programId==p.id }}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "Только твои записи. Всё хранится на телефоне.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else if (exercise == null) {
                    item {
                        Text(
                            "Последние подходы — сразу перед глазами",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val exercises = notebook.exercises.filter { it.programId == program.id }
                    if (exercises.isEmpty())
                        item {
                            EmptyCard(
                                "Добавь первое упражнение",
                                "Название, тип нагрузки и, если нужно, фото тренажёра. Всё можно изменить позже.",
                            )
                        }
                    items(exercises, key = { "e${it.id}" }) { ex ->
                        ExerciseCard(
                            ex,
                            notebook.history(ex.id).firstOrNull(),
                            draft?.takeIf { it.exerciseId == ex.id },
                            busy,
                            draft != null,
                            vm,
                            onOpen = { vm.openExercise(ex.id) },
                            onEdit = { exerciseDialogId = ex.id },
                            onDelete = {
                                deletion =
                                    "Убрать «${ex.name}» из этой программы? Общая история сохранится." to
                                        {
                                            vm.deleteExercise(ex.id)
                                        }
                            },
                            onDiscard = { discardDialog = true },
                        )
                    }
                } else {
                    item {
                        ExerciseDetails(
                            exercise,
                            notebook.history(exercise.id),
                            draft,
                            busy,
                            vm,
                            onDiscard = { discardDialog = true },
                            onDeleteEntry = { entry ->
                                deletion =
                                    "Удалить запись за ${entry.date.format(dateFormat)} из общей истории во всех программах?" to
                                        {
                                            vm.deleteEntry(entry.id)
                                        }
                            },
                            onDeleteExercise = {
                                deletion =
                                    "Убрать «${exercise.name}» из этой программы? Общая история сохранится." to
                                        {
                                            vm.deleteExercise(exercise.id)
                                        }
                            },
                        )
                    }
                }
            }
    }
    programDialog?.let { p ->
        var name by rememberSaveable(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { if (!busy) programDialogId = null },
            title = { Text(if (p.id == 0L) "Новая программа" else "Название программы") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Например, Верх") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy,
                    onClick = { vm.saveProgram(p.copy(name = name)) { programDialogId = null } },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { programDialogId = null }) { Text("Отмена") }
            },
        )
    }
    exerciseDialog?.let { ex ->
        ExerciseDialog(
            ex,
            notebook.catalog,
            notebook.history(ex.id).isNotEmpty(),
            busy,
            error,
            onDismiss = { exerciseDialogId = null },
            onSave = { e, uri -> vm.saveExercise(e, uri) { exerciseDialogId = null } },
        )
    }
    deletion?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { deletion = null },
            title = { Text("Удалить запись?") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        action()
                        deletion = null
                    },
                    enabled = !busy,
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("Отмена") } },
        )
    }
    if (discardDialog)
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            title = { Text("Закрыть ввод?") },
            text = { Text("Несохранённые изменения подходов будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.discard()
                        discardDialog = false
                    }
                ) {
                    Text("Удалить изменения")
                }
            },
            dismissButton = {
                TextButton(onClick = { discardDialog = false }) { Text("Продолжить ввод") }
            },
        )
}

@Composable
internal fun EmptyCard(title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.AddCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
