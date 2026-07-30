#!/usr/bin/env python3
"""Sanity-check the Android sources on a host with no Kotlin toolchain.

There is no kotlinc, Gradle or Android SDK in this environment, so the Kotlin
half of the app cannot be compiled locally; CI is what proves it builds. This
script catches the two mistakes that are actually plausible when editing Compose
by hand — an unbalanced brace and a reference to a preset/symbol that does not
exist — so a push does not burn a CI run on a typo.

Usage: python3 scripts/verify-kotlin-braces.py
Exits non-zero on the first problem found.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "android/app/src/main/java/com/qezawat/iprocker"

# Named arguments belonging to Compose's own copy() overloads, not to our data
# classes: Color.copy(alpha = ...) is the common one.
FRAMEWORK_COPY_ARGS = {
    "alpha", "red", "green", "blue",
    "fontSize", "fontWeight", "color", "lineHeight", "letterSpacing",
}


def strip_code(text: str) -> list[tuple[int, str]]:
    """Return (line_no, char) for every char outside strings and comments."""
    out: list[tuple[int, str]] = []
    i, n, line = 0, len(text), 1
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        # raw string
        if text.startswith('"""', i):
            i += 3
            while i < n and not text.startswith('"""', i):
                if text[i] == "\n":
                    line += 1
                i += 1
            i += 3
            continue
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            i += 2
            while i < n and not text.startswith("*/", i):
                if text[i] == "\n":
                    line += 1
                i += 1
            i += 2
            continue
        out.append((line, c))
        i += 1
    return out


def check_braces(path: pathlib.Path) -> list[str]:
    errs = []
    depth = 0
    for line, c in strip_code(path.read_text()):
        if c in "{([":
            depth += 1
        elif c in "})]":
            depth -= 1
            if depth < 0:
                errs.append(f"{path.name}:{line}: closing bracket with nothing open")
                return errs
    if depth != 0:
        errs.append(f"{path.name}: {depth} bracket(s) left unclosed")
    return errs


def check_symbols() -> list[str]:
    """Constants and copy() fields referenced from the UI must be declared.

    copy() is checked against the union of every data-class property in the
    module rather than ScanSettings alone: `it.copy(...)` appears on UiState too
    and the receiver type is not recoverable without a real type checker. The
    union still catches a typo'd field name, which is the failure mode worth
    catching before a CI run.

    Framework copy() overloads (Color.copy(alpha = ...), Modifier, TextStyle)
    are not data classes of ours, so their named arguments are allowlisted.
    """
    errs = []
    settings = (SRC / "data/Settings.kt").read_text()
    declared = set(re.findall(r"val\s+([A-Z][A-Z0-9_]+)\s*=", settings))
    fields: set[str] = set()
    for kt in SRC.rglob("*.kt"):
        fields |= set(re.findall(r"va[lr]\s+([a-z][A-Za-z0-9]*)\s*:", kt.read_text()))
    for kt in SRC.rglob("*.kt"):
        text = kt.read_text()
        for name in re.findall(r"ScanSettings\.([A-Z][A-Z0-9_]+)", text):
            if name not in declared:
                errs.append(f"{kt.name}: ScanSettings.{name} is not declared")
        for name in re.findall(r"\.copy\(\s*([a-zA-Z0-9]+)\s*=", text):
            if name in FRAMEWORK_COPY_ARGS:
                continue
            if name not in fields:
                errs.append(f"{kt.name}: copy(...) sets unknown field '{name}'")
    return errs


def check_bridge_setters() -> list[str]:
    """Every req.setX called from Kotlin must exist on the gomobile bridge."""
    bridge = (ROOT / "mobile/mobile.go").read_text()
    exported = set(re.findall(r"func \(r \*ScanRequest\) (Set[A-Za-z0-9]+)\(", bridge))
    errs = []
    for kt in SRC.rglob("*.kt"):
        for call in re.findall(r"req\.(set[A-Za-z0-9]+)\(", kt.read_text()):
            want = call[0].upper() + call[1:]
            if want not in exported:
                errs.append(f"{kt.name}: req.{call}() has no {want} on ScanRequest")
    return errs


def main() -> int:
    errs: list[str] = []
    for kt in sorted(SRC.rglob("*.kt")):
        errs += check_braces(kt)
    errs += check_symbols()
    errs += check_bridge_setters()
    if errs:
        for e in errs:
            print("FAIL", e)
        return 1
    files = len(list(SRC.rglob("*.kt")))
    print(f"ok: {files} Kotlin files balanced, presets and bridge setters resolve")
    print("note: this is a syntax/symbol check, not a compile. CI proves the build.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
