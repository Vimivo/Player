package dev.vimivo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @Test fun packageContextIsCorrect() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.startsWith("dev.vimivo.app"))
    }
}
