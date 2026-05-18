package com.smartscreenshot.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotDaoTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: ScreenshotDao

  @Before
  fun setup() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    dao = db.screenshotDao()
  }

  @After fun tearDown() = db.close()

  private fun entity(id: Long, title: String, ocr: String) =
    ScreenshotEntity(
      id = id,
      contentUri = "content://media/$id",
      dateAdded = id,
      ocrText = ocr,
      title = title,
      summary = "summary $id",
      category = "WORK",
      tagsJson = "[]",
      detectedAppsJson = "[]",
      importantTextJson = "[]",
      priorityScore = 5,
      sourceApp = null,
      embedding = null,
      embeddingModelVersion = null,
      status = "INDEXED",
    )

  @Test
  fun upsertAndQueryById() = runTest {
    dao.upsert(entity(1, "Invoice", "total amount due"))
    val row = dao.getById(1)
    assertEquals("Invoice", row?.title)
    assertTrue(dao.exists(1))
  }

  @Test
  fun ftsMatchFindsByOcrText() = runTest {
    dao.upsert(entity(1, "Invoice", "total amount due 42 dollars"))
    dao.upsert(entity(2, "Meme", "funny cat picture"))

    val byOcr = dao.ftsSearch("amount*", 10)
    assertEquals(1, byOcr.size)
    assertEquals(1L, byOcr.first().id)

    val byTitle = dao.ftsSearch("meme*", 10)
    assertEquals(2L, byTitle.first().id)
  }

  @Test
  fun clearRemovesEverything() = runTest {
    dao.upsert(entity(1, "A", "x"))
    dao.clear()
    assertEquals(false, dao.exists(1))
  }
}
