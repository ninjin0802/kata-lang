#!/usr/bin/env python3
"""On-device checks for the kata examples (spec 08 #device).

Requires exactly one connected emulator or device. Run through scripts/e2e.sh.
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KATA = os.path.join(ROOT, "compiler", "build", "install", "kata", "bin", "kata")
ACTIVITY = "kata.gen.MainActivity"


# The device every adb call talks to; fixed at start so that a device appearing later cannot interfere.
SERIAL = None


def adb_command(*args):
    return ["adb", *(["-s", SERIAL] if SERIAL else []), *args]


def adb(*args, check=True):
    result = subprocess.run(adb_command(*args), capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stdout}{result.stderr}")
    return result.stdout


def build(example):
    """Runs `kata build` on examples/<example> and returns the APK path."""
    project = os.path.join(ROOT, "examples", example)
    result = subprocess.run([KATA, "build", project], capture_output=True, text=True)
    output = json.loads(result.stdout)
    if not output["ok"]:
        raise RuntimeError(f"kata build {example} failed:\n{result.stdout}")
    return os.path.join(project, output["artifact"])


def install_and_launch(apk, app_id):
    adb("install", "-r", apk)
    adb("shell", "am", "force-stop", app_id)
    adb("shell", "am", "start", "-W", "-n", f"{app_id}/{ACTIVITY}")
    wait_for(lambda root: any(n.get("package") == app_id for n in root.iter("node")),
             f"{app_id} to appear")


class Node:
    def __init__(self, element):
        self.element = element
        self.text = element.get("text")
        self.cls = element.get("class")
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", element.get("bounds")))
        self.left, self.top, self.right, self.bottom = x1, y1, x2, y2
        self.width, self.height = x2 - x1, y2 - y1

    @property
    def center(self):
        return (self.left + self.right) // 2, (self.top + self.bottom) // 2

    def __repr__(self):
        return f"Node({self.cls.split('.')[-1]} {self.text!r} [{self.left},{self.top}][{self.right},{self.bottom}])"


def dump():
    """The current UI hierarchy as an ElementTree root."""
    for _ in range(5):
        output = subprocess.run(adb_command("exec-out", "uiautomator", "dump", "/dev/tty"),
                                capture_output=True, text=True).stdout
        end = output.rfind("</hierarchy>")
        start = output.find("<?xml")
        if start >= 0 and end >= 0:
            root = ET.fromstring(output[start:end + len("</hierarchy>")])
            if dismiss_system_dialog(root):
                continue
            return root
        time.sleep(0.5)
    raise RuntimeError("could not dump the UI hierarchy")


# Buttons that close a dialog some other app put over the screen (seen on emulators with an old
# security patch level). Only tapped when the dialog's own package is not a kata example.
DISMISS_LABELS = ("閉じる", "Close", "Dismiss")


def dismiss_system_dialog(root):
    for element in root.iter("node"):
        if element.get("text") in DISMISS_LABELS and not element.get("package", "").startswith("dev.kata."):
            x, y = Node(element).center
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1)
            return True
    return False


def find_all(root, text=None, cls=None):
    nodes = []
    for element in root.iter("node"):
        if text is not None and element.get("text") != text:
            continue
        if cls is not None and element.get("class") != cls:
            continue
        nodes.append(Node(element))
    return nodes


def find(root, text=None, cls=None):
    nodes = find_all(root, text, cls)
    if not nodes:
        raise AssertionError(f"no node with text={text!r} class={cls!r}")
    return nodes[0]


def exists(root, text=None, cls=None):
    return bool(find_all(root, text, cls))


def wait_for(predicate, what, timeout=10):
    """Polls the UI until predicate(root) is true; returns that root."""
    deadline = time.time() + timeout
    while True:
        root = dump()
        if predicate(root):
            return root
        if time.time() > deadline:
            texts = [n.get("text") for n in root.iter("node") if n.get("text")]
            raise AssertionError(f"timed out waiting for {what}; texts on screen: {texts}")
        time.sleep(0.3)


def tap(node):
    x, y = node.center
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(text):
    tap(find(dump(), text=text))


def density():
    """Pixels per dp."""
    output = adb("shell", "wm", "density")
    override = re.search(r"Override density: (\d+)", output)
    physical = re.search(r"Physical density: (\d+)", output)
    return int((override or physical).group(1)) / 160.0


def px(dp):
    """dp to pixels, rounded the way the runtime rounds."""
    return int(dp * density() + 0.5)


class Rotation:
    """Rotates the screen for the duration of a with-block, then restores the previous settings."""

    def __enter__(self):
        self.auto = adb("shell", "settings", "get", "system", "accelerometer_rotation").strip()
        self.user = adb("shell", "settings", "get", "system", "user_rotation").strip()
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "1")
        time.sleep(2)
        return self

    def __exit__(self, *exc):
        adb("shell", "settings", "put", "system", "user_rotation", self.user if self.user.isdigit() else "0")
        adb("shell", "settings", "put", "system", "accelerometer_rotation",
            self.auto if self.auto.isdigit() else "0")
        time.sleep(2)


def check_counter():
    app_id = "dev.kata.counter"
    install_and_launch(build("counter"), app_id)
    wait_for(lambda r: exists(r, text="0"), "the counter to show 0")
    tap_text("+1")
    wait_for(lambda r: exists(r, text="1"), "the counter to show 1")
    with Rotation():
        wait_for(lambda r: exists(r, text="1"), "the counter to still show 1 after rotation")
    wait_for(lambda r: exists(r, text="1"), "the counter to still show 1 after rotating back")
    tap_text("Reset")
    wait_for(lambda r: exists(r, text="0"), "the counter to show 0 after Reset")
    adb("uninstall", app_id, check=False)


def near(actual, expected, what, tolerance=1):
    if abs(actual - expected) > tolerance:
        raise AssertionError(f"{what}: expected {expected} (±{tolerance}), got {actual}")


def screen_size():
    width, height = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups()
    return int(width), int(height)


def pixel(x, y):
    """The (r, g, b) of one screen pixel."""
    png = os.path.join(ROOT, "build", "e2e-screen.png")
    os.makedirs(os.path.dirname(png), exist_ok=True)
    with open(png, "wb") as out:
        subprocess.run(adb_command("exec-out", "screencap", "-p"), stdout=out, check=True)
    output = subprocess.run(["java", os.path.join(ROOT, "scripts", "Pixel.java"), png, str(x), str(y)],
                            capture_output=True, text=True, check=True).stdout
    r, g, b, _ = map(int, output.split())
    return r, g, b


def has_bounds(root, left, top, right, bottom):
    """Whether some node has exactly these bounds (within one pixel)."""
    for element in root.iter("node"):
        node = Node(element)
        if (abs(node.left - left) <= 1 and abs(node.top - top) <= 1
                and abs(node.right - right) <= 1 and abs(node.bottom - bottom) <= 1):
            return True
    return False


def check_styled():
    app_id = "dev.kata.styled"
    install_and_launch(build("styled"), app_id)
    root = wait_for(lambda r: exists(r, text="vcenter"), "the styled example to appear")
    screen_width, _ = screen_size()
    amber, teal, ink = (255, 193, 7), (0, 150, 136), (20, 20, 20)

    # Width / Height fix the size; Background paints behind it.
    sized = find(root, text="sized")
    near(sized.width, px(120), "Width 120dp")
    near(sized.height, px(48), "Height 48dp")
    assert pixel(sized.right - 4, sized.bottom - 4) == amber, "Background amber behind 'sized'"

    # Padding and PaddingEdges leave the given space around the child.
    padded = find(root, text="padded")
    assert has_bounds(root, padded.left - px(16), padded.top - px(16), padded.right + px(16),
                      padded.bottom + px(16)), "Padding 16dp around 'padded'"
    assert pixel(padded.left - px(8), padded.top - px(8)) == teal, "Background teal inside the padding"
    edges = find(root, text="edges")
    assert has_bounds(root, edges.left - px(8), edges.top - px(4), edges.right + px(2),
                      edges.bottom + px(1)), "PaddingEdges 8/4/2/1dp around 'edges'"

    # FillWidth, AlignEnd, CenterHorizontal use the whole width.
    fill = find(root, text="fill")
    near(fill.left, 0, "FillWidth left")
    near(fill.right, screen_width, "FillWidth right")
    near(find(root, text="end").right, screen_width, "AlignEnd right edge")
    near(find(root, text="middle").center[0], screen_width // 2, "CenterHorizontal")

    # Row: Space is a fixed gap, Expand takes what is left.
    left, after = find(root, text="left"), find(root, text="afterSpace")
    expand, right = find(root, text="expand"), find(root, text="right")
    near(after.left - left.right, px(24), "Space 24dp")
    near(expand.left, after.right, "Expand starts after 'afterSpace'")
    near(expand.right, right.left, "Expand ends at 'right'")
    near(right.right, screen_width, "Row reaches the right edge")

    # StyledText and StyledButton.
    assert find(root, text="big").height > fill.height, "StyledText 32sp is taller than default text"
    button = find(root, text="styled 0")
    assert pixel(button.left + 4, button.top + 4) == ink, "StyledButton background"

    # Expand + Center: 'center' sits in the middle of the space left in the Column.
    center, bottom, vcenter = find(root, text="center"), find(root, text="bottom"), find(root, text="vcenter")
    region_top, region_bottom = button.bottom, bottom.bottom - px(40)
    near(center.center[0], screen_width // 2, "Center horizontally")
    near(center.center[1], (region_top + region_bottom) // 2, "Center vertically", tolerance=2)

    # AlignBottom and CenterVertical inside a 40dp-high box.
    near(vcenter.center[1], bottom.bottom + px(40) // 2, "CenterVertical", tolerance=2)

    # A tap re-renders in place.
    tap(button)
    wait_for(lambda r: exists(r, text="styled 1"), "the styled button to count a tap")
    adb("uninstall", app_id, check=False)


def type_text(text):
    adb("shell", "input", "text", text)


def focus_field(index):
    """Taps the index-th input field until it has the focus."""
    for _ in range(5):
        field = find_all(dump(), cls="android.widget.EditText")[index]
        if field.element.get("focused") == "true":
            return
        tap(field)
        time.sleep(1)
    raise AssertionError(f"input field {index} did not take the focus")


def check_form():
    app_id = "dev.kata.form"
    install_and_launch(build("form"), app_id)
    root = wait_for(lambda r: len(find_all(r, cls="android.widget.EditText")) == 2, "two input fields")

    # The input method of the device may transform what is typed (for example to full-width
    # characters), so the checks compare against whatever the field shows, not the keys sent.
    def field_text(root, index):
        return find_all(root, cls="android.widget.EditText")[index].text

    def follows(root):
        return exists(root, text="Hello, " + field_text(root, 0))

    # Typing updates the text that depends on the field; focus survives the re-render.
    focus_field(0)
    type_text("123")
    root = wait_for(lambda r: field_text(r, 0) not in ("", "Name") and follows(r), "the greeting to follow the name")
    first = field_text(root, 0)
    type_text("456")
    wait_for(lambda r: len(field_text(r, 0)) > len(first) and field_text(r, 0).startswith(first) and follows(r),
             "more typing to reach the same field without tapping it again")
    # A converting input method (such as Japanese) shows a candidate window over the next field
    # until the text is committed; Enter commits it.
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    time.sleep(1)
    name = field_text(dump(), 0)

    # onChange that does not store the value: the field goes back, and nothing loops.
    focus_field(1)
    type_text("1234")
    wait_for(lambda r: len(field_text(r, 1)) == 3 and field_text(r, 1) != "Code",
             "the code field to keep only three characters")
    wait_for(lambda r: field_text(r, 0) == name, "the name to be unaffected")

    # The list scrolls; the last row is not on screen at first.
    root = dump()
    assert exists(root, text="Row 1"), "Row 1 is visible"
    assert not exists(root, text="Row 50"), "Row 50 is off screen before scrolling"
    row = find(root, text="Row 1")
    rows = [n for n in find_all(root) if n.text and n.text.startswith("Row ")]
    x, top, bottom = row.center[0], rows[0].center[1], rows[-1].center[1]
    for _ in range(15):
        if exists(dump(), text="Row 50"):
            break
        adb("shell", "input", "swipe", str(x), str(bottom), str(x), str(top), "200")
        time.sleep(0.5)
    wait_for(lambda r: exists(r, text="Row 50"), "Row 50 after scrolling")

    # A re-render keeps the scroll position.
    tap_text("Taps: 0")
    root = wait_for(lambda r: exists(r, text="Taps: 1"), "the tap to be counted")
    assert exists(root, text="Row 50"), "Row 50 is still visible after the re-render"
    adb("uninstall", app_id, check=False)


def check_calculator():
    app_id = "dev.kata.calculator"
    install_and_launch(build("calculator"), app_id)
    root = wait_for(lambda r: exists(r, text="=", cls="android.widget.Button"), "the calculator keys")
    keys = {n.text: n for n in find_all(root, cls="android.widget.Button")}

    def shows(text):
        return lambda r: exists(r, text=text, cls="android.widget.TextView")

    def press(sequence):
        for label in sequence:
            tap(keys[label])

    def expect(sequence, result):
        press("C")
        press(sequence)
        wait_for(shows(result), f"{' '.join(sequence)} to show {result}")

    wait_for(shows("0"), "the display to start at 0")
    expect(["1", ".", "5", "+", "2", ".", "2", "5", "="], "3.75")
    expect(["7", "÷", "2", "="], "3.5")
    expect(["0", ".", "1", "+", "0", ".", "2", "="], "0.3")
    expect(["6", "×", "7", "="], "42")
    expect(["9", "-", "1", "0", "="], "-1")
    expect(["2", "+", "3", "×", "4", "="], "20")
    expect(["1", ".", ".", "5"], "1.5")
    expect(["5", "÷", "0", "="], "Error")
    press(["7"])
    wait_for(shows("Error"), "keys to be ignored after an error")
    press("C")
    wait_for(shows("0"), "C to reset the display")

    # The keys share each row equally and the rows span the screen.
    screen_width, _ = screen_size()
    near(keys["7"].width, keys["÷"].width, "keys in a row have equal width", tolerance=2)
    near(keys["÷"].right, screen_width, "the key rows reach the right edge")
    near(keys["="].width, screen_width, "the = key spans the screen")
    adb("uninstall", app_id, check=False)


def check_controls():
    app_id = "dev.kata.controls"
    install_and_launch(build("controls"), app_id)
    root = wait_for(lambda r: exists(r, text="taps=0"), "the controls example to appear")
    screen_width, _ = screen_size()

    # Checkbox and Switch report the new state and show it.
    tap(find(root, cls="android.widget.CheckBox"))
    wait_for(lambda r: exists(r, text="done=yes"), "the checkbox to turn on")
    tap(find(dump(), cls="android.widget.CheckBox"))
    wait_for(lambda r: exists(r, text="done=no"), "the checkbox to turn off")
    tap(find(dump(), cls="android.widget.Switch"))
    wait_for(lambda r: exists(r, text="dark=yes"), "the switch to turn on")

    # Slider spans the width; tapping near its right end raises the value, and Progress follows.
    slider = find(dump(), cls="android.widget.SeekBar")
    near(slider.width, screen_width, "Slider fills the width")
    adb("shell", "input", "tap", str(slider.left + int(slider.width * 0.8)), str(slider.center[1]))
    wait_for(lambda r: any(exists(r, text=f"level={n}") for n in (7, 8, 9)), "the slider to move to about 8")
    bars = find_all(dump(), cls="android.widget.ProgressBar")
    assert len(bars) == 2, f"expected Progress and Loading, found {bars}"
    near(bars[0].width, screen_width, "Progress fills the width")

    # Divider is a 1dp line across the width.
    assert has_bounds(dump(), 0, bars[0].bottom, screen_width, bars[0].bottom + px(1)), "Divider below Progress"

    # TextArea is taller than a single-line field and reports its text.
    area = find(dump(), cls="android.widget.EditText")
    tap(area)
    type_text("12345")
    wait_for(lambda r: not exists(r, text="notes=0"), "the text area to report its text")
    adb("shell", "input", "keyevent", "KEYCODE_ESCAPE")

    # Tappable makes the rounded box respond; Rounded paints inside but not in the corner.
    label = find(dump(), text="taps=0")
    tap(label)
    root = wait_for(lambda r: exists(r, text="taps=1"), "the tappable box to count a tap")
    label = find(root, text="taps=1")
    box_left, box_top = label.left - px(16), label.top - px(16)
    teal = (0, 150, 136)
    assert pixel(box_left + px(8), label.center[1]) == teal, "Rounded paints its color"
    assert pixel(box_left + 1, box_top + 1) != teal, "Rounded leaves the corner unpainted"
    adb("uninstall", app_id, check=False)


CHECKS = {
    "calculator": check_calculator,
    "controls": check_controls,
    "counter": check_counter,
    "styled": check_styled,
    "form": check_form,
}


def main(argv):
    global SERIAL
    devices = [line.split()[0] for line in adb("devices").splitlines()[1:] if line.strip().endswith("device")]
    SERIAL = os.environ.get("ANDROID_SERIAL")
    if SERIAL is None:
        if len(devices) != 1:
            print(f"expected exactly one connected device, found {len(devices)}; set ANDROID_SERIAL to choose",
                  file=sys.stderr)
            return 2
        SERIAL = devices[0]
    names = argv or list(CHECKS)
    for name in names:
        print(f"== {name}", flush=True)
        CHECKS[name]()
        print(f"ok {name}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
