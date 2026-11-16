package com.sunrisesunset.clock

import android.content.Context
import android.content.Intent
import android.util.Log
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
        val diagnostics = StringBuilder()

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("input keyevent 82")
        val launchResult = device.executeShellCommand(
            "am start -W -n ${context.packageName}/.LocationSetupActivity"
        )
        diagnostics.appendLine("=== am start -W ===\n$launchResult")
        diagnostics.appendLine("=== resumed activity ===\n" + shell(device,
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"))
        diagnostics.appendLine("=== focused window ===\n" + shell(device,
            "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"))

        val setupActionVisible = device.wait(
            Until.hasObject(By.textContains("Use approximate location")),
            30_000
        )
        if (!setupActionVisible) {
            diagnostics.appendLine("=== UI hierarchy ===\n" + shell(device,
                "uiautomator dump /sdcard/app-window.xml >/dev/null 2>&1; cat /sdcard/app-window.xml"))
            saveDiagnostics(context, device, "01-app-launch-debug.png", diagnostics.toString())
        }

        assertTrue(
            "Approximate-location setup action did not appear. See diagnostics artifact.",
            setupActionVisible
        )

        val screenshot = screenshotFile(context, "01-app-setup.png")
        assertTrue("Could not capture app screenshot", device.takeScreenshot(screenshot))
        assertTrue("Screenshot is empty", screenshot.length() > 10_000)
    }

    @Test
    fun placeWidgetOnLauncherAndCaptureHomeScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val diagnostics = StringBuilder()

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

        diagnostics.appendLine("=== launcher activity ===\n" + shell(device,
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"))
        diagnostics.appendLine("=== launcher package ===\n" + shell(device,
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
        diagnostics.appendLine("=== initial launcher hierarchy ===\n" + shell(device,
            "uiautomator dump /sdcard/launcher-initial.xml >/dev/null 2>&1; cat /sdcard/launcher-initial.xml"))
        saveDiagnostics(context, device, "02-launcher-initial.png", diagnostics.toString())

        val width = device.displayWidth
        val height = device.displayHeight
        var widgetsButton: UiObject2? = null

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
            diagnostics.appendLine("=== launcher after long-press attempts ===\n" + shell(device,
                "uiautomator dump /sdcard/launcher-after-longpress.xml >/dev/null 2>&1; cat /sdcard/launcher-after-longpress.xml"))
            diagnostics.appendLine("=== resumed activity ===\n" + shell(device,
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"))
            saveDiagnostics(context, device, "02-launcher-menu-debug.png", diagnostics.toString())
        }
        assertTrue(
            "Launcher edit menu did not expose Widgets after long-pressing the workspace. See diagnostics artifact.",
            widgetsButton != null
        )
        widgetsButton!!.click()
        device.waitForIdle()

        var appEntry = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")),
            15_000
        )
        if (appEntry == null) {
            for (attempt in 0 until 5) {
                device.swipe(width / 2, (height * 0.78).toInt(), width / 2, (height * 0.30).toInt(), 35)
                device.waitForIdle()
                appEntry = device.findObject(By.textContains("Sunrise Sunset Clock"))
                if (appEntry != null) break
            }
        }
        if (appEntry == null) {
            diagnostics.appendLine("=== widget picker hierarchy ===\n" + shell(device,
                "uiautomator dump /sdcard/widget-picker.xml >/dev/null 2>&1; cat /sdcard/widget-picker.xml"))
            saveDiagnostics(context, device, "02-widget-picker-debug.png", diagnostics.toString())
        }
        assertTrue("Widget picker did not list Sunrise Sunset Clock.", appEntry != null)

        appEntry!!.click()
        device.waitForIdle()

        val widgetEntry = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")),
            8_000
        )
        if (widgetEntry == null) {
            diagnostics.appendLine("=== widget preview hierarchy ===\n" + shell(device,
                "uiautomator dump /sdcard/widget-preview.xml >/dev/null 2>&1; cat /sdcard/widget-preview.xml"))
            saveDiagnostics(context, device, "02-widget-preview-debug.png", diagnostics.toString())
        }
        assertTrue("Could not locate the Sunrise Sunset Clock widget preview.", widgetEntry != null)

        val bounds = widgetEntry!!.visibleBounds
        assertTrue("Widget preview has no visible bounds: $bounds", !bounds.isEmpty)
        widgetEntry.drag(android.graphics.Point(width / 2, (height * 0.48).toInt()), 1000)
        device.waitForIdle()

        val widgetVisible = device.wait(
            Until.hasObject(By.textContains("SUNRISE")),
            12_000
        ) || device.hasObject(By.textContains("SUNSET"))
        if (!widgetVisible) {
            diagnostics.appendLine("=== home screen after widget drag ===\n" + shell(device,
                "uiautomator dump /sdcard/widget-placement.xml >/dev/null 2>&1; cat /sdcard/widget-placement.xml"))
            saveDiagnostics(context, device, "02-widget-placement-debug.png", diagnostics.toString())
        }
        assertTrue("Widget was not placed on the launcher home screen.", widgetVisible)

        val screenshot = screenshotFile(context, "02-home-screen-widget.png")
        assertTrue("Could not capture launcher widget screenshot", device.takeScreenshot(screenshot))
        assertTrue("Widget screenshot is empty", screenshot.length() > 10_000)
    }

    private fun shell(device: UiDevice, command: String): String =
        runCatching { device.executeShellCommand(command) }.getOrElse { "COMMAND FAILED: $it" }

    private fun screenshotFile(context: Context, name: String): File {
        // Keep files app-private so the workflow can retrieve them reliably with run-as.
        val directory = File(context.filesDir, "screenshots")
        assertTrue("Could not create screenshot directory", directory.mkdirs() || directory.isDirectory)
        return File(directory, name)
    }

    private fun saveDiagnostics(
        context: Context,
        device: UiDevice,
        screenshotName: String,
        details: String
    ) {
        val screenshot = screenshotFile(context, screenshotName)
        runCatching { device.takeScreenshot(screenshot) }
        val report = File(screenshot.parentFile, screenshotName.removeSuffix(".png") + ".txt")
        report.writeText(details)
        Log.e("StoreScreenshotTest", "$screenshotName diagnostics saved to ${report.absolutePath}\n$details")
    }
}
