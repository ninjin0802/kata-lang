"""Runs the unit-converter requirements against an installed APK and prints one line per check."""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "scripts"))
import e2e

APP = "dev.kata.converter"


def main(apk, activity, serial, keep):
    e2e.SERIAL = serial
    e2e.adb("uninstall", APP, check=False)
    e2e.adb("install", "-r", apk)
    e2e.adb("shell", "am", "start", "-W", "-n", f"{APP}/{activity}")
    e2e.wait_for(lambda r: e2e.exists(r, text="Length") and e2e.exists(r, text="Swap"), "the converter to appear")

    def result(root):
        texts = [n.text for n in e2e.find_all(root) if n.text and n.text.startswith("=")]
        return texts[0] if texts else None

    def pick(row, unit):
        nodes = sorted(e2e.find_all(e2e.dump(), text=unit), key=lambda n: n.top)
        e2e.tap(nodes[row])

    def type_text(text):
        e2e.tap(e2e.find(e2e.dump(), cls="android.widget.EditText"))
        e2e.adb("shell", "input", "text", text)

    def clear():
        e2e.tap_text("Clear")

    steps = [
        ("starts with '= ?'", lambda: None, "= ?"),
        ("1500 m -> km", lambda: type_text("1500"), "= 1.5 km"),
        ("Clear empties the input", clear, "= ?"),
        ("1 mi -> km", lambda: (type_text("1"), pick(0, "mi")), "= 1.609344 km"),
        ("Swap: 1 km -> mi", lambda: e2e.tap_text("Swap"), "= 0.621371 mi"),
        ("Weight keeps the input: 1 kg -> g", lambda: e2e.tap_text("Weight"), "= 1000 g"),
        ("1 lb -> kg", lambda: (pick(0, "lb"), pick(1, "kg")), "= 0.453592 kg"),
        ("100 C -> F", lambda: (e2e.tap_text("Temperature"), clear(), type_text("100")), "= 212 F"),
        ("100 C -> K", lambda: pick(1, "K"), "= 373.15 K"),
        ("32 F -> C", lambda: (clear(), type_text("32"), pick(0, "F"), pick(1, "C")), "= 0 C"),
        ("-40 C -> F", lambda: (clear(), type_text("-40"), pick(0, "C"), pick(1, "F")), "= -40 F"),
        ("a lone minus is not a number", lambda: (clear(), type_text("-")), "= ?"),
        ("a lone point is not a number", lambda: (clear(), type_text(".")), "= ?"),
        ("0.5 C -> F after bad input", lambda: (clear(), type_text("0.5")), "= 32.9 F"),
    ]
    passed = 0
    for name, action, expected in steps:
        try:
            action()
            e2e.wait_for(lambda r: result(r) == expected, name, timeout=6)
            print(f"PASS  {name}")
            passed += 1
        except Exception as error:  # the next checks still run
            shown = None
            try:
                shown = result(e2e.dump())
            except Exception:
                pass
            print(f"FAIL  {name}: expected {expected!r}, screen shows {shown!r}")
    print(f"{passed}/{len(steps)} passed")
    if not keep:
        e2e.adb("uninstall", APP, check=False)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], len(sys.argv) > 4 and sys.argv[4] == "keep")
