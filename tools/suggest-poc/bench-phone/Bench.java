import btools.mapaccess.MatchedWaypoint;
import btools.mapaccess.NodesCache;
import btools.mapaccess.OsmLink;
import btools.mapaccess.OsmNode;
import btools.mapaccess.OsmNodePairSet;
import btools.mapaccess.OsmTransferNode;
import btools.router.ProfileCache;
import btools.router.RoutingContext;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Phone feasibility benchmark for the tour suggestions (docs/design/tour-suggestions.md §3) – a measurement, not a port.
 * Does the expensive core of one POC run on the real routing data, the way a phone port would:
 *   1. network: walk BRouter's node cache around the start exactly as service/RouteService.java /graph does, but copy
 *      it into compact arrays (int / float, no object per link); then drop BRouter's objects.
 *   2. facts (stand-in): per link a few summed-area lookups in a 45 m grid and a road-beside check in a 12 m grid –
 *      the shape of facts.py's work, on made-up grid content (the real grids come from the map areas).
 *   3. searches: one full search from the start, then as many stop searches (turning point → next point / start, with
 *      the "used ×6, beside ×2.5" penalties of generate.assemble) and length-limited searches as the POC made for
 *      this wish (count_searches.py), plus the bookkeeping of assemble.
 * Prints times per phase, peak heap (sampled every 2 ms) and nodes settled. Plain Java 8, only BRouter's jars.
 *
 *   java -cp <brouter jars>:<classes> Bench <segmentsDir> <profileDir> <case> [repeats=2] [node cache MB=2000]
 *   case = hike2 | hike4 | bike2 | bike3 | zons-bike3   (start, radius and search counts below)
 */
public class Bench {
  // name, lon, lat, profile, bike?, radius m, max_m, target_m, sectors, stop searches, bounded searches (from count_searches.py)
  static final Object[][] CASES = {
    {"hike2", 6.9395, 51.168, "hiking-mountain", false, 3948.0, 9400.0, 8400.0, 12, 102, 3},
    {"hike4", 6.9395, 51.168, "hiking-mountain", false, 7000.0, 17800.0, 16800.0, 12, 96, 3},
    {"bike2", 6.9395, 51.168, "touring", true, 17220.0, 41000.0, 36000.0, 6, 60, 2},
    {"bike3", 6.9395, 51.168, "touring", true, 22000.0, 59000.0, 54000.0, 6, 58, 2},
    {"zons-bike3", 6.8448, 51.1231, "touring", true, 22000.0, 59000.0, 54000.0, 6, 54, 2},
    {"muenchen-bike3", 11.5755, 48.1374, "touring", true, 22000.0, 59000.0, 54000.0, 6, 92, 2},
  };

  // ---- the compact network
  static int nNodes, nEdges, start;
  static int[] nLon, nLat;
  static short[] nEle;
  static int[] eA, eB, eTag, eGeoOff;   // eGeoOff[e]..eGeoOff[e+1] = index pairs into geo (lon, lat)
  static float[] eLen, eCf, eCb;
  static int[] geo;
  static int geoN;
  static List<String> tagText = new ArrayList<String>();
  static int[] adjOff, adjEdge, adjNode;  // CSR, both directions

  static int cacheMb, descKinds;
  static volatile long peak;
  static volatile boolean sampling = true;

