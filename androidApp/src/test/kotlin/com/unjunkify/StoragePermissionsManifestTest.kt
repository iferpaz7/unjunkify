package com.unjunkify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T1 gate: storage permissions declared per official scoped-storage docs.
 * Granular READ_MEDIA_* (API 33+), legacy READ_EXTERNAL_STORAGE capped at
 * maxSdkVersion=32, no PACKAGE_USAGE_STATS hard declaration (T4 opt-in),
 * no MANAGE_EXTERNAL_STORAGE, no INTERNET (offline-first).
 */
class StoragePermissionsManifestTest {

    private fun manifestText(): String = manifestFile().readText()

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("androidApp/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
    }

    @Test
    fun granularMediaPermissions_declared() {
        val text = manifestText()
        assertTrue(text.contains("android.permission.READ_MEDIA_IMAGES"))
        assertTrue(text.contains("android.permission.READ_MEDIA_VIDEO"))
    }

    @Test
    fun legacyStorageRead_cappedAt32() {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(manifestFile())
        val perms = doc.getElementsByTagName("uses-permission")
        var found = false
        for (i in 0 until perms.length) {
            val node = perms.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue
            if (name == "android.permission.READ_EXTERNAL_STORAGE") {
                found = true
                val maxSdk = node.attributes.getNamedItem("android:maxSdkVersion")?.nodeValue
                assertTrue("READ_EXTERNAL_STORAGE must set maxSdkVersion=32", maxSdk == "32")
            }
        }
        assertTrue("READ_EXTERNAL_STORAGE must be declared", found)
    }

    @Test
    fun notifications_kept_privilegedPermissions_absent() {
        val text = manifestText()
        assertTrue(text.contains("android.permission.POST_NOTIFICATIONS"))
        assertFalse(text.contains("PACKAGE_USAGE_STATS"))
        assertFalse(text.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(text.contains("QUERY_ALL_PACKAGES"))
        assertFalse(text.contains("android.permission.INTERNET"))
    }
}
