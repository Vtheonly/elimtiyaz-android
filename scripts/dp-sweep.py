#!/usr/bin/env python3
"""
dp-sweep.py — the El-Imtiyaz spacing-token gate + sweep (F-19 b + a).

Recreated + extended by the 133rd session (the 132nd session's tool was run
but never committed — the gate that "IS the standard" must live in the repo).

TWO gates (both must be ZERO on main):

  1. OFF-GRID gate (F-19 option b, the 132nd session's owner decision —
     scoped to FEATURE code): every dp literal in ui/features must sit on
     the 4dp grid (N % 4 == 0). The DS components' own internal geometry
     (chip/badge/snackbar micro-sizes like 6/10/14/18.dp) is deliberately
     OUT OF SCOPE — the (b) decision's scope was feature code, and
     re-gridding ~40 tuned DS components is a visual-redesign decision the
     owner has not taken. Documented exemptions (below) are not counted.

  2. TOKEN gate (F-19(a), the 133rd session's full-tokenization mandate):
     every token-sized dp literal (0/4/8/12/16/24/32/48/64) in feature AND
     designsystem code must be expressed as an ElTheme.spacing token
     (e.g. `ElTheme.spacing.sm` instead of `8.dp`). theme/ is exempt —
     it is where the scales are DEFINED.

DOCUMENTED EXEMPTIONS (a violation outside these classes is a real one):
  - The whole theme/ directory — the token/scale definitions themselves
    (Spacing, Shape, Borders, Elevation, Motion, TextStyles …).
  - The sub-4dp micro class: 1.dp, 2.dp, 3.dp (hairlines, borders, strokes).
  - Corner radii: any N.dp inside a RoundedCornerShape(...) argument span
    (tracked across newlines) — radii belong to the shape scale
    (ElTheme.shapes), not the spacing scale.
  - Explicit inline markers: a trailing `// spacing-token-exempt: <reason>`
    on a line exempts that line from the TOKEN gate (used for
    non-composable contexts where ElTheme.spacing cannot be read —
    every marker is a reviewed, justified exclusion, same philosophy as
    the ARCH-012 release-exclusion list).
  - Off-grid values INSIDE ui/designsystem (components/overlays/foundation)
    are out of the (b) gate's scope by the owner decision above.

USAGE
  python3 scripts/dp-sweep.py --check     # report violations, exit 1 if any
  python3 scripts/dp-sweep.py --apply     # rewrite token-sized literals to tokens
  python3 scripts/dp-sweep.py --status    # one-line summary per directory
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FEATURES = REPO / "app/src/main/java/com/example/ui/features"
DESIGNSYSTEM = REPO / "app/src/main/java/com/example/ui/designsystem"
THEME_EXEMPT_FILES = {  # token definitions — never rewritten, never counted
    "theme/Spacing.kt",
    "theme/Shape.kt",
}

# value -> token name (ElSpacing base scale; 48 also has the semantic alias
# `touchTarget`, accepted interchangeably — the gate checks literal absence)
TOKENS = {0: "none", 4: "xs", 8: "sm", 12: "md", 16: "lg", 24: "xl", 32: "xxl", 48: "xxxl", 64: "huge"}
MICRO = {1, 2, 3}
ELTHEME_IMPORT = "import com.example.ui.designsystem.theme.ElTheme"

DP_LITERAL = re.compile(r"\b(\d+)\.dp\b")


def strip_line_comment(line: str) -> tuple[str, str]:
    """Split a source line into (code, comment) on a // that is not part of a URL (://)."""
    idx = 0
    in_str = False
    while idx < len(line) - 1:
        ch = line[idx]
        if ch == '"':
            in_str = not in_str
        if not in_str and ch == "/" and line[idx + 1] == "/" and (idx == 0 or line[idx - 1] != ":"):
            return line[:idx], line[idx:]
        idx += 1
    return line, ""


def corner_radius_spans(text: str) -> list[tuple[int, int]]:
    """Char spans of RoundedCornerShape(...) argument lists (may span lines)."""
    spans: list[tuple[int, int]] = []
    for m in re.finditer(r"RoundedCornerShape\s*\(", text):
        depth = 1
        i = m.end()
        start = m.end()
        in_str = False
        while i < len(text) and depth > 0:
            ch = text[i]
            if ch == '"':
                in_str = not in_str
            elif not in_str:
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
            i += 1
        if depth == 0:
            spans.append((start, i - 1))
    return spans


def in_span(pos: int, spans: list[tuple[int, int]]) -> bool:
    return any(s <= pos < e for s, e in spans)


