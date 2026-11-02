package ru.setnote.domain

import org.junit.Assert.*
import org.junit.Test

class SaveEntryTest {
    @Test
    fun decimalCommaAndDifferentWeightsArePreserved() {
        val sets =
            SaveEntry.parse(
                ExerciseType.WEIGHT,
                listOf(SetInput("12,5", "12"), SetInput("15", "8")),
            )
        assertEquals(12.5, sets[0].weight!!, 0.0)
        assertEquals(8, sets[1].reps)
    }

    @Test
    fun timeNeedsNoWeightOrReps() {
        assertEquals(
            WorkoutSet(seconds = 30),
            SaveEntry.parse(ExerciseType.TIME, listOf(SetInput(seconds = "30"))).single(),
        )
    }

    @Test
    fun bodyweightNeedsOnlyReps() {
        assertEquals(
            WorkoutSet(reps = 15),
            SaveEntry.parse(ExerciseType.REPS, listOf(SetInput(reps = "15"))).single(),
        )
    }

    @Test
    fun zeroExternalWeightIsValid() {
        assertEquals(
            0.0,
            SaveEntry.parse(ExerciseType.WEIGHT, listOf(SetInput("0", "10"))).single().weight!!,
            0.0,
        )
    }

    @Test
    fun malformedOrIncompleteRowsAreRejected() {
        listOf(
                SetInput("NaN", "10"),
                SetInput("-1", "10"),
                SetInput("20", "0"),
                SetInput("20", ""),
                SetInput("1,2,3", "5"),
            )
            .forEach { row ->
                assertThrows(IllegalArgumentException::class.java) {
                    SaveEntry.parse(ExerciseType.WEIGHT, listOf(row))
                }
            }
    }

    @Test
    fun emptyListIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SaveEntry.parse(ExerciseType.REPS, emptyList())
        }
    }

    @Test
    fun datesAreSortedPerExercise() {
        val a = Entry(1, 1, java.time.LocalDate.parse("2026-09-01"), emptyList())
        val b = a.copy(id = 2, date = java.time.LocalDate.parse("2026-09-20"))
        val c = b.copy(id = 3, exerciseId = 2)
        assertEquals(listOf(b, a), Notebook(entries = listOf(a, c, b)).history(1))
    }
}
