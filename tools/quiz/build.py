"""Builds app/src/main/assets/quiz/{religion,general}.json from the *_x.py question lists."""
import json, glob, os, random, collections
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "..", "app", "src", "main", "assets", "quiz")
os.makedirs(out, exist_ok=True)
for kind in ("religion", "general"):
    items, seen = [], set()
    for f in sorted(glob.glob(os.path.join(here, f"{kind}_*.py"))):
        ns = {}; exec(open(f, encoding="utf-8").read(), ns)
        for t in ns["Q"]:
            q, c, w1, w2, w3, cat, lvl, exp = t
            opts = [c, w1, w2, w3]
            assert len(set(opts)) == 4, ("dup options", q)
            assert q not in seen, ("dup question", q)
            seen.add(q)
            r = random.Random(q)
            order = list(range(4)); r.shuffle(order)
            items.append({"q": q, "a": [opts[i] for i in order], "c": order.index(0), "cat": cat, "lvl": lvl, "exp": exp})
    json.dump(items, open(os.path.join(out, f"{kind}.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(kind, len(items), dict(collections.Counter(i["cat"] for i in items)), dict(collections.Counter(i["lvl"] for i in items)))