def scan_file(path: Path, root: Path) -> dict:
    """Return {offgrid: [(line, val)], token: [(line, val)]} violation lists."""
    rel = path.relative_to(root).as_posix()
    if rel.startswith("theme/"):
        return {"offgrid": [], "token": []}
    # The (b) off-grid gate is scoped to FEATURE code only (owner decision).
    offgrid_in_scope = root == FEATURES
    text = path.read_text(encoding="utf-8")
    radii = corner_radius_spans(text)
    offgrid: list[tuple[int, int]] = []
    token: list[tuple[int, int]] = []

    # Walk line by line, tracking the char offset so we can test the radius spans.
    offset = 0
    for lineno, raw in enumerate(text.splitlines(), start=1):
        code, comment = strip_line_comment(raw)
        line_start = offset
        offset += len(raw) + 1
        # Full-block comment lines are ignored entirely.
        if code.strip().startswith("*") or code.strip().startswith("/*"):
            continue
        has_marker = "spacing-token-exempt" in comment or "spacing-token-exempt" in code
        for m in DP_LITERAL.finditer(code):
            val = int(m.group(1))
            pos = line_start + m.start()
            if in_span(pos, radii):
                continue  # corner radius — the shape scale, not spacing
            if val in MICRO:
                continue  # sub-4dp micro class (hairlines/strokes)
            if val % 4 != 0:
                if offgrid_in_scope:
                    offgrid.append((lineno, val))
            elif val in TOKENS and not has_marker:
                token.append((lineno, val))
    return {"offgrid": offgrid, "token": token}


def iter_kt(root: Path):
    for p in sorted(root.rglob("*.kt")):
        yield p


def apply_file(path: Path, root: Path) -> int:
    """Rewrite token-sized literals to ElTheme.spacing tokens. Returns sites rewritten."""
    rel = path.relative_to(root).as_posix()
    if rel.startswith("theme/"):
        return 0
    text = path.read_text(encoding="utf-8")
    radii = corner_radius_spans(text)
    lines = text.splitlines(keepends=True)
    changed = 0
    offset = 0
    out: list[str] = []

    for raw in lines:
        code, comment = strip_line_comment(raw)
        line_start = offset
        offset += len(raw)
        if code.strip().startswith("*") or code.strip().startswith("/*") or "spacing-token-exempt" in comment:
            out.append(raw)
            continue

        def repl(m: re.Match) -> str:
            nonlocal changed
            val = int(m.group(1))
            pos = line_start + m.start()
            if val in TOKENS and val not in MICRO and not in_span(pos, radii):
                changed += 1
                return f"ElTheme.spacing.{TOKENS[val]}"
            return m.group(0)

        new_code = DP_LITERAL.sub(repl, code)
        out.append(new_code + comment if comment else new_code)

    if changed == 0:
        return 0

    new_text = "".join(out)
    # Import management: ElTheme.spacing needs the ElTheme import (unless the
    # file already reads ElTheme — then it is imported, or it would not compile).
    if "ElTheme." not in text and ELTHEME_IMPORT not in new_text:
        lines2 = new_text.splitlines(keepends=True)
        last_import = max(
            (i for i, ln in enumerate(lines2) if ln.startswith("import ")), default=None
        )
        if last_import is not None:
            lines2.insert(last_import + 1, ELTHEME_IMPORT + "\n")
            new_text = "".join(lines2)
        else:
            # No imports at all: insert after the package line.
            pkg = next((i for i, ln in enumerate(lines2) if ln.startswith("package ")), None)
            if pkg is not None:
                lines2.insert(pkg + 2, "\n" + ELTHEME_IMPORT + "\n")
                new_text = "".join(lines2)

    path.write_text(new_text, encoding="utf-8")
    return changed


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
    if mode not in ("--check", "--apply", "--status"):
        print(__doc__)
        return 2

    total_off = total_tok = 0
    changed_files = 0
    for root in (FEATURES, DESIGNSYSTEM):
        for path in iter_kt(root):
            if mode == "--apply":
                n = apply_file(path, root)
                if n:
                    changed_files += 1
                    print(f"  rewrote {n:3d} sites  {path.relative_to(REPO)}")
                continue
            r = scan_file(path, root)
            if r["offgrid"] or r["token"]:
                rel = path.relative_to(REPO)
                for lineno, val in r["offgrid"]:
                    print(f"OFF-GRID  {rel}:{lineno}  {val}.dp not on the 4dp grid")
                for lineno, val in r["token"]:
                    print(f"RAW-TOKEN {rel}:{lineno}  {val}.dp should be ElTheme.spacing.{TOKENS[val]}")
                total_off += len(r["offgrid"])
                total_tok += len(r["token"])

    if mode == "--apply":
        print(f"\nDone: {changed_files} files rewritten. Re-run with --check (must be 0) and compile.")
        return 0

    print(f"\noff-grid violations: {total_off}   raw token-sized literals: {total_tok}")
    if total_off or total_tok:
        print("GATE FAILED — the standard is ZERO for both counters.")
        return 1
    print("GATE GREEN — 0 off-grid, 0 raw token-sized spacing literals.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
