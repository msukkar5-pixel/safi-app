"""Build every book in tools/books.json into dist/<id>.zip (index.json + vNN.json) and dist/catalog.json."""
import io, json, os, re, shutil, subprocess, sys, tempfile, time, urllib.request, zipfile, html
from html.parser import HTMLParser

sys.path.insert(0, os.path.dirname(__file__))
import openiti

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "dist")
WORK = tempfile.mkdtemp()
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) SafiApp-books/1.0"}
only = set(sys.argv[1:])


def get(url, tries=3):
    for i in range(tries):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90).read()
        except Exception as e:
            err = e
            # "too many requests" (Wikipedia): back off much longer before retrying
            time.sleep((20 + i * 30) if getattr(e, "code", 0) == 429 else 2 + i * 3)
    raise err


# ---------------- OpenITI ----------------
_repos = {}

def openiti_file(repo, path):
    d = _repos.get(repo)
    if not d:
        d = os.path.join(WORK, repo)
        subprocess.run(["git", "clone", "-q", "--depth", "1", "--filter=blob:none", "--no-checkout",
                        f"https://github.com/OpenITI/{repo}", d], check=True)
        _repos[repo] = d
    data = subprocess.run(["git", "-C", d, "show", f"HEAD:{path}"], check=True, capture_output=True).stdout
    fn = os.path.join(WORK, os.path.basename(path) + ".txt")
    open(fn, "wb").write(data)
    return fn


# ---------------- HTML -> sections ----------------
class Blocks(HTMLParser):
    """Collects headings and paragraphs from (x)html."""
    BLOCK = {"p", "div", "li", "blockquote", "td", "h1", "h2", "h3", "h4", "h5", "h6", "br", "tr", "section", "article"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out, self.buf, self.tag, self.skip = [], [], None, 0

    def flush(self):
        t = re.sub(r"\s+", " ", "".join(self.buf)).strip()
        if t:
            self.out.append((self.tag if self.tag and self.tag[0] == "h" and len(self.tag) == 2 else "p", t))
        self.buf = []

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "nav", "footer", "header", "noscript", "svg", "button", "form"):
            self.skip += 1
        if tag in self.BLOCK:
            self.flush(); self.tag = tag

    def handle_endtag(self, tag):
        if tag in ("script", "style", "nav", "footer", "header", "noscript", "svg", "button", "form"):
            self.skip = max(0, self.skip - 1)
        if tag in self.BLOCK:
            self.flush(); self.tag = None

    def handle_data(self, d):
        if not self.skip:
            self.buf.append(d)


def html_blocks(h):
    p = Blocks(); p.feed(h); p.flush(); return p.out


def sections_from_blocks(blocks, vol, base_level=1, default_title="…"):
    secs, cur = [], None
    for tag, t in blocks:
        if tag in ("h1", "h2", "h3", "h4"):
            lvl = base_level + max(0, int(tag[1]) - 1)
            cur = {"v": vol, "p": 0, "l": min(lvl, 5), "t": t[:200], "paras": []}
            secs.append(cur)
        else:
            if cur is None:
                cur = {"v": vol, "p": 0, "l": base_level, "t": default_title, "paras": []}
                secs.append(cur)
            cur["paras"].append(t)
    return secs


# ---------------- EPUB (Hindawi) ----------------
def epub_sections(data, vol):
    z = zipfile.ZipFile(io.BytesIO(data))
    container = z.read("META-INF/container.xml").decode("utf-8", "replace")
    opf_path = re.search(r'full-path="([^"]+)"', container).group(1)
    opf = z.read(opf_path).decode("utf-8", "replace")
    base = os.path.dirname(opf_path)
    items = dict(re.findall(r'<item[^>]*?id="([^"]+)"[^>]*?href="([^"]+)"', opf))
    items.update({i: h for h, i in re.findall(r'<item[^>]*?href="([^"]+)"[^>]*?id="([^"]+)"', opf)})
    spine = re.findall(r'<itemref[^>]*?idref="([^"]+)"', opf)
    secs = []
    for idref in spine:
        href = items.get(idref)
        if not href:
            continue
        path = os.path.normpath(os.path.join(base, urllib.request.unquote(href))).replace("\\", "/")
        try:
            h = z.read(path).decode("utf-8", "replace")
        except KeyError:
            continue
        body = re.search(r"<body.*?>(.*)</body>", h, re.S)
        blocks = html_blocks(body.group(1) if body else h)
        if not blocks:
            continue
        title = next((t for tag, t in blocks if tag[0] == "h"), blocks[0][1][:80])
        s = sections_from_blocks(blocks, vol, 1, title)
        secs += s
    # drop Hindawi boilerplate (cover, copyright) sections
    secs = [s for s in secs if not re.search(r"(مؤسسة هنداوي|جميع الحقوق|Hindawi Foundation|الغلاف)", s["t"]) or len("".join(s["paras"])) > 2000]
    return secs


