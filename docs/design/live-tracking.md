# Live tracking – my tour as a link for the family ([tracking], 2026-11-19)

Owner 2026-11-19: „Ich schicke meiner Frau eine Signal- oder WhatsApp-Message mit einem Link. Mit dem Link kommt sie auf
eine Webseite, wo sie meinen Weg tracken kann: ob ich noch unterwegs bin, richtig auf der Spur, wie es mir geht – oder
zumindest meinen letzten Ort, falls mir etwas passiert.“ He calls it the last feature that matters to him.

Status: **design, nothing built.** No product code on `main` before the weekend release (2026-11-20) is out. Open owner
decisions: §12. Numbers marked *estimate* are not measured yet (§10 says how they get measured).

## 1. In one picture

```
 phone (tour is recording)                  plan.onpath.earth                    her phone / any browser
 ┌───────────────────────┐   locked data   ┌──────────────────┐   locked data   ┌────────────────────────┐
 │ RecordingService      │ ──────────────▶ │ live mailbox     │ ──────────────▶ │ viewer page  /live/    │
 │  writes points to DB  │  every ~2 min   │ (Worker + D1)    │  asks every 30 s │  opens with the key    │
 │ LiveShare reads them  │  1 request      │ cannot read it   │                  │  from the link         │
 └───────────────────────┘                 └──────────────────┘                  └────────────────────────┘
        the link (with the key after "#") goes by Signal / WhatsApp – never through our server
```

It is the plan sync's idea again (`web-sync.md`): a small mailbox that stores locked data it cannot read. New here: one
side only writes, the other only reads, and the box lives for one tour.

## 2. What the sharer sees (app)

Sketches only – before anything is built the owner gets real pictures (≤ 3 variants) and says yes to one.

**Start sharing.** While a tour records, Navigate's menu has **„Live teilen“**. A tap opens Android's share sheet at
once with one text:

    Verfolge meine Tour live: https://plan.onpath.earth/live/#t=AQEIDxYd…

He picks Signal or WhatsApp and the person – done. No dialog before, no account, no setup. (Android share sheet =
`Intent.ACTION_SEND`, text/plain; no Google service involved.) The box on the server is created in the background; if
there is no network right then, the link still works – it shows „noch keine Daten“ until the phone gets through.

**While sharing.** One small sign on Navigate beside the GPS / recording indicator:

| Sign | Meaning |
| --- | --- |
| „Live“ with a green dot | the last upload went through (age < 5 min) |
| „Live“ with a grey dot + „wartet auf Netz“ | no network; the way is kept and goes up later by itself |
| nothing | not sharing |

The tour notification gets „· Live“ in its line. A tap on the sign: **„Link nochmal teilen“** (same link, a second
person) and **„Teilen beenden“**.

**End.** „Teilen beenden“ deletes everything on the server at once; the link then says „Diese Tour wird nicht mehr
geteilt“. Ending the tour ends the sharing too: the viewer sees „Tour beendet 16:42“ with the whole way for a while
(§12 question 2), then the server deletes it. A new tour = a new link (§12 question 1).

**Never in the way.** Sharing never asks anything during the tour, never shows an error box. Upload trouble is the grey
dot and a line in the Problems log – nothing else.

## 3. What the viewer sees (web page)

`https://plan.onpath.earth/live/` – a page of its own beside the planner, made for a phone screen (she opens it from
the chat), works on a desktop too. No app, no login. German / English by the browser's language.

```
┌──────────────────────────────────────┐
│ Christians Tour · Wandern            │
│ ● Unterwegs · zuletzt vor 2 Min.     │   ← the one line that matters
├──────────────────────────────────────┤
│                                      │
│      our map (same look as the app)  │
│      ─── planned route (if any)      │
│      ━━━ the way so far              │
│        ◉ last place                  │
│                                      │
├──────────────────────────────────────┤
│ 12,4 km · 3:10 h · ↑ 640 m           │
│ noch 6,2 km · Ankunft ca. 16:40      │   ← only when he follows a plan
│ Handy-Akku 43 %                      │
│ Letzter Ort 14:02 · 46.6890, 11.1400 │   ← tap: copy / open in a map app
└──────────────────────────────────────┘
```

