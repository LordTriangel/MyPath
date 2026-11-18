// The one message box, as the app's ui/MessageCard (docs/design/ui-style.md "The box: MessageCard"): a white card under
// the top bar with a 4 px coloured left edge – FRAGE (a question: blue edge, two verb buttons, until answered), HINWEIS
// (must know: one button – OK or the one way out; grey edge, amber ⚠ attention, red ⚠ error; never goes by itself).
// One box at a time (the app's Messages.pick: a question first, then the oldest); the others wait and come next.
// ANZEIGE (a state shown in place) is no box – lines in the sheet, the map strip.

import { runStripe } from './mapnotice';

export type MessageKind = 'frage' | 'hinweis' | 'hinweis-warn' | 'hinweis-error';
export interface MessageButton { label: string; act: () => void }
export interface Message {
  /** One message per id: posting the same id again replaces it (e.g. "new version" asked on every focus). */
  id: string;
  kind: MessageKind;
  title: string;
  detail?: string;
  /** Last = the primary one. A HINWEIS has exactly one, a FRAGE two. */
  buttons: MessageButton[];
  /** A wait the user started (the route being calculated): a spinner before the title; its one button is the way out. */
  busy?: boolean;
  /** The primary button's colour by what it does (the app's ButtonTone, colour rule 2026-11-06): 'edit' = it plans
   * (blue), else walnut. The web never goes to a navigation, so there is no green. */
  tone?: 'edit' | 'neutral';
  /** Where the box's centre goes (page pixels) instead of 12 px under the top bar – e.g. the middle of the free map. */
  at?: () => { x: number; y: number };
  /** A wait's progress under the title (owner 2026-11-18): a thin bar filled to [share] (0..1) and a [line]. */
  progress?: WaitProgress;
}
export interface WaitProgress {
  share: number;
  line: string;
  /** The work goes on without a new number (a long leg, the route put together): a stripe runs over the bar. */
  running?: boolean;
  /** A calm line under it once the wait took a while („A long tour can take a few minutes.“ – loadoverlay HINT_MS). */
  hint?: string;
}

/** Elapsed time as m:ss (h:mm:ss from an hour) – the wait's clock that shows it is alive. */
export function elapsed(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), ss = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

/**
 * The progress of message [id] changed: kept for the next draw, and on screen updated in place – the box is not built
 * anew each second (a click on its Cancel must never fall between two boxes).
 */
export function setProgress(id: string, p: WaitProgress) {
  const m = waiting.find((x) => x.id === id);
  if (!m) return;
  const had = !!m.progress;
  m.progress = p;
  const card = host?.querySelector<HTMLElement>(`.msgcard[data-id="${id}"]`);
  if (!card) return;
  if (!had || !card.querySelector('.msgprog')) { draw(); return; }
  fillProgress(card.querySelector<HTMLElement>('.msgprog')!, p);
}

function fillProgress(el: HTMLElement, p: WaitProgress) {
  el.querySelector<HTMLElement>('.msgbar i')!.style.width = `${(Math.min(1, Math.max(0, p.share)) * 100).toFixed(1)}%`;
  const bar = el.querySelector<HTMLElement>('.msgbar')!;
  if (!!p.running !== bar.classList.contains('running')) {
    bar.classList.toggle('running', !!p.running);
    if (p.running) runStripe(bar.querySelector<HTMLElement>('b')!);
  }
  el.querySelector<HTMLElement>('.msgprogline')!.textContent = p.line;
  el.querySelector<HTMLElement>('.msgproghint')!.textContent = p.hint ?? '';
}

const waiting: Message[] = [];
let host: HTMLElement | null = null;

/** The app's Messages.pick: a question (FRAGE) wins; else the first one posted. */
export function pick(ms: Message[]): Message | null {
  return ms.find((m) => m.kind === 'frage') ?? ms[0] ?? null;
}

/** Shows [m] (or keeps it waiting behind the box on screen). */
export function post(m: Message) {
  const i = waiting.findIndex((x) => x.id === m.id);
  if (i >= 0) waiting[i] = m; else waiting.push(m);
  draw();
}

/** Takes message [id] away (answered, or its state is gone). */
export function dismiss(id: string) {
  const i = waiting.findIndex((x) => x.id === id);
  if (i >= 0) waiting.splice(i, 1);
  draw();
}

/** Called after every change with the message on screen (onewait.ts: the route's wait box is a wait). */
let listener: (m: Message | null) => void = () => {};
export function onShown(f: (m: Message | null) => void) { listener = f; f(pick(waiting)); }

/** The message on screen now (for the browser tests). */
export function shown(): Message | null { return pick(waiting); }

function draw() {
  if (!host) {
    host = document.createElement('div');
    host.id = 'msgcards';
    document.body.append(host);
  }
  const m = pick(waiting);
  host.replaceChildren();
  host.hidden = !m;
  listener(m);
  if (!m) return;
  // 12 px under the top element (the app's MessagePlacement): the search bar over the map, else the header
  const top = document.querySelector('.topbar') ?? document.getElementById('header');
  const at = m.at?.();
  host.classList.toggle('at', !!at);
  host.style.left = at ? `${Math.round(at.x)}px` : '';
  host.style.top = at ? `${Math.round(at.y)}px` : `${Math.round((top?.getBoundingClientRect().bottom ?? 56) + 12)}px`;
  const card = document.createElement('div');
  card.className = `msgcard ${m.kind}${m.busy ? ' busy' : ''}`;
  card.dataset.id = m.id;
  card.setAttribute('role', m.kind === 'frage' ? 'alertdialog' : 'alert');
  const text = document.createElement('div');
  text.className = 'msgtext';
  const title = document.createElement('div');
  title.className = 'msgtitle';
  if (m.kind === 'hinweis-warn' || m.kind === 'hinweis-error') title.append(Object.assign(document.createElement('span'), { className: 'msgwarn', textContent: '⚠ ' }));
  if (m.busy) title.append(Object.assign(document.createElement('span'), { className: 'spin msgspin', ariaHidden: 'true' }));
  title.append(m.title);
  text.append(title);
  if (m.detail) text.append(Object.assign(document.createElement('div'), { className: 'msgdetail', textContent: m.detail }));
  if (m.progress) {
    const pr = document.createElement('div');
    pr.className = 'msgprog';
    pr.innerHTML = '<div class="msgbar" role="presentation"><i></i><b></b></div><div class="msgprogline"></div><div class="msgproghint"></div>';
    fillProgress(pr, m.progress);
    text.append(pr);
  }
  const row = document.createElement('div');
  row.className = 'msgbuttons';
  m.buttons.forEach((b, k) => {
    const btn = document.createElement('button');
    btn.className = k === m.buttons.length - 1 ? `msgbtn primary${m.tone === 'edit' ? ' edit' : ''}` : `msgbtn${m.tone === 'edit' ? ' edit' : ''}`;
    btn.textContent = b.label;
    btn.onclick = () => { dismiss(m.id); b.act(); };
    row.append(btn);
  });
  card.append(text, row);
  card.classList.toggle('one', m.buttons.length === 1); // one button: beside the text (the app's layout)
  host.append(card);
}
