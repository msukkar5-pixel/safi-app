"""Verifies hadith quotes against the six books (fawazahmed0/hadith-api Arabic editions) and resolves Quran refs."""
import json, os, re, sys
SRC = os.environ.get("DEEN_SRC", "")
_books, _norm = {}, {}
NAMES = {"bukhari": "البخاري", "muslim": "مسلم", "abudawud": "أبو داود", "tirmidhi": "الترمذي", "nasai": "النسائي", "ibnmajah": "ابن ماجه"}

def norm(s):
    s = re.sub(r"[ؐ-ًؚ-ٰٟۖ-ۭـ]", "", s)
    s = re.sub(r"[أإآٱ]", "ا", s).replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
    s = s.replace("ﷺ", "صلي الله عليه وسلم")
    s = re.sub(r"[^ء-ي0-9]+", "", s)
    return s

def book(b):
    if b not in _books:
        H = json.load(open(os.path.join(SRC, f"ara-{b}.json"), encoding="utf-8"))["hadiths"]
        _books[b] = H
        _norm[b] = [norm(h["text"]) for h in H]
    return _books[b], _norm[b]

def find(b, matn):
    """All pieces of matn (split on … or ...) must occur, in one hadith. Returns that hadith's number."""
    H, N = book(b)
    pieces = [norm(p) for p in re.split(r"…|\.\.\.", matn) if len(norm(p)) >= 6]
    if not pieces:
        raise ValueError("empty quote")
    for h, n in zip(H, N):
        if all(p in n for p in pieces):
            num = h.get("arabicnumber") or h["hadithnumber"]
            return int(float(num)) if num else h["hadithnumber"]
    return None

_q = None
def ayah(s, a1, a2=None):
    global _q
    if _q is None:
        d = json.load(open(os.path.join(SRC, "quran.json"), encoding="utf-8"))["quran"]
        _q = {(v["chapter"], v["verse"]): v["text"] for v in d}
    a2 = a2 or a1
    return " ".join(f"{_q[(s, i)]} ﴿{ar(i)}﴾" for i in range(a1, a2 + 1))

def ar(n): return "".join("٠١٢٣٤٥٦٧٨٩"[int(c)] for c in str(n))

if __name__ == "__main__":
    print(find(sys.argv[1], sys.argv[2]))
