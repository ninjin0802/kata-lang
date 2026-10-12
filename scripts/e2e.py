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


def adb(*args, check=True):
    result = subprocess.run(["adb", *args], capture_output=True, text=True)
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
        output = subprocess.run(["adb", "exec-out", "uiautomator", "dump", "/dev/tty"],
                                capture_output=True, text=True).stdout
        end = output.rfind("</hierarchy>")
        start = output.find("<?xml")
        if start >= 0 and end >= 0:
            return ET.fromstring(output[start:end + len("</hierarchy>")])
        time.sleep(0.5)
    raise RuntimeError("could not dump the UI hierarchy")


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


CHECKS = {
    "counter": check_counter,
}


def main(argv):
    devices = [line for line in adb("devices").splitlines()[1:] if line.strip().endswith("device")]
    if len(devices) != 1:
        print(f"expected exactly one connected device, found {len(devices)}", file=sys.stderr)
        return 2
    names = argv or list(CHECKS)
    for name in names:
        print(f"== {name}", flush=True)
        CHECKS[name]()
        print(f"ok {name}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
