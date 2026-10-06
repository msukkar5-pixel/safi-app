"""Check the bundled live streams (assets/media/radio.json, tv.json, kidstv.json) and drop the dead ones.

Run in CI before the build:  python3 tools/check_streams.py app/src/main/assets/media --prune
A stream counts as alive when it answers 2xx and sends audio/video bytes (or a valid HLS playlist).
Entries may have "urls" (tried in order); dead ones are dropped and "url" becomes the first live one.
Without --prune it only reports. If nothing at all is reachable (no network), the list is kept as is.
"""
import json, os, sys, urllib.error, urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) SafiApp/1.0"

def alive(url, timeout=12):
    """(ok, reason, network_error). network_error is True when the host couldn't be reached at all."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Icy-MetaData": "0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            if not 200 <= r.status < 300:
                return False, f"HTTP {r.status}", False
            data = r.read(65536)
            ctype = r.headers.get("Content-Type", "")
            if url.split("?")[0].endswith(".m3u8") or "mpegurl" in ctype.lower():
                txt = data.decode("utf-8", "ignore")
                if "#EXTM3U" not in txt:
                    return False, "not an HLS playlist", False
                return True, "hls", False
            if len(data) < 2048:
                return False, f"only {len(data)} bytes ({ctype})", False
            return True, ctype or "bytes", False
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code}", False
    except Exception as e:  # noqa: BLE001 - any failure means the stream is not usable
        return False, type(e).__name__ + ": " + str(e)[:120], True


def main():
    folder = sys.argv[1]
    prune = "--prune" in sys.argv
    for name in ("radio.json", "tv.json", "kidstv.json"):
        path = os.path.join(folder, name)
        if not os.path.exists(path):
            continue
        items = json.load(open(path, encoding="utf-8"))
        kept, network_only = [], True
        for it in items:
            urls = it.get("urls") or [it["url"]]
            good = []
            for u in urls:
                ok, why, net = alive(u)
                print(("OK  " if ok else "DEAD"), it["name"], "-", u, "-", why)
                if ok:
                    good.append(u)
                elif not net:
                    network_only = False
            if good:
                it = dict(it, url=good[0], urls=good) if "urls" in it else it
                kept.append(it)
            else:
                print(f"::warning title=stream dead::{name}: {it['name']}")
        if not prune:
            continue
        if not kept and network_only:
            print(f"{name}: nothing reachable (network problem?), keeping the list as is")
            continue
        json.dump(kept, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print(f"{name}: kept {len(kept)} of {len(items)}")


if __name__ == "__main__":
    main()
