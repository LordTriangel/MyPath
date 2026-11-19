#!/usr/bin/env python3
"""Tests of backlog_slim.py: python3 tools/dev/test_backlog_slim.py (stdlib unittest; writes only to a temp dir)."""
import os
import re
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import backlog_slim as b  # noqa: E402

HEADER = "# Backlog\n\nIntro.\n\n"


def doc(roles="", progress="", nxt="", later=""):
    return (HEADER + "## For the roles\n" + roles + "<!-- c -->\n\n## In progress\n" + progress + "\n## Next\n" + nxt +
            "\n## Later\n" + later)


class T(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        b.ITEMS = os.path.join(self.tmp, "items")

    def slim(self, text):
        return b.slim(text, today="2026-01-01")

    def files(self):
        d = b.ITEMS
        return {f: self.read(os.path.join(d, f)) for f in os.listdir(d)} if os.path.isdir(d) else {}

    @staticmethod
    def read(p):
        with open(p, encoding="utf-8", newline="") as f:
            return f.read()

    def sample(self):
        long_role = "- → [donald] (captain 2026-11-18, **Big word**): " + "text with `code span` and more words. " * 20 + "\n"
        long_next = "- **[laptop] A long title for the next item (owner 2026-11-10)** body " + "x " * 120 + "\n  continuation line\n"
        short = "- **[donald] short one** – fine\n"
        return doc(long_role + short, "- [IR] claim " + "word " * 100 + "\n", long_next + short, "- **Later thing** " + "y " * 100 + "\n")

    def test_no_text_lost_and_idempotent(self):
        orig = self.sample()
        new, written = self.slim(orig)
        self.assertLess(len(new), len(orig))
        allnew = set((new + "\n" + "\n".join(self.files().values())).split("\n"))
        for l in orig.split("\n"):
            if l.strip():
                self.assertIn(l, allnew)
        again, written2 = self.slim(new)
        self.assertEqual(new, again)
        self.assertEqual(written2, [])
        # structure
        heads = lambda t: [l for l in t.split("\n") if l.startswith(("## ", "<!--", "# "))]
        self.assertEqual(heads(orig), heads(new))

    def test_link_style(self):
        new, _ = self.slim(self.sample())
        for l in new.split("\n"):
            if "items/" in l:
                self.assertRegex(l, r" → \[detail\]\(items/[^)]+\.md\)$")

    def test_bold_continues_on_next_line(self):
        item = "- **[donald] Local Rundgang emulator `Foo#bar` times\n  out after the sheet swipe** (very janky) " + "z " * 100 + "\n"
        new, _ = self.slim(doc(nxt=item))
        line = [l for l in new.split("\n") if "items/" in l][0]
        self.assertEqual(line.count("**") % 2, 0)
        self.assertIn("Local Rundgang", line)

    def test_unclosed_bold_in_source_is_not_passed_on(self):
        item = "- **[donald] Local Rundgang emulator: times out after the sheet\n  swipe (very janky), " + "z " * 100 + "\n"
        new, _ = self.slim(doc(nxt=item))
        line = [l for l in new.split("\n") if "items/" in l][0]
        self.assertEqual(line.count("**") % 2, 0)

    def test_already_slimmed_item_not_moved(self):
        line = "- **[donald] Some very long title " + "t " * 100 + "** → [detail](items/2026-01-01-x.md)\n"
        new, w = self.slim(doc(nxt=line))
        self.assertIn(line.rstrip(), new)
        self.assertEqual(w, [])
        old_style = "- [IR] " + "w " * 300 + "→ docs/team/items/2026-01-01-y.md\n"
        new, w = self.slim(doc(roles=old_style))
        self.assertEqual(w, [])

    def test_head_never_inside_link_code_paren_quote(self):
        for filler in ("[a link text](https://example.org/very/long/path/that/goes/on)", "`some code span with spaces inside it`",
                       "(an opened paren with a long explanation inside it)", "„a german quote that goes on and on“"):
            text = "- → [x] (y 2026-01-01): " + "w " * 100 + filler + " " + "tail words " * 40 + "\n"
            for pad in range(0, 40, 3):
                t = text.replace("(y 2026-01-01): ", "(y 2026-01-01): " + "p" * pad)
                head = b.safe_cut(" ".join(t.split()), b.ROLE_HEAD)
                self.assertEqual(head.count("`") % 2, 0, head)
                self.assertNotRegex(head, r"\[[^\]]*\]\([^)]*$")
                self.assertNotRegex(head, r"\[[^\]]*\]$" if "a link text" in filler and head.endswith("]…") else r"^\Z$")
                for o, c in b.PAIRS.items():
                    self.assertLessEqual(head.count(o) - head.count(c), 0, head)

    def test_long_bold_is_cut_and_closed(self):
        item = "- **[donald] " + "very long bold title " * 10 + "** rest " + "q " * 100 + "\n"
        new, _ = self.slim(doc(nxt=item))
        line = [l for l in new.split("\n") if "items/" in l][0]
        self.assertRegex(line, r"^- \*\*\[donald\] .*…\*\* → \[detail\]")
        role = "- → [a] (b 2026-01-01): **" + "long bold " * 60 + "** tail\n"
        new, _ = self.slim(doc(roles=role))
        line = [l for l in new.split("\n") if "items/" in l][0]
        self.assertEqual(line.count("**") % 2, 0)
        self.assertIn("…**", line)

    def test_bullets_in_multiline_comment_are_not_items(self):
        com = "<!-- example:\n- [donald] " + "w " * 300 + "\n-->\n"
        orig = doc(nxt=com + "- **[a] real " + "r " * 100 + "**\n")
        new, w = self.slim(orig)
        self.assertIn("- [donald] " + "w " * 300, new)
        self.assertEqual(len(w), 1)

    def test_utf8_cut(self):
        s = "äöü€ " * 80
        for lim in range(20, 60):
            h = b.safe_cut(s, lim)
            h.encode("utf-8")  # never raises
            self.assertLessEqual(b.nbytes(h), lim + 6)

    def test_slug_collision_and_existing(self):
        item = "- **[donald] Same title** " + "z " * 100 + "\n"
        new, w = self.slim(doc(nxt=item + "\n" + item.replace("z ", "k ")))
        self.assertEqual(len(set(w)), 2)
        self.assertTrue(any(f.endswith("-2.md") for f in w))
        # a different file with the same name already exists → not overwritten
        os.makedirs(b.ITEMS, exist_ok=True)
        existing = os.path.join(b.ITEMS, w[0])
        with open(existing, "w") as f:
            f.write("other")
        _, w3 = self.slim(doc(nxt=item))
        self.assertNotEqual(w3[0], w[0])
        self.assertEqual(self.read(existing), "other")

    def test_crlf(self):
        orig = self.sample().replace("\n", "\r\n")
        new, _ = self.slim(orig)
        self.assertNotRegex(new, r"[^\r]\n")
        self.assertEqual(new, self.slim(new)[0])
        for body in self.files().values():
            self.assertNotIn("\r", body)

    def test_check_without_file(self):
        old = b.BACKLOG
        b.BACKLOG = os.path.join(self.tmp, "nope.md")
        try:
            self.assertEqual(b.check(), 0)
        finally:
            b.BACKLOG = old


if __name__ == "__main__":
    unittest.main()
