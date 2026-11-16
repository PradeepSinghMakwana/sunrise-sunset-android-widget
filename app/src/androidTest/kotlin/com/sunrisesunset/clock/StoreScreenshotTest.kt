package com.sunrisesunset.clock

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("input keyevent 82")
        context.startActivity(
            Intent(context, LocationSetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        val appWindowVisible = device.wait(
            Until.hasObject(By.pkg(context.packageName)),
            30_000
        )
        val setupActionVisible = device.wait(
            Until.hasObject(By.textContains("Use approximate location")),
            10_000
        )
        if (!appWindowVisible || !setupActionVisible) {
            println("Current activity: " + device.executeShellCommand(
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"
            ))
            saveDiagnosticScreenshot(context, device, "01-app-launch-debug.png")
        }
        assertTrue("Location setup activity window did not become visible", appWindowVisible)
        assertTrue("Approximate-location setup action did not appear", setupActionVisible)

        val screenshot = screenshotFile(context, "01-app-setup.png")
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

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("input keyevent 82")
        device.pressHome()
        device.waitForIdle()

        val width = device.displayWidth
        val height = device.displayHeight
        var widgetsButton: UiObject2? = null

        // Launcher layouts differ. Try several empty workspace positions rather
        // than assuming one fixed coordinate contains a long-press target.
        for (fraction in listOf(0.42, 0.50, 0.58, 0.66, 0.72)) {
            val x = width / 2
            val y = (height * fraction).toInt()
            device.executeShellCommand("input swipe $x $y $x $y 1200")
            device.waitForIdle()
            widgetsButton = device.wait(
                Until.findObject(By.textContains("Widgets")),
                1_000
            ) ?: device.wait(
                Until.findObject(By.descContains("Widgets")),
                1_000
            )
            if (widgetsButton != null) break
            device.pressBack()
            device.waitForIdle()
        }

        if (widgetsButton == null) {
            println("Launcher activity: " + device.executeShellCommand(
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"
            ))
            println("Launcher UI hierarchy: " + device.executeShellCommand(
                "uiautomator dump /sdcard/launcher-window.xml >/dev/null 2>&1; cat /sdcard/launcher-window.xml"
            ))
            saveDiagnosticScreenshot(context, device, "02-launcher-menu-debug.png")
        }
        assertTrue(
            "Launcher edit menu did not expose Widgets after long-pressing the workspace",
            widgetsButton != null
        )
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
                    width / 2, (height * 0.78).toInt(),
                    width / 2, (height * 0.30).toInt(), 35
                )
                device.waitForIdle()
                appEntry = device.findObject(By.textContains("Sunrise Sunset Clock"))
                if (appEntry != null) break
            }
        }
        if (appEntry == null) {
            saveDiagnosticScreenshot(context, device, "02-widget-picker-debug.png")
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
        if (widgetEntry == null) {
            saveDiagnosticScreenshot(context, device, "02-widget-preview-debug.png")
        }
        assertTrue(
            "Could not locate the Sunrise Sunset Clock widget preview.",
            widgetEntry != null
        )

        val bounds = widgetEntry!!.visibleBounds
        assertTrue("Widget preview has no visible bounds: $bounds", !bounds.isEmpty)
        // Drag the selected widget preview onto the launcher workspace.
        widgetEntry.drag(
            android.graphics.Point(width / 2, (height * 0.48).toInt()),
            1000
        )
        device.waitForIdle()

        val widgetVisible = device.wait(
            Until.hasObject(By.textContains("SUNRISE")),
            12_000
        ) || device.hasObject(By.textContains("SUNSET"))
        if (!widgetVisible) {
            saveDiagnosticScreenshot(context, device, "02-widget-placement-debug.png")
        }
        assertTrue(
            "Widget was not placed on the launcher home screen.",
            widgetVisible
        )

        val screenshot = screenshotFile(context, "02-home-screen-widget.png")
        assertTrue("Could not capture launcher widget screenshot", device.takeScreenshot(screenshot))
        assertTrue("Widget screenshot is empty", screenshot.length() > 10_000)
    }

    private fun screenshotFile(context: Context, name: String): File {
        val baseDirectory = context.getExternalFilesDir(null) ?: context.filesDir
        val directory = File(baseDirectory, "screenshots")
        assertTrue(
            "Could not create screenshot directory",
            directory.mkdirs() || directory.isDirectory
        )
        return File(directory, name)
    }

    private fun saveDiagnosticScreenshot(context: Context, device: UiDevice, name: String) {
        val screenshot = screenshotFile(context, name)
        device.takeScreenshot(screenshot)
    }
}
