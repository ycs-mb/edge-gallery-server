package com.smartscreenshot.data.llm

import com.smartscreenshot.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtractionTest {

  @Test
  fun parsesPlainJson() {
    val raw =
      """{"title":"Receipt","summary":"A coffee receipt","category":"Receipts",
         "tags":["coffee","receipt"],"detected_apps":["Starbucks"],
         "important_text":["$4.50"],"priority_score":7}"""
    val result = JsonExtraction.parse(raw)!!
    assertEquals("Receipt", result.title)
    assertEquals(Category.RECEIPTS, result.category)
    assertEquals(listOf("coffee", "receipt"), result.tags)
    assertEquals(7, result.priorityScore)
    assertFalse(result.needsReview)
  }

  @Test
  fun parsesFencedJsonWithPreamble() {
    val raw =
      "Here is the result:\n```json\n{\"title\":\"Chat\",\"summary\":\"A message\"," +
        "\"category\":\"Chat\",\"tags\":[],\"priority_score\":15}\n```\nDone."
    val result = JsonExtraction.parse(raw)!!
    assertEquals("Chat", result.title)
    assertEquals(Category.CHAT, result.category)
    // Out-of-range score is clamped.
    assertEquals(10, result.priorityScore)
  }

  @Test
  fun unknownCategoryFallsBackToOther() {
    val result = JsonExtraction.parse("""{"title":"X","summary":"y","category":"Nonsense"}""")!!
    assertEquals(Category.OTHER, result.category)
  }

  @Test
  fun returnsNullWhenNoJson() {
    assertNull(JsonExtraction.parse("totally not json"))
  }

  @Test
  fun returnsNullWhenEmptyTitleAndSummary() {
    assertNull(JsonExtraction.parse("""{"title":"","summary":""}"""))
  }

  @Test
  fun handlesNestedBracesInStrings() {
    val raw = """{"title":"A {weird} title","summary":"contains } brace","category":"Work"}"""
    val result = JsonExtraction.parse(raw)!!
    assertTrue(result.title.contains("{weird}"))
    assertEquals(Category.WORK, result.category)
  }
}
