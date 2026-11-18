package com.onpath.app.cells

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Risk: `CellStore.tileHash` is stored on the phone (store.json, `InstalledCell.content`) and decides whether an
 * installed map area's tiles are reused or downloaded again. If the hash ever changes for the same tiles, every
 * installed area is downloaded again and the problem log fills with false alarms. In the rebuild (2026-11-19) an edit
 * dropped the tile ids from the hash and no test noticed – this one computes the hash by its definition
 * (per owned tile in tile-id order: 8 bytes id, big-endian, then SHA-256 of the stored tile bytes; SHA-256 over all).
 */
class TileHashTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun byDefinition(tiles: Map<Triple<Int, Int, Int>, ByteArray>, cell: CellId): String {
        val md = MessageDigest.getInstance("SHA-256")
        tiles.filterKeys { (z, x, y) -> cell.ownsTile(z, x, y) }
            .mapKeys { (k, _) -> PmTiles.zxyToTileId(k.first, k.second, k.third) }.toSortedMap()
            .forEach { (id, bytes) -> md.update(ByteBuffer.allocate(8).putLong(id).array()); md.update(sha(bytes)) }
        return hex(md.digest())
    }

    private fun archive(name: String, tiles: Map<Triple<Int, Int, Int>, ByteArray>): File =
        File(tmp.root, name).also { PmTiles.write(tiles, it) }

    @Test fun theHashIsTileIdsAndTileBytesOfTheCellsOwnTilesInIdOrder() {
        val cell = CellId.ofTile(12, 2200, 1440)!!
        val other = CellId.ofTile(12, 100, 100)!!
        assertNotEquals(cell, other)
        val tiles = mapOf(
            Triple(12, 2200, 1440) to byteArrayOf(1, 2, 3),
            Triple(12, 2201, 1440) to byteArrayOf(4, 5),
            Triple(13, 4400, 2880) to byteArrayOf(1, 2, 3), // the same bytes at another tile
            Triple(12, 100, 100) to byteArrayOf(9),          // another cell's tile: not in this cell's hash
        ).filterKeys { (z, x, y) -> cell.ownsTile(z, x, y) || other.ownsTile(z, x, y) }
        val owned = tiles.keys.count { (z, x, y) -> cell.ownsTile(z, x, y) }
        check(owned >= 2) { "the fixture must hold at least two tiles of the cell, has $owned" }
        assertEquals(byDefinition(tiles, cell), CellStore.tileHash(archive("a.pmtiles", tiles), cell))
    }

    @Test fun theSameBytesAtAnotherTileGiveAnotherHash() {
        val cell = CellId.ofTile(12, 2200, 1440)!!
        val a = CellStore.tileHash(archive("a.pmtiles", mapOf(Triple(12, 2200, 1440) to byteArrayOf(7))), cell)
        val b = CellStore.tileHash(archive("b.pmtiles", mapOf(Triple(12, 2201, 1440) to byteArrayOf(7))), cell)
        check(cell.ownsTile(12, 2201, 1440)) { "the fixture's second tile must belong to the cell" }
        assertNotEquals(a, b)
    }
}
