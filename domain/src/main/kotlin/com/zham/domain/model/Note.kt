package com.zham.domain.model

import java.time.Instant

/**
 * A note. The [body] is plain text with a little markup (see the usecase NoteMarkup): lists,
 * checklists, bold, italic and colour, so it stays readable even outside the app.
 */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val categoryId: Long?,
    val projectId: Long?,
    val created: Instant,
    val updated: Instant,
    val pinned: Boolean = false,
)

interface NoteRepository {
    fun observeNotes(): kotlinx.coroutines.flow.Flow<List<Note>>
    suspend fun findById(id: Long): Note?

    /** Inserts when [Note.id] is 0, otherwise updates. Returns the note's id, new or not. */
    suspend fun save(note: Note): Long
    suspend fun delete(id: Long)
}
