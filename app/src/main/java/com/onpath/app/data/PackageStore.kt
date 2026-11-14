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
 *
 * Crash safety: every change that moves more than one file first writes a small journal (`packages/.pending.json`:
 * phase + the regions involved). The moves themselves can be repeated; [recover] (called at app start, before the map
 * reads any file) finishes or rolls back a change the app did not survive, so the data folder never stays half one
 * package and half another.
 */
object PackageStore {
    const val PACKAGES_DIR = "packages"

    internal fun isPackageRootFile(f: File): Boolean =
        f.isFile && (f.name == DataFiles.PACKAGE_FILE ||
            f.name == "addresses.sqlite" ||
            f.name.endsWith(".pmtiles") ||
            f.name.endsWith(".geojson"))

    /** [DataImporter] target path of a file that belongs to one package (not the shared segments / styles). */
    fun isPackageRootName(target: String): Boolean =
        !target.contains('/') && (target == DataFiles.PACKAGE_FILE || target == "addresses.sqlite" ||
            target.endsWith(".pmtiles") || target.endsWith(".geojson"))

    private const val JOURNAL = ".pending.json"
    internal const val PHASE_STASH = "stash"       // root -> packages/<out>/ (then, with "in", packages/<in>/ -> root)
    internal const val PHASE_ACTIVATE = "activate" // packages/<in>/ -> root
    internal const val PHASE_IMPORT = "import"     // <out> is stashed, a zip is being written into the root
    internal const val PHASE_RESTORE = "restore"   // packages/<in>/ fills what the zip did not bring

    private fun journalFile(dataDir: File) = File(File(dataDir, PACKAGES_DIR), JOURNAL)

    internal fun writeJournal(dataDir: File, phase: String, out: String?, into: String?) {
        val f = journalFile(dataDir)
        f.parentFile?.mkdirs()
        val o = JSONObject().put("phase", phase)
        if (out != null) o.put("out", out)
        if (into != null) o.put("in", into)
        AtomicFiles.writeText(f, o.toString())
    }

    private fun clearJournal(dataDir: File) { journalFile(dataDir).delete() }

    /** An import that stashed the active package is complete: nothing left to recover. */
    fun importFinished(dataDir: File) = clearJournal(dataDir)

    /** Moves every package file of [from] into [to]; package.json goes last (while it is there, the folder is "the" package). */
    private fun moveAll(from: File, to: File) {
        val files = (from.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()).sortedBy { it.name == DataFiles.PACKAGE_FILE }
        if (files.isEmpty()) return
        to.mkdirs()
        for (f in files) moveOrCopy(f, File(to, f.name))
    }

    /**
     * Finishes or rolls back a package change the app did not survive (journal present). Returns true when something
     * was repaired. Safe to call at every start: without a journal it only checks that one file is absent.
     */
    fun recover(dataDir: File): Boolean {
        val jf = journalFile(dataDir)
        if (!jf.isFile) return false
        return try {
            val o = JSONObject(jf.readText())
            val phase = o.optString("phase")
            val out = o.optString("out").takeIf { it.isNotEmpty() }
            val into = o.optString("in").takeIf { it.isNotEmpty() }
            when (phase) {
                PHASE_STASH -> if (out != null) {
                    if (into != null && File(packageDir(dataDir, into), DataFiles.PACKAGE_FILE).isFile) {
                        // a switch: finish it
                        moveAll(dataDir, packageDir(dataDir, out))
                        writeJournal(dataDir, PHASE_ACTIVATE, null, into)
                        moveAll(packageDir(dataDir, into), dataDir)
                        packageDir(dataDir, into).delete()
                    } else {
                        // stashed for an import that never started writing: everything back
                        moveAll(packageDir(dataDir, out), dataDir)
                        packageDir(dataDir, out).delete()
                    }
                }
                PHASE_ACTIVATE -> if (into != null) {
                    moveAll(packageDir(dataDir, into), dataDir)
                    packageDir(dataDir, into).delete()
                }
                PHASE_IMPORT -> if (out != null && packageDir(dataDir, out).isDirectory) {
                    // the zip was not written completely: its files go, the package from before comes back
                    (dataDir.listFiles { f -> isPackageRootFile(f) || (f.isFile && f.name.endsWith(".importing")) } ?: emptyArray())
                        .forEach { it.delete() }
                    moveAll(packageDir(dataDir, out), dataDir)
                    packageDir(dataDir, out).delete()
                }
                PHASE_RESTORE -> if (into != null) restoreMissingFromStash(dataDir, into)
            }
            clearJournal(dataDir)
            Problems.warn("data", "An interrupted map package change ($phase) was repaired at start")
            true
        } catch (e: Exception) {
            // the journal stays: the next start tries again
            Problems.error("data", "An interrupted map package change could not be repaired", e)
            false
        }
    }

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
     * [forImport]: a zip is written into [dataDir] next – the journal stays ("import") until [importFinished] or
     * [rollbackFailedImport], so a start after a crash brings the stashed package back.
     */
    fun stashActive(dataDir: File, forImport: Boolean = false): String? {
        val pkgFile = File(dataDir, DataFiles.PACKAGE_FILE)
        if (!pkgFile.isFile) return null
        val meta = parseMeta(pkgFile, 0L, active = true) ?: return null
        val destDir = packageDir(dataDir, meta.region)
        if (destDir.exists()) deleteDirContents(destDir)
        writeJournal(dataDir, PHASE_STASH, meta.region, null)
        moveAll(dataDir, destDir)
        if (forImport) writeJournal(dataDir, PHASE_IMPORT, meta.region, null) else clearJournal(dataDir)
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
        writeJournal(dataDir, PHASE_RESTORE, null, stashedRegion)
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
        clearJournal(dataDir)
    }

    /** Rolls back partially written root files after a failed zip import and re-activates [stashedRegion]. */
    fun rollbackFailedImport(dataDir: File, stashedRegion: String, importedPaths: List<String>) {
        for (rel in importedPaths) {
            if (!rel.startsWith("segments/") && !rel.startsWith("styles/")) {
                File(dataDir, rel).delete()
            }
        }
        if (!activate(dataDir, stashedRegion)) {
            Problems.error("data", "Map package $stashedRegion could not be brought back after a failed import")
        }
        clearJournal(dataDir)
    }

    /** Activates a stored package by stashing any active package first and moving [targetRegion] into [dataDir]. */
    fun activate(dataDir: File, targetRegion: String): Boolean {
        val srcDir = packageDir(dataDir, targetRegion)
        if (!File(srcDir, DataFiles.PACKAGE_FILE).isFile) return false
        val currentFile = File(dataDir, DataFiles.PACKAGE_FILE)
        val currentRegion = if (currentFile.isFile) parseMeta(currentFile, 0L, active = true)?.region else null
        if (currentRegion == sanitizeRegion(targetRegion)) return true
        if (currentRegion != null) {
            val destDir = packageDir(dataDir, currentRegion)
            if (destDir.exists()) deleteDirContents(destDir)
            writeJournal(dataDir, PHASE_STASH, currentRegion, sanitizeRegion(targetRegion))
            moveAll(dataDir, destDir)
        } else {
            // Clean stray package root files left without a package.json so packages don't mix
            (dataDir.listFiles { f -> isPackageRootFile(f) } ?: emptyArray()).forEach { it.delete() }
        }
        writeJournal(dataDir, PHASE_ACTIVATE, null, sanitizeRegion(targetRegion))
        moveAll(srcDir, dataDir)
        srcDir.delete()
        clearJournal(dataDir)
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
