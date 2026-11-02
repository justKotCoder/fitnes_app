package ru.setnote.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SaveEntryUseCaseTest {
    @Test
    fun savesExerciseIdentityDateAndAllSets() = runBlocking {
        val repo = RecordingRepository()
        val date = LocalDate.now().minusDays(1)
        val exercise = Exercise(id = 42, programId = 7, name = "Тяга", type = ExerciseType.WEIGHT)
        SaveEntry(repo)(exercise, date, listOf(SetInput("30", "12"), SetInput("35", "8")), id = 9)
        assertEquals(
            Entry(9, 42, date, listOf(WorkoutSet(30.0, 12), WorkoutSet(35.0, 8))),
            repo.saved,
        )
    }

    @Test
    fun invalidInputNeverReachesStorage() = runBlocking {
        val repo = RecordingRepository()
        val exercise = Exercise(id = 42, programId = 7, name = "Планка", type = ExerciseType.TIME)
        try {
            SaveEntry(repo)(exercise, LocalDate.now(), listOf(SetInput(seconds = "-30")))
            fail("Negative duration must be rejected")
        } catch (_: IllegalArgumentException) {
            assertNull(repo.saved)
        }
    }

    @Test
    fun futureDateNeverReachesStorage() = runBlocking {
        val repo = RecordingRepository()
        val exercise = Exercise(id = 42, programId = 7, name = "Планка", type = ExerciseType.TIME)
        try {
            SaveEntry(repo)(exercise, LocalDate.now().plusDays(1), listOf(SetInput(seconds = "30")))
            fail("Future dates must be rejected")
        } catch (_: IllegalArgumentException) {
            assertNull(repo.saved)
        }
    }

    @Test
    fun existingDateCannotBeOverwrittenByNewRecord() = runBlocking {
        val repo = RecordingRepository()
        val date = LocalDate.now()
        val exercise = Exercise(id = 42, programId = 7, name = "Планка", type = ExerciseType.TIME)
        repo.notebook.value =
            Notebook(entries = listOf(Entry(9, 42, date, listOf(WorkoutSet(seconds = 30)))))
        try {
            SaveEntry(repo)(exercise, date, listOf(SetInput(seconds = "40")))
            fail("Existing date must not be overwritten")
        } catch (_: IllegalArgumentException) {
            assertNull(repo.saved)
        }
        SaveEntry(repo)(exercise, date, listOf(SetInput(seconds = "40")), id = 9)
        assertEquals(40, repo.saved!!.sets.single().seconds)
    }

    private class RecordingRepository : NotebookRepository {
        override val notebook = MutableStateFlow(Notebook())
        var saved: Entry? = null

        override suspend fun saveEntry(entry: Entry) {
            saved = entry
        }

        override suspend fun load() = Unit

        override suspend fun saveProgram(program: Program) = Unit

        override suspend fun deleteProgram(id: Long) = Unit

        override suspend fun saveExercise(exercise: Exercise) = Unit

        override suspend fun deleteExercise(id: Long, programId: Long) = Unit

        override suspend fun moveExercise(id: Long, programId: Long, direction: Int) = Unit

        override suspend fun deleteEntry(id: Long) = Unit
    }
}
