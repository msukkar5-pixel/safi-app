"""Download a freely licensed Kaaba photo from Wikimedia Commons for the opening screen.
Writes <out>/kaaba.jpg and <out>/credit.json. Only accepts CC0 / public domain / CC BY / CC BY-SA (no ND / NC)."""
import json, os, re, sys, urllib.parse, urllib.request

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
UA = {"User-Agent": "SafiApp-CI/1.0 (personal Android app build; github actions)"}
CANDIDATES = [
    "File:الكعبة المشرفة ليلاً.jpg",
    "File:Kaaba at night.jpg",
    "File:Kaaba, Never still.jpg",
    "File:Kaaba Masjid Haraam Makkah.jpg",
    "File:Kaaba, Masjid Al-Haram, Mecca, Saudi Arabia - panoramio.jpg",
    "File:The Kaaba during Hajj - edited.jpg",
    "File:Masjid al-Haram.JPG",
]

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()

def strip(h):
    return re.sub(r"<[^>]+>", "", h or "").strip()

def search_more():
    q = urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": "Kaaba night", "srnamespace": 6, "srlimit": 20, "format": "json"})
    try:
        return [r["title"] for r in json.loads(get("https://commons.wikimedia.org/w/api.php?" + q))["query"]["search"]]
    except Exception as e:
        print("search failed", e)
        return []

for title in CANDIDATES + search_more():
    q = urllib.parse.urlencode({"action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url|size|extmetadata", "iiurlwidth": 1080, "format": "json"})
    try:
        pages = json.loads(get("https://commons.wikimedia.org/w/api.php?" + q))["query"]["pages"]
    except Exception as e:
        print("skip", title, e); continue
    for p in pages.values():
        ii = (p.get("imageinfo") or [None])[0]
        if not ii:
            continue
        md = ii.get("extmetadata", {})
        lic = strip(md.get("LicenseShortName", {}).get("value"))
        l = lic.lower()
        free = ("cc0" in l or "public domain" in l or l.startswith("cc by")) and "nd" not in l.split() and "-nd" not in l and "nc" not in l.split() and "-nc" not in l
        if not free or ii.get("width", 0) < 900:
            print("reject", title, lic, ii.get("width")); continue
        data = get(ii.get("thumburl") or ii["url"])
        if len(data) < 30000:
            print("too small", title); continue
        open(os.path.join(out, "kaaba.jpg"), "wb").write(data)
        credit = {"title": title, "artist": strip(md.get("Artist", {}).get("value"))[:80], "license": lic, "page": ii.get("descriptionurl", "")}
        json.dump(credit, open(os.path.join(out, "credit.json"), "w", encoding="utf-8"), ensure_ascii=False)
        print("OK", credit, len(data), "bytes")
        sys.exit(0)
print("no free Kaaba image found; splash will use plain background")
