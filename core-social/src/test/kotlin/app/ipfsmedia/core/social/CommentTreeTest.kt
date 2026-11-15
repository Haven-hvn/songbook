package app.ipfsmedia.core.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentTreeTest {

    private fun shape(posts: List<BoardRecord>) = nestComments(posts).map { it.record.key to it.depth }

    @Test
    fun `replies follow the comment they answer, oldest first at every level`() {
        val posts = listOf(
            post("c", block = 30),
            post("a", block = 10),
            post("a2", parent = "a", block = 25),
            post("a1", parent = "a", block = 15),
            post("a1x", parent = "a1", block = 40),
            post("b", block = 20),
        )
        assertEquals(
            listOf("a" to 0, "a1" to 1, "a1x" to 2, "a2" to 1, "b" to 0, "c" to 0),
            shape(posts),
        )
    }

    @Test
    fun `a reply whose parent has expired is shown at the top level`() {
        assertEquals(listOf("orphan" to 0), shape(listOf(post("orphan", parent = "gone"))))
    }

    @Test
    fun `same block breaks ties by key, so two readers see the same order`() {
        assertEquals(
            listOf("a" to 0, "b" to 0),
            shape(listOf(post("b", block = 5), post("a", block = 5))),
        )
    }

    @Test
    fun `depth is capped`() {
        val chain = (0..10).map { post("p$it", parent = if (it == 0) null else "p${it - 1}", block = it.toLong()) }
        val depths = nestComments(chain).map { it.depth }
        assertEquals((0..10).map { minOf(it, MAX_DEPTH) }, depths)
    }

    @Test
    fun `a reply cycle is shown once each and does not loop`() {
        val posts = listOf(post("x", parent = "y", block = 1), post("y", parent = "x", block = 2), post("self", parent = "self", block = 3))
        assertEquals(setOf("x", "y", "self"), nestComments(posts).map { it.record.key }.toSet())
        assertEquals(3, nestComments(posts).size)
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(emptyList<PlacedComment>(), nestComments(emptyList()))
    }
}
