package ru.setnote.domain

import kotlinx.coroutines.flow.StateFlow

interface NotebookRepository {
    val notebook: StateFlow<Notebook>

    suspend fun load()

    suspend fun saveProgram(program: Program)

    suspend fun deleteProgram(id: Long)

    suspend fun saveExercise(exercise: Exercise)

    suspend fun deleteExercise(id: Long, programId: Long)

    suspend fun moveExercise(id: Long, programId: Long, direction: Int)

    suspend fun saveEntry(entry: Entry)

    suspend fun deleteEntry(id: Long)
}
