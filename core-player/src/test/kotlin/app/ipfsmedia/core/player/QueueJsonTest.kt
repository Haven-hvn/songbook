package app.ipfsmedia.core.player

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueueJsonTest {

    private val queue = SavedQueue(
        entries = listOf(song("a", 0), song("a", 1), album("b", pieceCid = null, durationMs = null), video("c")),
        index = 2,
        positionMs = 91_500L,
        shuffle = true,
        repeatMode = 2,
        volume = 0.4f,
    )

    @Test
    fun `a queue survives being written and read`() {
        assertEquals(queue, QueueJson.decode(QueueJson.encode(queue)))
    }

    @Test
    fun `nothing in the saved queue is a file path`() {
        // Staged files can be trimmed at any time; the queue holds ids and is re-staged on resume.
        val text = QueueJson.encode(queue)
        assertFalse("/" in text.replace("\\/", ""), text)
        assertFalse(".bin" in text, text)
    }

    @Test
    fun `absent values come back as null and not as the word null`() {
        val restored = QueueJson.decode(QueueJson.encode(queue))!!.entries[2]
        assertNull(restored.pieceCid)
        assertNull(restored.endMs)
        assertNull(restored.durationMs)
    }

    @Test
    fun `a row that cannot be read is dropped and the current row keeps its place`() {
        val root = JSONObject(QueueJson.encode(queue))
        val entries = root.getJSONArray("entries")
        val damaged = JSONArray()
        for (i in 0 until entries.length()) {
            // Row 1 loses its record key.
            damaged.put(if (i == 1) JSONObject().put("title", "no key") else entries.get(i))
        }
        root.put("entries", damaged)

        val restored = QueueJson.decode(root.toString())!!
        assertEquals(3, restored.entries.size)
        assertEquals("b", restored.entries[restored.index].recordKey)
    }

    @Test
    fun `a queue from an unknown version is not guessed at`() {
        val root = JSONObject(QueueJson.encode(queue)).put("v", 99)
        assertNull(QueueJson.decode(root.toString()))
    }

    @Test
    fun `text that is not a queue reads as no queue`() {
        assertNull(QueueJson.decode("not json"))
        assertNull(QueueJson.decode("{}"))
        assertNull(QueueJson.decode("""{"v":1,"entries":[]}"""))
    }

    @Test
    fun `out of range values are brought into range`() {
        val root = JSONObject(QueueJson.encode(queue))
            .put("index", 40)
            .put("positionMs", -5)
            .put("repeat", 9)
            .put("volume", 3.0)
        val restored = QueueJson.decode(root.toString())!!
        assertEquals(3, restored.index)
        assertEquals(0L, restored.positionMs)
        assertEquals(2, restored.repeatMode)
        assertEquals(1f, restored.volume)
    }
}
