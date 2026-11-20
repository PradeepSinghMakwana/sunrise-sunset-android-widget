package com.sunrisesunset.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the Android launcher widget registration and resource contract.
 * These host-side tests catch broken manifest/resource wiring before emulator UI tests run.
 */
class WidgetResourceContractTest {
    @Test
    fun launcherWidgetDefinesExactSolarLabelIdsAndStrings() {
        val layout = parseXml(File("src/main/res/layout/sun_clock_widget.xml"))
        val strings = parseXml(File("src/main/res/values/strings.xml"))

        assertWidgetLabel(layout, "sunrise_label", "sunrise_label")
        assertWidgetLabel(layout, "sunset_label", "sunset_label")
        assertStringResource(strings, "sunrise_label", "SUNRISE")
        assertStringResource(strings, "sunset_label", "SUNSET")
    }

    @Test
    fun manifestRegistersTheWidgetReceiverAndProviderMetadata() {
        val manifest = parseXml(File("src/main/AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver")
        val matchingReceivers = (0 until receivers.length)
            .map { receivers.item(it) as Element }
            .filter { it.getAttribute("android:name") == ".SunClockWidgetProvider" }

        assertEquals("Manifest must register exactly one widget receiver", 1, matchingReceivers.size)
        val metadata = matchingReceivers.single().getElementsByTagName("meta-data")
        assertEquals("Widget receiver must declare exactly one provider metadata entry", 1, metadata.length)
        val providerMetadata = metadata.item(0) as Element
        assertEquals(
            "Widget receiver must reference the expected provider XML",
            "@xml/sun_clock_widget_info",
            providerMetadata.getAttribute("android:resource")
        )
    }

    @Test
    fun widgetProviderMetadataReferencesTheActualWidgetLayout() {
        val provider = parseXml(File("src/main/res/xml/sun_clock_widget_info.xml"))
        assertEquals(
            "Launcher provider must inflate the layout containing the solar labels",
            "@layout/sun_clock_widget",
            provider.documentElement.getAttribute("android:initialLayout")
        )
        assertTrue(
            "Widget must be eligible for the home screen",
            provider.documentElement.getAttribute("android:widgetCategory").contains("home_screen")
        )
    }

    @Test
    fun providerBuildsRemoteViewsFromTheRegisteredWidgetLayout() {
        val source = File(
            "src/main/kotlin/com/sunrisesunset/clock/SunClockWidgetProvider.kt"
        ).readText()
        assertTrue(
            "Widget provider must create RemoteViews from sun_clock_widget",
            source.contains("RemoteViews(context.packageName, R.layout.sun_clock_widget)")
        )
        assertTrue(
            "Widget provider must submit its RemoteViews to AppWidgetManager",
            source.contains("manager.updateAppWidget(id, views)")
        )
    }

    @Test
    fun screenshotUiTestVerifiesWidgetRegistrationWithoutQueryingRemoteViewsChildren() {
        val source = File(
            "src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt"
        ).readText()
        val registrationCheck = source.indexOf("getAppWidgetIds(")
        val screenshotCapture = source.indexOf(
            "val screenshot = File(output, \"02-home-screen-widget.png\")"
        )
        assertTrue("UI test must verify launcher registered a widget instance", registrationCheck >= 0)
        assertTrue(
            "UI test must capture the launcher screenshot only after widget registration",
            screenshotCapture > registrationCheck
        )
        assertTrue(
            "UI test must not query RemoteViews child IDs through UiAutomator",
            !source.contains("By.res(context.packageName, \"sunrise_label\")") &&
                !source.contains("By.res(context.packageName, \"sunset_label\")")
        )
        assertTrue(
            "UI test must explain why RemoteViews children are not queried",
            source.contains("RemoteViews child TextViews are not individually queryable")
        )
    }

    @Test
    fun screenshotUiTestDragsTheWidgetPreviewInsteadOfTheAppName() {
        val source = File(
            "src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt"
        ).readText()
        val previewSelector = source.indexOf(
            "By.res(\"com.android.launcher3\", \"widget_preview\")"
        )
        val dragCall = source.indexOf("widgetPreview!!.drag(")
        val oldTextSelector = source.indexOf(
            "Until.findObject(By.textContains(\"Sunrise Sunset Clock\")), 8_000)"
        )
        assertTrue(
            "Screenshot UI test must locate Launcher3's actual widget_preview view",
            previewSelector >= 0
        )
        assertTrue(
            "Screenshot UI test must drag the widget preview after locating it",
            dragCall > previewSelector
        )
        assertTrue(
            "The app-name text selector must not be used as the draggable widget preview",
            oldTextSelector < 0
        )
    }

    @Test
    fun screenshotUiTestImportsAppWidgetManagerFromAndroidAppwidgetPackage() {
        val source = File(
            "src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt"
        ).readText()
        assertTrue(
            "AppWidgetManager must be imported from android.appwidget, not android.app",
            source.contains("import android.appwidget.AppWidgetManager")
        )
        assertTrue(
            "Incorrect android.app.AppWidgetManager import must not return",
            !source.contains("import android.app.AppWidgetManager")
        )
    }

    private fun assertWidgetLabel(
        layout: org.w3c.dom.Document,
        viewId: String,
        stringName: String
    ) {
        val nodes = layout.getElementsByTagName("TextView")
        val matches = (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("android:id") == "@+id/" + viewId }

        assertEquals("Expected exactly one TextView with id " + viewId, 1, matches.size)
        assertEquals(
            "TextView " + viewId + " must reference @string/" + stringName,
            "@string/" + stringName,
            matches.single().getAttribute("android:text")
        )
    }

    private fun assertStringResource(
        strings: org.w3c.dom.Document,
        name: String,
        expectedText: String
    ) {
        val nodes = strings.getElementsByTagName("string")
        val matches = (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("name") == name }

        assertEquals("Expected exactly one string resource named " + name, 1, matches.size)
        assertTrue(
            "String resource " + name + " must contain " + expectedText,
            matches.single().textContent.contains(expectedText, ignoreCase = true)
        )
    }

    private fun parseXml(file: File): org.w3c.dom.Document {
        assertTrue("Expected Android resource file at " + file.path, file.isFile)
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(file)
    }

    @Test
    fun widgetProviderRefreshesEveryInstanceAndHandlesSystemTimeBroadcasts() {
        val source = File(
            "src/main/kotlin/com/sunrisesunset/clock/SunClockWidgetProvider.kt"
        ).readText()
        assertTrue(
            "Widget onUpdate must update every supplied widget instance",
            source.contains("appWidgetIds.forEach { updateWidget(context, manager, it) }")
        )
        assertTrue(
            "Date changes must refresh the widget",
            source.contains("Intent.ACTION_DATE_CHANGED")
        )
        assertTrue(
            "Manual time changes must refresh the widget",
            source.contains("Intent.ACTION_TIME_CHANGED")
        )
        assertTrue(
            "Time-zone changes must refresh the widget",
            source.contains("Intent.ACTION_TIMEZONE_CHANGED")
        )
    }

    @Test
    fun widgetProviderDeclaresResizeAndPeriodicRefreshConfiguration() {
        val provider = parseXml(File("src/main/res/xml/sun_clock_widget_info.xml"))
        val root = provider.documentElement
        assertEquals("250dp", root.getAttribute("android:minWidth"))
        assertEquals("130dp", root.getAttribute("android:minHeight"))
        assertEquals("horizontal|vertical", root.getAttribute("android:resizeMode"))
        assertEquals("1800000", root.getAttribute("android:updatePeriodMillis"))
        assertTrue(
            "Widget must remain eligible for the home screen",
            root.getAttribute("android:widgetCategory").contains("home_screen")
        )
    }

    @Test
    fun locationRepositoryRejectsMalformedAndOutOfRangeSavedCoordinates() {
        val source = File(
            "src/main/kotlin/com/sunrisesunset/clock/LocationRepository.kt"
        ).readText()
        assertTrue(
            "Saved coordinates must be parsed as numbers",
            source.contains("?.toDoubleOrNull()")
        )
        assertTrue(
            "Saved coordinates must pass SolarCalculator range validation",
            source.contains("SolarCalculator.hasValidCoordinates(latitude, longitude)")
        )
        assertTrue(
            "Invalid coordinates must not be persisted",
            source.contains("Cannot save an invalid latitude/longitude pair")
        )
    }

}
