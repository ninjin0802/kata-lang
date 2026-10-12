"""Draws the benchmark figures in img/ from data.json. Standard library only.

Usage: python3 docs/benchmarks/charts.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

THEMES = {
    "light": {"surface": "#fcfcfb", "text": "#0b0b0b", "muted": "#52514e", "grid": "#e4e3df",
              "kata": "#2a78d6", "kotlin": "#eb6834"},
    "dark": {"surface": "#1a1a19", "text": "#f0efec", "muted": "#c3c2b7", "grid": "#383836",
             "kata": "#3987e5", "kotlin": "#d95926"},
}
NAMES = {"kata": "kata", "kotlin": "Kotlin"}
TASKS = {"calculator": "Calculator", "converter": "Unit converter", "expense": "Expense tracker"}
MODELS = {"opus": "Opus", "sonnet": "Sonnet"}

WIDTH = 760
LEFT = 210
RIGHT = 110
BAR = 16
GAP = 2
GROUP_GAP = 18
TOP = 92
FONT = "-apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"


def esc(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def bar(x, y, width, height, color):
    """A bar with a flat end on the baseline and a rounded data end."""
    width = max(width, 2.0)
    r = min(4.0, width / 2)
    return ('<path d="M{x},{y} h{w} a{r},{r} 0 0 1 {r},{r} v{v} a{r},{r} 0 0 1 -{r},{r} h-{w} z" fill="{c}"/>'
            .format(x=x, y=y, w=round(width - r, 2), r=r, v=round(height - 2 * r, 2), c=color))


def draw(theme, title, subtitle, groups, ticks, tick_label):
    """groups: [(label, sublabel, [(lang, value, value_label)])]"""
    t = THEMES[theme]
    plot = WIDTH - LEFT - RIGHT
    top = ticks[-1]
    height = TOP + sum(len(bars) * (BAR + GAP) - GAP + GROUP_GAP for _, _, bars in groups) + 30
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
           'font-family="{f}" role="img" aria-label="{a}">'.format(w=WIDTH, h=height, f=FONT, a=esc(title)),
           '<rect width="{}" height="{}" rx="8" fill="{}"/>'.format(WIDTH, height, t["surface"]),
           '<text x="24" y="34" font-size="17" font-weight="600" fill="{}">{}</text>'.format(t["text"], esc(title)),
           '<text x="24" y="54" font-size="12.5" fill="{}">{}</text>'.format(t["muted"], esc(subtitle))]
    x = WIDTH - 24
    for lang in ("kotlin", "kata"):
        name = NAMES[lang]
        x -= 7.2 * len(name)
        out.append('<text x="{}" y="34" font-size="12.5" fill="{}">{}</text>'.format(round(x, 1), t["text"], name))
        x -= 18
        out.append('<rect x="{}" y="24" width="12" height="12" rx="3" fill="{}"/>'.format(round(x, 1), t[lang]))
        x -= 18
    bottom = height - 30
    for tick in ticks:
        tx = LEFT + plot * tick / top
        out.append('<line x1="{x}" y1="{a}" x2="{x}" y2="{b}" stroke="{c}" stroke-width="1"/>'
                   .format(x=round(tx, 1), a=TOP - 10, b=bottom - 8, c=t["grid"]))
        out.append('<text x="{}" y="{}" font-size="11" text-anchor="middle" fill="{}">{}</text>'
                   .format(round(tx, 1), bottom + 8, t["muted"], esc(tick_label(tick))))
    y = TOP
    for label, sublabel, bars in groups:
        block = len(bars) * (BAR + GAP) - GAP
        middle = y + block / 2
        if sublabel:
            out.append('<text x="{}" y="{}" font-size="13" text-anchor="end" fill="{}">{}</text>'
                       .format(LEFT - 12, middle - 2, t["text"], esc(label)))
            out.append('<text x="{}" y="{}" font-size="11.5" text-anchor="end" fill="{}">{}</text>'
                       .format(LEFT - 12, middle + 13, t["muted"], esc(sublabel)))
        else:
            out.append('<text x="{}" y="{}" font-size="13" text-anchor="end" fill="{}">{}</text>'
                       .format(LEFT - 12, middle + 4.5, t["text"], esc(label)))
        for lang, value, value_label in bars:
            width = plot * value / top
            out.append(bar(LEFT, y, width, BAR, t[lang]))
            out.append('<text x="{}" y="{}" font-size="12" fill="{}">{}</text>'
                       .format(round(LEFT + max(width, 2) + 8, 1), y + 12, t["text"], esc(value_label)))
            y += BAR + GAP
        y += GROUP_GAP - GAP
    out.append('</svg>')
    return "\n".join(out) + "\n"


def pairs(runs, value, label):
    """One group per task and model, a kata bar and a Kotlin bar. Repeated Kotlin runs are averaged;
    kata's calculator run with the full spec is left out, because the guide replaced it."""
    groups = []
    for task in TASKS:
        for model in MODELS:
            bars = []
            for lang in ("kata", "kotlin"):
                rows = [r for r in runs if (r["task"], r["model"], r["lang"]) == (task, model, lang)
                        and "full spec" not in r["variant"]]
                if rows:
                    mean = sum(value(r) for r in rows) / len(rows)
                    bars.append((lang, mean, label(mean)))
            if bars:
                groups.append((TASKS[task], MODELS[model], bars))
    return groups


