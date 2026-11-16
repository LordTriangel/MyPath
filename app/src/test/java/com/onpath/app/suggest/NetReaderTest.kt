package com.onpath.app.suggest

import btools.mapaccess.MatchedWaypoint
import btools.mapaccess.NodesCache
import btools.mapaccess.OsmLink
import btools.mapaccess.OsmNode
import btools.mapaccess.OsmNodePairSet
import btools.router.OsmNodeNamed
import btools.router.ProfileCache
import btools.router.RoutingContext
import btools.router.RoutingEngine
import com.onpath.app.routing.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.random.Random

/**
 * The tour suggestions' network reader on real routing data (E5_N50.rd5 – Hilden; skipped when it is not on this
 * computer: ONPATH_SEGMENTS, default ~/onpath-routing/segments).
 *
 * The key risk (docs/design/tour-suggestions.md §7): a suggestion must never use a way navigation refuses. So:
 * - the reader's graph is BRouter's own graph: walked again here on BRouter's node objects (as RouteService.graph and
 *   the bench did), the same nodes, links, heights, lengths, cost factors and verdicts (BRouter's routing rule applied
 *   to its objects – OsmPath: way cost factor > 9998 or the end node's initial cost ≥ 10⁶ = refused);
 * - BRouter, run with the app's profile and parameters ([RouterProfile] = BRouterEngine's), routes only over links the
 *   reader calls allowed in that direction, and routes along every sampled link the reader calls allowed;
 * - the counts equal the POC's for the same wish (tools/suggest-poc/bench-phone/README.md).
 */
class NetReaderTest {
    private val segments = File(System.getenv("ONPATH_SEGMENTS") ?: (System.getProperty("user.home") + "/onpath-routing/segments"))
    private val profiles = File("src/main/assets/brouter")
    private val hilden = 6.9395 to 51.168

    private fun needData() = assumeTrue("no E5_N50.rd5 in $segments", File(segments, "E5_N50.rd5").isFile)

    /** The POC's parameters (character.py, settings.json) – for the counts of its own runs. */
    private fun pocProfile(bike: Boolean): NetReader.Profile {
        val kv = hashMapOf("processUnusedTags" to "1", "mainroad_penalty" to "3.0")
        if (bike) kv["consider_traffic"] = "1" else { kv["SAC_scale_limit"] = "2"; kv["SAC_scale_preferred"] = "2" }
        return NetReader.Profile(File(profiles, if (bike) "touring.brf" else "hiking-mountain.brf"), kv)
    }

    @Test fun sameCountsAsThePoc() {
        needData()
        val r = NetReader(segments)
        val hike = r.read(pocProfile(false), hilden.first, hilden.second, 3948.0)
        assertEquals(19_966, hike.nodeCount)
        assertEquals(26_180, hike.linkCount)
        val bike = r.read(pocProfile(true), hilden.first, hilden.second, 17_220.0, NetReader.Coarse.BIKE)
        assertEquals(167_145, bike.nodeCount)
        assertEquals(194_026, bike.linkCount)
        assertTrue("tiles decoded again: ${r.stats}", r.stats.tilesDecodedAgain < r.stats.tilesDecoded / 10)
    }

    @Test fun sameGraphAsBRoutersObjects() {
        needData()
        for ((activity, sac, radius, coarse) in listOf(
            Quad(Activity.HIKE, 2, 2500.0, NetReader.Coarse.NONE),
            Quad(Activity.HIKE, 5, 2000.0, NetReader.Coarse.NONE),
            Quad(Activity.BIKE_TOURING, 0, 4000.0, NetReader.Coarse.BIKE),
            Quad(Activity.MTB, 0, 2500.0, NetReader.Coarse.NONE),
        )) {
            val profile = RouterProfile.of(profiles, activity, sac)
            val net = NetReader(segments).read(profile, hilden.first, hilden.second, radius, coarse)
            val want = objectWalk(profile, hilden.first, hilden.second, radius, coarse)
            val got = HashMap<String, Int>()
            for (e in 0 until net.linkCount) got.merge(key(net, e), 1, Int::plus)
            assertEquals("$activity T$sac nodes", want.nodes, net.nodeCount)
            assertEquals("$activity T$sac links", want.links.values.sum(), net.linkCount)
            val missing = want.links.filter { (k, n) -> got[k] != n }.keys.take(5)
            assertTrue("$activity T$sac links differ, e.g. $missing", missing.isEmpty())
            assertEquals(net.lon(net.start), want.startLon, 0.0)
            assertEquals(net.lat(net.start), want.startLat, 0.0)
        }
    }

