import sys, re
from verify import book, norm
b = sys.argv[1]; keys = [norm(k) for k in sys.argv[2:]]
H, N = book(b)
c = 0
for h, n in zip(H, N):
    if all(k in n for k in keys):
        t = re.sub(r"[ً-ْٰ]", "", h["text"])
        i = t.find(sys.argv[2][:4]) if False else 0
        print(h.get("arabicnumber") or h["hadithnumber"], "|", t[:700]); print(); c += 1
        if c >= 3: break
