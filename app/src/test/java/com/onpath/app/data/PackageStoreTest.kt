package com.onpath.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pkgJson(region: String, date: String = "2026-11-14T04:00:00Z") =
        """{"format":1,"region":"$region","bbox":[10.1,47.0,13.5,48.2],"osm_date":"$date","files":{"base.pmtiles":4}}"""

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bos ->
        ZipOutputStream(bos).use { z ->
            entries.forEach { (n, c) ->
                z.putNextEntry(ZipEntry(n))
                z.write(c.toByteArray())
                z.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun multiPackageImportSwitchAndDelete() {
        val dir = tmp.newFolder("onpath")

        // 1. Import oberbayern
        val obZip = zipBytes(
            "onpath-data/package.json" to pkgJson("oberbayern"),
            "onpath-data/base.pmtiles" to "BASE-OB",
            "onpath-data/segments/E10_N45.rd5" to "RD5-E10",
        )
        DataImporter.import(ByteArrayInputStream(obZip), "onpath-data-oberbayern.zip", dir)

        var pkgs = PackageStore.list(dir)
        assertEquals(1, pkgs.size)
        assertEquals("oberbayern", pkgs[0].region)
        assertTrue(pkgs[0].active)
        assertEquals("BASE-OB", File(dir, "base.pmtiles").readText())
        assertEquals("RD5-E10", File(dir, "segments/E10_N45.rd5").readText())

        // 2. Import tirol-salzburg -> oberbayern is automatically stashed, E10_N45.rd5 stays shared
        val tsZip = zipBytes(
            "onpath-data/package.json" to pkgJson("tirol-salzburg"),
            "onpath-data/base.pmtiles" to "BASE-TS",
            "onpath-data/segments/E10_N45.rd5" to "RD5-E10-NEW",
        )
        DataImporter.import(ByteArrayInputStream(tsZip), "onpath-data-tirol-salzburg.zip", dir)

        pkgs = PackageStore.list(dir)
        assertEquals(2, pkgs.size)
        assertEquals("tirol-salzburg", pkgs.first { it.active }.region)
        assertEquals("oberbayern", pkgs.first { !it.active }.region)
        assertEquals("BASE-TS", File(dir, "base.pmtiles").readText())
        assertEquals("BASE-OB", File(dir, "packages/oberbayern/base.pmtiles").readText())
        assertEquals("RD5-E10-NEW", File(dir, "segments/E10_N45.rd5").readText())

        // 3. Switch back to oberbayern
        assertTrue(PackageStore.activate(dir, "oberbayern"))
        pkgs = PackageStore.list(dir)
        assertEquals(2, pkgs.size)
        assertEquals("oberbayern", pkgs.first { it.active }.region)
        assertEquals("tirol-salzburg", pkgs.first { !it.active }.region)
        assertEquals("BASE-OB", File(dir, "base.pmtiles").readText())
        assertEquals("BASE-TS", File(dir, "packages/tirol-salzburg/base.pmtiles").readText())
        assertEquals("RD5-E10-NEW", File(dir, "segments/E10_N45.rd5").readText())

        // 4. Delete stored tirol-salzburg package
        assertTrue(PackageStore.deleteStored(dir, "tirol-salzburg"))
        pkgs = PackageStore.list(dir)
        assertEquals(1, pkgs.size)
        assertEquals("oberbayern", pkgs[0].region)
        assertTrue(pkgs[0].active)
        assertFalse(File(dir, "packages/tirol-salzburg").exists())
    }

    @Test
    fun failedSecondZipRollsBackActivePackage() {
        val dir = tmp.newFolder("onpath")
        val obZip = zipBytes(
            "onpath-data/package.json" to pkgJson("oberbayern"),
            "onpath-data/base.pmtiles" to "BASE-OB",
        )
        DataImporter.import(ByteArrayInputStream(obZip), "onpath-data-oberbayern.zip", dir)

        // Truncated zip stream right after package.json fails during extraction
        val partialZip = zipBytes(
            "onpath-data/package.json" to pkgJson("broken-region"),
            "onpath-data/base.pmtiles" to "PARTIAL",
        )
        val cutAt = partialZip.size - 12
        val brokenStream: InputStream = object : ByteArrayInputStream(partialZip, 0, cutAt) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n < 0) throw java.io.IOException("truncated zip")
                return n
            }
        }
        try {
            DataImporter.import(brokenStream, "broken.zip", dir)
            fail()
        } catch (_: Exception) {
            // expected
        }

        val pkgs = PackageStore.list(dir)
        assertEquals(1, pkgs.size)
        assertEquals("oberbayern", pkgs[0].region)
        assertTrue(pkgs[0].active)
        assertEquals("BASE-OB", File(dir, "base.pmtiles").readText())
    }
}
