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


def check_widgets():
    app_id = "dev.kata.widgets"
    install_and_launch(build("widgets"), app_id)
    root = wait_for(lambda r: exists(r, text="card"), "the widgets example to appear")
    screen_width, _ = screen_size()

    # RadioGroup reports the index of the chosen option.
    tap(find(root, text="Gamma"))
    wait_for(lambda r: exists(r, text="choice=2"), "the radio group to choose Gamma")
    tap(find(dump(), text="Beta"))
    wait_for(lambda r: exists(r, text="choice=1"), "the radio group to choose Beta")

    # Dropdown opens a menu and reports the chosen index; nothing is reported before a choice.
    assert exists(dump(), text="size=0"), "the dropdown did not report its initial selection"
    tap(find(dump(), text="Small"))
    tap(find(wait_for(lambda r: exists(r, text="Large"), "the dropdown menu"), text="Large"))
    wait_for(lambda r: exists(r, text="size=2"), "the dropdown to choose Large")

    # Rating: tapping near the right end selects four or five stars.
    stars = find(dump(), cls="android.widget.RatingBar")
    adb("shell", "input", "tap", str(stars.left + int(stars.width * 0.85)), str(stars.center[1]))
    wait_for(lambda r: exists(r, text="stars=4") or exists(r, text="stars=5"), "the rating to change")

    # Grid: two equal columns, the third cell starts a new row.
    root = dump()
    cell1, cell2, cell3 = find(root, text="cell1"), find(root, text="cell2"), find(root, text="cell3")
    near(cell1.left, 0, "first cell at the left edge")
    near(cell2.left, screen_width // 2, "second cell starts at the middle", tolerance=2)
    near(cell1.width, cell2.width, "cells have equal width", tolerance=2)
    near(cell3.left, 0, "third cell wraps to a new row")
    assert cell3.top >= cell1.bottom, "third cell is below the first row"

    # Stack: the text is drawn over the box, from the same corner.
    over = find(root, text="over")
    assert has_bounds(root, over.left, over.top, over.left + px(120), over.top + px(40)), "Stack box under 'over'"
    assert pixel(over.left + px(100), over.top + px(30)) == (255, 193, 7), "the box shows around the text"

    # Border: a 2dp line around the padded text.
    bordered = find(root, text="bordered")
    left, top = bordered.left - px(8) - px(2), bordered.top - px(8) - px(2)
    assert pixel(left + 1, bordered.center[1]) == (0, 150, 136), "Border line on the left edge"
    assert pixel(left + px(2) + 2, bordered.center[1]) != (0, 150, 136), "inside of the border is not painted"
    adb("uninstall", app_id, check=False)


def check_shell():
    app_id = "dev.kata.shell"
    install_and_launch(build("shell"), app_id)
    root = wait_for(lambda r: exists(r, text="tab=0"), "the shell example to appear")
    screen_width, screen_height = screen_size()

    # TopBar spans the width; Tabs and BottomNav split it equally and report the tapped index.
    near(find(root, text="Home", cls="android.widget.TextView").width, screen_width, "TopBar fills the width")
    one, two, three = find(root, text="One"), find(root, text="Two"), find(root, text="Three")
    near(one.width, two.width, "tabs have equal width", tolerance=2)
    near(three.right, screen_width, "tabs reach the right edge")
    tap(two)
    wait_for(lambda r: exists(r, text="tab=1"), "the second tab")
    me = find(dump(), text="Me")
    assert me.top > screen_height // 2, "BottomNav sits in the lower half"
    tap(me)
    wait_for(lambda r: exists(r, text="nav=2"), "the third destination")

    # Chip toggles, Fab counts into the Badge.
    tap(find(dump(), text="Pick me"))
    wait_for(lambda r: exists(r, text="picked=yes"), "the chip to be selected")
    fab = find(dump(), text="+")
    near(fab.width, px(56), "Fab is 56dp wide")
    assert fab.right > screen_width - px(40) and fab.bottom < me.top, "Fab floats above the bottom bar, at the right"
    tap(fab)
    tap(fab)
    wait_for(lambda r: exists(r, text="2"), "the badge to show 2")

    # Label is limited to one line.
    label = [n for n in find_all(dump()) if n.text and n.text.startswith("A long bold label")][0]
    assert label.height < px(40), f"Label is one line high, got {label.height}px"

    # BackBar's arrow and the system back gesture both run the back action; the app stays open.
    tap(find(dump(), text="Open detail"))
    wait_for(lambda r: exists(r, text="detail page"), "the detail page")
    tap(find(dump(), text="\u2190"))
    wait_for(lambda r: exists(r, text="tab=1"), "the arrow to go back, keeping the state")
    tap(find(dump(), text="Open detail"))
    wait_for(lambda r: exists(r, text="detail page"), "the detail page again")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for(lambda r: exists(r, text="tab=1"), "the system back to return to the home page")
    adb("uninstall", app_id, check=False)


def check_overlays():
    app_id = "dev.kata.overlays"
    install_and_launch(build("overlays"), app_id)
    wait_for(lambda r: exists(r, text="answer=none"), "the overlays example to appear")

    # Alert: both buttons close it and report which one was pressed.
    tap_text("Ask")
    wait_for(lambda r: exists(r, text="This cannot be undone."), "the alert")
    tap_text("Delete")
    wait_for(lambda r: exists(r, text="answer=delete"), "the confirm action")
    tap_text("Ask")
    wait_for(lambda r: exists(r, text="Delete?"), "the alert again")
    tap_text("Keep")
    wait_for(lambda r: exists(r, text="answer=keep"), "the cancel action")

    # BottomSheet and Modal show a kata-built panel whose button works.
    for opener, body in (("Sheet", "sheet body"), ("Modal", "modal body")):
        tap_text(opener)
        wait_for(lambda r: exists(r, text=body), f"the {opener.lower()}")
        tap_text("Close")
        wait_for(lambda r: exists(r, text="answer=keep") and not exists(r, text=body), f"the {opener.lower()} to close")
    # The system back gesture dismisses a sheet through onDismiss.
    tap_text("Sheet")
    wait_for(lambda r: exists(r, text="sheet body"), "the sheet")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for(lambda r: exists(r, text="answer=keep"), "back to dismiss the sheet")

    # Snackbar: its action runs and removes it; left alone, it asks to be dismissed.
    tap_text("Notify")
    # The bar leaves by itself after four seconds, so the action is tapped from the same dump.
    tap(find(wait_for(lambda r: exists(r, text="Item removed"), "the snackbar"), text="Undo"))
    wait_for(lambda r: exists(r, text="undone=1") and not exists(r, text="Item removed"), "the undo action")
    tap_text("Notify")
    wait_for(lambda r: exists(r, text="Item removed"), "the snackbar again")
    wait_for(lambda r: not exists(r, text="Item removed"), "the snackbar to time out", timeout=12)

    # LazyList: a thousand rows, only the visible ones exist; rows respond to taps after scrolling.
    root = dump()
    rows = [n for n in find_all(root) if n.text and n.text.startswith("Item ")]
    assert exists(root, text="Item 0") and len(rows) < 60, f"only visible rows are built, found {len(rows)}"
    # Start well above the bottom edge: a swipe from the very bottom is the system's "go home" gesture.
    x, top, bottom = rows[0].center[0], rows[0].center[1], rows[-4].center[1]
    for _ in range(3):
        adb("shell", "input", "swipe", str(x), str(bottom), str(x), str(top), "200")
        time.sleep(0.7)
    def item_rows(r):
        return [n for n in find_all(r) if n.text and n.text.startswith("Item ")]

    # A dump taken while the list is still moving can come back without rows; wait until it settles.
    root = wait_for(lambda r: not exists(r, text="Item 0") and len(item_rows(r)) > 3, "the list to scroll")
    rows = item_rows(root)
    target = rows[len(rows) // 2]
    tap(target)
    number = target.text.split()[1]
    root = wait_for(lambda r: exists(r, text=f"picked={number}"), f"row {number} to be picked")
    assert exists(root, text=target.text), "the list keeps its position after the re-render"
    adb("uninstall", app_id, check=False)


def check_media():
    app_id = "dev.kata.media"
    install_and_launch(build("media"), app_id)
    root = wait_for(lambda r: exists(r, text="choice=0"), "the media example to appear")

    # Image: one image pixel is one dp, and the picture's own color is shown.
    photo, dot = find_all(root, cls="android.widget.ImageView")[:2]
    near(photo.width, px(96), "Image width in dp")
    near(photo.height, px(64), "Image height in dp")
    assert pixel(*photo.center) == (30, 144, 255), "Image shows the asset"
    # Icon: a square of the given size, painted in the given color.
    near(dot.width, px(48), "Icon size")
    near(dot.height, px(48), "Icon size")
    assert pixel(*dot.center) == (0, 150, 136), "Icon is tinted"

    # Menu: the button opens a pop-up and reports the chosen index.
    tap_text("More")
    tap(find(wait_for(lambda r: exists(r, text="Rename"), "the pop-up menu"), text="Rename"))
    wait_for(lambda r: exists(r, text="choice=1"), "the menu choice")

    # DatePicker: tapping another day of the shown month reports the new date.
    tap_text("Date")
    root = wait_for(lambda r: exists(r, text="date=2026-10-12"), "the date page")
    tap(find(root, text="20"))
    wait_for(lambda r: exists(r, text="date=2026-10-20"), "the picked date")

    # TimePicker: shows the stored time; picking an hour on the dial reports it.
    tap_text("Time")
    root = wait_for(lambda r: exists(r, text="time=10:30"), "the time page")
    three = [n for n in find_all(root) if n.element.get("content-desc") == "3"][0]
    tap(three)
    wait_for(lambda r: exists(r, text="time=3:30"), "the picked hour, with the minute kept")
    adb("uninstall", app_id, check=False)


def close_to(actual, expected, tolerance=6):
    return all(abs(a - e) <= tolerance for a, e in zip(actual, expected))


def check_effects():
    app_id = "dev.kata.effects"
    install_and_launch(build("effects"), app_id)
    root = wait_for(lambda r: exists(r, text="blend"), "the effects example to appear")
    purple = (123, 31, 162)

    # Opacity blends with the white page; Rotate and Scale change what is drawn, around the center.
    plain, faded = find(root, text="plain"), find(root, text="faded")
    assert pixel(plain.right - 4, plain.bottom - 4) == (0, 0, 255), "the plain box is solid blue"
    assert close_to(pixel(faded.right - 4, faded.bottom - 4), (127, 127, 255)), "Opacity 50% over white"
    turned, big = find(root, text="turned"), find(root, text="big")
    near(turned.width, plain.height, "Rotate 90 swaps width and height", tolerance=2)
    near(turned.height, plain.width, "Rotate 90 swaps width and height", tolerance=2)
    # Scale draws beyond the box's own space, and the surrounding components do not cut it off.
    center_x, center_y = plain.left + px(30) + px(40), big.center[1]
    assert pixel(center_x - px(70), center_y) == (0, 0, 255), "Scale 200% reaches 70dp left of the center"
    assert pixel(center_x + px(70), center_y) == (0, 0, 255), "Scale 200% reaches 70dp right of the center"
    assert pixel(center_x - px(90), center_y) != (0, 0, 255), "Scale 200% ends 80dp from the center"

    # Gradient runs from the first color at the top to the second at the bottom.
    blend = find(root, text="blend")
    top, bottom = pixel(blend.center[0], blend.top + 2), pixel(blend.center[0], blend.bottom - 2)
    assert top[0] > 230 and top[1] < 25, f"Gradient starts red, got {top}"
    assert bottom[1] > 230 and bottom[0] < 25, f"Gradient ends green, got {bottom}"

    # Theme: the accent reaches the Fab.
    fab = find(root, text="+")
    assert pixel(fab.left + fab.width // 2, fab.top + 8) == purple, "Fab uses the Theme accent"

    # Disabled swallows the tap; the same button outside it works.
    tap_text("Touch")
    root = wait_for(lambda r: exists(r, text="taps=0"), "the touch page")
    tap(find(root, text="Blocked"))
    time.sleep(1)
    assert exists(dump(), text="taps=0"), "a Disabled button does nothing"
    tap_text("Open")
    wait_for(lambda r: exists(r, text="taps=1"), "the enabled button")

    # Swipeable and LongPress.
    # The flick stays inside the component: its text plus the 24dp padding around it.
    area = find(dump(), text="swipe=none")
    # It also stays clear of the screen's left edge, where a swipe is the system's back gesture.
    y, right, left = area.center[1], area.right + px(20), area.left + px(10)
    adb("shell", "input", "swipe", str(right), str(y), str(left), str(y), "120")
    wait_for(lambda r: exists(r, text="swipe=left"), "a swipe to the left")
    adb("shell", "input", "swipe", str(left), str(y), str(right), str(y), "120")
    wait_for(lambda r: exists(r, text="swipe=right"), "a swipe to the right")
    x, y = find(dump(), text="held=0").center
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
    wait_for(lambda r: exists(r, text="held=1"), "a long press")

    # SecretField hides its text but reports it.
    fields = find_all(dump(), cls="android.widget.EditText")
    assert fields[0].element.get("password") == "true", "SecretField is a password field"
    tap(fields[0])
    type_text("1234")
    wait_for(lambda r: not exists(r, text="secret=0"), "the secret field to report its text")
    adb("shell", "input", "keyevent", "KEYCODE_ESCAPE")

    # Animated: the row arrives (after its animation) and leaves again.
    tap_text("Toggle")
    wait_for(lambda r: exists(r, text="extra row"), "the extra row to appear")
    tap_text("Toggle")
    wait_for(lambda r: not exists(r, text="extra row"), "the extra row to leave")

    # WebView renders a page; using it gave the app the internet permission.
    tap_text("Web")
    wait_for(lambda r: any("kata-web" in (n.get("text") or "") + (n.get("content-desc") or "")
                           for n in r.iter("node")), "the web page", timeout=20)
    assert "android.permission.INTERNET" in adb("shell", "dumpsys", "package", app_id), "internet permission"
    adb("uninstall", app_id, check=False)


def check_data():
    app_id = "dev.kata.data"
    adb("uninstall", app_id, check=False)
    install_and_launch(build("data"), app_id)
    wait_for(lambda r: exists(r, text="count=0"), "the data example to appear")

    # Store: the value survives the app being stopped and started again.
    tap_text("Add")
    tap_text("Add")
    wait_for(lambda r: exists(r, text="count=2"), "the count to reach 2")
    adb("shell", "am", "force-stop", app_id)
    adb("shell", "am", "start", "-W", "-n", f"{app_id}/{ACTIVITY}")
    wait_for(lambda r: exists(r, text="count=2"), "the stored count after a restart")

    # Clock gives a plausible year; Timer keeps ticking by itself.
    root = dump()
    year = [n.text for n in find_all(root) if n.text and n.text.startswith("year=")][0]
    assert 2024 <= int(year.split("=")[1]) < 2200, year

    def ticks(r):
        return int([n.text for n in find_all(r) if n.text and n.text.startswith("ticks=")][0].split("=")[1])

    before = ticks(root)
    # The example ticks every two seconds: a screen that changes more often never becomes idle,
    # and the UI dump used by these checks waits for idle.
    wait_for(lambda r: ticks(r) >= before + 2, "the timer to tick two more times", timeout=25)

    # Request: a failed exchange reports through onError and the component goes away.
    tap_text("Load")
    wait_for(lambda r: exists(r, text="status=error"), "the failed request to report an error", timeout=30)
    assert "android.permission.INTERNET" in adb("shell", "dumpsys", "package", app_id), "internet permission"
    adb("uninstall", app_id, check=False)


CHECKS = {
    "calculator": check_calculator,
    "data": check_data,
    "effects": check_effects,
    "media": check_media,
    "shell": check_shell,
    "overlays": check_overlays,
    "widgets": check_widgets,
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
