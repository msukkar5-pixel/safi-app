"""Fetch every mp3quran.net radio, test each stream (trying link variants), keep the live ones.

Run in CI before the build:  python3 tools/radios.py app/src/main/assets/media
Writes <folder>/radio_mp3quran.json = [{"id","name","url","group"}] with only streams that answered with audio.
If the API can't be reached the existing file is left alone.
"""
import json, os, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))
from check_streams import alive

API = ["https://mp3quran.net/api/v3/radios?language=ar", "https://www.mp3quran.net/api/v3/radios?language=ar"]


def group_of(name):
    if "تفسير" in name or "تدبر" in name: return "التفسير"
    if "رقية" in name: return "الرقية"
    if "أذكار" in name or "اذكار" in name or "الصباح" in name or "المساء" in name: return "الأذكار"
    if "ترجمة" in name or "مترجم" in name: return "ترجمات"
    if "سيرة" in name or "فتاوى" in name or "حديث" in name: return "سيرة وفتاوى"
    return "القرّاء"


def variants(url):
    """The same stream under the forms mp3quran and its mirrors use."""
    u = url.strip()
    out = [u.replace("http://", "https://"), u.replace("https://", "http://")]
    if "backup.qurango.net" in u:
        out.append(u.replace("backup.qurango.net", "qurango.net").replace("http://", "https://"))
    if not u.rstrip("/").endswith((".mp3", ".m3u8", ".aac")):
        out.append(u.rstrip("/").replace("http://", "https://") + "/;stream.mp3")
    seen, res = set(), []
    for x in out:
        if x not in seen:
            seen.add(x); res.append(x)
    return res


def check(r):
    for u in variants(r["url"]):
        ok, why, _ = alive(u, timeout=15)
        if ok:
            # the app allows HTTPS only
            if u.startswith("https://"):
                return dict(r, url=u)
    return None


def main():
    folder = sys.argv[1]
    data = None
    for a in API:
        try:
            data = json.loads(urllib.request.urlopen(urllib.request.Request(a, headers={"User-Agent": "Mozilla/5.0"}), timeout=30).read())
            break
        except Exception as e:  # noqa: BLE001
            print("API failed:", a, e)
    if not data:
        print("::warning title=radios::mp3quran API unreachable, keeping the old list")
        return
    radios = [{"id": "mp3q_%s" % x.get("id"), "name": (x.get("name") or "").replace("*", "").strip(), "url": x.get("url", ""), "group": group_of(x.get("name") or "")}
              for x in data.get("radios", []) if x.get("url")]
    with ThreadPoolExecutor(24) as ex:
        res = list(ex.map(check, radios))
    good = sorted([r for r in res if r], key=lambda r: r["name"])
    dead = [r["name"] for r, ok in zip(radios, res) if not ok]
    print(f"radios: {len(good)} live of {len(radios)}")
    for n in dead:
        print("DEAD", n)
    if good:
        json.dump(good, open(os.path.join(folder, "radio_mp3quran.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)


if __name__ == "__main__":
    main()
