// Round-trip suggestions in the planner (suggest.ts, WebSuggest.kt; docs/design/tour-suggestions.md §7 stage 3): with
// the switch off nothing changes (no „Rundtour von hier“); on (the ?debug test setting) the first tap's menu has the row
// right below „Hier starten“ → the wish (activity, two range sliders, five Ziel chips, Abbrechen · OK) → the wait →
// cards (badge, km · h · ↑, at most 2 green + 2 yellow pills) with only the chosen loop on the map → „Übernehmen“ = a
// normal round-trip plan. German and English words. Needs E5_N50.rd5 (Hilden) and the cells' suggest files
// (<dir>/10-x-y/suggest.bin, content 2) – skipped without them (says so).
// Usage: node test/suggest-smoke.mjs [segmentsDir] [suggestFilesDir]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';
import { serve } from './serve.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const segments = path.resolve(process.argv[2] ?? '/home/user/build/routing-test/segments');
const filesDir = path.resolve(process.argv[3] ?? path.join(os.homedir(), 'onpath-suggest-poc/suggest-file/cells-c2'));
const HILDEN = [51.168, 6.9395];
if (!fs.existsSync(path.join(segments, 'E5_N50.rd5')) || !fs.existsSync(path.join(filesDir, '10-531-341/suggest.bin'))) {
  console.log(`skip: needs E5_N50.rd5 in ${segments} and the Hilden suggest files in ${filesDir}`);
  process.exit(0);
}
// a map store with the suggest files as the cells' current version (cells/f2/10/x/y/current.json + <version>/suggest.bin)
const store = fs.mkdtempSync(path.join(os.tmpdir(), 'suggest-smoke-'));
for (const d of fs.readdirSync(filesDir).filter((n) => /^10-\d+-\d+$/.test(n))) {
  const [, x, y] = d.split('-');
  const dir = path.join(store, 'cells/f2/10', x, y);
  fs.mkdirSync(path.join(dir, 't1'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'current.json'), JSON.stringify({ version: 't1' }));
  fs.copyFileSync(path.join(filesDir, d, 'suggest.bin'), path.join(dir, 't1', 'suggest.bin'));
}
const { server, url } = await serve({ dist: path.resolve(here, '../dist'), segments, dataDir: store });
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const fails = [];
const check = (ok, what) => { console.log((ok ? 'ok   ' : 'FAIL ') + what); if (!ok) fails.push(what); };

