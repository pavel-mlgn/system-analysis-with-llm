#!/usr/bin/env python3
"""Подсчёт баллов по JSON-вердиктам верификатора (эталон, раздел 0).

Запуск:
  python3 score.py <папка с verdict-*.json> [--mermaid mermaid.json] [--out summary.md]

mermaid.json (только кейс 04): {"A": 0, "B": 2, ...} — число нерендерящихся диаграмм.
"""
import argparse
import csv
import json
import math
import pathlib
import re
import sys

CHECKLIST = {
    "01": {"M": [f"K1-M{i}" for i in range(1, 7)], "S": [f"K1-S{i}" for i in range(1, 7)]},
    "02": {"M": [f"K2-M{i}" for i in range(1, 8)], "S": [f"K2-S{i}" for i in range(1, 10)]},
    "03": {"M": [f"K3-M{i}" for i in range(1, 6)], "S": [f"K3-S{i}" for i in range(1, 10)]},
    "04": {"M": [f"K4-M{i}" for i in range(1, 6)], "S": [f"K4-S{i}" for i in range(1, 8)]},
    "05": {"M": [f"K5-M{i}" for i in range(1, 8)], "S": [f"K5-S{i}" for i in range(1, 11)]},
}

# Особые пороги групп: (нужно для «закрыто», нужно для «частично»)
THRESHOLDS = {"K5-M7": (3, 2)}

ERROR_WEIGHTS = {
    "неточность": 0.5,
    "мелкая_ошибка": 1.0,
    "ложная_аномалия": 1.0,
    "галлюцинация": 2.0,
    "искажение": 2.0,
    "нарушение_формата": 0.5,
}

BASE, MIN_SCORE, MAX_SCORE = 7.0, 1.0, 10.0


def group_status(group_id, atoms):
    total = len(atoms)
    have = sum(1 for a in atoms if a.get("status") == "есть")
    if group_id in THRESHOLDS:
        full, part = THRESHOLDS[group_id]
    else:
        full, part = total, math.ceil(total / 2)
    if total and have >= full:
        return "закрыто", 0.0
    if total and have >= part:
        return "частично", 0.5
    return "не закрыто", 1.0


def expected_case(folder):
    """Номер кейса по имени папки: review/01-recon/claude → "01"."""
    for part in reversed(pathlib.Path(folder).resolve().parts):
        m = re.match(r"^(0[1-5])-", part)
        if m:
            return m.group(1)
    return None