  public static void main(String[] a) throws Exception {
    final Runtime rt = Runtime.getRuntime();
    Thread sampler = new Thread(new Runnable() {
      public void run() {
        while (sampling) {
          long u = rt.totalMemory() - rt.freeMemory();
          if (u > peak) peak = u;
          try { Thread.sleep(2); } catch (InterruptedException e) { return; }
        }
      }
    });
    sampler.setDaemon(true);
    sampler.start();
    Object[] c = null;
    for (Object[] x : CASES) if (x[0].equals(a[2])) c = x;
    if (c == null) throw new IllegalArgumentException("unknown case " + a[2]);
    int repeats = a.length > 3 ? Integer.parseInt(a[3]) : 2;
    cacheMb = a.length > 4 ? Integer.parseInt(a[4]) : 2000;
    boolean bike = (Boolean) c[4];
    System.gc();
    long base = used(rt);
    peak = base;
    out("case %s  profile %s  radius %.0f m  heap max %d MB  base %.1f MB", c[0], c[3], (Double) c[5], rt.maxMemory() >> 20, base / 1e6);

    long t0 = System.nanoTime();
    long loadPeak = load(new File(a[0]), new File(a[1]), (String) c[3], bike, (Double) c[1], (Double) c[2], (Double) c[5], rt);
    long tLoad = System.nanoTime() - t0;
    buildAdj();
    long tAdj = System.nanoTime() - t0 - tLoad;
    System.gc();
    long netHeap = used(rt) - base;
    out("network: %d nodes, %d links, %d way descriptions, %d tag texts, %d geometry points | read %.0f ms, arrays %.0f ms | peak while reading %.1f MB, network alone %.1f MB",
      nNodes, nEdges, descKinds, tagText.size(), geoN / 2, tLoad / 1e6, tAdj / 1e6, (loadPeak - base) / 1e6, netHeap / 1e6);

    t0 = System.nanoTime();
    float[] cost = facts(bike);
    long tFacts = System.nanoTime() - t0;
    tagText = null;  // a port keeps a few small codes per way kind, not BRouter's tag text
    System.gc();
    long arrHeap = used(rt) - base;
    out("facts (stand-in) + cost: %.0f ms | network as arrays + cost, without tag text: %.1f MB", tFacts / 1e6, arrHeap / 1e6);

    for (int r = 0; r < repeats; r++) {
      peak = used(rt);
      t0 = System.nanoTime();
      long[] s = searches(cost, (Double) c[6], (Double) c[7], (Integer) c[8], (Integer) c[9], (Integer) c[10]);
      long tS = System.nanoTime() - t0;
      out("searches run %d: %.0f ms  (full %d, stop %d, bounded %d, assemble %d; settled full %d, stop %d, bounded %d)  peak %.1f MB",
        r + 1, tS / 1e6, s[0], s[1], s[2], s[3], s[4], s[5], s[6], (peak - base) / 1e6);
    }
    sampling = false;
    out("RESULT %s nodes=%d links=%d read_ms=%.0f facts_ms=%.0f peak_read_mb=%.1f net_mb=%.1f arrays_mb=%.1f", c[0], nNodes,
      nEdges, (tLoad + tAdj) / 1e6, tFacts / 1e6, (loadPeak - base) / 1e6, netHeap / 1e6, arrHeap / 1e6);
  }

  static long used(Runtime rt) { return rt.totalMemory() - rt.freeMemory(); }

  static void out(String f, Object... a) { System.out.println(String.format(java.util.Locale.ROOT, f, a)); }

  static int ilon(double lon) { return (int) ((lon + 180.0) * 1_000_000.0 + 0.5); }
  static int ilat(double lat) { return (int) ((lat + 90.0) * 1_000_000.0 + 0.5); }

  static double dist(int lon1, int lat1, int lon2, int lat2) {
    double ky = 0.11132;
    double kx = ky * Math.cos(Math.toRadians((lat1 + lat2) / 2e6 - 90.0));
    return Math.hypot((lon1 - lon2) * kx, (lat1 - lat2) * ky);
  }

  // ---------------------------------------------------------------- 1. network (as RouteService.graph)

