// The world overview below the cells (docs/design/web-planner.md §16; the app: map/Overview.kt, map-cells.md §7):
// the APK's overview files (Natural Earth base z0–5, pre-rendered hill shade z0–7) drawn where there are no cells, so
// the map is never blank. The style is the app's own Overview.merge, run at build time (web/engine task
// overviewStyle); here only its asset:// URLs are pointed at the page's overview/ folder, and the cover (detail-cover:
// "hide the overview where the detailed map is") gets the cells found so far – the app uses its installed cells.
// Region packages (z7–9, map-cells.md §20): the build also writes the style merged for packages
// (onpath-overview-packages.json, Overview.merge with a placeholder box); the page prefers it and puts the boxes of the
// packages it finds into the `within` filter of the overview labels from z7 (followPackages) – the app reloads its
// style with the installed packages' boxes. Without any package that style draws like the plain overview one.
import type * as maplibregl from 'maplibre-gl';
import type { GeoJSONSource, StyleSpecification } from 'maplibre-gl';
import { PMTiles, type Protocol, type RangeResponse, type Source } from 'pmtiles';
import { problem } from './diag';

const FILES = ['base.pmtiles', 'hillshade.pmtiles'];
const ASSET = 'pmtiles://asset://overview/';

/**
 * The merged style when the overview files are deployed next to the page, else null (the plain style is used). The
 * files are read through [protocol] with [WholeOrRange] (the host may not serve byte ranges for them).
 */
export async function overviewStyle(base: string, protocol: Protocol): Promise<StyleSpecification | null> {
  const dir = new URL(base + 'overview/', location.href).href;
  const there = await Promise.all(FILES.map((f) => fetch(dir + f, { method: 'HEAD' }).then((r) => r.ok).catch(() => false)));
  if (!there.every(Boolean)) return null;
  let r = await fetch(base + 'style/onpath-overview-packages.json').catch(() => null);
  if (!r || !r.ok) {
    console.info('no region-package style (onpath-overview-packages.json): the overview style without packages is used');
    r = await fetch(base + 'style/onpath-overview.json').catch(() => null);
  }
  if (!r || !r.ok) return null;
  const s = (await r.json()) as StyleSpecification;
  for (const src of Object.values(s.sources)) {
    const x = src as { url?: string };
    if (typeof x.url === 'string' && x.url.startsWith(ASSET)) {
      const file = dir + x.url.slice(ASSET.length);
      x.url = 'pmtiles://' + file;
      if (!protocol.get(file)) protocol.add(new PMTiles(new WholeOrRange(file)));
    }
  }
  return s;
}

/**
 * pmtiles' Source for the overview files: byte ranges when the host serves them (206); when it answers a range with the
 * whole file (200 – the planner host's static assets do, field report 2026-11-18: below zoom 7 the map was plain
 * background, pmtiles' FetchSource refuses such an answer), that file is kept in memory and every later range is cut
 * from it. The overview files are small (a few MB, cached by the browser), so the map is never blank below the cells.
 */
export class WholeOrRange implements Source {
  private whole: Promise<ArrayBuffer> | null = null;
  constructor(private url: string, private fetchImpl: typeof fetch = (u, i) => fetch(u, i)) {}
  getKey() { return this.url; }
  async getBytes(offset: number, length: number, signal?: AbortSignal): Promise<RangeResponse> {
    let whole = this.whole;
    if (!whole) {
      const r = await this.fetchImpl(this.url, { signal, headers: { range: `bytes=${offset}-${offset + length - 1}` } });
      if (r.status === 206) return { data: await r.arrayBuffer(), etag: etag(r) };
      if (r.status !== 200) throw new Error(`Bad response code: ${r.status}`);
      if (!this.whole) { // concurrent ranges answered 200 too: the first whole file is kept, the others dropped
        console.info(`${this.url}: the host sends the whole file for a range – kept in memory`);
        // read without the tile's signal (the first read, the header, has none): an aborted tile never cuts the file
        // expected (cancel): the body is not needed, a failed cancel changes nothing
        const read = signal ? (r.body?.cancel().catch(() => {}), this.fetchImpl(this.url).then((w) => {
          if (!w.ok) throw new Error(`Bad response code: ${w.status}`);
          return w.arrayBuffer();
        })) : r.arrayBuffer();
        this.whole = read;
        read.catch((e) => { // a read cut off (tile aborted, connection lost): the next range asks again
          problem('overview', `overview file not read: ${this.url}`, e);
          if (this.whole === read) this.whole = null;
        });
      } else r.body?.cancel().catch(() => {}); // expected: the body is not needed, a failed cancel changes nothing
      whole = this.whole;
    }
    const all = await whole;
    return { data: all.slice(offset, offset + length) };
  }
}

const etag = (r: Response) => { const e = r.headers.get('etag'); return e && !e.startsWith('W/') ? e : undefined; };

/**
 * Keeps the cover on the cells found (z10 tiles "x/y"): there the detailed map hides the magnified overview. A cell
 * whose map stops drawing leaves [found] (cells.ts failed) and the overview shows there again.
 */
export function followCover(map: maplibregl.Map, found: Set<string>) {
  let shown: string | null = null; // the style's placeholder cover is replaced on the first update
  const update = () => {
    const now = [...found].sort().join(' ');
    if (now === shown) return;
    shown = now;
    const src = map.getSource('detail-cover') as GeoJSONSource | undefined;
    if (!src) return;
    src.setData({ type: 'FeatureCollection', features: [...found].map((k) => {
      const [x, y] = k.split('/').map(Number);
      const [w, n, e, s] = [lon(x), lat(y), lon(x + 1), lat(y + 1)];
      return { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [[[w, s], [e, s], [e, n], [w, n], [w, s]]] } };
    }) });
  };
  map.on('idle', update);
  map.on('sourcedata', update);
}

/**
 * Keeps the overview's labels from z7 (`<id>~outside`) out of the region packages found (z7 tiles "x/y"): their
 * filter's `within` geometry becomes the packages' boxes – the app's Overview.multiPolygon (web/parity.json).
 */
export function followPackages(map: maplibregl.Map, found: Set<string>) {
  let shown = 0;
  const update = () => {
    if (found.size === shown || !map.isStyleLoaded()) return;
    shown = found.size;
    const geo = { type: 'MultiPolygon', coordinates: [...found].map((k) => {
      const [x, y] = k.split('/').map(Number);
      const [w, n, e, s] = [lon(x, 7), lat(y, 7), lon(x + 1, 7), lat(y + 1, 7)];
      return [[[w, s], [e, s], [e, n], [w, n], [w, s]]];
    }) };
    for (const l of map.getStyle().layers) {
      if (!l.id.endsWith('~outside')) continue;
      const f = map.getFilter(l.id);
      if (f) map.setFilter(l.id, withGeometry(f, geo) as maplibregl.FilterSpecification);
    }
    console.info(`region packages: ${[...found].join(', ')} – overview labels left out there from zoom 7`);
  };
  map.on('idle', update);
  map.on('sourcedata', update);
}

/** [f] with every ["within", …] geometry replaced by [geo]. */
function withGeometry(f: unknown, geo: object): unknown {
  if (!Array.isArray(f)) return f;
  if (f[0] === 'within') return ['within', geo];
  return f.map((x) => withGeometry(x, geo));
}

const lon = (x: number, z = 10) => (x / 2 ** z) * 360 - 180;
const lat = (y: number, z = 10) => (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / 2 ** z))) * 180) / Math.PI;
