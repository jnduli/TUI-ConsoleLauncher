package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import io.mockk.*
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class NotesManagerTest {

    private lateinit var context: Context
    private lateinit var notesManager: NotesManager
    private lateinit var timeManager: TimeManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()

        mockkStatic(XMLPrefsManager::class)
        mockkStatic(Tuils::class)
        
        // Setup TimeManager singleton mock
        timeManager = mockk(relaxed = true)
        TimeManager.instance = timeManager
        every { timeManager.replace(any<CharSequence>(), any()) } answers { it.invocation.args[0] as CharSequence }

        // Mock XMLPrefsManager behavior for init
        every { XMLPrefsManager.get(any()) } returns "default"
        every { XMLPrefsManager.getColor(any()) } returns Color.WHITE
        every { XMLPrefsManager.getInt(any()) } returns 0
        every { XMLPrefsManager.getBoolean(any()) } returns false
        
        // Return null to skip XML document processing in load()
        every { XMLPrefsManager.buildDocument(any(), any()) } returns null
        every { XMLPrefsManager.resetFile(any(), any()) } returns true
        every { XMLPrefsManager.add(any(), any(), any(), any()) } returns null
        every { XMLPrefsManager.removeNode(any(), any<Array<String?>>(), any()) } returns null
        every { XMLPrefsManager.removeNode(any(), any<Array<String?>>(), any(), any(), any()) } returns null

        // Mock Tuils
        val tempDir = Files.createTempDirectory("tui_test").toFile()
        every { Tuils.getFolder(any()) } returns tempDir
        every { Tuils.span(any<CharSequence>(), any()) } answers { SpannableString(it.invocation.args[0] as CharSequence) }
        
        // Mock side-effect methods
        every { Tuils.sendXMLParseError(any(), any()) } just runs
        every { Tuils.sendOutput(any(), any<CharSequence>()) } just runs
        every { Tuils.sendOutput(any(), any<Int>()) } just runs

        notesManager = NotesManager(context)
    }

    @Test
    fun testAddNote() {
        val noteText = "Test Note"
        notesManager.addNote(noteText, false)
        
        assertEquals(1, notesManager.notes.size)
        assertEquals(noteText, notesManager.notes[0].text)
    }

    @Test
    fun testRmNote() {
        notesManager.addNote("Note 1", false)
        notesManager.addNote("Note 2", false)
        assertEquals(2, notesManager.notes.size)

        // Remove by index (1-based string)
        notesManager.rmNote("1")
        
        assertEquals(1, notesManager.notes.size)
        assertEquals("Note 2", notesManager.notes[0].text)
    }

    @Test
    fun testClearNotes() {
        notesManager.addNote("Normal", false)
        notesManager.addNote("Locked", true)
        assertEquals(2, notesManager.notes.size)

        notesManager.clearNotes(context)
        
        assertEquals(1, notesManager.notes.size)
        assertEquals("Locked", notesManager.notes[0].text)
    }

    /**
    @Test
    fun testLsNotes() {
        notesManager.addNote("Note 1", false)
        
        val outputSlot = slot<CharSequence>()
        every { Tuils.sendOutput(any(), capture(outputSlot)) } just runs
        
        notesManager.lsNotes(context)
        
        val expectedOutput = " - 1 -> Note 1"
        assertEquals(expectedOutput, outputSlot.captured.toString())
    }
    */

    @Test
    fun testLockNote() {
        notesManager.addNote("Note", false)
        assertEquals(false, notesManager.notes[0].lock)

        every { XMLPrefsManager.set(any(), any(), any(), any(), any(), any(), any()) } returns null
        
        notesManager.lockNote(context, "1", true)
        
        assertEquals(true, notesManager.notes[0].lock)
    }
}