  static long load(File segDir, File profileDir, String profile, boolean bike, double lon, double lat, double radius,
                   Runtime rt) throws Exception {
    Map<String, String> kv = new HashMap<String, String>();
    kv.put("processUnusedTags", "1");
    kv.put("mainroad_penalty", "3.0");
    if (bike) kv.put("consider_traffic", "1");
    else { kv.put("SAC_scale_limit", "2"); kv.put("SAC_scale_preferred", "2"); }
    double maxcf = bike ? 9000 : 1e9;
    Pattern skip = bike ? Pattern.compile("highway=(service|footway|steps|pedestrian|platform|corridor|bridleway)") : null;
    Pattern keep = bike ? Pattern.compile("route_bicycle_[a-z]+=yes|bicycle=designated") : null;

    RoutingContext rc = new RoutingContext();
    rc.localFunction = new File(profileDir, profile + ".brf").getAbsolutePath();
    rc.keyValues = kv;
    ProfileCache.parseProfile(rc);
    long cacheBytes = cacheMb * 1024L * 1024;  // BRouter's node cache limit (the POC service: 2000 MB)
    NodesCache nc = new NodesCache(segDir, rc.expctxWay, rc.forceSecondaryData, cacheBytes, null, true);
    long localPeak = 0;
    try {
      MatchedWaypoint mwp = new MatchedWaypoint();
      mwp.waypoint = new OsmNode(ilon(lon), ilat(lat));
      mwp.name = "from";
      List<MatchedWaypoint> l = new ArrayList<MatchedWaypoint>();
      l.add(mwp);
      nc.matchWaypointsToNodes(l, 400, new OsmNodePairSet(1));
      nc = new NodesCache(segDir, rc.expctxWay, rc.forceSecondaryData, cacheBytes, nc, true);
      OsmNode centre = new OsmNode(ilon(lon), ilat(lat));
      OsmNode s = nc.getGraphNode(mwp.node1);
      if (!nc.obtainNonHollowNode(s)) throw new IllegalStateException("start node not loadable");
      IdentityHashMap<OsmNode, Integer> ids = new IdentityHashMap<OsmNode, Integer>();
      IdentityHashMap<OsmLink, Boolean> seen = new IdentityHashMap<OsmLink, Boolean>();
      // per way description (BRouter shares the byte arrays) and direction: tag id, cost factors, skip verdict
      IdentityHashMap<byte[], float[]> desc = new IdentityHashMap<byte[], float[]>();
      Map<String, Integer> tagIds = new HashMap<String, Integer>();
      ArrayDeque<OsmNode> queue = new ArrayDeque<OsmNode>();
      int capN = 1 << 15, capE = 1 << 15, capG = 1 << 17;
      nLon = new int[capN]; nLat = new int[capN]; nEle = new short[capN];
      eA = new int[capE]; eB = new int[capE]; eTag = new int[capE]; eLen = new float[capE]; eCf = new float[capE];
      eCb = new float[capE]; eGeoOff = new int[capE + 1]; geo = new int[capG];
      nNodes = 0; nEdges = 0; geoN = 0;
      ids.put(s, addNode(s));
      start = 0;
      queue.add(s);
      int step = 0;
      while (!queue.isEmpty()) {
        OsmNode n = queue.poll();
        nc.expandHollowLinkTargets(n);
        for (OsmLink link = n.firstlink; link != null; link = link.getNext(n)) {
          if (link.descriptionBitmap == null || seen.put(link, Boolean.TRUE) != null) continue;
          OsmNode t = link.getTarget(n);
          if (t == null || !nc.obtainNonHollowNode(t)) continue;
          boolean rev = link.isReverse(n);
          float[] d = desc.get(link.descriptionBitmap);
          if (d == null) {  // [cf fwd, cf back, tag id fwd, tag id back, skip] – fwd = as stored
            d = new float[5];
            rc.expctxWay.evaluate(false, link.descriptionBitmap);
            d[0] = rc.expctxWay.getCostfactor();
            rc.expctxWay.evaluate(true, link.descriptionBitmap);
            d[1] = rc.expctxWay.getCostfactor();
            String tf = rc.expctxWay.getKeyValueDescription(false, link.descriptionBitmap);
            String tb = rc.expctxWay.getKeyValueDescription(true, link.descriptionBitmap);
            d[2] = tagId(tagIds, tf);
            d[3] = tagId(tagIds, tb);
            d[4] = skip != null && skip.matcher(tf).find() && !keep.matcher(tf).find() ? 1 : 0;
            desc.put(link.descriptionBitmap, d);
          }
          boolean far = t.calcDistance(centre) > 500;
          if (far && Math.min(d[0], d[1]) >= maxcf) continue;
          if (far && d[4] != 0) continue;
          Integer tid = ids.get(t);
          if (tid == null) {
            tid = addNode(t);
            ids.put(t, tid);
            if (t.calcDistance(centre) <= radius) queue.add(t);
          }
          int e = nEdges++;
          if (e + 1 >= eA.length) growEdges();
          eA[e] = ids.get(n); eB[e] = tid;
          eCf[e] = rev ? d[1] : d[0]; eCb[e] = rev ? d[0] : d[1];
          eTag[e] = (int) (rev ? d[3] : d[2]);
          eGeoOff[e] = geoN;
          double len = 0;
          int plon = n.ilon, plat = n.ilat;
          OsmTransferNode tn = link.geometry == null ? null : rc.geometryDecoder.decodeGeometry(link.geometry, n, t, rev);
          for (; tn != null; tn = tn.next) {
            len += dist(plon, plat, tn.ilon, tn.ilat);
            plon = tn.ilon; plat = tn.ilat;
            if (geoN + 2 > geo.length) geo = Arrays.copyOf(geo, geo.length * 2);
            geo[geoN++] = plon; geo[geoN++] = plat;
          }
          len += dist(plon, plat, t.ilon, t.ilat);
          eLen[e] = (float) len;
        }
        if ((++step & 1023) == 0) localPeak = Math.max(localPeak, rt.totalMemory() - rt.freeMemory());
      }
      eGeoOff[nEdges] = geoN;
      descKinds = desc.size();
      localPeak = Math.max(localPeak, Math.max(peak, rt.totalMemory() - rt.freeMemory()));
      return localPeak;
    } finally {
      nc.close();
      ProfileCache.releaseProfile(rc);
    }
  }

