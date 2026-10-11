#!/usr/bin/env python3
"""Сравнение вердиктов двух верификаторов по одним и тем же обезличенным ответам.

Запуск:
  python3 compare.py <папка верификатора 1> <папка верификатора 2> [--out compare.md]
"""
import argparse
import json
import pathlib

import sys

from score import ERROR_WEIGHTS, expected_case, score_verdict


def load(folder):
    res = {}
    expected = expected_case(folder)
    for f in sorted(pathlib.Path(folder).glob("verdict-*.json")):
        v = json.loads(f.read_text(encoding="utf-8"))
        if expected and str(v.get("case", "")).zfill(2) != expected:
            print(f"ОШИБКА: {folder}/{f.name} — в вердикте кейс {v.get('case')}, ожидался {expected}. "
                  f"Вердикт пропущен: перепроверьте ответ с «КЕЙС: {expected}».", file=sys.stderr)
            continue
        res[v["answer_id"]] = v
    return res


ATOM_ALIASES = {"K5-M7": {"начальное значение": "a", "валюта": "b", "права доступа": "c",
                          "аудит": "d", "снижение ниже израсходованного": "e"}}


def item_status(v):
    st = {}
    for g in v.get("mandatory", []):
        aliases = ATOM_ALIASES.get(g["id"], {})
        for a in g.get("atoms", []):
            st[f"{g['id']}{aliases.get(a['atom'], a['atom'])}"] = a.get("status")
    for s in v.get("strong", []):
        st[s["id"]] = s.get("status")
    return st


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("first")
    ap.add_argument("second")
    ap.add_argument("--out")
    args = ap.parse_args()
    n1, n2 = pathlib.Path(args.first).name, pathlib.Path(args.second).name
    a, b = load(args.first), load(args.second)
    common = sorted(set(a) & set(b))

    lines = [f"# Сравнение верификаторов: {n1} и {n2}", "",
             f"| Ответ | Оценка {n1} | Оценка {n2} | Разница | Совпадение пунктов | Ошибки {n1} | Ошибки {n2} |",
             "|---|---|---|---|---|---|---|"]
    disagreements = []
    total_items = total_agree = 0
    for aid in common:
        s1, s2 = score_verdict(a[aid]), score_verdict(b[aid])
        i1, i2 = item_status(a[aid]), item_status(b[aid])
        keys = sorted(set(i1) | set(i2))
        agree = sum(1 for k in keys if i1.get(k) == i2.get(k))
        total_items += len(keys)
        total_agree += agree
        e1 = sum(s1["errors"].values())
        e2 = sum(s2["errors"].values())
        lines.append(f"| {aid} | {s1['score']} | {s2['score']} | {round(s1['score'] - s2['score'], 1)} | "
                     f"{agree}/{len(keys)} ({100 * agree // max(len(keys), 1)}%) | {e1} | {e2} |")
        for k in keys:
            if i1.get(k) != i2.get(k):
                disagreements.append(f"- {aid} · {k}: {n1} = {i1.get(k)}, {n2} = {i2.get(k)}")

    lines += ["", f"**Общее совпадение по пунктам:** {total_agree}/{total_items} "
                  f"({100 * total_agree // max(total_items, 1)}%)", ""]

    r1 = [aid for aid in sorted(common, key=lambda x: -score_verdict(a[x])["raw"])]
    r2 = [aid for aid in sorted(common, key=lambda x: -score_verdict(b[x])["raw"])]
    lines += [f"**Порядок {n1}:** {' > '.join(r1)}", f"**Порядок {n2}:** {' > '.join(r2)}", ""]

    lines += ["## Расхождения по пунктам", *disagreements, "", "## Ошибки по категориям", ""]
    for aid in common:
        lines.append(f"**{aid}**")
        for name, v in ((n1, a[aid]), (n2, b[aid])):
            for e in v.get("errors", []):
                lines.append(f"- [{name}] {e.get('category')}: «{e.get('quote', '')[:120]}»")
        lines.append("")

    report = "\n".join(lines)
    print(report)
    if args.out:
        pathlib.Path(args.out).write_text(report, encoding="utf-8")


if __name__ == "__main__":
    main()