The status line, in this order of priority (calm words – a dead zone in the mountains is normal and must not frighten):

| State | Line |
| --- | --- |
| moving, fresh | „● Unterwegs · zuletzt vor 2 Min.“ (green) |
| paused / standing > 5 min | „● Pause seit 12 Min.“ |
| away from the planned route (the app's own off-route alarm) | „● Abseits der geplanten Route (150 m)“ (amber) |
| no upload for > 10 min | „● Kein Kontakt seit 25 Min. – ohne Netz normal. Letzter Ort 14:02“ (grey, amber after 60 min) |
| tour ended | „Tour beendet 16:42“ (+ „am Ziel“ when the plan's end was reached) |
| sharing stopped / expired | „Diese Tour wird nicht mehr geteilt.“ (nothing else, no map) |
| link broken / key wrong | „Dieser Link funktioniert nicht.“ |

- **„Wie es mir geht“** in version 1 = what the phone knows by itself: moving or pausing, on the route or not, how far,
  phone battery, when last heard. A deliberate sign from him („Alles gut 👍“, a short text) is a later step (§11, §12
  question 4).
- **The last place** is always on the page with its time and coordinates as text – what one reads to the mountain rescue.
  A tap copies it; „In Karten-App öffnen“ is a `geo:` link.
- **After a gap the whole way appears**, not a straight jump: the phone uploads everything it recorded meanwhile.
- A small fixed line at the bottom: „Kein Notruf-Dienst – im Notfall 112.“
- The page asks the server every 30 s while it is visible, at once when the tab comes back; nothing while hidden.
- Age („vor 2 Min.“) is computed from the **server's** clock (the answer carries the server's time and the time of the
  last upload), so a wrong clock on either phone does not matter.

## 4. Privacy

**End-to-end, the key is in the link** – the same building blocks as plan sync (`core/…/sync/SyncCrypto.kt`: HKDF,
AES-256-GCM, proven by test vectors and byte-equal to `javax.crypto`).

| Who | Sees |
| --- | --- |
| The server (Cloudflare, or anyone reading its database) | a random box number, locked blobs and their size, when uploads came, the requests' IP in Cloudflare's own logs (the Worker stores none; limits use the daily salted hash as elsewhere). So: *that* someone is on a tour and for how long – never where, who, or the route. |
| Someone with the link | everything the page shows, until sharing ends. A link can be forwarded – whoever has it, sees. |
| Someone with the link, **writing** | cannot. The link holds the read key only; uploads need a second token that never leaves the phone (§6). A viewer cannot fake a position. |
| The messenger (Signal / WhatsApp) | the link as message text, inside its own encryption. Link previews fetch only the address before „#“ – the key is never sent. The page has a neutral title („OnPath – Live-Tour“). |

- The part after „#“ never reaches any server (browser rule). The page takes it out of the address bar after reading.
- **Stop = gone:** „Teilen beenden“ deletes the box; the link in the chat history stays but is dead.
- **Nothing lives long:** a box is deleted by the server some hours after the tour ended (§12 question 2) and in any
  case 48 h after the last upload (a phone that died – the last place stays visible for two days, then goes).
- No account, no name on the server. The name on the page („Christians Tour“) is inside the locked data; it is the
  phone's owner name field for sharing (asked once at the first share, changeable; default empty → „Live-Tour“).
- Not kept on the server after the end; the recorded tour itself stays only on the phone as today.
- Privacy text: one new paragraph (proposal §13) – goes by pull request (CLAUDE.md: privacy texts).

## 5. What goes over the network

One box per shared tour. Three kinds of items, each one sealed (AES-256-GCM, 96-bit random nonce, the box id and item
name in the additional data so the server cannot move a blob elsewhere):

| Item | Content | Size | Written |
| --- | --- | --- | --- |
| `h` head | version, state (recording / paused / ended / arrived), last fix (lat, lon, ele, time, accuracy), GPS lost, off-route metres, distance, ascent, moving time, to go + arrival (when following), battery %, activity, display name, upload counter | ~80 bytes | every upload (replaced) |
| `r` route | the followed line, thinned (≤ 2000 points), delta-coded like `TrackCode` | ≤ 12 KB | at start, again after a re-route or a new plan |
| `t<n>` track chunk | the recorded points since the last chunk: segment marks, lat / lon / ele / time as deltas | ~7 bytes a point; ≤ 16 KB a chunk | appended, never changed |

A walking hour is about 1,000 kept points (`PointFilter`) ≈ 7 KB; a ten-hour day ≈ 70 KB up in total – *estimate*.

**One request per upload:** `POST …/push` carries the new chunk(s) and the head together. One radio wake-up, not two.

**When the phone uploads** (`LivePolicy`, pure rules, unit-tested like `PointFilter`):

| Situation | Upload |
| --- | --- |
| moving | every **2 min** |
| paused, standing still, or battery saver / below 15 % | every **10 min** (head only – „still here, battery 38 %“) |
| start, pause, resume, off-route alarm, arrived, end | at once (at most one event upload per 30 s) |
| upload failed / no network | nothing is lost: next try after 30 s, doubling to 10 min; at once when Android reports the network back (`ConnectivityManager` callback, no Play Services) |
| server says busy (429 / 5xx) | wait as told (`retry-after`), like plan sync |

Why 2 minutes: every upload wakes the mobile radio for some seconds. *Estimate*: 2 min ≈ 0.5 % battery an hour on top of
the recording, 1 min about double, 5 min under 0.3 %. For „is he on his way and where“ two minutes old is fresh; events
go at once anyway. The number is one constant – measured on the Pixel before release (§10), changed if the cost is off.

## 6. Link, keys, server

**Link:** `https://plan.onpath.earth/live/#t=<code>`. The app's App Link filter takes only the path `/` of that host
(plan links), so `/live/` opens in the browser even on a phone with OnPath installed (checked in the manifest).

```
code = Base64url, 44 characters:  [0] version = 1 | [1..32] view secret (32 random bytes, a new one per tour)

HKDF-SHA256(view secret, salt "OnPath live v1", info = label):
  "box id"      16 bytes   the box's name on the server
  "read token"  32 bytes   Authorization for GET
  "content key" 32 bytes   AES-256-GCM key – never leaves the two devices

write token = 32 random bytes made on the phone, NOT derived from the view secret, never in the link
```

**Server:** a new file `web/host/src/live.js` in the planner's Worker, routes `/live/v1/…`, two tables in the existing D1
database (migration `0004_live.sql`: `live_box` – id, read hash, write hash, rev, created, last write, ended, deleted;
`live_item` – box, name, rev, blob). Same host as the page → no CORS. All answers JSON, `no-store`.

| Call | Who | Answer |
| --- | --- | --- |
| `POST /live/v1/<box>` + both token hashes | phone (write token) | 201 created; 200 same tokens; 403 other; 410 ended |
| `POST /live/v1/<box>/push` body = items `{name, blob}` (+ `ended: true` at the end) | phone | 200 `{rev}`; 413 too big; 507 box full; 410 deleted |
| `GET /live/v1/<box>?since=N` | viewer (read token) | 200 `{rev, now, lastWrite, ended, items:[{name, rev, blob}]}` – only what changed since N; 404 not there (yet); 410 stopped |
| `DELETE /live/v1/<box>` | phone | 200 – everything deleted, 410 from then on |

- The server stamps `lastWrite` itself – the page's „vor 2 Min.“ needs no phone clock.
- Limits (safety net, far above a real tour): chunk 16 KiB, box 2 MiB / 2,000 items, 60 pushes an hour per box;
  per IP: 120 requests a minute, 10 new boxes a day (the sync's counter with the daily salted hash).
- Clean-up by the existing daily cron, made hourly for this table: ended boxes after the keep time, any box 48 h after
  its last write.
- Cost: an upload is ~3 row writes; a ten-hour tour ≈ 900 – nothing against D1's free 100,000 a day. The viewer's
  30-s question is one small read.
- A down server never touches the tour: uploads fail quietly and retry (§7).

## 7. Robust first – the recording must never suffer

The rule today is „plan sync never runs during a recording“ – because an import changes tours. Live sharing is the one
deliberate exception, and it earns it by being **read-only towards the tour**:

1. **It only reads.** `LiveShare` reads points from `TourDb` (a read cursor „uploaded up to point n of tour x“) and the
   `RecState` flow. It writes nothing the recording uses – no table, no file, no flag of the service.
2. **The track database is the queue.** No second buffer that could fill or get lost: after three hours without network
   the cursor is three hours behind, and the upload catches up in chunks (≤ 16 KB each, a few per round).
3. **Its own lane.** One background coroutine / thread, never the main thread, never the service's point-writing path;
   no lock shared with it. Network calls with short timeouts (10 s connect, 15 s read), one at a time.
4. **Every failure is caught** and goes to `Problems` (`Problems.warn("live", …)`, deduped) – an upload error, a broken
   state file, an odd server answer. Three failures in a row of the same kind = the grey dot, still no dialog.
5. **Killed and continued:** the share's state (`files/live/share.json`: view secret, write token, tour id, cursor,
   chunk number – written atomically, excluded from Android backup like `files/sync/`) survives a process death.
   „Continue tour“ continues the sharing with the same link; a tour saved instead ends it (best effort `ended`, else
   the 48 h rule).
6. **No new wake lock, no new permission.** It runs only while the recording service runs (which holds the process and
   has `INTERNET` / `ACCESS_NETWORK_STATE` already). No alarm, no WorkManager, no Play Services.
7. **A switch that really stops it:** „Teilen beenden“ cancels the lane; from then on zero network.
8. **Tests first** (JVM, with a fake box and a fake clock): no network for hours then back; flaky network (every second
   call fails mid-way – no point twice, none missing); process death between upload and cursor write (a chunk sent
   twice is the same item name → harmless); server busy; box deleted from outside; and one that proves the recording's
   write path never waits for the uploader (the uploader blocked forever – points still land).

Platform facts to **prove on the Pixel before release**, not assume: network works from the foreground service with the
screen off in the pocket for hours (expected: yes – a foreground service is exempt from Doze's network block, and a
moving phone does not doze deeply); Data Saver and Battery Saver; the measured battery cost of 2-min uploads on a 3 h walk.

## 8. Where the code goes (fits the rebuild)

| Where | What |
| --- | --- |
| `:core` `live/` (pure Kotlin, also compiled by the web engine) | `LiveCode` (link code + keys), `LiveItem` (head / route / chunk formats, frozen test bytes), `LiveChunker` (points → chunks from a cursor), `LivePolicy` (when to upload), `LiveUploader` (the state machine: cursor, retry, back-off) behind two ports – `LiveBox` (the four calls) and `TrackReader` (points after n). Crypto = the existing `sync/SyncCrypto` (`Aead`). |
| `:state` `ui/live/LiveShareSlice.kt` | what Navigate shows: not sharing / live / waiting, the menu's actions; port to start / stop. Web-ready rules do not apply (phone only). |
| `:app` (Android edge only) | `HttpLiveBox` (`HttpURLConnection`, like `HttpMailbox`), the network-back callback, battery level, the share intent, the wiring into `RecordingService` – as a part beside it, so phase 4's service split moves it without change. |
| `web/host` | `src/live.js`, `migrations/0004_live.sql`, `test/live.test.js` (Node's SQLite as fake D1, as for sync) |
| `web/app` | a second small entry `live/index.html` + `src/live/*.ts` (MapLibre, our map areas as the planner loads them, the status lines). Decoding = the shared Kotlin through the engine – no hand mirror. If the planner's full engine (≈ 0.5 MB gzip, ~1.5 s on a phone) is too heavy for this page, a slim engine build with only `live/` + crypto; decided by measuring. |
| Tests | `LiveFormatTest` (frozen bytes, tamper refused, a viewer key cannot write), `LiveUploaderTest` (§7.8), `live.test.js`, `web/app/test/live-smoke.mjs` (a fake phone in Node walks a track through a real `live.js`; the page shows way, states, gap, end, stop) |

Requirements: „Live tracking / sharing position with others“ moves from *out of scope* to in scope with new IDs
F-LIVE-01… (after the owner's answers). Not a core feature at first; it becomes one (C20) once the owner relies on it.

## 9. Risks

| Risk | What we do |
| --- | --- |
| It costs battery on a long day | 2-min batching, 10 min when standing / low battery, one request per upload; measured before release; the constant can go up |
| She worries at „kein Kontakt“ in a normal dead zone | calm wording, the last place and time always shown, grey not red; battery % explains a dead phone |
| It is taken for a rescue service | said on the page and at the first share: no alarm, nobody is notified, she has to look |
| The link gets around | read-only, one tour, short life, „Teilen beenden“; later a permanent link only as a deliberate choice |
| The phone maker cuts background network | the setup guide's battery exemption already exists; the viewer simply sees „kein Kontakt“; the tour is untouched |
| The server is down or over its limit | uploads retry quietly; the page says „Server nicht erreichbar – gleich nochmal“ |
| Someone uses the box as free storage | size / rate / new-box limits, 48 h life, blobs are opaque but small |
| Sharing code hurts the recording | §7 – read-only, own lane, tests that prove it |
| The clocks differ | ages from the server's clock; track times are GPS / UTC as today |

## 10. Build order (after the release; each step green on its own)

1. `:core` `live/` formats + keys with frozen bytes and tests. Announce the shared code to [laptop].
2. `web/host` `live.js` + migration + Node tests. No app or page yet.
3. Viewer page with the smoke test (fake phone). Owner looks at real pictures of the page here.
4. `LiveUploader` + `LivePolicy` with the §7 tests; `HttpLiveBox`.
5. App UI: sketches as real pictures → the owner's yes → slice + screens, texts DE / EN.
6. Pixel: a 3 h walk with sharing on – battery beside a walk without, screen-off network, a tunnel / airplane-mode gap.
7. Privacy text (pull request), requirements IDs, help page line; then into a release.

`web/` has one writer at a time – steps 2–3 are agreed with [laptop] in the backlog before they start.

## 11. Small first version – and what waits

**In version 1:** share a running tour by link (Navigate menu → share sheet); one link per tour; way so far, planned
route, last place with age and coordinates, the status lines of §3, distance / time / ascent / to go, phone battery;
stop sharing; ends with the tour; DE / EN.

**Later, each its own small step:** a sign from him („Alles gut 👍“ / a short text) · a permanent link for the family ·
sharing switched on by itself at every Start · „zuletzt angesehen“ for the sharer · a notice to the viewer when he
arrives or contact is lost for long · height profile on the page · multi-day tours as one link · the viewer inside the
OnPath app.

## 12. Open for the owner (asked one by one in the chat; recommendation first)

1. **One link per tour, or one permanent link for the family?** Recommended: per tour (simplest, nothing lives on);
   the permanent link later.
2. **How long stays the finished tour visible?** Recommended: 12 h after the end, then deleted.
3. **Where does „Live teilen“ sit – only in the running tour's menu, or also offered at Start?** Recommended: menu
   only in version 1.
4. **„Wie es mir geht“ – automatic signs only in version 1?** Recommended: yes; „Alles gut 👍“ as the first later step.
5. **Show the phone's battery level to the viewer?** Recommended: yes – it explains a silent phone.
6. **The name on the page** – asked once at the first share, or none? Recommended: asked once.

## 13. Privacy text (proposal)

> **Live-Tour teilen (nur wenn du es einschaltest).** Teilst du eine Tour, schickt dein Handy während der Tour deinen
> Weg verschlüsselt an unseren Server (Cloudflare, EU). Der Schlüssel steckt nur in dem Link, den du selbst verschickst –
> der Server kann weder deinen Ort noch deine Route lesen und kennt deinen Namen nicht. Er speichert eine zufällige
> Nummer, die verschlossenen Daten und wann sie kamen. Wer den Link hat, sieht deine Tour, bis du das Teilen beendest.
> „Teilen beenden“ löscht alles sofort; sonst löscht der Server die Daten 12 Stunden nach Tourende, spätestens 48 Stunden
> nach dem letzten Lebenszeichen.
