package ru.setnote.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SharedExercisesTest {
    private val day = LocalDate.of(2026, 10, 10)
    private val first =
        Exercise(1, 10, "Тяга блока", ExerciseType.WEIGHT, "Сиденье 4", "/photo1", position = 2)
    private val second =
        first.copy(
            id = 2,
            programId = 20,
            name = "  ТЯГА   БЛОКА ",
            note = "Хват шире",
            photo = "/photo2",
            position = 1,
        )

    @Test
    fun normalizesCaseAndWhitespaceOnly() {
        assertEquals(exerciseNameKey(first.name), exerciseNameKey(second.name))
        assertEquals("жим ногами", exerciseNameKey(" ЖИМ\u00a0\tногами "))
        assertNotEquals(exerciseNameKey("тяга блока"), exerciseNameKey("тяга нижнего блока"))
    }

    @Test
    fun mergesIdentityButKeepsBothProgramPositions() {
        val plan = migrateSharedExercises(listOf(first, second), emptyList())
        assertEquals(1, plan.catalog.size)
        assertEquals(
            setOf(ProgramExercise(10, 1, 2), ProgramExercise(20, 1, 1)),
            plan.links.toSet(),
        )
        assertTrue(plan.catalog.single().note.contains("Сиденье 4"))
        assertTrue(plan.catalog.single().note.contains("Хват шире"))
    }

    @Test
    fun sameDateKeepsEverySetEvenWhenIdentical() {
        val set = WorkoutSet(30.0, 12)
        val plan =
            migrateSharedExercises(
                listOf(first, second),
                listOf(
                    Entry(1, 1, day, listOf(set)),
                    Entry(2, 2, day, listOf(set, WorkoutSet(35.0, 8))),
                ),
            )
        assertEquals(listOf(set, set, WorkoutSet(35.0, 8)), plan.entries.single().sets)
        assertEquals(1L, plan.entries.single().exerciseId)
    }

    @Test
    fun latestResultsAreVisibleFromEitherProgram() {
        val entries =
            listOf(
                Entry(1, 1, day, listOf(WorkoutSet(20.0, 12))),
                Entry(2, 2, day.plusDays(1), listOf(WorkoutSet(30.0, 10))),
            )
        val plan = migrateSharedExercises(listOf(first, second), entries)
        val notebook = Notebook(entries = plan.entries)
        plan.links.forEach {
            assertEquals(30.0, notebook.history(it.exerciseId).first().sets.single().weight!!, 0.0)
        }
        assertEquals("/photo2", plan.catalog.single().photo)
    }

    @Test
    fun incompatibleTypesAndWeightConventionsStaySeparate() {
        assertEquals(
            3,
            migrateSharedExercises(
                    listOf(
                        first,
                        second.copy(type = ExerciseType.TIME),
                        second.copy(id = 3, perDumbbell = true),
                    ),
                    emptyList(),
                )
                .catalog
                .size,
        )
    }

    @Test
    fun duplicatesInOneProgramBecomeOnePlacementWithoutLosingHistory() {
        val plan =
            migrateSharedExercises(
                listOf(first, second.copy(programId = 10)),
                listOf(
                    Entry(1, 1, day, listOf(WorkoutSet(1.0, 1))),
                    Entry(2, 2, day.plusDays(1), listOf(WorkoutSet(2.0, 2))),
                ),
            )
        assertEquals(1, plan.links.size)
        assertEquals(2, plan.entries.size)
    }

    @Test
    fun emptyDatabaseMigrates() {
        assertEquals(
            SharedMigration(emptyList(), emptyList(), emptyList()),
            migrateSharedExercises(emptyList(), emptyList()),
        )
    }
}