def size(kb):
    return "{:.1f} MB".format(kb / 1000) if kb >= 1000 else "{:.0f} KB".format(kb)


def figures(data):
    runs = data["runs"]
    langs = ("kata", "kotlin")
    first = [(lang, sum(1 for r in runs if r["lang"] == lang and r["failed_builds"] == 0)) for lang in langs]
    passed = [(lang, sum(1 for r in runs if r["lang"] == lang and r["checks_passed"] == r["checks_total"]))
              for lang in langs]
    total = sum(1 for r in runs if r["lang"] == "kata")
    return {
        "tokens": ("Tokens the agent used to build the app", "Total tokens for the whole task. Lower is better.",
                   pairs(runs, lambda r: r["tokens"], lambda v: "{:,.0f}".format(v)),
                   [0, 20000, 40000, 60000], lambda v: "{}k".format(v // 1000)),
        "time": ("Time the agent took, start to finish", "Wall time in seconds. Lower is better.",
                 pairs(runs, lambda r: r["seconds"], lambda v: "{:.0f} s".format(v)),
                 [0, 50, 100, 150, 200], lambda v: "{} s".format(v)),
        "apk": ("Size of the APK", "Debug APK as built by the agent. Lower is better.",
                pairs(runs, lambda r: r["apk_kb"], size),
                [0, 4000, 8000, 12000], lambda v: "{} MB".format(v // 1000)),
        "reliability": ("Runs that worked without a retry", "Out of {} runs per language. Higher is better.".format(total),
                        [("Built on the", "first attempt", [(l, n, "{} of {}".format(n, total)) for l, n in first]),
                         ("Passed every", "on-device check", [(l, n, "{} of {}".format(n, total)) for l, n in passed])],
                        list(range(total + 1)), str),
        "build": ("Build time for the expense tracker", "Measured directly, same machine, in seconds. Lower is better.",
                  [(b["step"], "", [(b["lang"], b["seconds"], "{:.1f} s".format(b["seconds"]))])
                   for b in data["build_seconds"]],
                  [0, 5, 10, 15, 20, 25], lambda v: "{} s".format(v)),
    }


def main():
    with open(os.path.join(HERE, "data.json"), encoding="utf-8") as source:
        data = json.load(source)
    for name, (title, subtitle, groups, ticks, tick_label) in figures(data).items():
        for theme in THEMES:
            path = os.path.join(HERE, "img", "{}-{}.svg".format(name, theme))
            with open(path, "w", encoding="utf-8") as target:
                target.write(draw(theme, title, subtitle, groups, ticks, tick_label))


if __name__ == "__main__":
    main()
