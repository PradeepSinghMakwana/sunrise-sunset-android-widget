package com.sunrisesunset.clock

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val expectedProvider = ComponentName(context, SunClockWidgetProvider::class.java)
        assertTrue(
            "Sunrise Sunset Clock is missing from Android's installed widget providers",
            appWidgetManager.installedProviders.any { it.provider == expectedProvider }
        )

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

        val widgetEntrySelector = By.textContains("Sunrise Sunset Clock")
        var appEntry: UiObject2? = null
        val pickerViewportTop = (device.displayHeight * 0.18).toInt()
        val pickerViewportBottom = (device.displayHeight * 0.88).toInt()
        // Launcher3 can expose a partially clipped row to UiAutomator. Keep
        // scrolling until the title is actually inside the picker viewport; a
        // click on a clipped row can hit another app/category or empty space.
        for (attempt in 0 until 12) {
            appEntry = device.findObject(widgetEntrySelector)
            val bounds = appEntry?.visibleBounds
            val fullyVisible = bounds != null && bounds.width() > 0 && bounds.height() > 0 &&
                bounds.top >= pickerViewportTop && bounds.bottom <= pickerViewportBottom
            if (fullyVisible) break
            device.swipe(device.displayWidth / 2, (device.displayHeight * 0.78).toInt(),
                device.displayWidth / 2, (device.displayHeight * 0.30).toInt(), 35)
            device.waitForIdle()
        }
        assertNotNull("Widget picker did not list Sunrise Sunset Clock", appEntry)
        val appEntryBounds = appEntry!!.visibleBounds
        assertTrue(
            "Widget picker row must be fully visible before selection: $appEntryBounds",
            appEntryBounds.height() > 0 && appEntryBounds.top >= pickerViewportTop &&
                appEntryBounds.bottom <= pickerViewportBottom
        )
        // Tap the center of the fully visible widget row, not an off-screen
        // accessibility node whose bounds may be clipped to a neighboring item.
        device.click(appEntryBounds.centerX(), appEntryBounds.centerY())
        device.waitForIdle()

        // Save evidence immediately after tapping the provider row. Launcher3's
        // widget_name accessibility ID is not stable across Android 13 builds.
        captureCheckpoint(device, output, "07-widget-row-selected")
        val widgetPreview = device.wait(
            Until.findObject(By.res("com.android.launcher3", "widget_preview")), 8_000)
        device.dumpWindowHierarchy(File(output, "07-widget-row-selected.xml"))
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

        // Android 13 keeps the widget selected after the drag, with resize handles
        // visible. Dismiss that mode before treating the home screen as final.
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1_000)

        val widgetIds = appWidgetManager.getAppWidgetIds(expectedProvider)
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

        // Android 13 Launcher3 accessibility represents RemoteViews children as one
        // AppWidgetHostView node; individual TextViews aren't queryable via UiAutomator.
        // The launcher host must exist after leaving resize mode: a preview tile or
        // placement outline is not an acceptable substitute for a bound host view.
        val widgetHost = device.wait(
            Until.findObject(By.clazz("com.android.launcher3.widget.LauncherAppWidgetHostView")),
            8_000
        )
        assertNotNull(
            "Placed widget host is not present after dismissing placement mode; " +
                "inspect 08-widget-after-placement.xml",
            widgetHost
        )
        val widgetBounds = widgetHost!!.visibleBounds
        assertTrue("Widget host is clipped or has zero size: $widgetBounds",
            widgetBounds.width() >= 250 && widgetBounds.height() >= 130)
        assertTrue("Widget host extends beyond the display: $widgetBounds",
            widgetBounds.left >= 0 && widgetBounds.top >= 0 &&
                widgetBounds.right <= device.displayWidth &&
                widgetBounds.bottom <= device.displayHeight)

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
        val sunriseAccentPixels = countPixelsNear(
            after, widgetBounds, 0xFFFFD27A.toInt(), tolerance = 38
        )
        val sunsetAccentPixels = countPixelsNear(
            after, widgetBounds, 0xFFFFB28A.toInt(), tolerance = 38
        )
        assertTrue(
            "Screenshot does not visibly contain the widget's sunrise label accent",
            sunriseAccentPixels > 3
        )
        assertTrue(
            "Screenshot does not visibly contain the widget's sunset label accent",
            sunsetAccentPixels > 3
        )
        before.recycle()
        after.recycle()
        device.dumpWindowHierarchy(File(output, "02-home-screen-widget.xml"))
    }


    private fun countPixelsNear(
        bitmap: Bitmap,
        bounds: Rect,
        target: Int,
        tolerance: Int
    ): Int {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        var matches = 0
        val left = bounds.left.coerceIn(0, bitmap.width)
        val top = bounds.top.coerceIn(0, bitmap.height)
        val right = bounds.right.coerceIn(0, bitmap.width)
        val bottom = bounds.bottom.coerceIn(0, bitmap.height)
        for (y in top until bottom) {
            for (x in left until right) {
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
