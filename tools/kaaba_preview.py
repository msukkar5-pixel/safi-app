"""List free-licensed Kaaba photos on Commons, save 360px previews + index.json (for choosing the splash image)."""
import json, os, re, sys, urllib.parse, urllib.request
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
UA = {"User-Agent": "SafiApp-CI/1.0 (personal Android app build)"}
def get(u): return urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60).read()
def strip(h): return re.sub(r"<[^>]+>", "", h or "").strip()
titles = []
for term in ["Kaaba", "Kaaba night", "Kaaba Masjid al-Haram", "الكعبة", "Kaaba close", "Kaaba tawaf", "Kaaba Kiswah"]:
    q = urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": term + " filetype:bitmap", "srnamespace": 6, "srlimit": 30, "format": "json"})
    try: titles += [r["title"] for r in json.loads(get("https://commons.wikimedia.org/w/api.php?" + q))["query"]["search"]]
    except Exception as e: print("search", term, e)
seen, idx = set(), []
for t in titles:
    if t in seen: continue
    seen.add(t)
    q = urllib.parse.urlencode({"action": "query", "titles": t, "prop": "imageinfo", "iiprop": "url|size|extmetadata", "iiurlwidth": 360, "format": "json"})
    try: p = list(json.loads(get("https://commons.wikimedia.org/w/api.php?" + q))["query"]["pages"].values())[0]
    except Exception as e: continue
    ii = (p.get("imageinfo") or [None])[0]
    if not ii: continue
    lic = strip(ii.get("extmetadata", {}).get("LicenseShortName", {}).get("value")); l = lic.lower()
    if not ("cc0" in l or "public domain" in l or l.startswith("cc by")) or "nd" in re.split(r"[\s-]", l) or "nc" in re.split(r"[\s-]", l): continue
    if ii.get("width", 0) < 1200: continue
    n = len(idx)
    try: open(f"{out}/p{n:02d}.jpg", "wb").write(get(ii["thumburl"]))
    except Exception: continue
    idx.append({"n": n, "title": t, "lic": lic, "w": ii["width"], "h": ii["height"]})
    if len(idx) >= 40: break
json.dump(idx, open(f"{out}/index.json", "w"), ensure_ascii=False, indent=1)
print(len(idx), "previews")
