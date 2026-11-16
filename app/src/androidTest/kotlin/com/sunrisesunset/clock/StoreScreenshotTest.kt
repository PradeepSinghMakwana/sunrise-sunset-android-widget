package com.sunrisesunset.clock

import android.content.Context
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

        instrumentation.targetContext.startActivity(
            android.content.Intent(
                instrumentation.targetContext,
                LocationSetupActivity::class.java
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue(
            "App setup screen did not appear",
            device.wait(Until.hasObject(By.text("Sunrise Sunset Clock")), 15_000)
        )

        val screenshot = screenshotFile(instrumentation.targetContext, "01-app-setup.png")
        assertTrue("Could not capture app screenshot", device.takeScreenshot(screenshot))
        assertTrue("Screenshot is empty", screenshot.length() > 10_000)
    }

    @Test
    fun placeWidgetOnLauncherAndCaptureHomeScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        // Deterministic demo coordinates belong only to this emulator test.
        // They are not written into the user's installed app or release defaults.
        context.getSharedPreferences(
            LocationSetupActivity.PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(LocationSetupActivity.LATITUDE, "37.4219983")
            .putString(LocationSetupActivity.LONGITUDE, "-122.084")
            .commit()

        device.pressHome()
        device.waitForIdle()

        val display = device.displayWidth to device.displayHeight
        val blankX = display.first / 2
        val blankY = (display.second * 0.62).toInt()
        device.executeShellCommand(
            "input swipe $blankX $blankY $blankX $blankY 1100"
        )
        device.waitForIdle()

        val widgetsButton = device.wait(
            Until.findObject(By.text("Widgets")),
            10_000
        )
        assertTrue("Launcher edit menu did not expose Widgets", widgetsButton != null)
        widgetsButton!!.click()
        device.waitForIdle()

        var appEntry = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")),
            15_000
        )
        if (appEntry == null) {
            // Some launcher builds put the widget list in a vertically scrolling panel.
            for (attempt in 0 until 5) {
                device.swipe(
                    display.first / 2, (display.second * 0.78).toInt(),
                    display.first / 2, (display.second * 0.30).toInt(), 35
                )
                device.waitForIdle()
                appEntry = device.findObject(By.textContains("Sunrise Sunset Clock"))
                if (appEntry != null) break
            }
        }
        assertTrue(
            "Widget picker did not list Sunrise Sunset Clock.",
            appEntry != null
        )

        // Expand the app's widget group if the picker presents grouped entries.
        appEntry!!.click()
        device.waitForIdle()

        val widgetEntry = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")),
            8_000
        )
        assertTrue(
            "Could not locate the Sunrise Sunset Clock widget preview.",
            widgetEntry != null
        )

        val bounds = widgetEntry!!.visibleBounds
        assertTrue("Widget preview has no visible bounds: $bounds", !bounds.isEmpty)
        // Drag the selected widget preview onto the launcher workspace.
        widgetEntry.drag(
            android.graphics.Point(
                display.first / 2,
                (display.second * 0.48).toInt()
            ),
            1000
        )
        device.waitForIdle()

        val widgetVisible = device.wait(
            Until.hasObject(By.textContains("SUNRISE")),
            12_000
        ) || device.hasObject(By.textContains("SUNSET"))
        assertTrue(
            "Widget was not placed on the launcher home screen.",
            widgetVisible
        )

        val screenshot = screenshotFile(context, "02-home-screen-widget.png")
        assertTrue("Could not capture launcher widget screenshot", device.takeScreenshot(screenshot))
        assertTrue("Widget screenshot is empty", screenshot.length() > 10_000)
    }

    private fun screenshotFile(context: Context, name: String): File {
        val directory = File(context.getExternalFilesDir(null), "screenshots")
        assertTrue(
            "Could not create screenshot directory",
            directory.mkdirs() || directory.isDirectory
        )
        return File(directory, name)
    }
}
