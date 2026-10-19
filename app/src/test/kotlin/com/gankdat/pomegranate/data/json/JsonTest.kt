package com.gankdat.pomegranate.data.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
    @Test
    fun `parses nested objects, arrays, numbers, strings, booleans and null`() {
        val root = Json.parseObject(
            """{"a": "x\n\"y\" \u00e9", "n": 42, "d": -1.5e2, "b": true, "z": null, "o": {"k": [1, {"q": false}, "s"]}, "big": 12345678901234}""",
        )
        assertEquals("x\n\"y\" é", root.string("a"))
        assertEquals(42L, root.long("n"))
        assertEquals(-150.0, root["d"])
        assertEquals(true, root.bool("b"))
        assertNull(root["z"])
        assertTrue("z" in root)
        assertEquals(12345678901234L, root.long("big"))
        val list = root.obj("o")!!.array("k")!!
        assertEquals(1L, list[0])
        assertEquals(mapOf("q" to false), list[1])
        assertEquals("s", list[2])
        assertEquals(listOf(mapOf("q" to false)), root.obj("o")!!.objects("k"))
    }

    @Test
    fun `empty containers and whitespace`() {
        assertEquals(emptyMap<String, Any?>(), Json.parse(" { } "))
        assertEquals(emptyList<Any?>(), Json.parse("[ ]"))
        assertEquals(mapOf("a" to emptyList<Any?>()), Json.parse("""{ "a" : [ ] }"""))
    }

    @Test
    fun `rejects malformed input`() {
        listOf("", "{", """{"a":}""", """{"a":1,}""", "[1,]", "tru", """{"a":"unterminated}""", "{} x", """{"a":1 "b":2}""", "-", "01x").forEach { bad ->
            try {
                Json.parse(bad)
                fail("expected failure for: $bad")
            } catch (_: JsonException) {
                // expected
            }
        }
    }

    @Test
    fun `jsonOf round-trips flat records`() {
        val text = jsonOf("s" to "a\"b\\c\n", "n" to 7L, "b" to false, "z" to null)
        val parsed = Json.parseObject(text)
        assertEquals("a\"b\\c\n", parsed.string("s"))
        assertEquals(7L, parsed.long("n"))
        assertEquals(false, parsed.bool("b"))
        assertNull(parsed["z"])
    }

    @Test
    fun `long accessor tolerates numeric strings and doubles`() {
        val obj = Json.parseObject("""{"a":"12","b":3.0,"c":"x"}""")
        assertEquals(12L, obj.long("a"))
        assertEquals(3L, obj.long("b"))
        assertNull(obj.long("c"))
        assertNull(obj.long("missing"))
    }
}
