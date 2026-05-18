package com.smartscreenshot.data.local

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {

  @TypeConverter
  fun floatArrayToBytes(value: FloatArray?): ByteArray? {
    if (value == null) return null
    val buffer = ByteBuffer.allocate(value.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    value.forEach { buffer.putFloat(it) }
    return buffer.array()
  }

  @TypeConverter
  fun bytesToFloatArray(bytes: ByteArray?): FloatArray? {
    if (bytes == null) return null
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(bytes.size / 4) { buffer.float }
  }
}
