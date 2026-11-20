package com.sunrisesunset.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards launcher widget registration and resource contracts before emulator UI tests run. */
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
        val matching = (0 until receivers.length).map { receivers.item(it) as Element }
            .filter { it.getAttribute("android:name") == ".SunClockWidgetProvider" }
        assertEquals("Manifest must register exactly one widget receiver", 1, matching.size)
        val metadata = matching.single().getElementsByTagName("meta-data")
        assertEquals("Widget receiver must declare exactly one provider metadata entry", 1, metadata.length)
        assertEquals("@xml/sun_clock_widget_info", (metadata.item(0) as Element).getAttribute("android:resource"))
    }

    @Test
    fun widgetProviderMetadataReferencesTheActualWidgetLayout() {
        val provider = parseXml(File("src/main/res/xml/sun_clock_widget_info.xml")).documentElement
        assertEquals("@layout/sun_clock_widget", provider.getAttribute("android:initialLayout"))
        assertTrue(provider.getAttribute("android:widgetCategory").contains("home_screen"))
    }

    @Test
    fun providerBuildsRemoteViewsFromTheRegisteredWidgetLayout() {
        val source = File("src/main/kotlin/com/sunrisesunset/clock/SunClockWidgetProvider.kt").readText()
        assertTrue(source.contains("RemoteViews(context.packageName, R.layout.sun_clock_widget)"))
        assertTrue(source.contains("manager.updateAppWidget(id, buildRemoteViews(context))"))
    }

    @Test
    fun screenshotUiTestVerifiesWidgetRegistrationBeforeCapturingHomeScreen() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        val registration = source.indexOf("getAppWidgetIds(")
        val screenshot = source.indexOf("val screenshot = File(output, \"02-home-screen-widget.png\")")
        assertTrue("UI test must verify launcher registered a widget instance", registration >= 0)
        assertTrue("Home-screen screenshot must follow widget registration", screenshot > registration)
        assertTrue(!source.contains("By.res(context.packageName, \"sunrise_label\")"))
        assertTrue(!source.contains("By.res(context.packageName, \"sunset_label\")"))
        assertTrue(source.contains("RemoteViews children as one"))
        assertTrue(source.contains("individual TextViews aren't queryable"))
    }

    @Test
    fun screenshotUiTestDragsTheWidgetPreviewInsteadOfTheAppName() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        val selector = source.indexOf("By.res(\"com.android.launcher3\", \"widget_preview\")")
        val drag = source.indexOf("widgetPreview!!.drag(")
        assertTrue("Must locate Launcher3 widget_preview", selector >= 0)
        assertTrue("Must drag the preview after locating it", drag > selector)
        assertTrue(!source.contains("Until.findObject(By.textContains(\"Sunrise Sunset Clock\")), 8_000)"))
    }

    @Test
    fun screenshotUiTestScrollsWidgetRowFullyIntoViewportBeforeTapping() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        assertTrue(source.contains("bounds.bottom <= pickerViewportBottom"))
        assertTrue(source.contains("Widget picker row must be fully visible before selection"))
        assertTrue(source.contains("appEntry = device.findObject(widgetEntrySelector)"))
    }

    @Test
    fun screenshotUiTestDumpsHierarchyAsShellUidDirectlyIntoCollectedEvidenceDirectory() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        assertTrue(source.contains("/data/local/tmp/sunrise-sunset-ci"))
        assertTrue(source.contains("screencap -p"))
        assertTrue(source.contains("uiautomator dump --compressed"))
        assertTrue(source.contains("test -s \$hierarchyPath && echo HIERARCHY_DUMP_OK"))
        assertTrue(source.contains("verification.contains(\"HIERARCHY_DUMP_OK\")"))
        val evidenceFunction = source.substringAfter("private fun captureShellEvidence")
            .substringBefore("private fun hierarchyFile")
        assertTrue(!evidenceFunction.contains("getExternalFilesDir"))
    }

    @Test
    fun everyRequiredUiCheckpointRequestsShellOwnedHierarchyEvidence() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        assertTrue(source.contains("captureShellEvidence(device, context, \"01-app-setup\")"))
        assertTrue(source.contains("captureShellEvidence(device, context, \"02-launcher-before\")"))
        assertTrue(source.contains("captureShellEvidence(device, context, \"02-home-screen-widget\")"))
        assertTrue(source.contains("captureShellEvidence(device, context, name)"))
        assertTrue(source.contains("captureCheckpoint(device, context, \"08-widget-after-placement\")"))
    }

    @Test
    fun screenshotUiTestImportsAppWidgetManagerFromAndroidAppwidgetPackage() {
        val source = File("src/androidTest/kotlin/com/sunrisesunset/clock/StoreScreenshotTest.kt").readText()
        assertTrue(source.contains("import android.appwidget.AppWidgetManager"))
        assertTrue(!source.contains("import android.app.AppWidgetManager"))
    }

    @Test
    fun widgetProviderRefreshesEveryInstanceAndHandlesSystemTimeBroadcasts() {
        val source = File("src/main/kotlin/com/sunrisesunset/clock/SunClockWidgetProvider.kt").readText()
        assertTrue(source.contains("appWidgetIds.forEach { updateWidget(context, manager, it) }"))
        assertTrue(source.contains("Intent.ACTION_DATE_CHANGED"))
        assertTrue(source.contains("Intent.ACTION_TIME_CHANGED"))
        assertTrue(source.contains("Intent.ACTION_TIMEZONE_CHANGED"))
    }

    @Test
    fun widgetProviderDeclaresResizeAndPeriodicRefreshConfiguration() {
        val root = parseXml(File("src/main/res/xml/sun_clock_widget_info.xml")).documentElement
        assertEquals("250dp", root.getAttribute("android:minWidth"))
        assertEquals("130dp", root.getAttribute("android:minHeight"))
        assertEquals("horizontal|vertical", root.getAttribute("android:resizeMode"))
        assertEquals("1800000", root.getAttribute("android:updatePeriodMillis"))
        assertTrue(root.getAttribute("android:widgetCategory").contains("home_screen"))
    }

    @Test
    fun locationRepositoryRejectsMalformedAndOutOfRangeSavedCoordinates() {
        val source = File("src/main/kotlin/com/sunrisesunset/clock/LocationRepository.kt").readText()
        assertTrue(source.contains("?.toDoubleOrNull()"))
        assertTrue(source.contains("SolarCalculator.hasValidCoordinates(latitude, longitude)"))
        assertTrue(source.contains("Cannot save an invalid latitude/longitude pair"))
    }

    @Test
    fun savingLocationTriggersRefreshOfEveryExistingWidget() {
        val source = File("src/main/kotlin/com/sunrisesunset/clock/LocationSetupViewModel.kt").readText()
        val save = source.indexOf("repository.save(location)")
        val refresh = source.indexOf("refreshWidgets()", save)
        assertTrue(save >= 0 && refresh > save)
        assertTrue(source.contains("manager.getAppWidgetIds("))
        assertTrue(source.contains("ids.forEach { SunClockWidgetProvider.updateWidget(context, manager, it) }"))
    }

    @Test
    fun widgetRootOpensTheLocationSetupActivityAndManifestDeclaresTimeEvents() {
        val provider = File("src/main/kotlin/com/sunrisesunset/clock/SunClockWidgetProvider.kt").readText()
        assertTrue(provider.contains("Intent(context, LocationSetupActivity::class.java)"))
        assertTrue(provider.contains("setOnClickPendingIntent(R.id.widget_root, openApp)"))
        val manifest = parseXml(File("src/main/AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver")
        val widgetReceiver = (0 until receivers.length).map { receivers.item(it) as Element }
            .single { it.getAttribute("android:name") == ".SunClockWidgetProvider" }
        val actions = widgetReceiver.getElementsByTagName("action")
        val names = (0 until actions.length).map {
            (actions.item(it) as Element).getAttribute("android:name")
        }
        assertTrue(names.contains("android.intent.action.DATE_CHANGED"))
        assertTrue(names.contains("android.intent.action.TIME_SET"))
        assertTrue(names.contains("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun mainScreenKeepsOnlyLocationSetupAndRealSolarTimePlaceholders() {
        val source = File("src/main/kotlin/com/sunrisesunset/clock/LocationSetupScreen.kt").readText()
        assertTrue(source.contains("Set your location to see sunrise and sunset times."))
        assertTrue(source.contains("Use approximate location"))
        assertTrue(source.contains("Refresh approximate location"))
        assertTrue(source.contains("SolarTimeItem(icon = \"☀\", label = \"SUNRISE\")"))
        assertTrue(source.contains("SolarTimeItem(icon = \"☼\", label = \"SUNSET\")"))
        assertTrue(source.contains("text = \"--:--\""))
        assertTrue(!source.contains("Calendar"))
        assertTrue(!source.contains("History"))
        assertTrue(!source.contains("Moon phase"))
        assertTrue(!source.contains("Day length"))
    }

    private fun assertWidgetLabel(layout: org.w3c.dom.Document, viewId: String, stringName: String) {
        val nodes = layout.getElementsByTagName("TextView")
        val matches = (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("android:id") == "@+id/$viewId" }
        assertEquals("Expected exactly one TextView with id $viewId", 1, matches.size)
        assertEquals("@string/$stringName", matches.single().getAttribute("android:text"))
    }

    private fun assertStringResource(strings: org.w3c.dom.Document, name: String, expectedText: String) {
        val nodes = strings.getElementsByTagName("string")
        val matches = (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("name") == name }
        assertEquals("Expected exactly one string resource named $name", 1, matches.size)
        assertTrue(matches.single().textContent.contains(expectedText, ignoreCase = true))
    }

    private fun parseXml(file: File): org.w3c.dom.Document {
        assertTrue("Expected Android resource file at \${file.path}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(file)
    }
}