    @Test fun routerUsesOnlyAllowedLinks() {
        needData()
        for ((activity, sac) in listOf(Activity.HIKE to 2, Activity.HIKE to 4, Activity.BIKE_TOURING to 0, Activity.MTB to 0)) {
            val profile = RouterProfile.of(profiles, activity, sac)
            val net = NetReader(segments).read(profile, hilden.first, hilden.second, 3000.0)
            val at = HashMap<Long, Int>()
            for (v in 0 until net.nodeCount) at[pos(net.nodeLon[v], net.nodeLat[v])] = v
            val rnd = Random(7)
            var steps = 0
            var routes = 0
            var tries = 0
            while (routes < 12 && tries++ < 500) {
                val a = rnd.nextInt(net.nodeCount); val b = rnd.nextInt(net.nodeCount)
                if (a == b || far(net, a) || far(net, b)) continue
                val track = route(profile, net, intArrayOf(a, b))?.points ?: continue
                routes++
                var last = -1
                for (p in track) {
                    val v = at[p] ?: continue
                    if (last >= 0 && v != last) {
                        val links = (net.adjOff[last] until net.adjOff[last + 1]).filter { net.adjNode[it] == v }.map { net.adjLink[it] }
                        if (links.isNotEmpty()) {
                            assertTrue("$activity T$sac: BRouter goes ${net.lon(last)},${net.lat(last)} → ${net.lon(v)},${net.lat(v)} " +
                                "where the reader says no", links.any { net.allowed(it, last) })
                            steps++
                        }
                    }
                    last = v
                }
            }
            assertTrue("$activity: too few steps checked ($steps)", steps > 100)
        }
    }

    @Test fun routerTakesEveryAllowedLink() {
        needData()
        for ((activity, sac) in listOf(Activity.HIKE to 3, Activity.BIKE_TOURING to 0)) {
            val profile = RouterProfile.of(profiles, activity, sac)
            val net = NetReader(segments).read(profile, hilden.first, hilden.second, 3000.0)
            val rnd = Random(11)
            var checked = 0
            var tries = 0
            var direct = 0
            var cheaper = 0
            val wrong = ArrayList<String>()
            while (checked < 25 && tries++ < 2000) {
                val e = rnd.nextInt(net.linkCount)
                val a = net.linkA[e]; val b = net.linkB[e]
                if (net.linkLen[e] < 40f || far(net, a) || !net.allowed(e, a)) continue
                // a → a point on the link → b: BRouter goes along the link, unless it refuses it – or turns back and
                // takes a way that costs less than the link could ever cost (cost factor × metres is its lower bound)
                val r = route(profile, net, intArrayOf(a, -1 - e, b))
                checked++
                if (r == null) { wrong.add("link $e: no route"); continue }
                val m = trackMetres(r.points)
                if (abs(m - net.linkLen[e]) <= maxOf(3.0, 0.03 * net.linkLen[e])) { direct++; continue }
                val least = net.costFactor(e, a) * net.linkLen[e]
                if (r.cost < least) { cheaper++; continue }
                wrong.add("link $e (${net.lon(a)},${net.lat(a)} → ${net.lon(b)},${net.lat(b)}, ${net.linkLen[e]} m, " +
                    "cost ≥ $least): BRouter $m m, cost ${r.cost}")
            }
            assertTrue("$activity: ${wrong.size} of $checked allowed links not taken: ${wrong.take(5)}", wrong.isEmpty())
            assertEquals(25, checked)
            assertTrue("$activity: only $direct of 25 taken straight ($cheaper cheaper ways)", direct >= 20)
        }
    }

    // ---------------------------------------------------------------- helpers

