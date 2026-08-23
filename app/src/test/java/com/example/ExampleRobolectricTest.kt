package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// FIX (fonts race): this plain-config test and the NATIVE-graphics screenshot
// test both load Robolectric's native runtime in the same JVM; the second
// load crashes with FileSystemAlreadyExistsException while copying fonts.
// Aligning graphics mode + SDK keeps a single runtime path.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("El-Imtiyaz Staff", appName)
  }
}
