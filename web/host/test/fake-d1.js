// A stand-in for Cloudflare D1 in tests: the same SQL on Node's built-in SQLite (node:sqlite), with D1's small API
// (prepare → bind → first / all / run, batch = one transaction). Used by the host tests and the browser tests.
import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const migrations = path.join(path.dirname(fileURLToPath(import.meta.url)), "../migrations");

/**
 * D1 binds `?NNN` (numbered) parameters by their number, and the host's SQL uses them (`?1` and `?3` twice …). Node's
 * SQLite binds plain arguments to `?NNN` slots only in newer releases; older ones skip every named slot, `?NNN`
 * included, and fail with SQLite's SQLITE_RANGE text "column index out of range" (donald 2026-11-13: sync-smoke and
 * sync-rate-smoke red at pairing, "sync POST: column index out of range"). So the fake rewrites each `?NNN` to a plain
 * `?` with its argument in place: the same binding on every Node version.
 */
export function plainParams(sql, args) {
  const out = [];
  const text = sql.replace(/\?(\d+)/g, (_m, n) => {
    const i = Number(n) - 1;
    if (i < 0 || i >= args.length) throw new Error(`D1 parameter ?${n} has no value (${args.length} bound)`);
    out.push(args[i]);
    return "?";
  });
  return { sql: text, args: out };
}

export function fakeD1() {
  const db = new DatabaseSync(":memory:");
  for (const f of fs.readdirSync(migrations).sort()) db.exec(fs.readFileSync(path.join(migrations, f), "utf8"));
  const plain = (r) => (r == null ? null : { ...r });
  const stmt = (sql, args) => { const p = plainParams(sql, args); return { st: db.prepare(p.sql), args: p.args }; };
  function run(sql, args0) {
    const { st, args } = stmt(sql, args0);
    if (/^\s*select/i.test(sql)) return { success: true, results: st.all(...args).map(plain), meta: { changes: 0 } };
    const r = st.run(...args);
    return { success: true, results: [], meta: { changes: Number(r.changes) } };
  }
  const make = (sql, args = []) => ({
    sql, args,
    bind: (...a) => make(sql, a),
    first: async (col) => { const { st, args: a } = stmt(sql, args); const r = plain(st.get(...a)); return r && col ? r[col] : r; },
    all: async () => { const { st, args: a } = stmt(sql, args); return { success: true, results: st.all(...a).map(plain), meta: {} }; },
    run: async () => run(sql, args),
  });
  return {
    sqlite: db,
    prepare: (sql) => make(sql),
    batch: async (list) => {
      db.exec("BEGIN");
      try { const out = list.map((st) => run(st.sql, st.args)); db.exec("COMMIT"); return out; } catch (e) { db.exec("ROLLBACK"); throw e; }
    },
  };
}
