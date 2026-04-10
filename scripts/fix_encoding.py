"""
Fix double-encoded UTF-8 characters in Kotlin source files.

The files were originally UTF-8, but were opened in an editor that read them
as cp1252 (Windows-1252), then re-saved as UTF-8, causing each non-ASCII char
to be replaced by 2-4 garbled Latin characters.

Fix: read as UTF-8 (gives garbled), encode to cp1252 (recovers original bytes),
decode those bytes as UTF-8 (restores correct characters).
"""

import os
import sys

SRC_DIR = r"c:\Dev\ManagerFoot\app\src\main\java"

# Emoji and other multi-byte sequences that can't be fixed by the
# encode/decode approach (bytes lost during double-encoding).
EXPLICIT_REPLACEMENTS = [
    ("\u00f0\u0178\u2020", "\U0001F3C6"),  # ðŸ† -> 🏆  (trophy)
    ("\u00f0\u0178\u02c6\u2019", "\U0001F4B0"),  # 💰 if present
]


def fix_double_encoding(text: str) -> str:
    """
    Reverses double-encoding: collects runs of non-ASCII characters,
    tries to encode as cp1252 and decode as utf-8 to restore originals.
    ASCII characters (0x00-0x7F) are passed through unchanged.
    """
    # Apply explicit replacements first (emojis etc.)
    for bad, good in EXPLICIT_REPLACEMENTS:
        text = text.replace(bad, good)

    result = []
    i = 0
    length = len(text)

    while i < length:
        c = text[i]
        if ord(c) <= 127:
            result.append(c)
            i += 1
            continue

        # Collect up to 4 consecutive non-ASCII characters
        j = i
        while j < min(i + 4, length) and ord(text[j]) > 127:
            j += 1

        segment_len = j - i
        fixed = False

        for seg_len in range(segment_len, 0, -1):
            segment = text[i : i + seg_len]
            try:
                raw_bytes = segment.encode("cp1252")
                decoded = raw_bytes.decode("utf-8")
                result.append(decoded)
                i += seg_len
                fixed = True
                break
            except (UnicodeEncodeError, UnicodeDecodeError):
                pass

        if not fixed:
            result.append(c)
            i += 1

    return "".join(result)


def process_file(filepath: str) -> bool:
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        original = f.read()

    fixed = fix_double_encoding(original)

    if fixed != original:
        with open(filepath, "w", encoding="utf-8", newline="") as f:
            f.write(fixed)
        return True
    return False


def main():
    fixed_files = []
    for root, _dirs, files in os.walk(SRC_DIR):
        for fname in files:
            if not fname.endswith(".kt"):
                continue
            fpath = os.path.join(root, fname)
            try:
                if process_file(fpath):
                    fixed_files.append(fpath)
                    print(f"  Fixed: {fname}")
            except Exception as e:
                print(f"  ERROR in {fname}: {e}", file=sys.stderr)

    print(f"\nDone. Fixed {len(fixed_files)} file(s).")


if __name__ == "__main__":
    main()
