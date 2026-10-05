"""Block helpers for the Hajj/Umrah/Ruqyah guides. Every hadith is checked against the source text; every ayah is pulled from the mushaf."""
from verify import find, ayah, ar, NAMES

SURAH = "الفاتحة البقرة آل_عمران النساء المائدة الأنعام الأعراف الأنفال التوبة يونس هود يوسف الرعد إبراهيم الحجر النحل الإسراء الكهف مريم طه الأنبياء الحج المؤمنون النور الفرقان الشعراء النمل القصص العنكبوت الروم لقمان السجدة الأحزاب سبأ فاطر يس الصافات ص الزمر غافر فصلت الشورى الزخرف الدخان الجاثية الأحقاف محمد الفتح الحجرات ق الذاريات الطور النجم القمر الرحمن الواقعة الحديد المجادلة الحشر الممتحنة الصف الجمعة المنافقون التغابن الطلاق التحريم الملك القلم الحاقة المعارج نوح الجن المزمل المدثر القيامة الإنسان المرسلات النبأ النازعات عبس التكوير الانفطار المطففين الانشقاق البروج الطارق الأعلى الغاشية الفجر البلد الشمس الليل الضحى الشرح التين العلق القدر البينة الزلزلة العاديات التكاثر العصر الهمزة الفيل قريش الماعون الكوثر الكافرون النصر المسد الإخلاص الفلق الناس".replace("_", " ").split()
assert len(SURAH) == 114
ERR = []

def P(x): return {"t": "p", "x": x}
def H(x): return {"t": "h", "x": x}
def STEPS(*items): return {"t": "steps", "items": list(items)}
def LIST(*items): return {"t": "list", "items": list(items)}
def NOTE(x): return {"t": "note", "x": x}
def WARN(x): return {"t": "warn", "x": x}
def TIP(x): return {"t": "tip", "x": x}
def DIAG(k, cap=""): return {"t": "diagram", "k": k, "x": cap}
def QA(q, a): return {"t": "qa", "q": q, "x": a}
def TABLE(head, *rows): return {"t": "table", "head": list(head), "rows": [list(r) for r in rows]}
def VIDEO(title, q, who=""): return {"t": "video", "x": title, "q": q, "who": who}
def LINK(title, route, sub=""): return {"t": "link", "x": title, "route": route, "sub": sub}

def aref(s, a1, a2=None):
    return f"سورة {SURAH[s-1]}: " + (ar(a1) if not a2 or a2 == a1 else f"{ar(a1)}–{ar(a2)}")

def A(s, a1, a2=None, n=0, title=""):
    """Full ayah text from the mushaf."""
    return {"t": "ayah", "x": ayah(s, a1, a2), "src": aref(s, a1, a2), "n": n, "title": title, "s": s, "a": a1}

def _src(matn, books, alt, grade):
    parts = []
    for b in books:
        q = alt.get(b, matn)
        n = find(b, q)
        if n is None:
            ERR.append(f"NOT FOUND in {b}: {q[:70]}")
            parts.append(f"{NAMES[b]} (؟)")
        else:
            parts.append(f"{NAMES[b]} ({ar(n)})")
    if books == ["bukhari", "muslim"] or books == ("bukhari", "muslim"):
        s = "متفق عليه: رواه " + " و".join(parts)
    else:
        s = "رواه " + " و".join(parts)
    if grade: s += "، " + grade
    return s

def HD(matn, *books, alt=None, grade=None, who=""):
    """Hadith, verified in each book. matn may use … for elisions."""
    return {"t": "hadith", "x": matn, "src": _src(matn, list(books), alt or {}, grade), "who": who}

def DUA(x, *books, n=1, alt=None, grade=None, src=None, title=""):
    s = src if src else (_src(x, list(books), alt or {}, grade) if books else "")
    return {"t": "dua", "x": x, "src": s, "n": n, "title": title}

def ATHAR(x, src): return {"t": "hadith", "x": x, "src": src, "who": "athar"}

def SEC(id, title, icon, *blocks, sub=""): return {"id": id, "title": title, "icon": icon, "sub": sub, "blocks": [b for b in blocks if b]}
