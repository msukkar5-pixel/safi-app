"""Converts OpenITI's البداية والنهاية (Shamela 4445, Dar Hajr ed., editor paratext removed)
into compact per-volume JSON for the app: assets/bidaya/index.json + vNN.json."""
import json, re, sys, os

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
text = open(src, encoding="utf-8").read()
text = text.split("#META#Header#End#", 1)[1]

page_re = re.compile(r"PageV(\d+)P(\d+)")
ms_re = re.compile(r"\s*\bms\d+\b\s*")
vol, page = 0, 0
sections = []
cur = None

def new_section(level, title):
    global cur
    cur = {"v": vol, "p": page, "l": level, "t": title, "paras": []}
    sections.append(cur)

def add_text(s, cont):
    if cur is None:
        new_section(1, "مقدمة")
    s = ms_re.sub(" ", s).strip()
    if not s:
        return
    if cont and cur["paras"]:
        cur["paras"][-1] += " " + s
    else:
        cur["paras"].append(s)

for raw in text.splitlines():
    line = raw.rstrip()
    if not line:
        continue
    for m in page_re.finditer(line):
        vol, page = int(m.group(1)), int(m.group(2))
    line = page_re.sub("", line).rstrip()
    if line.strip() in ("#", ""):
        continue
    if line.startswith("### "):
        h = line[4:].strip()
        if h.startswith("$"):
            name = h.lstrip("$").strip()
            if name:
                add_text("◆ " + name, False)
            continue
        m = re.match(r"(\|+)\s*(.*)", h)
        if m:
            title = ms_re.sub(" ", m.group(2)).strip()
            if title.startswith("[") and title.endswith("]"):
                title = title[1:-1].strip()
            if title.upper().startswith("EDITOR"):
                title = "تتمة"
            new_section(len(m.group(1)), title or "…")
            continue
        add_text(h, False)
        continue
    if line.startswith("~~"):
        add_text(line[2:], True)
    elif line.startswith("# "):
        add_text(line[2:], False)
    elif line.startswith("#"):
        add_text(line.lstrip("#"), False)
    else:
        add_text(line, True)

vols = {}
for s in sections:
    vols.setdefault(max(s["v"], 1), []).append(s)

index = []
total = 0
for v in sorted(vols):
    secs = vols[v]
    texts = ["\n".join(s["paras"]) for s in secs]
    total += sum(len(t) for t in texts)
    json.dump(texts, open(f"{out}/v{v:02d}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    firsts = [s["t"] for s in secs if s["l"] == 1]
    index.append({
        "v": v,
        "first": firsts[0] if firsts else secs[0]["t"],
        "last": firsts[-1] if firsts else secs[-1]["t"],
        "s": [[s["t"], s["l"], s["p"], len(texts[i])] for i, s in enumerate(secs)],
    })
json.dump(index, open(f"{out}/index.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("volumes", len(index), "sections", len(sections), "chars", total)
