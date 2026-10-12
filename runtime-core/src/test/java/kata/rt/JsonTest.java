package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void readsObjectsListsAndScalars() {
        Json doc = Json.parse(" {\"name\": \"kata\", \"stars\": 12, \"ratio\": 0.5, \"ok\": true, \"none\": null,"
                + " \"tags\": [\"a\", \"b\"], \"owner\": {\"id\": 7}} ");
        assertTrue(doc.isValid());
        assertTrue(doc.isObject());
        assertEquals("kata", doc.get("name").asString("?"));
        assertEquals(12L, doc.get("stars").asInt(-1));
        assertEquals(12.0, doc.get("stars").asFloat(-1));
        assertEquals(0.5, doc.get("ratio").asFloat(-1));
        assertEquals(true, doc.get("ok").asBool(false));
        assertTrue(doc.get("none").isNull());
        assertTrue(doc.get("tags").isList());
        assertEquals(2L, doc.get("tags").size());
        assertEquals("b", doc.get("tags").at(1).asString("?"));
        assertEquals(7L, doc.get("owner").get("id").asInt(-1));
        assertEquals(7L, doc.size());
        assertEquals("name", doc.keys().get(0));
        assertEquals("b", ((Json) doc.get("tags").items().get(1)).asString("?"));
    }

    @Test
    void missingOrMistypedValuesGiveTheFallback() {
        Json doc = Json.parse("{\"a\": \"text\", \"n\": 1.5}");
        assertTrue(doc.get("zzz").isNull());
        assertTrue(doc.get("zzz").get("deeper").at(3).isNull());
        assertEquals("fallback", doc.get("zzz").asString("fallback"));
        assertEquals(-1L, doc.get("a").asInt(-1));
        assertEquals(-1L, doc.get("n").asInt(-1), "1.5 is not an integer");
        assertEquals(false, doc.get("a").asBool(false));
        assertEquals(0L, doc.get("a").size());
        assertEquals(0L, doc.get("a").items().size());
        assertTrue(doc.at(0).isNull());
        assertTrue(doc.get("a").at(-1).isNull());
    }

    @Test
    void stringEscapesAndUnicode() {
        Json value = Json.parse("\"a\\\"b\\\\c\\n\\u65e5\\ud83d\\ude00 日本\"");
        assertEquals("a\"b\\c\n日😀 日本", value.asString("?"));
        assertEquals("\"a\\\"b\\\\c\\n日😀 日本\"", value.toText());
    }

    @Test
    void numbers() {
        assertEquals(-12L, Json.parse("-12").asInt(0));
        assertEquals(1500.0, Json.parse("1.5e3").asFloat(0));
        assertEquals(1500L, Json.parse("1.5e3").asInt(-1), "a whole number written with an exponent is an integer");
        assertEquals(9223372036854775807L, Json.parse("9223372036854775807").asInt(0));
        assertEquals("12", Json.parse("12").toText());
        assertEquals("0.5", Json.parse("0.5").toText());
    }

    @Test
    void invalidTextIsReportedNotThrown() {
        for (String text : new String[] {"", "{", "[1,]", "{\"a\":}", "{'a':1}", "tru", "1 2", "\"abc", "[1 2]",
            "{\"a\" 1}", "01", "+1", "NaN", "\"\\x\"", "nul", "{\"a\":1,}", "\u0000"}) {
            Json value = Json.parse(text);
            assertFalse(value.isValid(), text);
            assertTrue(value.isNull(), text);
        }
    }

    @Test
    void deepNestingIsRefusedInsteadOfOverflowingTheStack() {
        String deep = "[".repeat(100_000) + "]".repeat(100_000);
        assertFalse(Json.parse(deep).isValid());
        String fine = "[".repeat(50) + "]".repeat(50);
        assertTrue(Json.parse(fine).isValid());
    }

    @Test
    void buildsAndPrints() {
        Json object = new Json().withString("name", "kata").withInt("stars", 3).withFloat("ratio", 0.25)
                .withBool("ok", true).withNull("none")
                .withJson("tags", Json.parse("[]").appendString("a").appendInt(1).appendFloat(1.5).appendBool(false)
                        .append(new Json()));
        assertEquals("{\"name\":\"kata\",\"stars\":3,\"ratio\":0.25,\"ok\":true,\"none\":null,"
                + "\"tags\":[\"a\",1,1.5,false,{}]}", object.toText());
        assertEquals(object.toText(), Json.parse(object.toText()).toText());
    }

    @Test
    void valuesAreImmutable() {
        Json empty = new Json();
        Json one = empty.withInt("a", 1);
        assertEquals("{}", empty.toText());
        assertEquals("{\"a\":2}", one.withInt("a", 2).toText());
        assertEquals("{\"a\":1}", one.toText());
        Json list = Json.parse("[1]");
        assertEquals("[1,2]", list.appendInt(2).toText());
        assertEquals("[1]", list.toText());
        assertEquals("{}", new Json().appendInt(1).toText(), "append on a non-list changes nothing");
        assertEquals("[1]", list.withInt("a", 1).toText(), "with on a non-object changes nothing");
    }
}
