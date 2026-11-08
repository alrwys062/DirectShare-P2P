package com.example

import com.example.localization.AppLanguage
import com.example.model.formatBytes
import com.example.model.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testByteFormattersInArabic() {
    assertEquals("0 بايت", formatBytes(0))
    assertEquals("1.0 ك.ب", formatBytes(1024))
    assertEquals("1.5 م.ب", formatBytes((1.5 * 1024 * 1024).toLong()))
    assertEquals("2.0 ج.ب", formatBytes(2L * 1024 * 1024 * 1024))
  }

  @Test
  fun testSpeedFormattersInArabic() {
    assertEquals("0 ك.ب/ث", formatSpeed(0))
    assertEquals("500.0 ك.ب/ث", formatSpeed(500 * 1024))
    assertEquals("2.50 م.ب/ث", formatSpeed((2.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun testFormattersInEnglish() {
    assertEquals("0 B", formatBytes(0, AppLanguage.ENGLISH))
    assertEquals("1.0 KB", formatBytes(1024, AppLanguage.ENGLISH))
    assertEquals("2.50 MB/s", formatSpeed((2.5 * 1024 * 1024).toLong(), AppLanguage.ENGLISH))
  }
}
