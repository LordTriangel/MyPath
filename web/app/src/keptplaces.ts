// Kept places (owner 2026-11-15, „Orte merken“ – the app's b2504c3d, F-PLAN-14): „⌂ Zuhause“ (one place or none) and
// „★ Favoriten“ (newest first, never fall out), set in a held place's menu, shown on top of the empty search; and
// „Zuletzt gesucht“ (the last 20 picks, SearchStore.addRecent). The rules are the app's own (search/SavedPlaces, shared
// code, run in the engine – WebKept.kt); this module only keeps what they return.
//
// For now in this browser only (localStorage). The owner wants them synced with the phone later; that format is not
// defined yet – so everything goes through the small KeptStore interface below (list / keep / unkeep / setHome /
// clearHome / onChange) and a sync can be put behind it without touching the UI. A place is kept as ONE opaque line,
// the app's SearchStore line (id, name, kind, … tab-separated) – exactly what the phone stores.
//
// Robust first: a blocked or broken store never breaks the search – reads fall back to nothing kept, a failed write
// keeps the change for this page load; both are reported (diag.ts).
import { problem } from './diag';

/** A kept place: the app's SearchStore line, opaque to the page (the engine reads it). */
export type PlaceCode = string;
export interface KeptPlaces { home: PlaceCode | null; favourites: PlaceCode[] }
export type KeptOp = 'keep' | 'unkeep' | 'setHome' | 'clearHome';

/** The app's rules (engine: WebKept.kt – SavedPlaces / SearchStore.addRecent). */
export interface KeptRules {
  apply(k: KeptPlaces, op: KeptOp, code: PlaceCode): Promise<KeptPlaces>;
  state(k: KeptPlaces, code: PlaceCode): Promise<{ favourite: boolean; home: boolean }>;
  addRecent(recent: PlaceCode[], code: PlaceCode): Promise<PlaceCode[]>;
}

/** What the page sees of the kept places – a later sync goes behind this. */
export interface KeptStore {
  list(): KeptPlaces;
  /** „☆ Merken“: the place becomes a favourite (first); nothing when it is one already. */
  keep(code: PlaceCode): Promise<void>;
  /** „★ Gemerkt“ tapped: the place (every copy of it) is no favourite any more. */
  unkeep(code: PlaceCode): Promise<void>;
  /** „⌂ Als Zuhause festlegen“ (replaces the old home). */
  setHome(code: PlaceCode): Promise<void>;
  /** „Zuhause entfernen“. */
  clearHome(): Promise<void>;
  /** Is the place a favourite / the home (the menu's rows)? */
  state(code: PlaceCode): Promise<{ favourite: boolean; home: boolean }>;
  /** Any change – here, or in another tab of this browser. Returns the unsubscribe. */
  onChange(fn: (k: KeptPlaces) => void): () => void;
}

/** „Zuletzt gesucht“ (the app's SearchStore.recent – not in the app's backup, never synced). */
export interface SearchRecent {
  list(): PlaceCode[];
  add(code: PlaceCode): Promise<void>;
  clear(): void;
}

export const KEPT_KEY = 'onpath.keptPlaces';
export const RECENT_KEY = 'onpath.searchRecent';
const EMPTY: KeptPlaces = { home: null, favourites: [] };

function read<T>(key: string, parse: (v: unknown) => T, empty: T): T {
  let raw: string | null;
  try { raw = localStorage.getItem(key); } catch (e) { problem('search', `Kept places: storage blocked – ${key} not read`, e); return empty; }
  if (raw == null) return empty;
  try { return parse(JSON.parse(raw)); } catch (e) { problem('search', `Kept places: ${key} unreadable – none shown`, e); return empty; }
}

function write(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {
    problem('search', `Kept places: ${key} not saved (storage blocked or full) – kept for this page only`, e, 'error');
  }
}

const lines = (v: unknown): PlaceCode[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && x.length > 0) : []);

function parseKept(v: unknown): KeptPlaces {
  const o = (v ?? {}) as { home?: unknown; favourites?: unknown };
  return { home: typeof o.home === 'string' && o.home ? o.home : null, favourites: lines(o.favourites) };
}

export function keptStore(rules: KeptRules): KeptStore {
  let kept = read(KEPT_KEY, parseKept, EMPTY);
  const listeners = new Set<(k: KeptPlaces) => void>();
  const emit = () => { for (const f of listeners) { try { f(kept); } catch (e) { problem('search', 'Kept places: a listener failed', e); } } };
  // one change after the other: a second tap waits for the first one's answer
  let chain: Promise<void> = Promise.resolve();
  const change = (op: KeptOp, code: PlaceCode) => (chain = chain.then(async () => {
    const next = await rules.apply(kept, op, code);
    kept = { home: next.home, favourites: [...next.favourites] };
    write(KEPT_KEY, { v: 1, ...kept });
    emit();
  }).catch((e) => problem('search', `Kept places: ${op} failed`, e, 'error')));
  // another tab of this browser kept a place: the same lists here
  try {
    addEventListener('storage', (e: StorageEvent) => { if (e.key === KEPT_KEY) { kept = read(KEPT_KEY, parseKept, EMPTY); emit(); } });
  } catch { /* expected: no window (a test in plain Node) */ }
  return {
    list: () => kept,
    keep: (c) => change('keep', c),
    unkeep: (c) => change('unkeep', c),
    setHome: (c) => change('setHome', c),
    clearHome: () => change('clearHome', ''),
    state: (c) => rules.state(kept, c),
    onChange: (f) => { listeners.add(f); return () => { listeners.delete(f); }; },
  };
}

export function searchRecent(rules: KeptRules): SearchRecent {
  let recent = read(RECENT_KEY, lines, [] as PlaceCode[]);
  let chain: Promise<void> = Promise.resolve();
  return {
    list: () => recent,
    add: (code) => (chain = chain.then(async () => {
      recent = await rules.addRecent(recent, code);
      write(RECENT_KEY, recent);
    }).catch((e) => problem('search', 'Zuletzt gesucht: not added', e))),
    clear: () => { recent = []; write(RECENT_KEY, recent); },
  };
}
