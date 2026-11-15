package app.ipfsmedia.core.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverSpecTest {

    @Test
    fun `the same item always gets the same cover`() {
        assertEquals(coverSpec("0xabc123", "Night Drive"), coverSpec("0xabc123", "Night Drive"))
        assertEquals(coverSpec("0xABC123", "Night Drive"), coverSpec("0xabc123", "Night Drive"))
    }

    @Test
    fun `the hash is the published FNV-1a, so covers do not change between runs or devices`() {
        // Reference values for 32-bit FNV-1a, with the sign bit cleared.
        assertEquals(0x811C9DC5.toInt() and Int.MAX_VALUE, stableHash(""))
        assertEquals(0xE40C292C.toInt() and Int.MAX_VALUE, stableHash("a"))
        assertEquals(0xBF9CF968.toInt() and Int.MAX_VALUE, stableHash("foobar"))
    }

    @Test
    fun `every seed lands on a palette and a pattern that exist`() {
        for (i in 0 until 2_000) {
            val spec = coverSpec("record-$i", "Title $i")
            assertTrue(spec.palette in COVER_PALETTES.indices)
            assertTrue(spec.angleDegrees in 0..330 && spec.angleDegrees % 30 == 0)
            assertTrue(spec.focusX in 0.3f..0.7f && spec.focusY in 0.3f..0.7f)
        }
    }

    @Test
    fun `neighbours rarely look alike`() {
        val looks = (0 until 200).map { coverSpec("record-$it", "") }.map { it.palette to it.pattern }.toSet()
        // 15 palettes by 6 patterns: two hundred items should use most of the ninety combinations.
        assertTrue(looks.size > 60, "only ${looks.size} distinct looks")
    }

    @Test
    fun `every palette has three ground colours and one for the lettering`() {
        COVER_PALETTES.forEach { assertEquals(4, it.size) }
    }

    @Test
    fun `initials are the first letters of the first two words`() {
        assertEquals("ND", initialsOf("Night Drive"))
        assertEquals("N", initialsOf("nocturne"))
        assertEquals("LA", initialsOf("live at the forum"))
        assertEquals("2T", initialsOf("24-track demo"))
        assertEquals("", initialsOf("  …  "))
        assertEquals("", initialsOf(""))
    }

    @Test
    fun `different items usually get different covers`() {
        assertNotEquals(coverSpec("record-1", "A"), coverSpec("record-2", "A"))
    }
}
