package io.github.tukiza7_debug.vibecast.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonPathTest {
    private val root =
        Json.parseToJsonElement(
            """
            {
              "a": { "b": [ { "name": "one" }, { "name": "two" } ], "title": "deep" },
              "list": [10, 20, 30],
              "n": null
            }
            """.trimIndent(),
        )

    @Test
    fun `resolves nested objects`() {
        assertEquals(JsonPrimitive("deep"), MiniJsonPath.resolve(root, "${'$'}.a.title"))
    }

    @Test
    fun `resolves array index`() {
        assertEquals(JsonPrimitive(20), MiniJsonPath.resolve(root, "${'$'}.list[1]"))
    }

    @Test
    fun `resolves star mapping over objects`() {
        val array = MiniJsonPath.resolve(root, "${'$'}.a.b[*]")!!
        assertEquals(2, (array as kotlinx.serialization.json.JsonArray).size)
        assertEquals("two", MiniJsonPath.asString(MiniJsonPath.resolve(array, "${'$'}[1].name")))
    }

    @Test
    fun `missing paths resolve to null`() {
        assertNull(MiniJsonPath.resolve(root, "${'$'}.a.missing"))
        assertNull(MiniJsonPath.resolve(root, "${'$'}.list[99]"))
        assertNull(MiniJsonPath.resolve(root, "no-dollar-prefix"))
    }

    @Test
    fun `root path returns the element`() {
        assertEquals(root, MiniJsonPath.resolve(root, "${'$'}"))
    }

    @Test
    fun `asString flattens single element arrays`() {
        assertEquals("42", MiniJsonPath.asString(kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(42)))))
        assertNull(MiniJsonPath.asString(kotlinx.serialization.json.JsonArray(emptyList())))
    }
}
