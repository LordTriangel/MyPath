// Kept places (owner 2026-11-15, „Orte merken“ – the app's b2504c3d, F-PLAN-14): „⌂ Zuhause“ (one place or none) and
// „★ Favoriten“ (newest first, never fall out), set in a held place's menu, shown on top of the empty search; and
// „Zuletzt gesucht“ (the last 20 picks, SearchStore.addRecent). The rules are the app's own (search/SavedPlaces, shared
// code, run in the engine – WebKept.kt); this module only keeps what they return.
//
// Kept in this browser (localStorage) and, while a phone is paired, synced with it (owner 2026-11-15 „Ja, mit Abgleich“,
// web-sync.md §5.4): the UI goes through the small KeptStore interface below (list / keep / unkeep / setHome / clearHome
// / onChange), the sync (sync.ts) through KeptSync (forExport / needsRestore / merge). A place is kept as ONE opaque
// line, the app's SearchStore line (id, name, kind, … tab-separated) – exactly what the phone stores and the sync carries.
//
// Robust first: a blocked or broken store never breaks the search – reads fall back to nothing kept, a failed write
// keeps the change for this page load; both are reported (diag.ts). Such a fallback is never sent to the phone: "nothing
// kept" would delete every place there (forExport → null). An unreadable store, or one written again over an unreadable
// one, first takes the folder's places back from the sync (needsRestore) before anything is exported again.
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

/** What the page sees of the kept places – the sync goes behind this. */
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
  /** Any change – here, in another tab of this browser, or from the phone. Returns the unsubscribe. */
  onChange(fn: (k: KeptPlaces) => void): () => void;
}

/**
 * The sync's side of the kept places (sync.ts, web-sync.md §5.4): before a round it exports what [forExport] gives,
 * after it [merge]s the round's changes in.
 */
export interface KeptSync {
  /** What is kept, for the export – null when the store could not be read or must be restored first (never export it). */
  forExport(): KeptPlaces | null;
  /** The store could not be read, or was written again over an unreadable one: the sync first takes every place back. */
  needsRestore(): boolean;
  /**
   * The sync's import, in turn with the user's changes: [fn] gets what is kept now and returns the new places; they are
   * stored and shown. [restored] = this was the restore ([needsRestore] ends). True = in the store (or nothing to store).
   */
  merge(fn: (k: KeptPlaces) => Promise<KeptPlaces>, restored?: boolean): Promise<boolean>;
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

type Failed = 'blocked' | 'unreadable' | null;

/** What a read found; [failed] = the store could not be read (blocked / unreadable) and [value] is the empty fallback. */
function readOr<T>(key: string, parse: (v: unknown) => T, empty: T): { value: T; failed: Failed } {
  let raw: string | null;
  try { raw = localStorage.getItem(key); } catch (e) {
    problem('search', `Kept places: storage blocked – ${key} not read`, e); return { value: empty, failed: 'blocked' };
  }
  if (raw == null) return { value: empty, failed: null };
  try { return { value: parse(JSON.parse(raw)), failed: null }; } catch (e) {
    problem('search', `Kept places: ${key} unreadable – none shown`, e); return { value: empty, failed: 'unreadable' };
  }
}
const read = <T>(key: string, parse: (v: unknown) => T, empty: T): T => readOr(key, parse, empty).value;

/** True = stored. */
function write(key: string, value: unknown): boolean {
  try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch (e) {
    problem('search', `Kept places: ${key} not saved (storage blocked or full) – kept for this page only`, e, 'error');
    return false;
  }
}

const lines = (v: unknown): PlaceCode[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && x.length > 0) : []);

interface Stored extends KeptPlaces { restore: boolean }

function parseKept(v: unknown): Stored {
  if (v == null || typeof v !== 'object' || Array.isArray(v)) throw new Error('not a kept-places object');
  const o = v as { home?: unknown; favourites?: unknown; restore?: unknown };
  return { home: typeof o.home === 'string' && o.home ? o.home : null, favourites: lines(o.favourites), restore: o.restore === true };
}

const same = (a: KeptPlaces, b: KeptPlaces) =>
  a.home === b.home && a.favourites.length === b.favourites.length && a.favourites.every((l, i) => l === b.favourites[i]);

export function keptStore(rules: KeptRules): KeptStore & KeptSync {
  // [failed]: this page could not read the store – what it shows is a fallback, never sent to the phone. [restore]: the
  // store was written again over an unreadable one – the sync takes the folder's places back first (kept in the store,
  // so a reload still knows it)
  let failed: Failed = null, restore = false;
  let kept: KeptPlaces = EMPTY;
  const load = () => {
    const r = readOr<Stored>(KEPT_KEY, parseKept, { ...EMPTY, restore: false });
    failed = r.failed; restore = r.value.restore;
    kept = { home: r.value.home, favourites: r.value.favourites };
  };
  load();
  const listeners = new Set<(k: KeptPlaces) => void>();
  const emit = () => { for (const f of listeners) { try { f(kept); } catch (e) { problem('search', 'Kept places: a listener failed', e); } } };
  /** Writes [kept]; [restored] = the sync's restore is in it. False = not stored (blocked / full). */
  const store = (restored = false): boolean => {
    const over = failed === 'unreadable'; // what the store held is gone here: the phone's places must come back first
    const flag = restored ? false : restore || over;
    const ok = write(KEPT_KEY, { v: 1, home: kept.home, favourites: kept.favourites, ...(flag ? { restore: true } : {}) });
    if (ok) {
      if (over && flag) problem('search', 'Kept places: written again over an unreadable store – the phone\'s places come back with the next sync');
      failed = null; restore = flag;
    }
    return ok;
  };
  // one change after the other: a second tap waits for the first one's answer; the sync's import waits its turn too
  let chain: Promise<unknown> = Promise.resolve();
  const change = (op: KeptOp, code: PlaceCode): Promise<void> => {
    const p = chain.then(async () => {
      const next = await rules.apply(kept, op, code);
      kept = { home: next.home, favourites: [...next.favourites] };
      store();
      emit();
    }).catch((e) => problem('search', `Kept places: ${op} failed`, e, 'error'));
    chain = p;
    return p;
  };
  // another tab of this browser kept a place (or its sync merged the phone's): the same lists here
  try {
    addEventListener('storage', (e: StorageEvent) => { if (e.key === KEPT_KEY) { load(); emit(); } });
  } catch { /* expected: no window (a test in plain Node) */ }
  return {
    list: () => kept,
    keep: (c) => change('keep', c),
    unkeep: (c) => change('unkeep', c),
    setHome: (c) => change('setHome', c),
    clearHome: () => change('clearHome', ''),
    state: (c) => rules.state(kept, c),
    onChange: (f) => { listeners.add(f); return () => { listeners.delete(f); }; },
    forExport: () => (failed || restore ? null : { home: kept.home, favourites: [...kept.favourites] }),
    needsRestore: () => failed === 'unreadable' || (!failed && restore),
    merge: (fn, restored = false) => {
      const p = chain.then(async () => {
        const next = await fn({ home: kept.home, favourites: [...kept.favourites] });
        const changed = !same(next, kept);
        if (!changed && !restored) return failed !== 'blocked';
        kept = { home: next.home, favourites: [...next.favourites] };
        const ok = failed !== 'blocked' && store(restored);
        if (changed) emit();
        return ok;
      });
      chain = p.catch(() => undefined); // expected: the sync sees the error and tries again next round
      return p;
    },
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
