package ru.setnote.domain

import java.util.Locale

fun exerciseNameKey(name: String): String =
    name.trim().replace(Regex("[\\s\\u00a0]+"), " ").lowercase(Locale.ROOT)

fun Exercise.identityKey() = Triple(exerciseNameKey(name), type, perDumbbell)

data class ProgramExercise(val programId: Long, val exerciseId: Long, val position: Int)

data class SharedMigration(
    val catalog: List<Exercise>,
    val links: List<ProgramExercise>,
    val entries: List<Entry>,
)

/** Keeps every recorded set, including conflicting records on the same date. */
fun migrateSharedExercises(exercises: List<Exercise>, entries: List<Entry>): SharedMigration {
    val mapping = mutableMapOf<Long, Long>()
    val catalog =
        exercises
            .groupBy { it.identityKey() }
            .values
            .map { group ->
                val id = group.minOf { it.id }
                group.forEach { mapping[it.id] = id }
                val latest =
                    group.sortedWith(
                        compareByDescending<Exercise> { e ->
                                entries.filter { it.exerciseId == e.id }.maxOfOrNull { it.date }
                            }
                            .thenByDescending { it.id }
                    )
                latest
                    .first()
                    .copy(
                        id = id,
                        programId = 0,
                        position = 0,
                        note =
                            latest
                                .map { it.note.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .joinToString("\n\n"),
                        photo = latest.firstOrNull { !it.photo.isNullOrBlank() }?.photo,
                    )
            }
    val links =
        exercises
            .sortedWith(compareBy<Exercise> { it.position }.thenBy { it.id })
            .map { ProgramExercise(it.programId, mapping.getValue(it.id), it.position) }
            .distinctBy { it.programId to it.exerciseId }
    val merged =
        entries
            .groupBy { mapping.getValue(it.exerciseId) to it.date }
            .map { (key, records) ->
                Entry(
                    records.minOf { it.id },
                    key.first,
                    key.second,
                    records.sortedBy { it.id }.flatMap { it.sets },
                )
            }
    return SharedMigration(catalog, links, merged)
}
