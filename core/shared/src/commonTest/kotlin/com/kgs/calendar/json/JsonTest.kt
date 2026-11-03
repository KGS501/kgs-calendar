package com.kgs.calendar.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected values are Android org.json's output for the same calls. */
class JsonTest {
    @Test
    fun writesLikeAndroid() {
        val o = JSONObject().put("b", 1).put("a", "x/y\"z\n").put("d", 2.0).put("e", 2.5).put("n", JSONObject.NULL)
            .put("arr", JSONArray().put(1).put("s").put(JSONObject().put("k", true)))
        assertEquals("""{"b":1,"a":"x\/y\"z\n","d":2,"e":2.5,"n":null,"arr":[1,"s",{"k":true}]}""", o.toString())
        o.put("b", null)
        assertFalse(o.has("b"))
    }

    @Test
    fun readsLikeAndroid() {
        val o = JSONObject("""{"s":"t","i":"42","l":12345678901,"b":"TRUE","n":null,"o":{"x":[1,2]}}""")
        assertEquals(42, o.getInt("i"))
        assertEquals(12345678901L, o.getLong("l"))
        assertTrue(o.optBoolean("b"))
        assertEquals("null", o.optString("n"))
        assertEquals("fallback", o.optString("missing", "fallback"))
        assertTrue(o.isNull("n"))
        assertTrue(o.isNull("missing"))
        assertEquals(2, o.getJSONObject("o").getJSONArray("x").getInt(1))
        assertNull(o.optJSONObject("s"))
        assertEquals("""{"s":"t","i":"42","l":12345678901,"b":"TRUE","n":null,"o":{"x":[1,2]}}""", o.toString())
        assertEquals("é\u0001", JSONObject("""{"u":"é\u0001"}""").getString("u"))
    }
}