async function open(lang, on) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 860 } });
  if (on) await ctx.addInitScript(() => { try { localStorage.setItem('onpath.suggestTest', '1'); } catch { /* expected: no storage */ } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(`${url}index.html?lang=${lang}&data=${encodeURIComponent(url + 'data/')}`);
  await page.waitForFunction(() => document.body.dataset.engine === 'ready', null, { timeout: 60_000 });
  await page.evaluate(([la, lo]) => window.__map.jumpTo({ center: [lo, la], zoom: 14 }), HILDEN);
  await page.waitForTimeout(500);
  return { ctx, page, errors };
}
/** The first tap's menu at Hilden: its row labels. */
async function firstMenu(page) {
  const p = await page.evaluate(([la, lo]) => { const q = window.__map.project([lo, la]), r = window.__map.getCanvas().getBoundingClientRect(); return [q.x + r.x, q.y + r.y]; }, HILDEN);
  await page.mouse.click(p[0], p[1]);
  await page.waitForSelector('.pointmenu');
  return page.$$eval('.pointmenu button[role=menuitem] .txt > span:first-child', (b) => b.map((x) => x.textContent));
}

try {
  // ---- switched off (the default): the planner as before ----
  {
    const { ctx, page } = await open('de', false);
    const rows = await firstMenu(page);
    check(!rows.includes('Rundtour von hier') && rows[0] === 'Hier starten', `switch off: no „Rundtour von hier“ (${rows.join(' · ')})`);
    check(await page.evaluate(() => !document.body.dataset.suggest), 'switch off: nothing set up');
    await ctx.close();
  }
  // ---- switched on, German ----
  {
    const { ctx, page, errors } = await open('de', true);
    const rows = await firstMenu(page);
    check(rows[0] === 'Hier starten' && rows[1] === 'Rundtour von hier', `the row directly below „Hier starten“ (${rows.join(' · ')})`);
    await page.click('.pointmenu button:nth-of-type(2)');
    await page.waitForSelector('#suggestPanel #sgOk');
    const wish = await page.evaluate(() => ({
      head: document.querySelector('#suggestPanel .sghead')?.textContent?.trim(),
      labels: [...document.querySelectorAll('#suggestPanel .sglbl')].map((x) => x.textContent),
      ranges: document.querySelectorAll('#suggestPanel .sgrange input[type=range]').length,
      chips: [...document.querySelectorAll('#suggestPanel .sgz')].map((x) => x.textContent),
      time: document.getElementById('sgTimeVal')?.textContent, up: document.getElementById('sgUpVal')?.textContent,
      buttons: [...document.querySelectorAll('#suggestPanel .sgfoot button')].map((x) => x.textContent),
      icons: document.querySelectorAll('#suggestPanel .sgrow svg').length,
    }));
    check(wish.head === 'Rundtour von hier', `wish: title (${wish.head})`);
    check(JSON.stringify(wish.labels) === JSON.stringify(['Aktivität', 'Zeit', 'Höhenmeter', 'Ziel']), `wish: rows ${wish.labels.join(' · ')}`);
    check(wish.ranges === 4, 'wish: time and ascent as two-handle range sliders');
    check(JSON.stringify(wish.chips) === JSON.stringify(['Gipfel', 'Wasser', 'Aussicht', 'Einkehr', 'Kultur']), `wish: Ziel chips ${wish.chips.join(' · ')}`);
    check(wish.time === '2–3 h' && wish.up === 'bis 400 m', `wish: pre-set from the activity (${wish.time}, ${wish.up})`);
    check(JSON.stringify(wish.buttons) === JSON.stringify(['Abbrechen', 'OK']) && wish.icons === 0, `wish: ${wish.buttons.join(' · ')}, no icons`);
    // a slider moves its value
    await page.$eval('#sgTime .hi', (el) => { el.value = '4'; el.dispatchEvent(new Event('input')); });
    check(await page.textContent('#sgTimeVal') === '2–4 h', 'wish: the upper time handle moves the range');
    await page.$eval('#sgTime .hi', (el) => { el.value = '3'; el.dispatchEvent(new Event('input')); });
    const t0 = Date.now();
    await page.click('#sgOk');
    const waited = await page.waitForSelector('#suggestPanel .sgwait', { timeout: 5000 }).then(() => true, () => false);
    check(waited, 'the wait card while it computes');
    await page.waitForSelector('#suggestPanel .sgcard', { timeout: 120_000 });
    const ms = Date.now() - t0;
    const cards = await page.$$eval('#suggestPanel .sgcard', (cs) => cs.map((c) => ({
      badge: c.querySelector('.sgbadge')?.textContent, name: c.querySelector('.sgname')?.textContent,
      stats: c.querySelector('.sgstats')?.textContent, green: c.querySelectorAll('.sgpill[class*=" g"]').length,
      yellow: c.querySelectorAll('.sgpill[class*=" y"]').length, on: c.classList.contains('on') })));
    check(cards.length >= 1 && cards.length <= 5, `${cards.length} cards in ${ms} ms: ${cards.map((c) => c.name).join(' | ')}`);
    check(cards.every((c) => c.badge === 'Vorschlag' || /^Ausgeschildert · /.test(c.badge ?? '')), `badges ${[...new Set(cards.map((c) => c.badge))].join(', ')}`);
    check(cards.every((c) => /^\d+,\d km · \d+:\d\d h · ↑ \d+ m$/.test(c.stats ?? '')), `km · h · ↑ in German (${cards[0]?.stats})`);
    check(cards.every((c) => c.green <= 2 && c.yellow <= 2), 'at most 2 green + 2 yellow pills per card');
    check(cards[0].on && cards.filter((c) => c.on).length === 1, 'the first card is the chosen one');
    const lines = () => page.evaluate(async () => ((await window.__map.getSource('suggest-loop')?.getData())?.features ?? [])
      .filter((f) => f.geometry.type === 'LineString').map((f) => f.geometry.coordinates.length));
    const l1 = await lines();
    check(l1.length === 1 && l1[0] > 10, `only the chosen loop on the map (${l1.length} line, ${l1[0]} points)`);
    if (cards.length > 1) {
      await page.click('#suggestPanel .sgcard:nth-of-type(2) .sgname');
      await page.waitForTimeout(300);
      const on = await page.$$eval('#suggestPanel .sgcard', (cs) => cs.map((c) => c.classList.contains('on')));
      const l2 = await lines();
      check(on[1] && on.filter(Boolean).length === 1 && l2.length === 1 && l2[0] !== l1[0], 'a click on another card shows its loop, alone');
    }
    // „Übernehmen“ → a normal plan, round trip
    await page.click('#suggestPanel .sgcard.on .sgtake');
    const planned = await page.waitForFunction(() => !document.getElementById('suggestPanel') && document.querySelector('#summary .big')
      && document.body.dataset.planning !== '1' && window.__state.points.length >= 3, null, { timeout: 120_000 }).then(() => true, () => false);
    const st = await page.evaluate(() => ({ n: window.__state.points.length, round: window.__state.roundTrip,
      first: window.__state.points[0], last: window.__state.points[window.__state.points.length - 1] }));
    check(planned && st.round, `„Übernehmen“ → a normal round-trip plan (${st.n} points, round trip ${st.round})`);
    const bad = errors.filter((e) => !/Failed to load resource|favicon|\[Problems\] (TRACE|INFO)/.test(e));
    check(bad.length === 0, `no page errors (${bad.slice(0, 3).join(' | ')})`);
    await ctx.close();
  }
  // ---- English words ----
  {
    const { ctx, page } = await open('en', true);
    const rows = await firstMenu(page);
    check(rows[1] === 'Round trip from here', `English: ${rows.join(' · ')}`);
    await page.click('.pointmenu button:nth-of-type(2)');
    await page.waitForSelector('#suggestPanel #sgOk');
    const chips = await page.$$eval('#suggestPanel .sgz', (b) => b.map((x) => x.textContent));
    check(chips.join(',') === 'Summit,Water,View,Food stop,Culture', `English chips ${chips.join(' · ')}`);
    await page.click('#sgCancel');
    check(await page.evaluate(() => !document.getElementById('suggestPanel')), 'Abbrechen gives the sheet back');
    await ctx.close();
  }
} finally {
  await browser.close();
  server.close();
  fs.rmSync(store, { recursive: true, force: true });
}
console.log(fails.length ? `\n${fails.length} check(s) failed` : '\nsuggest-smoke: all checks passed');
process.exit(fails.length ? 1 : 0);