  static int tagId(Map<String, Integer> tagIds, String s) {
    Integer i = tagIds.get(s);
    if (i == null) { i = tagText.size(); tagIds.put(s, i); tagText.add(s); }
    return i;
  }

  static int addNode(OsmNode n) {
    if (nNodes >= nLon.length) {
      int c = nLon.length * 2;
      nLon = Arrays.copyOf(nLon, c); nLat = Arrays.copyOf(nLat, c); nEle = Arrays.copyOf(nEle, c);
    }
    nLon[nNodes] = n.ilon; nLat[nNodes] = n.ilat;
    nEle[nNodes] = (short) Math.max(-32000, Math.min(32000, Math.round(n.getElev())));
    return nNodes++;
  }

  static void growEdges() {
    int c = eA.length * 2;
    eA = Arrays.copyOf(eA, c); eB = Arrays.copyOf(eB, c); eTag = Arrays.copyOf(eTag, c); eLen = Arrays.copyOf(eLen, c);
    eCf = Arrays.copyOf(eCf, c); eCb = Arrays.copyOf(eCb, c); eGeoOff = Arrays.copyOf(eGeoOff, c + 1);
  }

  static void buildAdj() {
    adjOff = new int[nNodes + 1];
    for (int e = 0; e < nEdges; e++) { adjOff[eA[e] + 1]++; adjOff[eB[e] + 1]++; }
    for (int i = 0; i < nNodes; i++) adjOff[i + 1] += adjOff[i];
    adjEdge = new int[2 * nEdges];
    adjNode = new int[2 * nEdges];
    int[] fill = Arrays.copyOf(adjOff, nNodes);
    for (int e = 0; e < nEdges; e++) {
      int p = fill[eA[e]]++; adjEdge[p] = e; adjNode[p] = eB[e];
      p = fill[eB[e]]++; adjEdge[p] = e; adjNode[p] = eA[e];
    }
  }

  // ---------------------------------------------------------------- 2. facts (stand-in) and the search cost

