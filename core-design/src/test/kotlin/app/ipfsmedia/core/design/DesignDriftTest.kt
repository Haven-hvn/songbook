package app.ipfsmedia.core.design

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The design rules, enforced (docs/PLAN.md §6), after Parachord's `design-drift.test.js`.
 *
 * Screens are built from `core-design`. A screen that declares its own colour, measurement,
 * icon button or icon set has stepped outside the design system, and the next person to change
 * the system will not find it. This test reads the source of every `feature-*` module and of
 * `:app` and fails on the first such line.
 *
 * It is a text scan on purpose: it needs no device, runs with the unit tests, and its failures
 * name a file and a line.
 */
class DesignDriftTest {

    private data class Rule(val name: String, val pattern: Regex, val fix: String)

    private val rules = listOf(
        Rule(
            "hex colour",
            Regex("""Color\(\s*0x[0-9A-Fa-f]+"""),
            "use a role from appColors or MaterialTheme.colorScheme",
        ),
        Rule(
            "hex colour string",
            Regex(""""#[0-9A-Fa-f]{6,8}""""),
            "use a role from appColors or MaterialTheme.colorScheme",
        ),
        Rule(
            "named colour",
            Regex("""\bColor\.(Black|White|Red|Green|Blue|Gray|DarkGray|LightGray|Yellow|Cyan|Magenta)\b"""),
            "use a role from appColors (Color.Transparent and Color.Unspecified are fine)",
        ),
        Rule(
            "raw dp or sp",
            Regex("""(?<![\w.])-?\d+(\.\d+)?\.(dp|sp)\b"""),
            "use Space, Sizes, Radius or a text style",
        ),
        Rule(
            "bare IconButton",
            Regex("""(?<![\w.])IconButton\s*\("""),
            "use IconAction, which always has a label and a tooltip",
        ),
        Rule(
            "old Material icons",
            Regex("""androidx\.compose\.material\.icons"""),
            "use AppIcons (Material Symbols)",
        ),
        Rule(
            "own RoundedCornerShape",
            Regex("""\bRoundedCornerShape\s*\("""),
            "use Radius",
        ),
        Rule(
            "own TextStyle",
            Regex("""(?<![\w.])TextStyle\s*\("""),
            "use MaterialTheme.typography or AppType",
        ),
    )

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun screenSources(): List<File> =
        root.listFiles { file -> file.isDirectory && (file.name.startsWith("feature-") || file.name == "app") }
            .orEmpty()
            .flatMap { module ->
                File(module, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }

    @Test
    fun `screens declare no colours, measurements, icon buttons or text styles of their own`() {
        val sources = screenSources()
        val violations = mutableListOf<String>()
        for (file in sources) {
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.substringBefore("//")
                for (rule in rules) {
                    if (rule.pattern.containsMatchIn(line)) {
                        violations += "${file.relativeTo(root)}:${index + 1}  ${rule.name}: ${rule.fix}\n    ${raw.trim()}"
                    }
                }
            }
        }
        assertTrue(violations.isEmpty()) {
            "${violations.size} design rule violation(s):\n" + violations.joinToString("\n")
        }
    }

    @Test
    fun `the scan found the screens`() {
        // A scan that reads nothing passes for the wrong reason.
        val modules = screenSources().map { it.relativeTo(root).path.substringBefore(File.separatorChar) }.toSet()
        assertTrue("app" in modules) { "the app module was not scanned (root: $root)" }
        assertTrue(modules.count { it.startsWith("feature-") } >= 10) { "feature modules scanned: $modules" }
    }

    @Test
    fun `spacing is on the four dp scale`() {
        val steps = listOf(
            Space.x1, Space.x2, Space.x3, Space.x4, Space.x5, Space.x6, Space.x8, Space.x10, Space.x12, Space.x16,
        )
        steps.forEach { assertTrue(it.value % 4f == 0f) { "$it is off the scale" } }
        assertTrue(Space.gutter.value == 24f)
    }

    @Test
    fun `touch targets are at least 48 dp`() {
        assertTrue(Sizes.touchTarget.value >= 48f)
        assertTrue(Sizes.rowHeightTouch >= Sizes.rowHeight)
    }

    @Test
    fun `every icon has a path`() {
        AppIcons.all.forEach { (name, icon) ->
            val vector = icon()
            assertTrue(vector.viewportWidth > 0f) { name }
            assertTrue(vector.root.size > 0) { "$name has no path" }
        }
    }
}
