#!/usr/bin/env python3
"""Keep docs/backlog.md small: one line per item, detail in docs/items/<date>-<slug>.md (stdlib only).

  python3 tools/dev/backlog_slim.py            rewrite docs/backlog.md, write the item files, print sizes + files
  python3 tools/dev/backlog_slim.py --check    only check the cap (exit 1 + the 5 longest lines when over it)
  python3 tools/dev/backlog_slim.py --dry-run  show what would change, write nothing

Section structure, header, HTML comments (also multi-line ones) and the order of items stay. Idempotent: an item whose
head line already ends with a link to docs/items is never touched again, so a second run changes nothing.
  - "For the roles", "For the captain", "In progress": an item up to ROLE_MAX bytes stays complete; a longer one keeps
    a head of ~ROLE_HEAD bytes + " → [detail](items/<file>.md)"; the full text goes to the file.
  - every other section ("Next", "Waiting for the owner", "Later", ...): an item over ITEM_MAX bytes becomes one line:
    its tags + bold title (<= TITLE_MAX bytes) + " → [detail](items/<file>.md)".
An item = a top-level "- " bullet with all its continuation lines (indented lines / sub-bullets).
A head is never cut inside a `code span`, a markdown link, or an opened ( [ „ – and never leaves a ** open.
"""
import datetime
import os
import re
import sys
import unicodedata

CAP = 40_000  # target 20,000 (architecture plan phase 0c): lower it step by step as read role lines are deleted, never raise it
ROLE_MAX, ROLE_HEAD = 400, 260      # For the roles / For the captain / In progress: complete up to ROLE_MAX, else a head
ITEM_MAX, ONE_LINER = 150, 0        # every other section; ONE_LINER = first-sentence bytes after the title
TITLE_MAX = 60                      # tags + bold title kept in the one-liners of those sections
SLUG_MAX = 40
ROLE_SECTIONS = ("For the roles", "For the captain", "In progress")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BACKLOG = os.path.join(ROOT, "docs", "backlog.md")
ITEMS = os.path.join(ROOT, "docs", "items")
LINK_END = re.compile(r"(docs/items/[^\s)]+\.md|\]\(items/[^)\s]+\.md\))\s*$")
ATOMIC = re.compile(r"`[^`\n]*`|\[[^\]\n]*\]\([^)\n]*\)")  # code span, markdown link
PAIRS = {"(": ")", "[": "]", "„": "“"}


def nbytes(s):
    return len(s.encode("utf-8"))


def raw_cut(s, limit):
    """Length c so that s[:c] is <= limit bytes: whole sentences if they fit, else a word boundary."""
    if nbytes(s) <= limit:
        return len(s)
    best = 0
    for m in re.finditer(r"[.!?](?=\s)", s):
        if nbytes(s[:m.end()]) <= limit:
            best = m.end()
        else:
            break
    if best >= 40:
        return best
    b = s.encode("utf-8")[:limit].decode("utf-8", "ignore")
    sp = b.rfind(" ")
    return sp if sp > limit // 2 else len(b)


def safe_cut(text, limit):
    """text (one line) shortened to about `limit` bytes with a trailing '…': never ends inside a `code span`, a
    markdown link or an opened ( [ „, and never leaves a ** open (it is closed after the '…')."""
    s = " ".join(text.split())
    if nbytes(s) <= limit:
        return s
    c = raw_cut(s, limit - 5)
    atoms = [(m.start(), m.end()) for m in ATOMIC.finditer(s)]
    pairs, stack = [], []
    for i, ch in enumerate(s):
        if any(a <= i < b for a, b in atoms):
            continue
        if ch in PAIRS:
            stack.append((ch, i))
        elif ch in PAIRS.values() and stack and PAIRS[stack[-1][0]] == ch:
            pairs.append((stack.pop()[1], i))
    changed = True
    while changed:
        changed = False
        for a, b in atoms + pairs:
            if a < c <= b:
                c, changed = a, True
    head = s[:c].rstrip(" ,;:–-(")
    if head.count("**") % 2:
        if head.endswith("**"):
            head = head[:-2].rstrip(" ,;:–-(")
        else:
            return head + "…**"
    return head + "…"


def split_items(lines):
    """[(kind, payload)]: ('line', text) for anything outside an item, ('item', [lines]) for a bullet block.
    Lines inside a (multi-line) <!-- … --> comment are never items."""
    out, i, in_comment = [], 0, False
    while i < len(lines):
        l = lines[i]
        if in_comment or l.startswith("<!--"):
            in_comment = "-->" not in l
            out.append(("line", l))
            i += 1
        elif l.startswith("- "):
            blk = [l]
            i += 1
            while i < len(lines) and lines[i].strip() and not lines[i].startswith(("- ", "#", "<!--")):
                blk.append(lines[i])
                i += 1
            out.append(("item", blk))
        else:
            out.append(("line", l))
            i += 1
    return out


def slugify(title):
    t = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode()
    t = re.sub(r"\[[^\]]*\]", " ", t)
    t = re.sub(r"[^a-zA-Z0-9]+", "-", t.lower()).strip("-")
    t = t[:SLUG_MAX].rstrip("-")
    return t or "item"


def parse_head(flat):
    """(prefix_with_title, title) of the flattened item text: everything up to and including the first bold span
    (when it starts early; it may continue on the next source line), else tags + the first words."""
    body = flat[2:]
    m = re.search(r"\*\*(.+?)\*\*", body)
    if m and m.start() <= 120:
        return "- " + body[:m.end()], m.group(1)
    tags = re.match(r"((?:\[[^\]]+\]\s*)+(?:\([^)]*\)\s*)?)", body)
    t = tags.group(1) if tags else ""
    title = safe_cut(body[len(t):], 60).rstrip("…*").strip()
    return "- " + t + title, title