def hindawi_list(contributor):
    """All book ids on a contributor page (follows pagination)."""
    ids = []
    for url in [f"https://www.hindawi.org/contributors/{contributor}/"] + \
               [f"https://www.hindawi.org/contributors/{contributor}/{p}/" for p in range(2, 8)] + \
               [f"https://www.hindawi.org/contributors/{contributor}/?page={p}" for p in range(2, 8)]:
        try:
            h = get(url, tries=1).decode("utf-8", "replace")
        except Exception:
            continue
        new = [b for b in dict.fromkeys(re.findall(r"/books/(\d{6,})/", h)) if b not in ids]
        print("   list", url, len(new))
        ids += new
    return ids


def epub_title(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    container = z.read("META-INF/container.xml").decode("utf-8", "replace")
    opf = z.read(re.search(r'full-path="([^"]+)"', container).group(1)).decode("utf-8", "replace")
    m = re.search(r"<dc:title[^>]*>(.*?)</dc:title>", opf, re.S)
    return html.unescape(m.group(1)).strip() if m else ""


def hindawi_epub(bid):
    for url in (f"https://downloads.hindawi.org/books/{bid}.epub",):
        try:
            d = get(url)
            if d[:2] == b"PK":
                return d
        except Exception as e:
            print("  epub fail", bid, e)
    page = get(f"https://www.hindawi.org/books/{bid}/").decode("utf-8", "replace")
    m = re.search(r'href="([^"]+\.epub)"', page)
    if m:
        u = m.group(1)
        u = u if u.startswith("http") else "https://www.hindawi.org" + u
        return get(u)
    raise RuntimeError("no epub for " + bid)


ORD = ["الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
       "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر", "الثامن عشر"]

def part_no(title):
    m = re.search(r"الجزء\s+([^):]+)", title)
    if not m:
        return 99
    t = m.group(1).strip()
    for i in range(len(ORD) - 1, -1, -1):
        if t.startswith(ORD[i]):
            return i + 1
    return 99


# ---------------- web pages ----------------
JUNK = re.compile(r"(Most Recent|Related|Read more|Share|أخبار ذات صلة|آخر الأخبار|اقرأ أيضا|المزيد عن|مواضيع ذات صلة|روابط|تابعونا|شارك|القائمة|الرئيسية\s*>|حكومة دولة الامارات)", re.I)

def web_sections(url, vol):
    h = get(url).decode("utf-8", "replace")
    main = re.search(r"<main.*?>(.*)</main>", h, re.S)
    body = main.group(1) if main else (re.search(r"<body.*?>(.*)</body>", h, re.S) or [None, h])[1]
    blocks = [b for b in html_blocks(body) if len(b[1]) > 1 and " > " not in b[1]]
    keep = []
    for tag, t in blocks:
        latin = sum(c.isascii() and c.isalpha() for c in t) / max(1, len(t))
        if latin > 0.5:
            continue
        if tag[0] == "h":
            if JUNK.search(t) and len(t) < 60:
                continue
            keep.append((tag, t))
        elif len(t) >= 60:
            keep.append((tag, t))
    # drop a trailing heading block that only lists other news (short paragraphs after a junk heading were removed above)
    title = next((t for tag, t in keep if tag == "h1"), None)
    if not title:
        m = re.search(r"<title[^>]*>(.*?)</title>", h, re.S)
        title = re.split(r"\s[|\-–]\s", html.unescape(m.group(1)).strip())[0] if m else url
    keep = [k for k in keep if not (k[0] == "h1" and k[1] == title)]
    secs = sections_from_blocks(keep, vol, 2, title)
    total = sum(len(" ".join(s["paras"])) for s in secs)
    if total < 400:
        raise RuntimeError(f"too little text ({total})")
    secs = [s for s in secs if s["paras"]]
    secs[0]["l"] = 1
    if secs[0]["t"] == "…":
        secs[0]["t"] = title
    secs[-1]["paras"].append(f"المصدر الرسمي: {url}")
    return secs


WIKI_DROP = {"مراجع", "المراجع", "المصادر", "مصادر", "وصلات خارجية", "انظر أيضا", "انظر أيضًا", "ملاحظات", "هوامش", "قراءات إضافية", "روابط خارجية", "معرض الصور"}


def wiki_sections(lang, title, vol):
    """A Wikipedia article as plain text (no references or links), split at its headings."""
    import urllib.parse
    u = (f"https://{lang}.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki"
         f"&redirects=1&format=json&titles={urllib.parse.quote(title)}")
    time.sleep(3)  # be gentle with the Wikipedia API between articles
    d = json.loads(get(u, tries=5))
    page = next(iter(d["query"]["pages"].values()))
    text = page.get("extract") or ""
    if len(text) < 1500:
        raise RuntimeError(f"too little text ({len(text)})")
    secs, skip = [], False
    cur = {"v": vol, "p": 0, "l": 1, "t": page.get("title", title), "paras": []}
    for line in text.split("\n"):
        line = line.strip()
        m = re.match(r"^(=+)\s*(.*?)\s*=+$", line)
        if m:
            if cur["paras"] and not skip:
                secs.append(cur)
            name = m.group(2)
            skip = name in WIKI_DROP
            cur = {"v": vol, "p": 0, "l": min(len(m.group(1)), 5), "t": name, "paras": []}
        elif line and not skip:
            cur["paras"].append(line)
    if cur["paras"] and not skip:
        secs.append(cur)
    secs[0]["l"] = 1
    secs[-1]["paras"].append(f"المصدر: ويكيبيديا العربية، مقالة «{page.get('title', title)}» (رخصة المشاع الإبداعي CC BY-SA).")
    return secs


def build(b):
    out = os.path.join(WORK, "out_" + b["id"])
    shutil.rmtree(out, ignore_errors=True)
    t = b["type"]
    if t == "openiti":
        info = openiti.convert(openiti_file(b["repo"], b["path"]), out)
        return [(b, out, info)]
    if t == "web":
        secs = []
        for url in b["pages"]:
            try:
                secs += web_sections(url, 1); print("  ok", url)
            except Exception as e:
                print("  skip", url, e)
        if not secs:
            for url in b.get("fallback", []):
                try:
                    secs += web_sections(url, 1); print("  ok", url)
                except Exception as e:
                    print("  skip", url, e)
        if not secs:
            raise RuntimeError("no pages")
        os.makedirs(out, exist_ok=True)
        return [(b, out, openiti.write(secs, out))]
    if t == "wikipedia":
        secs = []
        for title in b["titles"]:
            try:
                secs += wiki_sections(b.get("lang", "ar"), title, 1); print("  ok", title)
                if not b.get("all"):
                    break
            except Exception as e:
                print("  skip", title, e)
        if not secs:
            raise RuntimeError("no article")
        os.makedirs(out, exist_ok=True)
        return [(b, out, openiti.write(secs, out))]
    if t == "hindawi_series":
        parts = {}
        for bid in hindawi_list(b["contributor"]):
            try:
                data = hindawi_epub(bid)
            except Exception as e:
                print("   skip", bid, e); continue
            title = epub_title(data)
            if b["match"] not in title:
                continue
            n = part_no(title)
            if n != 99 and n not in parts:
                parts[n] = (title, data)
        print("  parts", sorted(parts))
        secs = []
        for n in sorted(parts):
            title, data = parts[n]
            s2 = epub_sections(data, n)
            for x in s2:
                x["l"] = min(x["l"] + 1, 5)
            secs += [{"v": n, "p": 0, "l": 1, "t": title, "paras": []}] + s2
        os.makedirs(out, exist_ok=True)
        return [(b, out, openiti.write(secs, out))]
    if t == "hindawi_book":
        s2 = epub_sections(hindawi_epub(b["bid"]), 1)
        os.makedirs(out, exist_ok=True)
        return [(b, out, openiti.write(s2, out))]
    if t == "hindawi_each":
        res = []
        for bid in hindawi_list(b["contributor"]):
            try:
                data = hindawi_epub(bid)
                title = epub_title(data) or bid
                s2 = epub_sections(data, 1)
                o = out + "_" + bid
                os.makedirs(o, exist_ok=True)
                info = openiti.write(s2, o)
                res.append(({**b, "id": f"{b['id']}_{bid}", "title": title, "desc": b["desc"]}, o, info))
                print("   ", title, info)
            except Exception as e:
                print("   skip", bid, e)
        return res
    raise RuntimeError("unknown type " + t)


def main():
    os.makedirs(DIST, exist_ok=True)
    books = json.load(open(os.path.join(ROOT, "tools", "books.json"), encoding="utf-8"))
    catalog, failed = [], []
    for b in books:
        if only and b["id"] not in only:
            continue
        print("==", b["id"], b["title"])
        try:
            for meta, out, info in build(b):
                if info["chars"] < 2000:
                    print("  too small, skipped", info); continue
                zp = os.path.join(DIST, meta["id"] + ".zip")
                with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
                    for f in sorted(os.listdir(out)):
                        z.write(os.path.join(out, f), f)
                entry = {k: meta[k] for k in ("id", "cat", "title", "author", "desc")}
                entry.update({"volumes": info["volumes"], "chars": info["chars"], "size": os.path.getsize(zp)})
                if meta["type"].startswith("hindawi"):
                    entry["source"] = "مؤسسة هنداوي (hindawi.org)"
                elif meta["type"] == "openiti":
                    entry["source"] = "مشروع OpenITI — نص المكتبة الشاملة"
                else:
                    entry["source"] = "نسخة من الصفحات الرسمية بتاريخ " + time.strftime("%Y-%m-%d")
                catalog.append(entry)
                print("  =>", entry["id"], info, entry["size"] // 1024, "KB")
        except Exception as e:
            print("  FAILED", b["id"], e)
            failed.append(b["id"])
    json.dump(catalog, open(os.path.join(DIST, "catalog.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print("catalog", len(catalog), "failed", failed)


main()
