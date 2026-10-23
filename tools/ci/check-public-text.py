#!/usr/bin/env python3
"""Check tracked text without executing code from the tree being checked."""

import argparse
import codecs
from pathlib import Path
import re
import subprocess
import sys


FORBIDDEN = re.compile(r"[\u0400-\u052f]")
MESSAGE = "a non-English letter in a public file"


def text(data):
    for marker, encoding in (
        (codecs.BOM_UTF32_LE, "utf-32"),
        (codecs.BOM_UTF32_BE, "utf-32"),
        (codecs.BOM_UTF16_LE, "utf-16"),
        (codecs.BOM_UTF16_BE, "utf-16"),
    ):
        if data.startswith(marker):
            return data.decode(encoding)
    if b"\0" in data:
        return None
    return data.decode("utf-8", errors="replace")


def check(root):
    tracked = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        check=True, capture_output=True,
    ).stdout
    rejected = False
    for raw in tracked.split(b"\0"):
        if not raw:
            continue
        path = Path(raw.decode("utf-8", errors="surrogateescape"))
        candidate = root / path
        # A tracked symlink is a link, not text to read outside the checkout.
        if candidate.is_symlink():
            continue
        content = text(candidate.read_bytes())
        if content is None:
            continue
        for number, line in enumerate(content.splitlines(), 1):
            if FORBIDDEN.search(line):
                safe_path = str(path).encode("ascii", errors="backslashreplace").decode("ascii")
                print(f"{safe_path}:{number}: {MESSAGE}", file=sys.stderr)
                rejected = True
    return not rejected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    try:
        if not check(args.root.resolve()):
            return 1
    except (OSError, UnicodeError, subprocess.CalledProcessError):
        print("public text check could not read the tracked tree", file=sys.stderr)
        return 1
    print("public text check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
