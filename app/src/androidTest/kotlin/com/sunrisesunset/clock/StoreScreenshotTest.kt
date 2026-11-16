package com.sunrisesunset.clock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    @Test
    fun captureRealAppSetupScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        device.executeShellCommand(
            "am start -n com.sunrisesunset.clock/.LocationSetupActivity"
        )

        assertTrue(
            "App setup screen did not appear",
            device.wait(Until.hasObject(By.text("Sunrise Sunset Clock")), 15_000)
        )

        val context = instrumentation.targetContext
        val screenshotDirectory = File(
            context.getExternalFilesDir(null),
            "screenshots"
        )
        assertTrue(
            "Could not create screenshot directory",
            screenshotDirectory.mkdirs() || screenshotDirectory.isDirectory
        )

        val screenshot = File(screenshotDirectory, "01-app-setup.png")
        assertTrue("Could not capture app screenshot", device.takeScreenshot(screenshot))
        assertTrue("Screenshot is empty", screenshot.length() > 10_000)
    }
}