  static float[] facts(boolean bike) {
    int minLon = Integer.MAX_VALUE, maxLon = Integer.MIN_VALUE, minLat = Integer.MAX_VALUE, maxLat = Integer.MIN_VALUE;
    for (int i = 0; i < nNodes; i++) {
      minLon = Math.min(minLon, nLon[i]); maxLon = Math.max(maxLon, nLon[i]);
      minLat = Math.min(minLat, nLat[i]); maxLat = Math.max(maxLat, nLat[i]);
    }
    double kx = 0.11132 * Math.cos(Math.toRadians((minLat + maxLat) / 2e6 - 90.0)), ky = 0.11132;
    // land grid 45 m, three kinds (nature, built, water) as summed-area tables, as land.py keeps them
    int gw = (int) ((maxLon - minLon) * kx / 45) + 2, gh = (int) ((maxLat - minLat) * ky / 45) + 2;
    Random rnd = new Random(1);
    int[][] sat = new int[3][(gw + 1) * (gh + 1)];
    for (int k = 0; k < 3; k++) {
      int[] s = sat[k];
      for (int y = 0; y < gh; y++) {
        int row = 0;
        for (int x = 0; x < gw; x++) {
          row += rnd.nextInt(4) == 0 ? 1 : 0;
          s[(y + 1) * (gw + 1) + x + 1] = s[y * (gw + 1) + x + 1] + row;
        }
      }
    }
    // road grid 12 m: the heaviest road per square (facts.RoadGrid)
    int rw = (int) ((maxLon - minLon) * kx / 12) + 3, rh = (int) ((maxLat - minLat) * ky / 12) + 3;
    byte[] road = new byte[rw * rh];
    float[] roadW = new float[tagText.size()];
    for (int t = 0; t < roadW.length; t++) {
      String s = tagText.get(t);
      roadW[t] = s.contains("highway=primary") || s.contains("highway=secondary") ? 0.9f
        : s.contains("highway=tertiary") ? 0.7f : s.contains("highway=unclassified") ? 0.35f : 0f;
    }
    for (int e = 0; e < nEdges; e++) {
      if (roadW[eTag[e]] < 0.3f) continue;
      byte w = (byte) (roadW[eTag[e]] * 100);
      int pl = nLon[eA[e]], pa = nLat[eA[e]];
      for (int g = eGeoOff[e]; g <= eGeoOff[e + 1]; g += 2) {
        int ql = g < eGeoOff[e + 1] ? geo[g] : nLon[eB[e]], qa = g < eGeoOff[e + 1] ? geo[g + 1] : nLat[eB[e]];
        int n = Math.max(1, (int) (dist(pl, pa, ql, qa) / 10));
        for (int k = 0; k <= n; k++) {
          int x = (int) (((pl + (ql - pl) * (double) k / n) - minLon) * kx / 12) + 1;
          int y = (int) (((pa + (qa - pa) * (double) k / n) - minLat) * ky / 12) + 1;
          int i = y * rw + x;
          if (road[i] < w) road[i] = w;
        }
        pl = ql; pa = qa;
      }
    }
    float[] cost = new float[nEdges];
    double sum = 0;
    for (int e = 0; e < nEdges; e++) {
      if (Math.min(eCf[e], eCb[e]) >= 9999) { cost[e] = -1; continue; }
      // mid point of the way
      int g0 = eGeoOff[e], g1 = eGeoOff[e + 1], ml, ma;
      if (g1 > g0) { int m = g0 + ((g1 - g0) / 4) * 2; ml = geo[m]; ma = geo[m + 1]; }
      else { ml = (nLon[eA[e]] + nLon[eB[e]]) / 2; ma = (nLat[eA[e]] + nLat[eB[e]]) / 2; }
      int gx = (int) ((ml - minLon) * kx / 45), gy = (int) ((ma - minLat) * ky / 45);
      double green = share(sat[0], gw, gh, gx, gy, 2), n300 = share(sat[0], gw, gh, gx, gy, 7);
      double n900 = share(sat[0], gw, gh, gx, gy, 20), built = share(sat[1], gw, gh, gx, gy, 1);
      double w90 = share(sat[2], gw, gh, gx, gy, 2), w300 = share(sat[2], gw, gh, gx, gy, 7);
      // beside a road: three points of the way, 3×3 squares each
      int bx = (int) ((ml - minLon) * kx / 12) + 1, by = (int) ((ma - minLat) * ky / 12) + 1, bw = 0;
      for (int k = 0; k < 3; k++)
        for (int i = -1; i <= 1; i++)
          for (int j = -1; j <= 1; j++) {
            int x = Math.max(0, Math.min(rw - 1, bx + i + k)), y = Math.max(0, Math.min(rh - 1, by + j));
            bw = Math.max(bw, road[y * rw + x]);
          }
      double traffic = Math.max(roadW[eTag[e]], 0.7 * bw / 100.0);
      double good = 0.3 * green + 0.3 * (n300 + n900) / 2 + 0.2 * (1 - built) + 0.2 * Math.max(w90, w300);
      double cf = Math.min(Math.min(eCf[e], eCb[e]), 5.0);
      cost[e] = (float) (eLen[e] * (1 + 4 * (1 - good)) * (1 + 3 * traffic) * (1 + 0.15 * (cf - 1)));
      sum += cost[e];
    }
    if (sum < 0) System.out.println(sum);  // keeps the work from being optimised away
    return cost;
  }

