"""OpenITI mARkdown -> app book format: <out>/index.json + vNN.json (list of section texts).
index.json: [{v, first, last, s:[[title, level, page, chars], ...]}]"""
import json, re, sys, os

MAX_SEC = 30000  # split very long sections so the reader stays fast


def convert(src, out, drop_editor=True):
    os.makedirs(out, exist_ok=True)
    text = open(src, encoding="utf-8").read()
    if "#META#Header#End#" in text:
        text = text.split("#META#Header#End#", 1)[1]
    page_re = re.compile(r"PageV(\d+)P(\d+)")
    ms_re = re.compile(r"\s*\bms\d+\b\s*")
    st = {"vol": 0, "page": 0}
    sections = []
    cur = [None]

    def new_section(level, title):
        cur[0] = {"v": st["vol"], "p": st["page"], "l": level, "t": title, "paras": []}
        sections.append(cur[0])

    def add_text(s, cont):
        if cur[0] is None:
            new_section(1, "مقدمة")
        s = ms_re.sub(" ", s).strip()
        s = re.sub(r"\s{2,}", " ", s)
        if not s:
            return
        c = cur[0]
        if cont and c["paras"]:
            c["paras"][-1] += " " + s
        else:
            c["paras"].append(s)

    for raw in text.splitlines():
        line = raw.rstrip()
        if not line:
            continue
        for m in page_re.finditer(line):
            st["vol"], st["page"] = int(m.group(1)), int(m.group(2))
        line = page_re.sub("", line).rstrip()
        if line.strip() in ("#", ""):
            continue
        if line.startswith("### "):
            h = line[4:].strip()
            if h.startswith("$"):
                name = h.lstrip("$").strip()
                if name:
                    add_text("◆ " + ms_re.sub(" ", name).strip(), False)
                continue
            m = re.match(r"(\|+)\s*(.*)", h)
            if m:
                title = ms_re.sub(" ", m.group(2)).strip()
                if title.startswith("[") and title.endswith("]"):
                    title = title[1:-1].strip()
                if title.upper().startswith("EDITOR"):
                    title = "تتمة"
                new_section(min(len(m.group(1)), 5), title[:200] or "…")
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
    return write(sections, out)


def split_long(sections):
    res = []
    for s in sections:
        paras, part, size, n = s["paras"], [], 0, 0
        if sum(len(p) for p in paras) <= MAX_SEC:
            res.append(s); continue
        chunks = []
        for p in paras:
            while len(p) > MAX_SEC:  # a single giant paragraph
                cut = p.rfind(" ", 0, MAX_SEC)
                cut = cut if cut > MAX_SEC // 2 else MAX_SEC
                chunks.append([p[:cut]]); p = p[cut:].strip()
            if size + len(p) > MAX_SEC and part:
                chunks.append(part); part, size = [], 0
            part.append(p); size += len(p)
        if part:
            chunks.append(part)
        for i, c in enumerate(chunks):
            res.append({**s, "t": s["t"] if i == 0 else f"{s['t']} (تابع {i})", "l": s["l"] if i == 0 else min(s["l"] + 1, 5), "paras": c})
    return res


def write(sections, out):
    # editor's numbered notes ("2 -", "15 -") become part of the previous section
    merged = []
    for s in sections:
        if merged and re.fullmatch(r"[\d\s\-–.()]*", s["t"] or ""):
            merged[-1]["paras"] += s["paras"]
        else:
            merged.append(s)
    sections = [s for s in merged if s["paras"] or s["l"] <= 2]
    sections = split_long(sections)
    vols = {}
    for s in sections:
        vols.setdefault(max(s["v"], 1), []).append(s)
    index, total = [], 0
    for n, v in enumerate(sorted(vols), 1):
        secs = vols[v]
        texts = ["\n".join(s["paras"]) for s in secs]
        total += sum(len(t) for t in texts)
        json.dump(texts, open(f"{out}/v{v:02d}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        firsts = [s["t"] for s in secs if s["l"] == 1] or [s["t"] for s in secs]
        index.append({"v": v, "first": firsts[0][:120], "last": firsts[-1][:120],
                      "s": [[s["t"], s["l"], s["p"], len(texts[i])] for i, s in enumerate(secs)]})
    json.dump(index, open(f"{out}/index.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    return {"volumes": len(index), "sections": sum(len(i["s"]) for i in index), "chars": total}


if __name__ == "__main__":
    print(convert(sys.argv[1], sys.argv[2]))
