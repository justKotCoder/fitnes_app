package ru.setnote.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.setnote.domain.*

data class EntryDraft(
    val exerciseId: Long,
    val entryId: Long,
    val date: LocalDate,
    val rows: List<SetInput>,
)

class NotebookViewModel(
    private val repository: NotebookRepository,
    private val photos: PhotoRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val notebook = repository.notebook
    val programId = saved.getStateFlow<Long?>("program", null)
    val exerciseId = saved.getStateFlow<Long?>("exercise", null)
    private val _draft = MutableStateFlow(decode(saved["draft"]))
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        run {
            repository.load()
            if (
                exerciseId.value != null &&
                    notebook.value.exercises.none {
                        it.id == exerciseId.value && it.programId == programId.value
                    }
            )
                saved["exercise"] = null
            if (
                draft.value != null &&
                    notebook.value.catalog.none { it.id == draft.value?.exerciseId }
            )
                setDraft(null)
            _ready.value = true
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun retry() {
        run {
            repository.load()
            if (
                exerciseId.value != null &&
                    notebook.value.exercises.none {
                        it.id == exerciseId.value && it.programId == programId.value
                    }
            )
                saved["exercise"] = null
            if (
                draft.value != null &&
                    notebook.value.catalog.none { it.id == draft.value?.exerciseId }
            )
                setDraft(null)
            _ready.value = true
        }
    }

    fun openProgram(id: Long) {
        saved["program"] = id
    }

    fun openExercise(id: Long) {
        saved["exercise"] = id
    }

    fun back() {
        if (exerciseId.value != null) saved["exercise"] = null else saved["program"] = null
    }

    private fun run(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось сохранить. Попробуй ещё раз."
            } finally {
                _busy.value = false
            }
        }
    }

    fun saveProgram(program: Program, done: () -> Unit) = run {
        repository.saveProgram(program)
        done()
    }

    fun deleteProgram(id: Long) = run {
        repository.deleteProgram(id)
        saved["program"] = null
    }

    fun saveExercise(exercise: Exercise, photo: Uri?, done: () -> Unit) = run {
        val path = if (photo != null) photos.import(photo.toString()) else exercise.photo
        repository.saveExercise(exercise.copy(photo = path))
        done()
    }

    fun deleteExercise(id: Long) = run {
        repository.deleteExercise(id, programId.value ?: return@run)
        if (exerciseId.value == id) saved["exercise"] = null
        if (draft.value?.exerciseId == id) setDraft(null)
    }

    fun move(id: Long, direction: Int) = run {
        repository.moveExercise(id, programId.value ?: return@run, direction)
    }

    fun deleteEntry(id: Long) = run { repository.deleteEntry(id) }

    fun begin(exercise: Exercise, entry: Entry? = null, newDate: Boolean = false) {
        if (draft.value != null) return
        val today = notebook.value.history(exercise.id).find { it.date == LocalDate.now() }
        val selected = entry ?: today.takeUnless { newDate }
        val suggestion = selected ?: notebook.value.history(exercise.id).firstOrNull()
        setDraft(
            EntryDraft(
                exercise.id,
                selected?.id ?: 0,
                selected?.date ?: if (newDate) LocalDate.now().minusDays(1) else LocalDate.now(),
                selected?.sets?.map { it.input() }
                    ?: listOf(suggestion?.sets?.firstOrNull()?.input() ?: SetInput()),
            )
        )
    }

    fun updateRow(index: Int, input: SetInput) {
        draft.value?.let { d ->
            setDraft(d.copy(rows = d.rows.toMutableList().also { it[index] = input }))
        }
    }

    fun addRow() {
        draft.value?.let { draft ->
            val previous =
                notebook.value.history(draft.exerciseId).firstOrNull {
                    it.date.isBefore(draft.date)
                }
            val suggestion =
                previous?.sets?.getOrNull(draft.rows.size)?.input()
                    ?: draft.rows.lastOrNull()
                    ?: SetInput()
            setDraft(draft.copy(rows = draft.rows + suggestion))
        }
    }

    fun removeRow(index: Int) {
        draft.value?.let { d ->
            setDraft(d.copy(rows = d.rows.filterIndexed { i, _ -> i != index }))
        }
    }

    fun date(date: LocalDate) {
        draft.value?.let { setDraft(it.copy(date = date)) }
    }

    fun discard() {
        setDraft(null)
    }

    fun saveDraft() = run {
        val d = draft.value ?: return@run
        val ex = notebook.value.exercises.first { it.id == d.exerciseId }
        SaveEntry(repository)(ex, d.date, d.rows, d.entryId)
        setDraft(null)
    }

    private fun setDraft(d: EntryDraft?) {
        _draft.value = d
        saved["draft"] =
            d?.let {
                JSONObject()
                    .apply {
                        put("exercise", it.exerciseId)
                        put("entry", it.entryId)
                        put("date", it.date.toString())
                        put(
                            "rows",
                            JSONArray().apply {
                                it.rows.forEach { r ->
                                    put(
                                        JSONObject().apply {
                                            put("w", r.weight)
                                            put("r", r.reps)
                                            put("s", r.seconds)
                                        }
                                    )
                                }
                            },
                        )
                    }
                    .toString()
            }
    }

    private fun decode(raw: String?): EntryDraft? =
        try {
            raw?.let {
                val o = JSONObject(it)
                val a = o.getJSONArray("rows")
                EntryDraft(
                    o.getLong("exercise"),
                    o.getLong("entry"),
                    LocalDate.parse(o.getString("date")),
                    (0 until a.length()).map { i ->
                        val r = a.getJSONObject(i)
                        SetInput(r.getString("w"), r.getString("r"), r.getString("s"))
                    },
                )
            }
        } catch (_: Exception) {
            null
        }
}
