package ru.setnote.domain

import java.time.LocalDate

/** The only conversion/validation path used by new records and history edits. */
class SaveEntry(private val repository: NotebookRepository) {
    suspend operator fun invoke(
        exercise: Exercise,
        date: LocalDate,
        inputs: List<SetInput>,
        id: Long = 0,
    ) {
        require(!date.isAfter(LocalDate.now())) { "Дата не может быть в будущем" }
        val collision =
            repository.notebook.value.history(exercise.id).any { it.date == date && it.id != id }
        require(!collision) { "На эту дату уже есть запись. Открой её в истории." }
        repository.saveEntry(Entry(id, exercise.id, date, parse(exercise.type, inputs)))
    }

    companion object {
        fun parse(type: ExerciseType, inputs: List<SetInput>): List<WorkoutSet> {
            require(inputs.isNotEmpty()) { "Добавь хотя бы один подход" }
            return inputs.mapIndexed { index, row ->
                val label = "Подход ${index + 1}: "
                when (type) {
                    ExerciseType.WEIGHT -> {
                        val weight = row.weight.trim().replace(',', '.').toDoubleOrNull()
                        val reps = row.reps.trim().toIntOrNull()
                        require(weight != null && weight.isFinite() && weight in 0.0..2000.0) {
                            label + "укажи вес от 0 до 2000 кг"
                        }
                        require(reps != null && reps in 1..999) {
                            label + "укажи от 1 до 999 повторений"
                        }
                        WorkoutSet(weight = weight, reps = reps)
                    }
                    ExerciseType.REPS -> {
                        val reps = row.reps.trim().toIntOrNull()
                        require(reps != null && reps in 1..999) {
                            label + "укажи от 1 до 999 повторений"
                        }
                        WorkoutSet(reps = reps)
                    }
                    ExerciseType.TIME -> {
                        val seconds = row.seconds.trim().toIntOrNull()
                        require(seconds != null && seconds in 1..86400) {
                            label + "укажи время в секундах"
                        }
                        WorkoutSet(seconds = seconds)
                    }
                }
            }
        }
    }
}
