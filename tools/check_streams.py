"""Check the bundled live streams (assets/media/radio.json, tv.json) and drop the dead ones.

Run in CI before the build:  python3 tools/check_streams.py app/src/main/assets/media --prune
A stream counts as alive when it answers 2xx and sends audio/video bytes (or a valid HLS playlist).
Without --prune it only reports. Never empties a list: if every entry fails (e.g. no network), it keeps them all.
"""
import json, os, sys, urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) SafiApp/1.0"

def alive(url, timeout=12):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Icy-MetaData": "0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            if not 200 <= r.status < 300:
                return False, f"HTTP {r.status}"
            data = r.read(65536)
            ctype = r.headers.get("Content-Type", "")
            if url.split("?")[0].endswith(".m3u8") or "mpegurl" in ctype.lower():
                txt = data.decode("utf-8", "ignore")
                if "#EXTM3U" not in txt:
                    return False, "not an HLS playlist"
                return True, "hls"
            if len(data) < 2048:
                return False, f"only {len(data)} bytes ({ctype})"
            return True, ctype or "bytes"
    except Exception as e:  # noqa: BLE001 - any failure means the stream is not usable
        return False, type(e).__name__ + ": " + str(e)[:120]

def main():
    folder = sys.argv[1]
    prune = "--prune" in sys.argv
    for name in ("radio.json", "tv.json"):
        path = os.path.join(folder, name)
        if not os.path.exists(path):
            continue
        items = json.load(open(path, encoding="utf-8"))
        ok = []
        for it in items:
            good, why = alive(it["url"])
            print(("OK  " if good else "DEAD"), it["name"], "-", why)
            if not good:
                print(f"::warning title=stream dead::{name}: {it['name']} ({why})")
            if good:
                ok.append(it)
        if prune and ok and len(ok) < len(items):
            json.dump(ok, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
            print(f"{name}: kept {len(ok)} of {len(items)}")
        elif prune and not ok:
            print(f"{name}: every stream failed, keeping the list as is (network problem?)")

if __name__ == "__main__":
    main()
