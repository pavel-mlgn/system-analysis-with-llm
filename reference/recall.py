#!/usr/bin/env python3
"""Проверка на признаки того, что модель вспоминает опубликованный эталон, а не читает код.

Из эталона берутся фразы из шести слов подряд, которых нет в коде. Для каждой модели считается,
какая доля шестисловных фраз ее ответов (все кейсы вместе) совпадает с такими фразами эталона.
Совпадения бывают и у честного ответа: общие термины и формулировки. Подозрительна доля
заметно выше, чем у моделей основного прогона, особенно у модели, вышедшей после публикации.

Запуск (из папки reference/):
  python3 recall.py [--n 6] [--out recall.md]

Слова - последовательности букв, цифр и подчеркиваний, без учета регистра, ё приравнивается к е.
Фразы не переходят через границу кейсов.
"""
import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent


def words(text):
    return re.findall(r"\w+", text.lower().replace("ё", "е"))


def grams(ws, n):
    return [tuple(ws[i:i + n]) for i in range(len(ws) - n + 1)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=6, help="длина фразы в словах")
    ap.add_argument("--out", help="записать таблицу в файл Markdown")
    a = ap.parse_args()

    etalon = words((ROOT / "reference" / "etalon.md").read_text(encoding="utf-8"))
    code = words((ROOT / "experiment" / "code.txt").read_text(encoding="utf-8"))
    suspect = set(grams(etalon, a.n)) - set(grams(code, a.n))

    files = sorted((ROOT / "experiment").glob("*/answer-*.md"))
    models = sorted({f.stem[len("answer-"):] for f in files})
    rows = []
    for m in models:
        total = hits = 0
        for f in files:
            if f.stem[len("answer-"):] != m:
                continue
            g = grams(words(f.read_text(encoding="utf-8")), a.n)
            total += len(g)
            hits += sum(1 for x in g if x in suspect)
        rows.append((m, hits, total, 100 * hits / total if total else 0.0))

    lines = [f"Фраз из {a.n} слов в эталоне, которых нет в коде: {len(suspect)}", "",
             "| Модель | Совпало фраз | Фраз в ответах | Доля |", "|---|---|---|---|"]
    for m, h, t, p in sorted(rows, key=lambda r: -r[3]):
        pct = f"{p:.2f}".replace(".", ",")
        lines.append(f"| {m} | {h} | {t} | {pct}% |")
    text = "\n".join(lines) + "\n"
    print(text)
    if a.out:
        pathlib.Path(a.out).write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
