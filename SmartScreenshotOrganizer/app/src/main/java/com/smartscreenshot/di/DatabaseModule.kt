package com.smartscreenshot.di

import android.content.Context
import androidx.room.Room
import com.smartscreenshot.data.local.AppDatabase
import com.smartscreenshot.data.local.ScreenshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "screenshots.db")
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()

  @Provides fun provideScreenshotDao(db: AppDatabase): ScreenshotDao = db.screenshotDao()
}
