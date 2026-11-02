package ru.setnote.domain

/** Imports a selected local image into private app storage. */
interface PhotoRepository {
    suspend fun import(source: String): String
}