def item_date(text, today):
    m = re.search(r"\b(20\d\d-\d\d-\d\d)\b", text)
    return m.group(1) if m else today


def write_item(title, slug_src, date, lines, written, dry):
    """Write docs/items/<date>-<slug>.md (reuse an identical existing file); return the file name."""
    body = "# " + re.sub(r"\s+", " ", re.sub(r"\*\*", "", title)).strip() + "\n\n" + "\n".join(lines) + "\n"
    base = f"{date}-{slugify(slug_src)}"
    n = 1
    while True:
        fn = base + ("" if n == 1 else f"-{n}") + ".md"
        p = os.path.join(ITEMS, fn)
        if fn in written:
            n += 1
            continue
        if os.path.exists(p):
            with open(p, encoding="utf-8", newline="") as f:
                same = f.read() == body
            if same:
                written.append(fn)
                return fn
            n += 1
            continue
        break
    if not dry:
        os.makedirs(ITEMS, exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(body)
    written.append(fn)
    return fn


def role_slug(line, title):
    """Slug source of a role line: its bold title, else <from-role>-to-<tags>-<first 3 words>."""
    m = re.match(r"- ([→←])\s*((?:\[[^\]]+\]\s*)*)(?:\(([^)]*)\))?\s*(.*)", line)
    if not m or re.search(r"\*\*.+?\*\*", line[:122]):
        return title
    arrow, tags, paren, rest = m.groups()
    tl = re.findall(r"\[([^\]]+)\]", tags) or ["all"]
    if arrow == "←":
        frm, to = tl[0], ["captain"]
    else:
        w = re.match(r"\(?\s*\[?([A-Za-z][\w-]*)", paren or "")
        frm, to = (w.group(1) if w else "note"), tl
    words = re.sub(r"[^\w ]+", " ", re.sub(r"\*\*", "", rest)).split()[:3]
    return "-".join([frm, "to"] + to + words)


def slim(text, dry=False, today=None):
    """(new_text, [item files written]). CRLF input keeps its line ends."""
    today = today or datetime.date.today().isoformat()
    crlf = "\r\n" in text
    lines = text.replace("\r\n", "\n").split("\n")
    section, buf, out, written = None, [], [], []

    def flush():
        for kind, p in split_items(buf):
            if kind == "line":
                out.append(p)
                continue
            full = "\n".join(p)
            role = section in ROLE_SECTIONS
            if LINK_END.search(p[0]) or LINK_END.search(full.rstrip()):
                out.extend(p)  # already slimmed: never moved again
                continue
            if nbytes(full) <= (ROLE_MAX if role else ITEM_MAX):
                out.extend(p)
                continue
            flat = " ".join(" ".join(p).split())
            pre, title = parse_head(flat)
            date = item_date(full, today)
            if role:
                fn = write_item(title, role_slug(p[0], title), date, p, written, dry)
                out.append(safe_cut(flat, ROLE_HEAD) + f" → [detail](items/{fn})")
            else:
                fn = write_item(title, title, date, p, written, dry)
                if nbytes(pre) > TITLE_MAX:
                    pre = "- " + safe_cut(pre[2:], TITLE_MAX)
                if pre.count("**") % 2:  # the source's bold never closes: do not pass that on
                    pre = pre.rstrip("…") + "…**"
                out.append(pre + f" → [detail](items/{fn})")
        buf.clear()

    for l in lines:
        if l.startswith("## "):
            flush()
            section = l[3:].strip()
            section = next((r for r in ROLE_SECTIONS if section.startswith(r)), section)
            out.append(l)
        elif section is None:
            out.append(l)
        else:
            buf.append(l)
    flush()
    res = "\n".join(out)
    return (res.replace("\n", "\r\n") if crlf else res), written


def check():
    if not os.path.exists(BACKLOG):
        print("docs/backlog.md does not exist – nothing to check")
        return 0
    size = os.path.getsize(BACKLOG)
    if size <= CAP:
        print(f"docs/backlog.md is {size} bytes (cap {CAP}) – ok")
        return 0
    ls = sorted(open(BACKLOG, encoding="utf-8").read().split("\n"), key=lambda x: -nbytes(x))[:5]
    print(f"docs/backlog.md is {size} bytes (cap {CAP}): delete role lines every tag has read and items that are done; "
          "then run tools/dev/backlog_slim.py")
    print("The 5 longest lines:")
    for l in ls:
        print(f"  {nbytes(l):5d} B  {l[:110]}")
    return 1


def main(argv):
    if "--check" in argv:
        return check()
    if not os.path.exists(BACKLOG):
        print("docs/backlog.md does not exist – nothing to do")
        return 0
    dry = "--dry-run" in argv
    old = open(BACKLOG, encoding="utf-8", newline="").read()
    new, written = slim(old, dry)
    print(f"docs/backlog.md: {nbytes(old)} -> {nbytes(new)} bytes ({len(old.splitlines())} -> {len(new.splitlines())} lines)")
    if new != old and not dry:
        with open(BACKLOG, "w", encoding="utf-8", newline="") as f:
            f.write(new)
    print(f"{len(written)} item file(s) {'that would be ' if dry else ''}written to docs/items/:")
    for fn in written:
        print("  " + fn)
    if nbytes(new) > CAP:
        print(f"over the cap ({CAP}): delete role lines every tag has read and items that are done")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