  static double share(int[] s, int gw, int gh, int x, int y, int r) {
    int x0 = Math.max(0, x - r), x1 = Math.min(gw, x + r + 1), y0 = Math.max(0, y - r), y1 = Math.min(gh, y + r + 1);
    int w = gw + 1;
    int v = s[y1 * w + x1] - s[y0 * w + x1] - s[y1 * w + x0] + s[y0 * w + x0];
    return v / (double) Math.max(1, (x1 - x0) * (y1 - y0));
  }

  // ---------------------------------------------------------------- 3. the searches of one run

  static float[] dist, len;
  static int[] prevE, heapN;
  static float[] heapK;
  static int heapSize;
  static int[] touched;
  static int touchedN;
  static long settled;

  /** Cheapest ways from src (cost < 0 = closed; pen = factor per link, 0 = none). Stops at stop (>= 0) or beyond maxLen. */
  static void dijkstra(int src, float[] cost, float[] pen, int stop, float maxLen) {
    for (int i = 0; i < touchedN; i++) { dist[touched[i]] = Float.POSITIVE_INFINITY; prevE[touched[i]] = -1; }
    touchedN = 0;
    heapSize = 0;
    dist[src] = 0; len[src] = 0; touched[touchedN++] = src;
    push(src, 0);
    while (heapSize > 0) {
      float du = heapK[0];
      int u = pop();
      if (du > dist[u]) continue;
      settled++;
      if (u == stop) break;
      if (len[u] > maxLen) continue;
      for (int p = adjOff[u], q = adjOff[u + 1]; p < q; p++) {
        int e = adjEdge[p];
        float c = cost[e];
        if (c < 0) continue;
        if (pen != null && pen[e] != 0) c *= pen[e];
        int v = adjNode[p];
        float nd = du + c;
        if (nd < dist[v]) {
          if (dist[v] == Float.POSITIVE_INFINITY) touched[touchedN++] = v;
          dist[v] = nd; prevE[v] = e; len[v] = len[u] + eLen[e];
          push(v, nd);
        }
      }
    }
  }

  static void push(int n, float k) {
    if (heapSize == heapN.length) { heapN = Arrays.copyOf(heapN, heapSize * 2); heapK = Arrays.copyOf(heapK, heapSize * 2); }
    int i = heapSize++;
    while (i > 0) {
      int p = (i - 1) >> 1;
      if (heapK[p] <= k) break;
      heapN[i] = heapN[p]; heapK[i] = heapK[p]; i = p;
    }
    heapN[i] = n; heapK[i] = k;
  }

  static int pop() {
    int top = heapN[0];
    int n = heapN[--heapSize];
    float k = heapK[heapSize];
    int i = 0;
    while (true) {
      int l = 2 * i + 1;
      if (l >= heapSize) break;
      int r = l + 1, m = r < heapSize && heapK[r] < heapK[l] ? r : l;
      if (heapK[m] >= k) break;
      heapN[i] = heapN[m]; heapK[i] = heapK[m]; i = m;
    }
    heapN[i] = n; heapK[i] = k;
    return top;
  }

  static int other(int e, int n) { return eA[e] == n ? eB[e] : eA[e]; }

