// The planner shows the signed routes further out than the app (owner 2026-11-20, Ulm / Langenau: "the green lines
// help planning"; a big screen is not a phone). The app's style (make_style.py route_tiers) starts regional routes
// (net 2) at z11 and every signed route at z12.5, as a 1–1.6 px line; here regional routes start at the cells' first
// zoom (z10 – below it no tile has them: region packages carry net >= 3 only) and every signed route at z11.5, a bit
// wider. Bikes and hiking alike; colours, the pink long-distance tier and the close-in bands stay the app's.
import type { StyleSpecification } from 'maplibre-gl';

export const REGIONAL_FROM = 10;
export const LOCAL_FROM = 11.5;
/** The overview line's width up to z13.5 (the app: 1.0 @ z9 … 2.2 @ z13.5); above, the layer's own stops (the band). */
const LOW_STOPS = [10, 1.8, 12.5, 2.2, 13.5, 2.4];
const UNTIL = 13.5;

/** Regional tier: ends where the local tier starts. */
const REGIONAL = ['routes-bike-regional', 'routes-hike-regional'];
/** Local tier = every signed route (the merged band lines; hiking: the band, its grey "over" part and their tint copies). */
const LOCAL = ['routes-bike-low', 'routes-hike', 'routes-hike-over', 'routes-hike-tint', 'routes-hike-over-tint'];

/** [width] with its stops up to [UNTIL] replaced by [LOW_STOPS] (an interpolate expression; anything else → LOW_STOPS). */
function lowWidth(width: unknown): unknown[] {
  const keep: unknown[] = [];
  if (Array.isArray(width) && width[0] === 'interpolate') {
    for (let i = 3; i + 1 < width.length; i += 2) if (typeof width[i] === 'number' && width[i] > UNTIL) keep.push(width[i], width[i + 1]);
  }
  return ['interpolate', ['linear'], ['zoom'], ...LOW_STOPS, ...keep];
}

export function plannerRouteZooms(s: StyleSpecification): StyleSpecification {
  for (const l of s.layers) {
    const regional = REGIONAL.includes(l.id);
    if (!regional && !LOCAL.includes(l.id)) continue;
    if (regional) { l.minzoom = REGIONAL_FROM; l.maxzoom = LOCAL_FROM; } else l.minzoom = LOCAL_FROM;
    const p = (l.paint ?? {}) as Record<string, unknown>;
    p['line-width'] = lowWidth(p['line-width']);
    (l as { paint?: unknown }).paint = p;
  }
  return s;
}
