package com.onpath.app.data

import com.onpath.app.debug.Problems
import org.json.JSONObject
import java.io.File

/** Metadata for one local offline map package installed or stashed in [PackageStore]. */
data class LocalMapPackage(
    val region: String,
    val area: String,
    val osmDate: String?,
    val bytes: Long,
    val active: Boolean,
)

/**
 * Manages multiple imported offline `.zip` map packages inside `dataDir` (`files/onpath/`).
 *
 * Shared routing tiles (`segments/` `.rd5`) and custom styles (`styles/` `.json`) live once in [dataDir] and remain
 * available across all packages. Each package's map tiles, overlays, terrain, contours, POIs, offline addresses, and
 * `package.json` live either in [dataDir] (when active) or stashed inside `dataDir/packages/<region>/`.
 *
 * Moving files between [dataDir] and `dataDir/packages/<region>/` happens on the same filesystem via [AtomicFiles.replace],
 * so switching regions takes milliseconds and never duplicates gigabytes of data.
 */
object PackageStore {
    const val PACKAGES_DIR = "packages"

    internal fun isPackageRootFile(f: File): Boolean =
        f.isFile && (f.name == DataFiles.PACKAGE_FILE ||
            f.name == "addresses.sqlite" ||
            f.name.endsWith(".pmtiles") ||
            f.name.endsWith(".geojson"))

    private fun sanitizeRegion(raw: String): String =
        raw.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "package" }

    private fun packageDir(dataDir: File, region: String): File =
        File(File(dataDir, PACKAGES_DIR), sanitizeRegion(region))

    internal fun parseMeta(packageJson: File, bytes: Long, active: Boolean): LocalMapPackage? = try {
        val o = JSONObject(packageJson.readText())
        val region = sanitizeRegion(o.optString("region", "?"))
        val b = o.optJSONArray("bbox")
        val area = if (b != null && b.length() == 4) {
            "%.2f–%.2f E · %.2f–%.2f N".format(
                java.util.Locale.ROOT, b.optDouble(0), b.optDouble(2), b.optDouble(1), b.optDouble(3),
            )
        } else ""
        val osmDate = o.optString("osm_date").takeIf { it.isNotBlank() }?.take(10)
        LocalMapPackage(region = region, area = area, osmDate = osmDate, bytes = bytes, active = active)
    } catch (e: Exception) {
        Problems.warn("data", "Could not read ${packageJson.absolutePath}", e)
        null
    }

    /** Returns the active package plus any stashed packages in `dataDir/packages/`, active first then by region name. */
    fun list(dataDir: File): List<LocalMapPackage> {
        val result = mutableListOf<LocalMapPackage>()
        val activeFile = File(dataDir, DataFiles.PACKAGE_FILE)
        var activeRegion: String? = null
        if (activeFile.isFile) {
            val activeBytes = (dataDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()).sumOf { it.length() }
            parseMeta(activeFile, activeBytes, active = true)?.let {
                result += it
                activeRegion = it.region
            }
        }
        val dir = File(dataDir, PACKAGES_DIR)
        val stored = (dir.listFiles { f -> f.isDirectory } ?: emptyArray())
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        for (sub in stored) {
            val metaFile = File(sub, DataFiles.PACKAGE_FILE)
            if (!metaFile.isFile) continue
            val bytes = (sub.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()).sumOf { it.length() }
            val meta = parseMeta(metaFile, bytes, active = false) ?: continue
            if (meta.region == activeRegion) continue
            result += meta
        }
        return result
    }

    private fun moveOrCopy(src: File, dst: File) {
        try {
            AtomicFiles.replace(src, dst)
        } catch (e: java.io.IOException) {
            val tmp = File(dst.parentFile, ".${dst.name}.moving")
            try {
                src.inputStream().use { input ->
                    tmp.outputStream().use { out ->
                        input.copyTo(out, 256 * 1024)
                        out.fd.sync()
                    }
                }
                AtomicFiles.replace(tmp, dst)
                src.delete()
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    /**
     * Moves the currently active package out of [dataDir] into `packages/<region>/`.
     * Returns the stashed region name, or null if no `package.json` was active in [dataDir].
     */
    fun stashActive(dataDir: File): String? {
        val pkgFile = File(dataDir, DataFiles.PACKAGE_FILE)
        if (!pkgFile.isFile) return null
        val meta = parseMeta(pkgFile, 0L, active = true) ?: return null
        val destDir = packageDir(dataDir, meta.region)
        if (destDir.exists()) deleteDirContents(destDir)
        destDir.mkdirs()
        val files = dataDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()
        for (f in files) {
            moveOrCopy(f, File(destDir, f.name))
        }
        return meta.region
    }

    /** Removes a stale stashed copy that matches the newly activated/imported region in [dataDir]. */
    fun removeDuplicateOfActive(dataDir: File) {
        val pkgFile = File(dataDir, DataFiles.PACKAGE_FILE)
        if (!pkgFile.isFile) return
        val region = parseMeta(pkgFile, 0L, active = true)?.region ?: return
        val stash = packageDir(dataDir, region)
        if (stash.isDirectory) {
            deleteDirContents(stash)
            stash.delete()
        }
    }

    /**
     * Restores files from `packages/[stashedRegion]/` into [dataDir] when an imported zip did not bring a new
     * `package.json` (e.g. a style-only zip), keeping any newly imported files that already exist in [dataDir].
     */
    fun restoreMissingFromStash(dataDir: File, stashedRegion: String) {
        val stashDir = packageDir(dataDir, stashedRegion)
        if (!stashDir.isDirectory) return
        val files = stashDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()
        for (f in files) {
            val target = File(dataDir, f.name)
            if (!target.exists()) {
                moveOrCopy(f, target)
            } else {
                f.delete()
            }
        }
        deleteDirContents(stashDir)
        stashDir.delete()
    }

    /** Rolls back partially written root files after a failed zip import and re-activates [stashedRegion]. */
    fun rollbackFailedImport(dataDir: File, stashedRegion: String, importedPaths: List<String>) {
        for (rel in importedPaths) {
            if (!rel.startsWith("segments/") && !rel.startsWith("styles/")) {
                File(dataDir, rel).delete()
            }
        }
        activate(dataDir, stashedRegion)
    }

    /** Activates a stored package by stashing any active package first and moving [targetRegion] into [dataDir]. */
    fun activate(dataDir: File, targetRegion: String): Boolean {
        val srcDir = packageDir(dataDir, targetRegion)
        if (!File(srcDir, DataFiles.PACKAGE_FILE).isFile) return false
        val currentFile = File(dataDir, DataFiles.PACKAGE_FILE)
        val currentRegion = if (currentFile.isFile) parseMeta(currentFile, 0L, active = true)?.region else null
        if (currentRegion == sanitizeRegion(targetRegion)) return true
        if (currentRegion != null) {
            stashActive(dataDir)
        } else {
            // Clean stray package root files left without a package.json so packages don't mix
            (dataDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()).forEach { it.delete() }
        }
        val files = srcDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()
        for (f in files) {
            moveOrCopy(f, File(dataDir, f.name))
        }
        srcDir.delete()
        return true
    }

    /** Deletes a stashed (inactive) package directory in `packages/[targetRegion]/`. */
    fun deleteStored(dataDir: File, targetRegion: String): Boolean {
        val dir = packageDir(dataDir, targetRegion)
        if (!dir.isDirectory) return false
        deleteDirContents(dir)
        return dir.delete()
    }

    private fun deleteDirContents(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) deleteDirContents(f)
            f.delete()
        }
    }
}
