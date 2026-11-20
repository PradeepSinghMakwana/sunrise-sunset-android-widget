package com.sunrisesunset.clock

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the widget's resource-name contract used by StoreScreenshotTest.
 * Host-side tests catch missing or misspelled IDs without booting an emulator.
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
}
