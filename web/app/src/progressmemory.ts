// The wait window's learned shares of time, per browser (the app's data/ProgressMemory.kt, mirrored – web/parity.json):
// each step owns the share of the ONE bar it really takes here, learned from the last waits (the engine's WebWait does
// the learning, bounded, in its stored forms "v1:…" / "r1:…" / "p1:…"). Robust first: storage blocked or full, a value
// damaged – the engine reads anything it does not know as the start values; nothing here ever throws.
import { problem } from './diag';

const KEY = { shares: 'onpath.progress.mapSteps', check: 'onpath.progress.routeCheck', part: 'onpath.progress.routePart' } as const;
type Name = keyof typeof KEY;
/** The session's values when storage cannot keep them. */
const mem: Partial<Record<Name, string>> = {};

export function readProgress(): Record<Name, string> {
  const out = { shares: '', check: '', part: '' };
  for (const k of Object.keys(KEY) as Name[]) {
    try { out[k] = localStorage.getItem(KEY[k]) ?? mem[k] ?? ''; } catch (e) {
      out[k] = mem[k] ?? '';
      problem('progress', 'Learned shares of the wait window not readable; the start values are used', e);
    }
    if (out[k].length > 40) out[k] = ''; // never a stranger value than the engine writes
  }
  return out;
}

export function writeProgress(v: Partial<Record<Name, string>>) {
  for (const k of Object.keys(KEY) as Name[]) {
    const s = v[k];
    if (typeof s !== 'string' || !s || s.length > 40) continue;
    mem[k] = s;
    try { localStorage.setItem(KEY[k], s); } catch (e) {
      problem('progress', 'Learned shares of the wait window not stored; kept for this session', e);
    }
  }
}
