#!/usr/bin/env python3
"""Сводная таблица по всем кейсам: python3 total.py <review> [--out total.md]
Берёт review/<NN-…>/final/verdict-*.json, review/_mapping.json и (для 04) mermaid.json."""
import argparse, json, pathlib
from score import score_verdict

NAMES = {
    "claude-opus-5.5-max": "Claude Opus 5.5 Max",
    "claude-opus-5.5-high": "Claude Opus 5.5 High",
    "chatgpt-gpt-5.6-sol-high": "ChatGPT GPT-5.6 Sol High",
    "deepseek-v4.1-flash-deepthink": "DeepSeek V4.1-Flash DeepThink",
    "gemini-3.1-pro-high": "Gemini 3.1 Pro High",
    "gemini-4-argon-high": "Gemini 4 Argon High",
}
CASES = ["01-recon", "02-business-logic", "03-specs", "04-diagrams", "05-change-request"]

def fmt(x):
    return f"{x:g}".replace(".", ",")

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("review"); ap.add_argument("--out"); a = ap.parse_args()
    root = pathlib.Path(a.review)
    mapping = json.loads((root / "_mapping.json").read_text(encoding="utf-8"))
    table, maxes, errs = {}, {}, {}
    for case in CASES:
        nn = case[:2]
        m = mapping[nn]
        mer_p = root / case / "mermaid.json"
        mer = json.loads(mer_p.read_text()) if mer_p.exists() else {}
        for f in sorted((root / case / "final").glob("verdict-*.json")):
            v = json.loads(f.read_text(encoding="utf-8"))
            r = score_verdict(v, int(mer.get(v["answer_id"], 0)))
            model = m[v["answer_id"]]["model"]
            table.setdefault(model, {})[nn] = r["raw"]
            maxes[nn] = r["raw_max"]
            e = errs.setdefault(model, {})
            for c, n in r["errors"].items(): e[c] = e.get(c, 0) + n
            if r["mermaid_invalid"]: e["mermaid"] = e.get("mermaid", 0) + r["mermaid_invalid"]
    total_max = sum(maxes.values())
    rows = sorted(table.items(), key=lambda kv: -sum(kv[1].values()))
    nns = sorted(maxes)
    lines = ["| Модель | " + " | ".join(f"К{int(n)} (из {fmt(maxes[n])})" for n in nns) + f" | Итого (из {fmt(total_max)}) | % |",
             "|---|" + "---|" * (len(nns) + 2)]
    for model, sc in rows:
        t = sum(sc.values())
        lines.append(f"| {NAMES.get(model, model)} | " + " | ".join(fmt(sc.get(n, 0)) for n in nns)
                     + f" | **{fmt(t)}** | {round(100 * t / total_max)}% |")
    lines += ["", "**Ошибки за все кейсы**", "",
              "| Модель | неточность | мелкая_ошибка | ложная_аномалия | галлюцинация | искажение | нарушение_формата | Mermaid |",
              "|---|---|---|---|---|---|---|---|"]
    for model, _ in rows:
        e = errs.get(model, {})
        lines.append(f"| {NAMES.get(model, model)} | " + " | ".join(str(e.get(c, 0)) for c in
                     ["неточность", "мелкая_ошибка", "ложная_аномалия", "галлюцинация", "искажение", "нарушение_формата", "mermaid"]) + " |")
    out = "\n".join(lines) + "\n"
    print(out)
    if a.out: pathlib.Path(a.out).write_text(out, encoding="utf-8")

if __name__ == "__main__":
    main()
