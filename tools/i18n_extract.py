"""Collect every Arabic string literal in the Kotlin sources into i18n keys.
Templates become patterns: "صرفت ${x} في $m" -> "صرفت {0} في {1}"."""
import json, os, re, sys

ROOT = "app/src/main/java/com/mohamed/safi"
SKIP_DIRS = {"ai", "sms"}
SKIP_FILES = {"Splash.kt", "Carpool.kt", "I18n.kt", "Digits.kt", "AsmaHusna.kt"}  # AI prompts stay as they are
AR = re.compile(r"[؀-ۿ]")

def decode_escapes(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "n": out.append("\n"); i += 2; continue
            if n == "t": out.append("\t"); i += 2; continue
            if n in "\"'\\$": out.append(n); i += 2; continue
            if n == "u": out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
        out.append(c); i += 1
    return "".join(out)

def literals(src):
    """Yield (start, end, body) for each normal "..." literal, handling ${...} nesting."""
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i); i = n if j < 0 else j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i); i = n if j < 0 else j + 2; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3; continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            i = j + 1; continue
        if c == '"':
            j = i + 1; depth = 0
            while j < n:
                if src[j] == "\\": j += 2; continue
                if depth == 0 and src[j] == '"': break
                if src.startswith("${", j): depth += 1; j += 2; continue
                if depth > 0 and src[j] == "{": depth += 1
                elif depth > 0 and src[j] == "}": depth -= 1
                elif depth > 0 and src[j] == '"':
                    # nested string inside ${}: skip it
                    k = j + 1
                    while k < n and src[k] != '"':
                        k += 2 if src[k] == "\\" else 1
                    j = k
                j += 1
            yield i, j + 1, src[i + 1:j]
            i = j + 1; continue
        i += 1

def to_pattern(body):
    """Split template body into text with {k} placeholders."""
    out, args, i = [], [], 0
    while i < len(body):
        if body.startswith("\\", i):
            out.append(body[i:i + 2] if body[i + 1] != "u" else body[i:i + 6]); i += 2 if body[i + 1] != "u" else 6; continue
        if body.startswith("${", i):
            depth, j = 1, i + 2
            while j < len(body) and depth:
                if body[j] == "{": depth += 1
                elif body[j] == "}": depth -= 1
                elif body[j] == '"':
                    k = j + 1
                    while k < len(body) and body[k] != '"':
                        k += 2 if body[k] == "\\" else 1
                    j = k
                j += 1
            args.append(body[i + 2:j - 1]); out.append("{%d}" % (len(args) - 1)); i = j; continue
        m = re.match(r"\$([A-Za-z_][A-Za-z0-9_]*)", body[i:])
        if m:
            args.append(m.group(1)); out.append("{%d}" % (len(args) - 1)); i += m.end(); continue
        out.append(body[i]); i += 1
    return decode_escapes("".join(out)), args

def main():
    keys = {}
    for d, _, fs in os.walk(ROOT):
        if set(os.path.relpath(d, ROOT).split(os.sep)) & SKIP_DIRS:
            continue
        for f in fs:
            if not f.endswith(".kt") or f in SKIP_FILES:
                continue
            src = open(os.path.join(d, f), encoding="utf-8").read()
            for s, e, body in literals(src):
                if not AR.search(body):
                    continue
                pat, args = to_pattern(body)
                if len(pat) > 600 or not AR.search(pat) or "\\" in pat or "(?" in pat:
                    continue
                keys.setdefault(pat, set()).add(os.path.relpath(os.path.join(d, f), ROOT))
    out = sorted(keys)
    json.dump(out, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(len(out), "keys,", sum(len(k) for k in out), "chars")

main()