  /** -> {full, stop, bounded, assemble, settled full, settled stop, settled bounded} */
  static long[] searches(float[] cost, double maxM, double targetM, int sectors, int nStop, int nBounded) {
    dist = new float[nNodes]; len = new float[nNodes]; prevE = new int[nNodes]; touched = new int[nNodes];
    heapN = new int[1 << 16]; heapK = new float[1 << 16];
    Arrays.fill(dist, Float.POSITIVE_INFINITY); Arrays.fill(prevE, -1);
    touchedN = 0;
    long[] r = new long[7];
    // the one full search from the start (place.py): keep its result
    settled = 0;
    dijkstra(start, cost, null, -1, Float.POSITIVE_INFINITY);
    r[0] = 1; r[4] = settled;
    float[] d0 = Arrays.copyOf(dist, nNodes), len0 = Arrays.copyOf(len, nNodes);
    int[] pe0 = Arrays.copyOf(prevE, nNodes);
    // turning points: per sector the most pleasantly reached node at a few distances (as generate.anchors)
    List<Integer> anchors = new ArrayList<Integer>();
    for (double f : new double[] {0.85, 1.0, 1.15, 0.7}) {
      int[] best = new int[sectors];
      float[] bestR = new float[sectors];
      Arrays.fill(best, -1);
      double want = f * targetM / 2.2;
      for (int v = 0; v < nNodes; v++) {
        if (d0[v] == Float.POSITIVE_INFINITY || len0[v] < 0.9 * want || len0[v] > 1.1 * want) continue;
        double b = Math.toDegrees(Math.atan2((nLon[v] - nLon[start]) * Math.cos(Math.toRadians(nLat[start] / 1e6 - 90)),
          nLat[v] - nLat[start]));
        int k = (int) (((b + 360) % 360) * sectors / 360.0) % sectors;
        float q = d0[v] / len0[v];
        if (best[k] < 0 || q < bestR[k]) { best[k] = v; bestR[k] = q; }
      }
      for (int v : best) if (v >= 0) anchors.add(v);
    }
    if (anchors.isEmpty()) throw new IllegalStateException("no turning points");
    float[] pen = new float[nEdges];
    int[] penSet = new int[nEdges];
    int[] leg = new int[nNodes];
    float free = targetM > 25000 ? 2500 : 700;
    long stopSettled = 0;
    int stops = 0, asm = 0, k = 0;
    while (stops < nStop) {
      // singles, pairs and chains of three, in turn – one stop search per point after the first
      int size = 1 + (asm % 3);
      int[] pts = new int[size + 1];
      for (int i = 0; i < size; i++) pts[i] = anchors.get((k + i * (1 + anchors.size() / 4)) % anchors.size());
      pts[size] = start;
      k++;
      asm++;
      int penN = 0, cur = start;
      double walked = 0;
      for (int i = 0; i <= size && stops < nStop; i++) {
        int w = pts[i], legN = 0;
        if (i == 0) {
          for (int x = w; pe0[x] >= 0; x = other(pe0[x], x)) leg[legN++] = pe0[x];
        } else {
          settled = 0;
          dijkstra(cur, cost, pen, w, Float.POSITIVE_INFINITY);
          stopSettled += settled;
          stops++;
          if (dist[w] == Float.POSITIVE_INFINITY) break;
          for (int x = w; x != cur && prevE[x] >= 0; x = other(prevE[x], x)) leg[legN++] = prevE[x];
        }
        // walk the leg in order: used ways cost 6x, ways touching them 2.5x (beyond the free stem)
        int node = cur;
        for (int j = legN - 1; j >= 0; j--) {
          int e = i == 0 ? leg[j] : leg[j];
          walked += eLen[e];
          node = other(e, node);
          if (walked > free) {
            if (pen[e] == 0) penSet[penN++] = e;
            pen[e] = 6f;
            for (int p = adjOff[node]; p < adjOff[node + 1]; p++) {
              int e2 = adjEdge[p];
              if (pen[e2] == 0) { pen[e2] = 2.5f; penSet[penN++] = e2; }
            }
          }
        }
        cur = w;
      }
      for (int i = 0; i < penN; i++) pen[penSet[i]] = 0;
    }
    r[1] = stops; r[3] = asm; r[5] = stopSettled;
    // lines: from an entry on the way out, a search limited to 0.75 × the longest wished length
    settled = 0;
    for (int i = 0; i < nBounded; i++) {
      int entry = anchors.get(i % anchors.size());
      dijkstra(entry, cost, null, -1, (float) (0.75 * maxM));
    }
    r[2] = nBounded; r[6] = settled;
    return r;
  }
}
