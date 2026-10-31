package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SNUG", appName)
  }

  @Test
  fun `compatibility calculation returns high score for shared interests`() {
    val me = com.example.data.model.UserProfile(
      id = "user1",
      interests = listOf("Travel", "Coffee", "Music"),
      lookingFor = "Long-term relationship",
      photoVerified = true
    )
    val other = com.example.data.model.UserProfile(
      id = "user2",
      interests = listOf("Coffee", "Music", "Photography"),
      lookingFor = "Long-term relationship",
      photoVerified = true
    )
    val score = com.example.data.model.calculateCompatibility(me, other)
    org.junit.Assert.assertTrue(score >= 75)
  }
}
