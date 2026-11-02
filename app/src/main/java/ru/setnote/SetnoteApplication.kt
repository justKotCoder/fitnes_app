package ru.setnote

import android.app.Application
import ru.setnote.data.*
import ru.setnote.domain.*

class SetnoteApplication : Application() {
    val repository: NotebookRepository by lazy { SqliteNotebookRepository(NotebookDatabase(this)) }
    val photos by lazy { PhotoStore(this) }
}