    private data class Quad(val a: Activity, val sac: Int, val radius: Double, val coarse: NetReader.Coarse)

    private fun pos(ilon: Int, ilat: Int): Long = (ilon.toLong() shl 32) or (ilat.toLong() and 0xffffffffL)

    /** Keep routes away from the edge of the read network (a route may leave it there). */
    private fun far(net: WayNet, v: Int) = NetSearch.dist(net.lon(v), net.lat(v), hilden.first, hilden.second) > 1500.0

    /** The link as the object walk sees it: ends (lower position first), metres, cost factors and verdict both ways. */
    private fun key(net: WayNet, e: Int): String {
        val a = net.linkA[e]; val b = net.linkB[e]
        val pa = pos(net.nodeLon[a], net.nodeLat[a]); val pb = pos(net.nodeLon[b], net.nodeLat[b])
        val (lo, hi) = if (pa <= pb) a to b else b to a
        return linkKey(pos(net.nodeLon[lo], net.nodeLat[lo]), pos(net.nodeLon[hi], net.nodeLat[hi]), net.linkLen[e].toDouble(),
            net.costFactor(e, lo), net.costFactor(e, hi), net.allowed(e, lo), net.allowed(e, hi), net.ele(a) + net.ele(b),
            net.kinds.tag(net.linkKind[e], WayKinds.HIGHWAY))
    }

    private fun linkKey(lo: Long, hi: Long, m: Double, cfLo: Float, cfHi: Float, okLo: Boolean, okHi: Boolean, ele: Double,
                        highway: String?) =
        "$lo-$hi ${"%.1f".format(m)} m cf $cfLo/$cfHi ok $okLo/$okHi ele ${"%.2f".format(ele)} $highway"

    private class Walked(val nodes: Int, val links: Map<String, Int>, val startLon: Double, val startLat: Double)

