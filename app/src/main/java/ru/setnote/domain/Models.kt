package ru.setnote.domain

import java.time.LocalDate

enum class ExerciseType(val title: String) {
    WEIGHT("Вес и повторы"),
    REPS("Только повторы"),
    TIME("Время"),
}

data class Program(val id: Long = 0, val name: String)

data class Exercise(
    val id: Long = 0,
    val programId: Long,
    val name: String,
    val type: ExerciseType,
    val note: String = "",
    val photo: String? = null,
    val perDumbbell: Boolean = false,
    val position: Int = 0,
)

data class WorkoutSet(val weight: Double? = null, val reps: Int? = null, val seconds: Int? = null)

data class Entry(
    val id: Long = 0,
    val exerciseId: Long,
    val date: LocalDate,
    val sets: List<WorkoutSet>,
)

data class Notebook(
    val programs: List<Program> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
) {
    fun history(exerciseId: Long) =
        entries.filter { it.exerciseId == exerciseId }.sortedByDescending { it.date }
}

data class SetInput(val weight: String = "", val reps: String = "", val seconds: String = "")

fun Double.display(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString().replace('.', ',')

fun WorkoutSet.summary(type: ExerciseType): String =
    when (type) {
        ExerciseType.WEIGHT -> "${weight?.display()} кг × $reps"
        ExerciseType.REPS -> "$reps повт."
        ExerciseType.TIME -> "$seconds с"
    }

fun WorkoutSet.input() =
    SetInput(weight?.display().orEmpty(), reps?.toString().orEmpty(), seconds?.toString().orEmpty())
