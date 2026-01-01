package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {

    @Test
    fun testNoteSortingTimeUpDown() {
        NotesManager.Note.sorting = 0 // SORTING_TIME_UPDOWN
        val n1 = NotesManager.Note(1000, "a", false)
        val n2 = NotesManager.Note(2000, "b", false)
        
        assertTrue(n1.compareTo(n2) < 0)
        assertTrue(n2.compareTo(n1) > 0)
    }

    @Test
    fun testNoteSortingTimeDownUp() {
        NotesManager.Note.sorting = 1 // SORTING_TIME_DOWNUP
        val n1 = NotesManager.Note(1000, "a", false)
        val n2 = NotesManager.Note(2000, "b", false)
        
        assertTrue(n1.compareTo(n2) > 0)
        assertTrue(n2.compareTo(n1) < 0)
    }

    @Test
    fun testNoteSortingAlphaUpDown() {
        NotesManager.Note.sorting = 2 // SORTING_ALPHA_UPDOWN
        val n1 = NotesManager.Note(1000, "a", false)
        val n2 = NotesManager.Note(1000, "b", false)
        
        // Note: Tuils.alphabeticCompare is used, which we assume works as standard string compare
        assertTrue(n1.compareTo(n2) < 0)
        assertTrue(n2.compareTo(n1) > 0)
    }

    @Test
    fun testNoteSortingLockBefore() {
        NotesManager.Note.sorting = 4 // SORTING_LOCK_BEFORE
        val n1 = NotesManager.Note(1000, "a", true)
        val n2 = NotesManager.Note(2000, "b", false)
        
        assertTrue(n1.compareTo(n2) < 0)
        assertTrue(n2.compareTo(n1) > 0)
    }
}
