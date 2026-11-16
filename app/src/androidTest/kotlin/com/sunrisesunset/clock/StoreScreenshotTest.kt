package com.sunrisesunset.clock

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
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
            captureSharedScreenshot(device, "03-test-app-setup.png")
            captureSharedHierarchy(device, "03-test-app-setup.xml")
            assertTrue("Setup action did not become visible to UI Automator",
                device.wait(Until.hasObject(By.text("Use approximate location")), 10_000))
            onView(withText("Use approximate location")).check(matches(isDisplayed()))
            device.waitForIdle()

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

        context.getSharedPreferences(LocationSetupActivity.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LocationSetupActivity.LATITUDE, "37.4219983")
            .putString(LocationSetupActivity.LONGITUDE, "-122.084")
            .commit()

        unlockDevice(device, context)
        device.pressHome()
        assertTrue("Android launcher did not become visible",
            device.wait(Until.hasObject(By.pkg("com.android.launcher3")), 10_000))
        device.waitForIdle()
        Thread.sleep(1_000)
        captureSharedScreenshot(device, "04-test-launcher-before-widget.png")
        captureSharedHierarchy(device, "04-test-launcher-before-widget.xml")
        device.takeScreenshot(File(output, "02-launcher-before.png"))
        device.dumpWindowHierarchy(File(output, "02-launcher-before.xml"))

        val workspace = device.wait(
            Until.findObject(By.res("com.android.launcher3", "workspace")), 10_000)
        assertNotNull("Launcher3 workspace was not exposed; inspect 02-launcher-before.xml", workspace)
        workspace!!.longClick()
        device.waitForIdle()

        val widgetsButton: UiObject2? = device.wait(
            Until.findObject(By.text("Widgets")), 5_000)
            ?: device.wait(Until.findObject(By.descContains("Widgets")), 2_000)
        assertNotNull("Launcher edit menu did not expose Widgets", widgetsButton)
        widgetsButton!!.click()
        device.waitForIdle()

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

        val widgetPreview = device.wait(
            Until.findObject(By.textContains("Sunrise Sunset Clock")), 8_000)
        assertNotNull("Could not locate the Sunrise Sunset Clock widget preview", widgetPreview)
        widgetPreview!!.drag(
            android.graphics.Point(device.displayWidth / 2, (device.displayHeight * 0.48).toInt()),
            1_000)
        device.waitForIdle()
        Thread.sleep(1_000)
        captureSharedScreenshot(device, "05-test-widget-after-placement.png")
        captureSharedHierarchy(device, "05-test-widget-after-placement.xml")

        val widgetVisible = device.wait(
            Until.hasObject(By.textContains("SUNRISE")), 12_000)
            || device.hasObject(By.textContains("SUNSET"))
        assertTrue("Widget was not visibly placed on the launcher home screen", widgetVisible)

        val screenshot = File(output, "02-home-screen-widget.png")
        assertTrue("Could not capture launcher widget screenshot", device.takeScreenshot(screenshot))
        assertTrue("Widget screenshot is empty", screenshot.length() > 10_000)
        device.dumpWindowHierarchy(File(output, "02-home-screen-widget.xml"))
    }

    private fun captureSharedScreenshot(device: UiDevice, name: String) {
        device.executeShellCommand("screencap -p /sdcard/$name")
    }

    private fun captureSharedHierarchy(device: UiDevice, name: String) {
        device.executeShellCommand("uiautomator dump /sdcard/$name")
    }

    private fun screenshotDirectory(context: Context): File {
        val directory = File(context.filesDir, "screenshots")
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
