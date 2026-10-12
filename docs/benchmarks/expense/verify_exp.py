"""Runs the expense-tracker requirements against an APK and prints one line per check.

Texts are compared without regard to letter case, as the requirements say.
Usage: verify_exp.py <apk> <activity> <serial> [keep]
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "scripts"))
import e2e

APP = "dev.kata.expenses"
EDIT = "android.widget.EditText"


def nodes(root, text):
    wanted = text.casefold()
    return [n for n in e2e.find_all(root) if n.text is not None and n.text.casefold() == wanted
            and n.cls != EDIT]


def has(root, text):
    return bool(nodes(root, text))


def tap_text(text, which=0):
    """Taps the which-th (top to bottom) element with this text."""
    found = sorted(nodes(e2e.dump(), text), key=lambda n: (n.top, n.left))
    if len(found) <= which:
        raise AssertionError(f"no element #{which} with text {text!r}")
    e2e.tap(found[which])


def wait(predicate, what, timeout=8):
    return e2e.wait_for(predicate, what, timeout=timeout)


def fields(root):
    return sorted(e2e.find_all(root, cls=EDIT), key=lambda n: n.top)


def type_into(index, text):
    e2e.tap(fields(e2e.dump())[index])
    time.sleep(0.4)
    e2e.adb("shell", "input", "text", text)
    time.sleep(0.4)


def add(amount, note_keys, category=None):
    """Fills the Add page and saves. Returns the note exactly as the field showed it."""
    tap_text("Add")
    wait(lambda r: has(r, "Save"), "the Add page")
    type_into(0, amount)
    type_into(1, note_keys)
    # A converting input method keeps the text "in composition" (and may cover the screen with
    # candidates) until it is committed.
    e2e.adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    time.sleep(0.6)
    note = fields(e2e.dump())[1].text.strip()
    if category:
        tap_text(category)
    tap_text("Save")
    return note


def top_of(root, text):
    return sorted(nodes(root, text), key=lambda n: n.top)[0].top


def main(apk, activity, serial, keep):
    e2e.SERIAL = serial
    e2e.adb("uninstall", APP, check=False)
    e2e.adb("install", "-r", apk)
    e2e.adb("shell", "am", "start", "-W", "-n", f"{APP}/{activity}")
    notes = {}
    results = []

    def check(name, action):
        try:
            action()
            results.append(True)
            print(f"PASS  {name}")
        except Exception as error:
            results.append(False)
            message = str(error).split(";")[0][:150]
            print(f"FAIL  {name}: {message}")

    def start_page():
        wait(lambda r: has(r, "Total: 0") and has(r, "No expenses") and has(r, "Sort: newest"), "the empty List page", 15)

    def add_validation_amount():
        tap_text("Add")
        wait(lambda r: has(r, "Save") and len(fields(r)) == 2, "the Add page with two fields")
        tap_text("Save")
        wait(lambda r: has(r, "Enter an amount greater than 0"), "the amount error")

    def add_validation_zero():
        type_into(0, "0")
        tap_text("Save")
        time.sleep(1)
        assert has(e2e.dump(), "Enter an amount greater than 0"), "0 is not accepted"

    def add_validation_note():
        tap_text("List")
        notes["blank"] = True
        tap_text("Add")
        wait(lambda r: has(r, "Save"), "the Add page")
        root = e2e.dump()
        e2e.tap(fields(root)[0])
        for _ in range(4):
            e2e.adb("shell", "input", "keyevent", "KEYCODE_DEL")
        e2e.adb("shell", "input", "text", "1200")
        tap_text("Save")
        wait(lambda r: has(r, "Enter a note"), "the note error")

    def add_first():
        # The amount typed for the note check is still there; only the note is missing.
        type_into(1, "n1")
        e2e.adb("shell", "input", "keyevent", "KEYCODE_ENTER")
        time.sleep(0.6)
        notes["first"] = fields(e2e.dump())[1].text.strip()
        tap_text("Save")
        wait(lambda r: has(r, "Total: 1200") and has(r, "Food 1200") and has(r, notes["first"]),
             "the List page with the first expense")
        assert not has(e2e.dump(), "Enter a note"), "the error text is gone"

    def form_is_reset():
        tap_text("Add")
        root = wait(lambda r: has(r, "Save"), "the Add page")
        shown = [f.text for f in fields(root)]
        assert all(not any(ch.isdigit() for ch in (t or "")) for t in shown), f"fields were emptied, got {shown}"
        tap_text("List")
        wait(lambda r: has(r, "Total: 1200"), "the List page")

    def add_second():
        notes["second"] = add("300", "n2", "Transport")
        wait(lambda r: has(r, "Total: 1500") and has(r, "Transport 300"), "the second expense")

    def newest_first():
        root = e2e.dump()
        assert top_of(root, "Transport 300") < top_of(root, "Food 1200"), "the newest expense is on top"

    def add_third():
        notes["third"] = add("5000", "n3", "Fun")
        wait(lambda r: has(r, "Total: 6500") and has(r, "Fun 5000"), "the third expense")

    def sort_largest():
        tap_text("Sort: newest")
        root = wait(lambda r: has(r, "Sort: largest"), "the sort label to change")
        order = [top_of(root, t) for t in ("Fun 5000", "Food 1200", "Transport 300")]
        assert order == sorted(order), "largest amount first"
        tap_text("Sort: largest")
        root = wait(lambda r: has(r, "Sort: newest"), "the sort label to change back")
        order = [top_of(root, t) for t in ("Fun 5000", "Transport 300", "Food 1200")]
        assert order == sorted(order), "newest first again"

    def filter_food():
        tap_text("Food")
        wait(lambda r: has(r, "Total: 1200") and has(r, "Food 1200") and not has(r, "Transport 300")
             and not has(r, "Fun 5000"), "only Food expenses")

    def filter_empty():
        tap_text("Other")
        wait(lambda r: has(r, "Total: 0") and has(r, "No expenses"), "an empty filtered list")

    def filter_all():
        tap_text("All")
        wait(lambda r: has(r, "Total: 6500") and has(r, "Transport 300") and not has(r, "No expenses"),
             "all expenses again")

    def detail_page():
        tap_text(notes["first"])
        wait(lambda r: has(r, "Note: " + notes["first"]) and has(r, "Amount: 1200") and has(r, "Category: Food")
             and has(r, "Back"), "the Detail page")
        tap_text("Back")
        wait(lambda r: has(r, "Total: 6500"), "the List page after Back")

    def detail_system_back():
        tap_text(notes["first"])
        wait(lambda r: has(r, "Amount: 1200"), "the Detail page")
        e2e.adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait(lambda r: has(r, "Total: 6500"), "the List page after the system back")

    def summary_page():
        tap_text("Summary")
        root = wait(lambda r: has(r, "Count: 3"), "the Summary page")
        for text in ("Food: 1200", "Transport: 300", "Fun: 5000", "Largest: " + notes["third"] + " 5000"):
            assert has(root, text), f"Summary shows {text!r}"
        assert not any(n.text and n.text.casefold().startswith("other:") for n in e2e.find_all(root)), \
            "a category without expenses is not listed"
        order = [top_of(root, t) for t in ("Food: 1200", "Transport: 300", "Fun: 5000")]
        assert order == sorted(order), "categories in the required order"

    def summary_ignores_filter():
        tap_text("List")
        wait(lambda r: has(r, "Sort: newest"), "the List page")
        tap_text("Food")
        wait(lambda r: has(r, "Total: 1200"), "the Food filter")
        tap_text("Summary")
        wait(lambda r: has(r, "Count: 3") and has(r, "Fun: 5000"), "Summary unaffected by the filter")
        tap_text("List")
        wait(lambda r: has(r, "Sort: newest"), "the List page")
        tap_text("All")
        wait(lambda r: has(r, "Total: 6500"), "all expenses")

    def delete_no():
        tap_text("Delete", 0)
        wait(lambda r: has(r, "Delete this expense?") and has(r, "Yes") and has(r, "No"), "the confirmation")
        tap_text("No")
        wait(lambda r: has(r, "Total: 6500") and not has(r, "Delete this expense?"), "nothing deleted")

    def delete_yes():
        tap_text("Delete", 0)
        wait(lambda r: has(r, "Delete this expense?"), "the confirmation")
        tap_text("Yes")
        wait(lambda r: has(r, "Total: 1500") and not has(r, "Fun 5000") and not has(r, "Delete this expense?"),
             "the newest expense deleted")

    def survives_restart():
        e2e.adb("shell", "am", "force-stop", APP)
        e2e.adb("shell", "am", "start", "-W", "-n", f"{APP}/{activity}")
        wait(lambda r: has(r, "Total: 1500") and has(r, "Food 1200") and has(r, "Transport 300")
             and has(r, notes["first"]), "the saved expenses after a restart", 15)

    def summary_after_delete():
        tap_text("Summary")
        root = wait(lambda r: has(r, "Count: 2"), "the Summary page")
        assert has(root, "Largest: " + notes["first"] + " 1200"), "Largest follows the deletion"
        assert not has(root, "Fun: 5000"), "the deleted category is gone"

    def empty_summary():
        tap_text("List")
        wait(lambda r: has(r, "Sort: newest"), "the List page")
        for _ in range(2):
            tap_text("Delete", 0)
            wait(lambda r: has(r, "Yes"), "the confirmation")
            tap_text("Yes")
            time.sleep(1)
        wait(lambda r: has(r, "Total: 0") and has(r, "No expenses"), "an empty list")
        tap_text("Summary")
        wait(lambda r: has(r, "Count: 0") and has(r, "Largest: -"), "the empty Summary")

    for name, action in [
        ("starts on an empty List page", start_page),
        ("Save without an amount shows the amount error", add_validation_amount),
        ("an amount of 0 is refused", add_validation_zero),
        ("Save without a note shows the note error", add_validation_note),
        ("a valid expense is saved and shown", add_first),
        ("the form is emptied after saving", form_is_reset),
        ("a second expense with another category", add_second),
        ("the newest expense comes first", newest_first),
        ("a third expense", add_third),
        ("Sort toggles between newest and largest", sort_largest),
        ("the Food filter", filter_food),
        ("a filter with no matches", filter_empty),
        ("the All filter", filter_all),
        ("Detail page and its Back", detail_page),
        ("the system back leaves the Detail page, not the app", detail_system_back),
        ("Summary page", summary_page),
        ("Summary ignores the List filter", summary_ignores_filter),
        ("Delete, answered No", delete_no),
        ("Delete, answered Yes", delete_yes),
        ("expenses survive a restart", survives_restart),
        ("Summary after a deletion", summary_after_delete),
        ("Summary with no expenses", empty_summary),
    ]:
        check(name, action)
    print(f"{sum(results)}/{len(results)} passed")
    if not keep:
        e2e.adb("uninstall", APP, check=False)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], len(sys.argv) > 4 and sys.argv[4] == "keep")