def score_verdict(v, mermaid_invalid=0):
    case = v["case"].zfill(2)
    chk = CHECKLIST[case]
    warnings = []

    groups = {g["id"]: g.get("atoms", []) for g in v.get("mandatory", [])}
    m_penalty, m_detail = 0.0, {}
    for gid in chk["M"]:
        if gid not in groups:
            warnings.append(f"нет группы {gid} — считаю «не закрыто»")
            m_detail[gid] = "не закрыто"
            m_penalty += 1.0
            continue
        st, pen = group_status(gid, groups[gid])
        m_detail[gid] = st
        m_penalty += pen
    for gid in groups:
        if gid not in chk["M"]:
            warnings.append(f"лишняя группа {gid} — пропущена")

    strong_ids = {s["id"] for s in v.get("strong", []) if s.get("status") == "есть" and s.get("quote")}
    for sid in strong_ids - set(chk["S"]):
        warnings.append(f"лишний сильный пункт {sid} — пропущен")
    strong = sorted(strong_ids & set(chk["S"]))
    missing_s = set(chk["S"]) - {s["id"] for s in v.get("strong", [])}
    if missing_s:
        warnings.append(f"не перечислены сильные пункты: {', '.join(sorted(missing_s))}")

    err_count, err_penalty = {}, 0.0
    for e in v.get("errors", []):
        cat = e.get("category")
        if cat not in ERROR_WEIGHTS:
            warnings.append(f"неизвестная категория ошибки «{cat}» — пропущена")
            continue
        err_count[cat] = err_count.get(cat, 0) + 1
    # нарушение формата — один раз на тип; верификатор пишет каждый тип отдельной строкой
    for cat, n in err_count.items():
        err_penalty += ERROR_WEIGHTS[cat] * n

    raw = BASE - m_penalty + len(strong) - err_penalty - mermaid_invalid
    final = max(MIN_SCORE, min(MAX_SCORE, raw))
    return {
        "case": case,
        "answer_id": v["answer_id"],
        "score": round(final, 1),
        "raw": round(raw, 1),
        "raw_max": BASE + len(chk["S"]),
        "groups_closed": sum(1 for s in m_detail.values() if s == "закрыто"),
        "groups_partial": sum(1 for s in m_detail.values() if s == "частично"),
        "groups_missed": sum(1 for s in m_detail.values() if s == "не закрыто"),
        "groups_total": len(chk["M"]),
        "strong": len(strong),
        "strong_ids": strong,
        "errors": err_count,
        "error_penalty": err_penalty,
        "mermaid_invalid": mermaid_invalid,
        "outside_correct": len(v.get("outside_etalon_correct", [])),
        "contamination": len(v.get("contamination", [])),
        "group_detail": m_detail,
        "warnings": warnings,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--mermaid")
    ap.add_argument("--out")
    args = ap.parse_args()

    folder = pathlib.Path(args.folder)
    mermaid = json.loads(pathlib.Path(args.mermaid).read_text(encoding="utf-8")) if args.mermaid else {}
    files = sorted(folder.glob("verdict-*.json"))
    if not files:
        sys.exit(f"В {folder} нет файлов verdict-*.json")

    expected = expected_case(folder)
    rows = []
    for f in files:
        try:
            v = json.loads(f.read_text(encoding="utf-8"))
        except json.JSONDecodeError as ex:
            print(f"ОШИБКА: {f.name} — невалидный JSON: {ex}", file=sys.stderr)
            continue
        if expected and str(v.get("case", "")).zfill(2) != expected:
            print(f"ОШИБКА: {f.name} — в вердикте кейс {v.get('case')}, а папка относится к кейсу {expected}. "
                  f"Вердикт пропущен: перепроверьте ответ с «КЕЙС: {expected}».", file=sys.stderr)
            continue
        rows.append(score_verdict(v, int(mermaid.get(v["answer_id"], 0))))
    if not rows:
        sys.exit("Нет вердиктов для подсчёта")

    cats = list(ERROR_WEIGHTS)
    lines = [
        "| Ответ | Индекс (из макс.) | Оценка 1–10 | Обяз. (закр/част/нет из N) | Сильные | "
        + " | ".join(cats) + " | Mermaid | Вне эталона | Контаминация |",
        "|" + "---|" * (8 + len(cats)),
    ]
    for r in sorted(rows, key=lambda r: -r["raw"]):
        lines.append(
            f"| {r['answer_id']} | **{r['raw']}** из {r['raw_max']:g} | {r['score']} | "
            f"{r['groups_closed']}/{r['groups_partial']}/{r['groups_missed']} из {r['groups_total']} | "
            f"{r['strong']} ({', '.join(r['strong_ids']) or '—'}) | "
            + " | ".join(str(r["errors"].get(c, 0)) for c in cats)
            + f" | {r['mermaid_invalid']} | {r['outside_correct']} | {r['contamination']} |"
        )
    out = "\n".join(lines)
    detail = []
    for r in rows:
        detail.append(f"\n**{r['answer_id']}** — группы: "
                      + ", ".join(f"{k}: {v}" for k, v in r["group_detail"].items()))
        for w in r["warnings"]:
            detail.append(f"- ⚠️ {w}")
    report = out + "\n" + "\n".join(detail) + "\n"
    print(report)

    if args.out:
        pathlib.Path(args.out).write_text(report, encoding="utf-8")
        with open(pathlib.Path(args.out).with_suffix(".csv"), "w", newline="", encoding="utf-8") as fh:
            w = csv.writer(fh)
            w.writerow(["case", "answer_id", "score", "raw", "raw_max", "groups_closed", "groups_partial",
                        "groups_missed", "strong", *cats, "mermaid_invalid", "outside_correct", "contamination"])
            for r in rows:
                w.writerow([r["case"], r["answer_id"], r["score"], r["raw"], r["raw_max"], r["groups_closed"],
                            r["groups_partial"], r["groups_missed"], r["strong"],
                            *[r["errors"].get(c, 0) for c in cats],
                            r["mermaid_invalid"], r["outside_correct"], r["contamination"]])


if __name__ == "__main__":
    main()
