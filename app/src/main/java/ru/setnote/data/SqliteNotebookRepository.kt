package ru.setnote.data

import android.content.ContentValues
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.setnote.domain.*

class SqliteNotebookRepository(private val helper: NotebookDatabase) : NotebookRepository {
    private val state = MutableStateFlow(Notebook())
    override val notebook = state.asStateFlow()
    private val mutex = Mutex()

    private suspend fun change(block: () -> Unit) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                block()
                refresh()
            }
        }

    override suspend fun load() = change {}

    private fun refresh() {
        val db = helper.readableDatabase
        val programs = buildList {
            db.rawQuery("SELECT id,name FROM programs ORDER BY id", null).use { c ->
                while (c.moveToNext()) add(Program(c.getLong(0), c.getString(1)))
            }
        }
        val catalog = buildList {
            db.rawQuery(
                    "SELECT id,name,type,note,photo,per_dumbbell FROM exercises ORDER BY name",
                    null,
                )
                .use { c ->
                    while (c.moveToNext()) add(
                        Exercise(
                            c.getLong(0),
                            0,
                            c.getString(1),
                            ExerciseType.valueOf(c.getString(2)),
                            c.getString(3),
                            c.getString(4),
                            c.getInt(5) == 1,
                        )
                    )
                }
        }
        val byId = catalog.associateBy { it.id }
        val exercises = buildList {
            db.rawQuery(
                    "SELECT exercise_id,program_id,position FROM program_exercises ORDER BY position,exercise_id",
                    null,
                )
                .use { c ->
                    while (c.moveToNext()) add(
                        byId
                            .getValue(c.getLong(0))
                            .copy(programId = c.getLong(1), position = c.getInt(2))
                    )
                }
        }
        val entries = buildList {
            db.rawQuery("SELECT id,exercise_id,date,sets FROM entries ORDER BY date DESC", null)
                .use { c ->
                    while (c.moveToNext()) {
                        val a = JSONArray(c.getString(3))
                        val sets =
                            (0 until a.length()).map { i ->
                                val o = a.getJSONObject(i)
                                WorkoutSet(
                                    if (o.has("w")) o.getDouble("w") else null,
                                    if (o.has("r")) o.getInt("r") else null,
                                    if (o.has("s")) o.getInt("s") else null,
                                )
                            }
                        add(
                            Entry(c.getLong(0), c.getLong(1), LocalDate.parse(c.getString(2)), sets)
                        )
                    }
                }
        }
        state.value = Notebook(programs, exercises, entries, catalog)
    }

    override suspend fun saveProgram(program: Program) = change {
        require(program.name.isNotBlank()) { "Введи название программы" }
        val values = ContentValues().apply { put("name", program.name.trim()) }
        if (program.id == 0L) helper.writableDatabase.insertOrThrow("programs", null, values)
        else
            helper.writableDatabase.update(
                "programs",
                values,
                "id=?",
                arrayOf(program.id.toString()),
            )
    }

    override suspend fun deleteProgram(id: Long) = change {
        helper.writableDatabase.delete("programs", "id=?", arrayOf(id.toString()))
        Unit
    }

    override suspend fun saveExercise(exercise: Exercise) = change {
        require(exercise.name.isNotBlank()) { "Введи название упражнения" }
        val old = state.value.catalog.find { it.id == exercise.id }
        val match = state.value.catalog.find { it.identityKey() == exercise.identityKey() }
        require(exercise.id == 0L || match == null || match.id == exercise.id) {
            "Такое упражнение уже есть. Добавь его из сохранённых, а это убери из программы."
        }
        require(
            old == null ||
                (old.type == exercise.type && old.perDumbbell == exercise.perDumbbell) ||
                state.value.history(exercise.id).isEmpty()
        ) {
            "Способ учёта нельзя менять, пока у упражнения есть история"
        }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val values =
                ContentValues().apply {
                    put("name", exercise.name.trim())
                    put("name_key", exerciseNameKey(exercise.name))
                    put("type", exercise.type.name)
                    put("note", exercise.note.trim())
                    put("photo", exercise.photo)
                    put("per_dumbbell", if (exercise.perDumbbell) 1 else 0)
                }
            val id =
                if (exercise.id != 0L) {
                    check(
                        db.update("exercises", values, "id=?", arrayOf(exercise.id.toString())) == 1
                    )
                    exercise.id
                } else match?.id ?: db.insertOrThrow("exercises", null, values)
            val link =
                ContentValues().apply {
                    put("program_id", exercise.programId)
                    put("exercise_id", id)
                    put(
                        "position",
                        (state.value.exercises
                            .filter { it.programId == exercise.programId }
                            .maxOfOrNull { it.position } ?: -1) + 1,
                    )
                }
            db.insertWithOnConflict(
                "program_exercises",
                null,
                link,
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Removing a placement never deletes the shared exercise or its history.
    override suspend fun deleteExercise(id: Long, programId: Long) = change {
        helper.writableDatabase.delete(
            "program_exercises",
            "exercise_id=? AND program_id=?",
            arrayOf(id.toString(), programId.toString()),
        )
        Unit
    }

    override suspend fun moveExercise(id: Long, programId: Long, direction: Int) = change {
        val ex = state.value.exercises.first { it.id == id && it.programId == programId }
        val list = state.value.exercises.filter { it.programId == ex.programId }.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + direction
        if (to in list.indices) {
            java.util.Collections.swap(list, from, to)
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                list.forEachIndexed { i, e ->
                    db.execSQL(
                        "UPDATE program_exercises SET position=? WHERE exercise_id=? AND program_id=?",
                        arrayOf<Any>(i, e.id, programId),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun saveEntry(entry: Entry) = change {
        val db = helper.writableDatabase
        val collision =
            state.value.entries.find { it.exerciseId == entry.exerciseId && it.date == entry.date }
        require(collision == null || collision.id == entry.id) {
            "На эту дату уже есть запись. Открой её в истории."
        }
        val array =
            JSONArray().apply {
                entry.sets.forEach { add ->
                    put(
                        JSONObject().apply {
                            add.weight?.let { put("w", it) }
                            add.reps?.let { put("r", it) }
                            add.seconds?.let { put("s", it) }
                        }
                    )
                }
            }
        val values =
            ContentValues().apply {
                put("exercise_id", entry.exerciseId)
                put("date", entry.date.toString())
                put("sets", array.toString())
            }
        val id = entry.id.takeIf { it != 0L }
        if (id == null) db.insertOrThrow("entries", null, values)
        else db.update("entries", values, "id=?", arrayOf(id.toString()))
    }

    override suspend fun deleteEntry(id: Long) = change {
        helper.writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))
        Unit
    }
}
