package ru.setnote.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import ru.setnote.domain.*

class NotebookDatabase(context: Context) : SQLiteOpenHelper(context, "notebook.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE programs(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)"
        )
        createSharedTables(db)
    }

    private fun createSharedTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE exercises(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL, photo TEXT, per_dumbbell INTEGER NOT NULL, UNIQUE(name_key,type,per_dumbbell))"
        )
        db.execSQL(
            "CREATE TABLE program_exercises(program_id INTEGER NOT NULL REFERENCES programs(id) ON DELETE CASCADE, exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE, position INTEGER NOT NULL, PRIMARY KEY(program_id,exercise_id))"
        )
        db.execSQL(
            "CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, exercise_id INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE, date TEXT NOT NULL, sets TEXT NOT NULL, UNIQUE(exercise_id,date))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "Missing database migration" }
        // SQLiteOpenHelper runs the upgrade in one transaction. Keep exact original rows locally.
        db.execSQL("CREATE TABLE legacy_programs_v1 AS SELECT * FROM programs")
        db.execSQL("CREATE TABLE legacy_exercises_v1 AS SELECT * FROM exercises")
        db.execSQL("CREATE TABLE legacy_entries_v1 AS SELECT * FROM entries")
        val exercises = buildList {
            db.rawQuery(
                    "SELECT id,program_id,name,type,note,photo,per_dumbbell,position FROM exercises",
                    null,
                )
                .use { c ->
                    while (c.moveToNext()) add(
                        Exercise(
                            c.getLong(0),
                            c.getLong(1),
                            c.getString(2),
                            ExerciseType.valueOf(c.getString(3)),
                            c.getString(4),
                            c.getString(5),
                            c.getInt(6) == 1,
                            c.getInt(7),
                        )
                    )
                }
        }
        val entries = buildList {
            db.rawQuery("SELECT id,exercise_id,date,sets FROM entries", null).use { c ->
                while (c.moveToNext()) add(
                    Entry(
                        c.getLong(0),
                        c.getLong(1),
                        LocalDate.parse(c.getString(2)),
                        decodeSets(c.getString(3)),
                    )
                )
            }
        }
        val plan = migrateSharedExercises(exercises, entries)
        db.execSQL("DROP TABLE entries")
        db.execSQL("DROP TABLE exercises")
        createSharedTables(db)
        plan.catalog.forEach { e ->
            db.execSQL(
                "INSERT INTO exercises(id,name,name_key,type,note,photo,per_dumbbell) VALUES(?,?,?,?,?,?,?)",
                arrayOf(
                    e.id,
                    e.name,
                    exerciseNameKey(e.name),
                    e.type.name,
                    e.note,
                    e.photo,
                    if (e.perDumbbell) 1 else 0,
                ),
            )
        }
        plan.links.forEach { l ->
            db.execSQL(
                "INSERT INTO program_exercises(program_id,exercise_id,position) VALUES(?,?,?)",
                arrayOf<Any>(l.programId, l.exerciseId, l.position),
            )
        }
        plan.entries.forEach { e ->
            db.execSQL(
                "INSERT INTO entries(id,exercise_id,date,sets) VALUES(?,?,?,?)",
                arrayOf(e.id, e.exerciseId, e.date.toString(), encodeSets(e.sets)),
            )
        }
    }
}
