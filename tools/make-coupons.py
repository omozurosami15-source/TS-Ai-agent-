#!/usr/bin/env python3
"""Create or withdraw coupon codes.
  python3 tools/make-coupons.py p2 30 5       5 codes for Plan 2, 30 days (0 days = lifetime)
  python3 tools/make-coupons.py p1 30 5       5 codes for Plan 1
  python3 tools/make-coupons.py owner 0 1     the owner code (lifetime)
  python3 tools/make-coupons.py revoke c003   withdraw coupon id c003
Only SHA-256 hashes are stored (docs/coupons.json and the app asset). The printed codes cannot be recovered, so save them."""
import hashlib, json, os, secrets, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = [os.path.join(ROOT, "docs", "coupons.json"), os.path.join(ROOT, "app", "src", "main", "assets", "coupons.json")]
ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

def load():
    for f in FILES:
        if os.path.exists(f):
            return json.load(open(f))
    return {"coupons": [], "revoked": []}

def save(book):
    for f in FILES:
        os.makedirs(os.path.dirname(f), exist_ok=True)
        json.dump(book, open(f, "w"), indent=1)
        open(f, "a").write("\n")

if len(sys.argv) < 3:
    sys.exit(__doc__)
book = load()
if sys.argv[1] == "revoke":
    if sys.argv[2] not in book["revoked"]:
        book["revoked"].append(sys.argv[2])
    save(book)
    sys.exit("revoked " + sys.argv[2])
plan, days, count = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 1
MODULES = ["neural","voice","memory","soul","vision","web","code","files","tasks","notes","briefing","translate","research","calendar","reminders","security","analytics"]
if plan.startswith("module:"):
    if plan[7:] not in MODULES: sys.exit("unknown module id, use one of: " + ", ".join(MODULES))
elif plan not in ("p1", "p2", "owner"):
    sys.exit("plan must be p1, p2, owner or module:<id>")
for _ in range(count):
    code = "".join(secrets.choice(ALPHA) for _ in range(16))
    cid = "c%03d" % (len(book["coupons"]) + 1)
    book["coupons"].append({"h": hashlib.sha256(code.encode()).hexdigest(), "plan": plan, "days": days, "id": cid})
    print(cid, plan, days, "-".join(code[i:i + 4] for i in range(0, 16, 4)))
save(book)
