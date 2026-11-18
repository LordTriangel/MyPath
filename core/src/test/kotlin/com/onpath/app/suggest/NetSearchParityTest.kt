package com.onpath.app.suggest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The port of the POC's graph routines gives the same results as the Python: `src/test/resources/suggest/net-parity.txt`
 * holds a small fixed network (a jittered grid with an island, a parallel way, a node without height) and what net.py
 * (`dijkstra` full / with stop / bounded, with and without the per-search factors, `path`, `seat_start`, `climb`) and
 * generate.py (`assemble`, `cut_spurs`) answer on it – made by tools/suggest-poc/port_parity.py. Costs so far, previous
 * node and link, metres so far: exactly equal for every node.
 */
class NetSearchParityTest {
    private val lines = File("src/test/resources/suggest/net-parity.txt").readLines().filter { !it.startsWith("#") }

    private fun nums(line: String, tag: String): List<String> {
        val parts = line.split(' ')
        assertEquals(line, tag, parts[0])
        return parts.drop(1).filter { it.isNotEmpty() }
    }

    private fun dbl(s: String) = if (s == "inf") Double.POSITIVE_INFINITY else s.toDouble()

    private fun build(): Triple<WayNet, DoubleArray, DoubleArray> {
        val b = WayNet.Builder()
        var cost = DoubleArray(0)
        var pen = DoubleArray(0)
        for (l in lines) {
            val f = l.split(' ')
            when (f[0]) {
                "N" -> {
                    val ele = f[3].toDouble()
                    b.addNode(WayNet.ilon(f[1].toDouble()), WayNet.ilat(f[2].toDouble()),
                        if (ele < -1000) WayNet.NO_ELE else (ele * 4).toInt().toShort())
                }
                "E" -> b.addLink(f[1].toInt(), f[2].toInt(), f[3].toFloat())
                "C" -> cost = f.drop(1).map { dbl(it) }.toDoubleArray()
                "P" -> {
                    pen = DoubleArray(b.linkCount)
                    for (k in 1 until f.size step 2) pen[f[k].toInt()] = f[k + 1].toDouble()
                }
            }
        }
        return Triple(b.build(0), cost, pen)
    }

    @Test fun samePythonResults() {
        val (net, cost, pen) = build()
        assertEquals(cost.size, net.linkCount)
        val s = NetSearch(net)
        var checked = 0
        var i = lines.indexOfFirst { it.startsWith("FULL") }
        while (i < lines.size) {
            val head = lines[i].split(' ')
            when (head[0]) {
                "FULL", "STOP", "BOUND" -> {
                    val src = head[1].toInt()
                    val p = if (head[0] != "FULL" && head[3] == "1") pen else null
                    when (head[0]) {
                        "FULL" -> s.full(src, cost)
                        "STOP" -> s.toStop(src, head[2].toInt(), cost, p)
                        else -> s.bounded(src, head[2].toDouble(), cost, p)
                    }
                    val d = nums(lines[i + 1], "D").map { dbl(it) }
                    val prev = nums(lines[i + 2], "PREV").map { it.toInt() }
                    val pe = nums(lines[i + 3], "PE").map { it.toInt() }
                    val ln = nums(lines[i + 4], "LN").map { it.toDouble() }
                    for (v in 0 until net.nodeCount) {
                        assertEquals("${lines[i]} cost to $v", d[v], s.dist[v], 0.0)
                        assertEquals("${lines[i]} prev of $v", prev[v], s.prevNode[v])
                        assertEquals("${lines[i]} link to $v", pe[v], s.prevLink[v])
                        assertEquals("${lines[i]} metres to $v", ln[v], s.metres[v], 0.0)
                    }
                    i += 5
                    if (head[0] == "STOP") {
                        val want = nums(lines[i], "PATH").map { it.toInt() }.toIntArray()
                        assertArrayEquals(lines[i - 5], want, s.path(head[2].toInt()))
                        i++
                    }
                }
                "SEAT" -> {
                    val ok = BooleanArray(net.linkCount) { head[2][it] == '1' }
                    val r = nums(lines[i + 1], "R").map { it.toInt() }
                    assertEquals(lines[i], r[0] to r[1], NetSearch.seatStart(net, head[1].toInt(), ok))
                    i += 2
                }
                "CLIMB" -> {
                    val walk = head.drop(1).map { it.toInt() }.toIntArray()
                    assertEquals(lines[i], nums(lines[i + 1], "R")[0].toDouble(), NetSearch.climb(net, walk), 0.0)
                    i += 2
                }
                "ASM" -> {
                    s.full(0, cost)
                    val asm = LoopAssembly(s, 0, cost, s.tree(), 300.0, 6.0, 2.5)
                    val pts = head.drop(1).map { it.toInt() }.toIntArray()
                    val r = nums(lines[i + 1], "R")
                    val got = asm.assemble(pts)
                    if (r == listOf("none")) assertNull(lines[i], got)
                    else assertArrayEquals(lines[i], r.map { it.toInt() }.toIntArray(), got)
                    i += 2
                }
                "CUT" -> {
                    val es = head.drop(1).map { it.toInt() }.toIntArray()
                    val want = nums(lines[i + 1], "R").map { it.toInt() }.toIntArray()
                    assertArrayEquals(lines[i], want, LoopAssembly.cutSpurs(net, 0, es))
                    i += 2
                }
                else -> throw AssertionError("unknown line ${lines[i]}")
            }
            checked++
        }
        assertEquals(30, checked)
    }
}
