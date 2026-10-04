"""Drive the app on an emulator: screenshots + crash detection. Output in results/."""
import os, re, subprocess, sys, time, json, xml.etree.ElementTree as ET

PKG = "com.mohamed.safi"
OUT = "results"
os.makedirs(OUT, exist_ok=True)
apk = sys.argv[1]
plan_file = sys.argv[2] if len(sys.argv) > 2 else "tools/uiplan.json"
log = open(f"{OUT}/steps.txt", "w", encoding="utf-8")
W, H = 1080, 2400
shot_n = 0
crashes = []


def adb(*a, timeout=60, check=False):
    r = subprocess.run(["adb", *a], capture_output=True, timeout=timeout)
    return r.stdout.decode("utf-8", "replace")


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def note(s):
    print(s); log.write(s + "\n"); log.flush()


def shot(name):
    global shot_n
    shot_n += 1
    fn = f"{OUT}/{shot_n:03d}_{re.sub(r'[^A-Za-z0-9_-]', '_', name)[:40]}.png"
    with open(fn, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    # shrink to save space
    subprocess.run(["python3", "-c", f"from PIL import Image;im=Image.open('{fn}');im.thumbnail((540,1200));im.convert('RGB').save('{fn[:-4]}.jpg',quality=70)"])
    os.remove(fn)
    note(f"  shot {fn[:-4]}.jpg")


def check_crash(step):
    out = adb("logcat", "-d", "-b", "crash")
    if out.strip():
        crashes.append({"step": step, "log": out[-6000:]})
        note(f"!!! CRASH at {step}\n{out[-3000:]}")
        adb("logcat", "-c", "-b", "crash")
        return True
    anr = sh("dumpsys activity processes | grep -c 'notResponding'")
    return False


def dump():
    for _ in range(4):
        sh("rm -f /sdcard/u.xml")
        sh("uiautomator dump --compressed /sdcard/u.xml", timeout=40)
        x = adb("exec-out", "cat /sdcard/u.xml")
        if x.startswith("<?xml"):
            try:
                return ET.fromstring(x)
            except Exception:
                pass
        time.sleep(1.5)
    return None


def find(text, exact=False):
    root = dump()
    if root is None:
        return None
    best = None
    for n in root.iter("node"):
        for attr in ("text", "content-desc"):
            t = n.get(attr) or ""
            if (t == text) if exact else (text in t):
                b = re.findall(r"\d+", n.get("bounds", ""))
                if len(b) == 4:
                    x1, y1, x2, y2 = map(int, b)
                    c = ((x1 + x2) // 2, (y1 + y2) // 2)
                    if best is None:
                        best = c
    return best


def tap_text(text, exact=False, tries=3):
    for _ in range(tries):
        c = find(text, exact)
        if c:
            sh(f"input tap {c[0]} {c[1]}")
            return True
        time.sleep(2)
    note(f"  (text not found: {text})")
    return False


def run_step(s):
    k = s[0]
    if k == "route":
        sh(f"am start -n {PKG}/.MainActivity --es route {s[1]}")
    elif k == "launch":
        sh(f"am start -W -n {PKG}/.MainActivity")
    elif k == "stop":
        sh(f"am force-stop {PKG}")
    elif k == "tap":
        tap_text(s[1], exact=len(s) > 2 and s[2])
    elif k == "tapxy":
        sh(f"input tap {int(s[1] * W)} {int(s[2] * H)}")
    elif k == "type":
        sh("input text " + s[1].replace(" ", "%s"))
    elif k == "back":
        sh("input keyevent 4")
    elif k == "scroll":
        sh(f"input swipe {W // 2} {int(H * 0.75)} {W // 2} {int(H * 0.3)} 400")
    elif k == "wait":
        time.sleep(s[1]); return
    elif k == "shot":
        shot(s[1]); return
    time.sleep(2.5)


def main():
    global W, H
    adb("wait-for-device")
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    if m:
        W, H = int(m.group(1)), int(m.group(2))
    note(f"screen {W}x{H}")
    note(adb("install", "-r", "-g", apk, timeout=300))
    for p in ["POST_NOTIFICATIONS", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "CAMERA"]:
        sh(f"pm grant {PKG} android.permission.{p}")
    sh(f"appops set {PKG} SCHEDULE_EXACT_ALARM allow")
    adb("logcat", "-c")
    adb("logcat", "-c", "-b", "crash")
    plan = json.load(open(plan_file, encoding="utf-8"))
    for i, s in enumerate(plan):
        note(f"[{i}] {s}")
        try:
            run_step(s)
        except Exception as e:
            note(f"  step error {e}")
        if check_crash(f"{i}:{s}"):
            shot(f"after_crash_{i}")
            sh(f"am start -n {PKG}/.MainActivity"); time.sleep(8)
            tap_text("آمين")
    full = adb("logcat", "-d", "-v", "brief", "*:W", timeout=120)
    open(f"{OUT}/logcat_warn.txt", "w").write("\n".join(l for l in full.splitlines() if PKG in l or "AndroidRuntime" in l or "FATAL" in l or "safi" in l.lower())[-200000:])
    json.dump(crashes, open(f"{OUT}/crashes.json", "w"), ensure_ascii=False, indent=1)
    note(f"DONE crashes={len(crashes)}")


main()
