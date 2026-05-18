package com.smartscreenshot.data.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosineTest {

  @Test
  fun identicalVectorsScoreOne() {
    val v = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
    assertEquals(1.0f, TextEmbedder.cosine(v, v), 1e-4f)
  }

  @Test
  fun orthogonalVectorsScoreZero() {
    val a = floatArrayOf(1f, 0f)
    val b = floatArrayOf(0f, 1f)
    assertEquals(0.0f, TextEmbedder.cosine(a, b), 1e-4f)
  }

  @Test
  fun oppositeVectorsAreNegative() {
    val a = floatArrayOf(1f, 1f)
    val b = floatArrayOf(-1f, -1f)
    assertTrue(TextEmbedder.cosine(a, b) < 0f)
  }

  @Test
  fun mismatchedSizesReturnZero() {
    assertEquals(0.0f, TextEmbedder.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
  }

  @Test
  fun similarVectorsRankHigherThanDissimilar() {
    val query = floatArrayOf(1f, 1f, 0f)
    val similar = floatArrayOf(0.9f, 1.1f, 0.1f)
    val dissimilar = floatArrayOf(-1f, 0.2f, 1f)
    assertTrue(
      TextEmbedder.cosine(query, similar) > TextEmbedder.cosine(query, dissimilar)
    )
  }
}
