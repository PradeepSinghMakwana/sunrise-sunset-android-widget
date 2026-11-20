package com.sunrisesunset.clock

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val output = screenshotDirectory(context)

        unlockDevice(device, context)
        val scenario = ActivityScenario.launch(LocationSetupActivity::class.java)
        try {
            Thread.sleep(1_000)
            assertTrue("Setup action did not become visible to UI Automator",
                device.wait(Until.hasObject(By.text("Use approximate location")), 10_000))
            device.waitForIdle()
            captureCheckpoint(device, output, "04-app-setup-ready")

            val screenshot = File(output, "01-app-setup.png")
            assertTrue("Could not capture app setup screen", device.takeScreenshot(screenshot))
            assertTrue("App screenshot is empty", screenshot.length() > 10_000)
            device.dumpWindowHierarchy(File(output, "01-app-setup.xml"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun placeWidgetOnLauncherAndCaptureHomeScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val output = screenshotDirectory(context)

        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LocationPreferences.LATITUDE, "37.4219983")
            .putString(LocationPreferences.LONGITUDE, "-122.084")
            .commit()

        unlockDevice(device, context)
        device.pressHome()
        assertTrue("Android launcher did not become visible",
            device.wait(Until.hasObject(By.pkg("com.android.launcher3")), 10_000))
        device.waitForIdle()
        Thread.sleep(1_000)
        device.takeScreenshot(File(output, "02-launcher-before.png"))
        device.dumpWindowHierarchy(File(output, "02-launcher-before.xml"))

        val workspace = device.wait(
            Until.findObject(By.res("com.android.launcher3", "workspace")), 10_000)
        assertNotNull("Launcher3 workspace was not exposed; inspect 02-launcher-before.xml", workspace)
        workspace!!.longClick()
        device.waitForIdle()
        captureCheckpoint(device, output, "05-launcher-edit-menu")

        val widgetsButton: UiObject2? = device.wait(
            Until.findObject(By.text("Widgets")), 5_000)
            ?: device.wait(Until.findObject(By.descContains("Widgets")), 2_000)
        assertNotNull("Launcher edit menu did not expose Widgets", widgetsButton)
        widgetsButton!!.click()
        device.waitForIdle()
        captureCheckpoint(device, output, "06-widget-picker-open")

        var appEntry = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")), 15_000)
        for (attempt in 0 until 5) {
            if (appEntry != null) break
            device.swipe(device.displayWidth / 2, (device.displayHeight * 0.78).toInt(),
                device.displayWidth / 2, (device.displayHeight * 0.30).toInt(), 35)
            device.waitForIdle()
            appEntry = device.findObject(By.textContains("Sunrise Sunset Clock"))
        }
        assertNotNull("Widget picker did not list Sunrise Sunset Clock", appEntry)
        appEntry!!.click()
        device.waitForIdle()
        captureCheckpoint(device, output, "07-widget-preview")

        val widgetPreview = device.wait(
            Until.findObject(By.res("com.android.launcher3", "widget_preview")), 8_000)
        assertNotNull(
            "Could not locate the actual Launcher3 widget_preview view; " +
                "do not drag the app-name text",
            widgetPreview
        )
        widgetPreview!!.drag(
            android.graphics.Point(device.displayWidth / 2, (device.displayHeight * 0.48).toInt()),
            1_000)
        device.waitForIdle()
        Thread.sleep(1_000)
        captureCheckpoint(device, output, "08-widget-after-placement")

        val widgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, SunClockWidgetProvider::class.java)
        )
        assertTrue(
            "Launcher drag did not register a Sunrise Sunset Clock widget instance; " +
                "inspect 08-widget-after-placement.xml",
            widgetIds.isNotEmpty()
        )
        widgetIds.forEach { widgetId ->
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
            assertNotNull("Registered widget $widgetId has no provider metadata", info)
            assertTrue(
                "Registered widget $widgetId belongs to an unexpected provider",
                info!!.provider == ComponentName(context, SunClockWidgetProvider::class.java)
            )
        }

        // Launcher3 exposes AppWidgetHostView as a single accessibility node on Android 13.
        // RemoteViews child TextViews are not individually queryable through UiAutomator.
        // Their IDs and text are verified by WidgetResourceContractTest; here verify
        // launcher registration and capture the actual rendered home-screen widget.

        val screenshot = File(output, "02-home-screen-widget.png")
        assertTrue("Could not capture launcher widget screenshot", device.takeScreenshot(screenshot))
        assertTrue("Widget screenshot is empty", screenshot.length() > 10_000)
        val before = BitmapFactory.decodeFile(File(output, "02-launcher-before.png").absolutePath)
        val after = BitmapFactory.decodeFile(screenshot.absolutePath)
        assertNotNull("Pre-placement launcher screenshot is not a valid PNG", before)
        assertNotNull("Widget screenshot is not a valid PNG", after)
        assertEquals("Launcher screenshot width changed unexpectedly", before!!.width, after!!.width)
        assertEquals("Launcher screenshot height changed unexpectedly", before.height, after.height)
        var changedPixels = 0
        for (y in 0 until after.height step 4) {
            for (x in 0 until after.width step 4) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changedPixels++
            }
        }
        assertTrue(
            "Home-screen screenshot is visually unchanged after widget placement",
            changedPixels > 500
        )
        val sunriseAccentPixels = countPixelsNear(after, 0xFFFFD27A.toInt(), tolerance = 72)
        val sunsetAccentPixels = countPixelsNear(after, 0xFFFFB28A.toInt(), tolerance = 72)
        assertTrue(
            "Screenshot does not visibly contain the widget's sunrise label accent",
            sunriseAccentPixels > 12
        )
        assertTrue(
            "Screenshot does not visibly contain the widget's sunset label accent",
            sunsetAccentPixels > 12
        )
        before.recycle()
        after.recycle()
        device.dumpWindowHierarchy(File(output, "02-home-screen-widget.xml"))
    }


    private fun countPixelsNear(bitmap: Bitmap, target: Int, tolerance: Int): Int {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        var matches = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= tolerance &&
                    kotlin.math.abs(green - targetGreen) <= tolerance &&
                    kotlin.math.abs(blue - targetBlue) <= tolerance
                ) matches++
            }
        }
        return matches
    }

    private fun captureCheckpoint(device: UiDevice, output: File, name: String) {
        device.takeScreenshot(File(output, "$name.png"))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }

    private fun screenshotDirectory(context: Context): File {
        val externalFilesDirectory = requireNotNull(context.getExternalFilesDir(null)) {
            "App external-files directory is unavailable; CI cannot collect screenshots"
        }
        val directory = File(externalFilesDirectory, "screenshots")
        assertTrue(
            "Could not create app screenshot directory at ${directory.absolutePath}",
            directory.mkdirs() || directory.isDirectory
        )
        return directory
    }

    private fun unlockDevice(device: UiDevice, context: Context) {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.waitForIdle()
        assertTrue("Emulator screen did not turn on", device.isScreenOn)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertTrue("Emulator keyguard is still locked", !keyguardManager.isKeyguardLocked)
    }
}
