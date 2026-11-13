#!/usr/bin/env python3
"""Runs the agent's eval phrases through the real planner on the phone, plan-only, and scores them.
usage: eval.py phrases.json out.jsonl [start] [count]"""
import json, shlex, subprocess, sys, time
ADB = "/Users/everettpenne/Library/Android/sdk/platform-tools/adb"
SERIAL = "192.168.1.206:44767"
PKG = "com.jake.duolauncher.dev"

def adb(*args, timeout=30):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout).stdout

def close_search():
    for _ in range(3):
        adb("shell", "input", "keyevent", "4"); time.sleep(0.5)

def focus():
    return adb("shell", "dumpsys window | grep mCurrentFocus")

def run(query):
    if PKG not in focus():
        adb("shell", f"am start -n {PKG}/com.jake.duolauncher.MainActivity"); time.sleep(1.5)
    adb("logcat", "-c")
    cmd = (f"am start -n {PKG}/com.jake.duolauncher.MainActivity --es duo_destination spotlight "
           f"--es uno_query {shlex.quote(query)} --ez uno_ask true --ez uno_plan_only true")
    adb("shell", cmd)
    t0 = time.time(); log = ""
    while time.time() - t0 < 120:
        time.sleep(1)
        log = adb("logcat", "-d", "-s", "UnoAgent", "UnoLabTiming")
        if "UnoAgent: outcome" in log or "UnoLabTiming: turn " in log: break
    reply = next((l.split("plan reply: ", 1)[1].strip() for l in log.splitlines() if "plan reply: " in l), None)
    ms = next((int(l.split("UnoLabTiming: plan ", 1)[1].split(" ms")[0]) for l in log.splitlines() if "UnoLabTiming: plan " in l and " ms" in l and "reply" not in l), None)
    outcome = next((l.split("UnoAgent: outcome ", 1)[1].strip() for l in log.splitlines() if "UnoAgent: outcome" in l), None)
    answered = "UnoLabTiming: turn " in log
    close_search()
    return reply, ms, outcome, answered

import re
EXACT = {"seconds", "in_minutes", "duration_minutes", "on", "panel"}
def norm(v): return " ".join(str(v).lower().replace("’", "'").split())
def same(key, want, got):
    """Numbers, switches and choices must match exactly; a clock time and a phone number by value; free text only has to be there
    (its wording is the model's to choose: "capital of peru" for "the capital of peru")."""
    w, g = norm(want), norm(got)
    if key in EXACT: return w == g
    if key == "at":
        f = lambda t: (lambda m: "%02d:%02d" % (int(m.group(1)), int(m.group(2))) if m else t)(re.match(r"^(\d{1,2}):(\d{2})$", t))
        return f(w) == f(g)
    if key == "number": return re.sub(r"\D", "", w) == re.sub(r"\D", "", g)
    return bool(g)

def score(row, reply, outcome, answered):
    if reply is None:
        return ("answered" if answered else "not_offered"), "no plan was asked for"
    try: plan = json.loads(reply)
    except Exception as e: return "bad_json", str(e)
    if "steps" in row:
        if "steps" not in plan: return "wrong_kind", "expected steps, got " + ("clarify" if "clarify" in plan else "refuse" if "refuse" in plan else "?")
        want = [s["tool"] for s in row["steps"]]; got = [s.get("tool") for s in plan["steps"]]
        if want != got: return "wrong_tool", f"expected {want}, got {got}"
        for w, g in zip(row["steps"], plan["steps"]):
            for k, v in (w.get("params") or {}).items():
                if not same(k, v, (g.get("params") or {}).get(k, "")): return "wrong_params", f"{k}: expected {v!r}, got {(g.get('params') or {}).get(k)!r}"
        if outcome and outcome.startswith("Failed"): return "rejected", outcome[:160]
        return "pass", ""
    kind = "clarify" if "clarify" in row else "refuse"
    if kind in plan: return "pass", ""
    return "wrong_kind", f"expected {kind}, got {list(plan)[:1]}"

rows = json.load(open(sys.argv[1]))
start = int(sys.argv[3]) if len(sys.argv) > 3 else 0
count = int(sys.argv[4]) if len(sys.argv) > 4 else len(rows)
with open(sys.argv[2], "a") as out:
    for i, row in enumerate(rows[start:start + count], start):
        reply, ms, outcome, answered = run(row["q"])
        verdict, why = score(row, reply, outcome, answered)
        rec = {"i": i, "q": row["q"], "verdict": verdict, "why": why, "reply": reply, "ms": ms, "outcome": (outcome or "")[:200]}
        out.write(json.dumps(rec, ensure_ascii=False) + "\n"); out.flush()
        print(i, verdict, ms, row["q"], "|", why, flush=True)