    /** RouteService.graph on BRouter's node objects (NodesCache, direct weaving – what BRouter routes on). */
    private fun objectWalk(profile: NetReader.Profile, lon: Double, lat: Double, radius: Double, coarse: NetReader.Coarse): Walked {
        val rc = RoutingContext()
        rc.localFunction = profile.file.absolutePath
        rc.keyValues = HashMap(profile.keyValues)
        ProfileCache.parseProfile(rc)
        var nc = NodesCache(segments, rc.expctxWay, rc.forceSecondaryData, 2000L shl 20, null, true)
        try {
            val mwp = MatchedWaypoint()
            mwp.waypoint = OsmNode(WayNet.ilon(lon), WayNet.ilat(lat))
            mwp.name = "from"
            nc.matchWaypointsToNodes(arrayListOf(mwp), 400.0, OsmNodePairSet(1))
            nc = NodesCache(segments, rc.expctxWay, rc.forceSecondaryData, 2000L shl 20, nc, true)
            val centre = OsmNode(WayNet.ilon(lon), WayNet.ilat(lat))
            val start = nc.getGraphNode(mwp.node1)
            assertTrue(nc.obtainNonHollowNode(start))
            val ids = IdentityHashMap<OsmNode, Int>()
            val seen = IdentityHashMap<OsmLink, Boolean>()
            val links = HashMap<String, Int>()
            val queue = ArrayDeque<OsmNode>()
            ids[start] = 0
            queue.add(start)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                nc.expandHollowLinkTargets(n)
                var link = n.firstlink
                while (link != null) {
                    val l = link
                    link = l.getNext(n)
                    if (l.descriptionBitmap == null || seen.put(l, true) != null) continue
                    val t = l.getTarget(n)
                    if (t == null || !nc.obtainNonHollowNode(t)) continue
                    val rev = l.isReverse(n)
                    rc.expctxWay.evaluate(rev, l.descriptionBitmap)
                    val cf = rc.expctxWay.costfactor
                    val grantF = rc.expctxWay.nodeAccessGranted != 0f
                    rc.expctxWay.evaluate(!rev, l.descriptionBitmap)
                    val cb = rc.expctxWay.costfactor
                    val grantB = rc.expctxWay.nodeAccessGranted != 0f
                    val far = t.calcDistance(centre) > 500
                    if (far && minOf(cf, cb) >= coarse.maxCostFactor) continue
                    val text = rc.expctxWay.getKeyValueDescription(rev, l.descriptionBitmap)
                    if (far && coarse.skip?.containsMatchIn(text) == true && coarse.keep?.containsMatchIn(text) != true) continue
                    if (ids[t] == null) {
                        ids[t] = ids.size
                        if (t.calcDistance(centre) <= radius) queue.add(t)
                    }
                    // OsmPath / StdPath: way cost factor > 9998 or the target node's initial cost ≥ 10⁶ = refused
                    fun barred(node: OsmNode, granted: Boolean): Boolean {
                        val d = node.nodeDescription ?: return false
                        rc.expctxNode.evaluate(granted, d)
                        return rc.expctxNode.initialcost >= 1_000_000f
                    }
                    val okF = cf <= 9998f && !barred(t, grantF)
                    val okB = cb <= 9998f && !barred(n, grantB)
                    var m = 0.0
                    var pl = n.ilon; var pa = n.ilat
                    var tn = if (l.geometry == null) null else rc.geometryDecoder.decodeGeometry(l.geometry, n, t, rev)
                    while (tn != null) { m += WayNet.metres(pl, pa, tn.ilon, tn.ilat); pl = tn.ilon; pa = tn.ilat; tn = tn.next }
                    m += WayNet.metres(pl, pa, t.ilon, t.ilat)
                    val pn = pos(n.ilon, n.ilat); val pt = pos(t.ilon, t.ilat)
                    val highway = Regex("(^| )highway=(\\S+)").find(text)?.groupValues?.get(2)
                    val k = if (pn <= pt) linkKey(pn, pt, m.toFloat().toDouble(), cf, cb, okF, okB, n.elev + t.elev, highway)
                    else linkKey(pt, pn, m.toFloat().toDouble(), cb, cf, okB, okF, n.elev + t.elev, highway)
                    links.merge(k, 1, Int::plus)
                }
            }
            return Walked(ids.size, links, (start.ilon - 180_000_000) / 1e6, (start.ilat - 90_000_000) / 1e6)
        } finally {
            nc.close()
            ProfileCache.releaseProfile(rc)
        }
    }

    /**
     * BRouter's route through the given nodes (a negative number −1 − e = the middle shape point / middle of link e),
     * with the profile and parameters the app routes with → the positions of the track, or null when it fails.
     */
    private class Routed(val points: List<Long>, val cost: Int)

    private fun route(profile: NetReader.Profile, net: WayNet, points: IntArray): Routed? {
        val wps = points.mapIndexed { i, p ->
            OsmNodeNamed().apply {
                name = if (i == 0) "from" else if (i == points.size - 1) "to" else "via$i"
                if (p >= 0) { ilon = net.nodeLon[p]; ilat = net.nodeLat[p] } else {
                    val e = -1 - p
                    val k = net.shapePoints(e)
                    if (k > 0) { val g = net.geoOff[e] + k / 2; ilon = net.geo[2 * g]; ilat = net.geo[2 * g + 1] } else {
                        ilon = (net.nodeLon[net.linkA[e]] + net.nodeLon[net.linkB[e]]) / 2
                        ilat = (net.nodeLat[net.linkA[e]] + net.nodeLat[net.linkB[e]]) / 2
                    }
                }
            }
        }
        val rc = RoutingContext()
        rc.localFunction = profile.file.absolutePath
        rc.keyValues = HashMap(profile.keyValues)
        val engine = RoutingEngine(null, null, segments, wps, rc)
        engine.quite = true
        engine.doRun(20_000)
        if (engine.errorMessage != null) return null
        val track = engine.foundTrack ?: return null
        return Routed(track.nodes.map { pos(it.iLon, it.iLat) }, track.cost)
    }

    private fun trackMetres(track: List<Long>): Double {
        var m = 0.0
        for (i in 1 until track.size) {
            val a = track[i - 1]; val b = track[i]
            m += WayNet.metres((a shr 32).toInt(), a.toInt(), (b shr 32).toInt(), b.toInt())
        }
        return m
    }
}
