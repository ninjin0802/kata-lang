# kata authoring guide

Everything needed to write a kata app. Derived from `docs/spec/` (the source of truth); if they disagree, the spec wins.

## Commands

```
kata check <project>   # JSON diagnostics only
kata build <project>   # also writes <project>/build/app.apk (needs ANDROID_HOME)
```

Output is one JSON object: `{"ok", "artifact", "diagnostics": [{code, message, file, line, column, endLine, endColumn, expected, found, fix}]}`. A non-null `fix` (`line/column/endLine/endColumn/replacement`) can be applied as is. Exit code 0 = ok, 1 = diagnostics.

## Project

One file: `<project>/src/app/<name>.kata`, starting with `module app.<name>;` (module = path under `src/`). No config files.

## Complete example

```
module app.tally;

import kata.ui.Button;
import kata.ui.Column;
import kata.ui.Expand;
import kata.ui.Row;
import kata.ui.Screen;
import kata.ui.Text;
import kata.ui.Ui;

// A button that carries a value: callbacks take no arguments, so each button gets an object.
class Step {
  let tally: Tally;
  let amount: Int;

  fn press(self) -> Unit {
    self.tally.add(amount: self.amount);
  }
}

class Tally implements Screen {
  var total: Int = 0;
  var history: List<Int> = [];

  fn view(self) -> Ui {
    return Column {
      children: [
        Text { text: "Total: ".concat(other: self.total.toString()) },
        Row { children: [self.step(amount: 1), self.step(amount: 10)] },
        Button { label: "Reset", onTap: self.reset },
      ],
    };
  }

  fn step(self, amount: Int) -> Ui {
    return Expand {
      child: Button { label: amount.toString(), onTap: Step { tally: self, amount: amount }.press },
    };
  }

  fn add(self, amount: Int) -> Unit {
    self.total = self.total + amount;
    self.history = self.history.append(item: amount);
  }

  fn reset(self) -> Unit {
    self.total = 0;
    self.history = [];
  }
}

app {
  id: "dev.example.tally",
  name: "Tally",
  root: Tally {},
}
```

## Rules that differ from Kotlin/Swift/Rust

- **Everything is typed explicitly**: fields, parameters, return types, locals (`let x: Int = 1;`), `for` variables. No inference.
- **Every argument is named and in declaration order**: `self.add(amount: 1)`, `Text { text: "a" }`. Trailing commas are allowed.
- **`self.` is mandatory** for own fields and methods; the first parameter of every method is `self`. There are no free functions and no static methods.
- **`;` ends** simple statements and field declarations. `if`, `while`, `for` need parentheses and braces; `if` is a statement, not an expression.
- **No operator precedence.** Only one kind of binary operator per unparenthesized expression. `+ * && ||` may repeat (`a + b + c`); every other operator appears once. Write `(a + b) * c`, `(a < b) && (c < d)`, `(a - b) - c`.
- **No null, no exceptions, no inheritance, no overloading, no default arguments, no shadowing, no lambdas/closures, no implicit conversions.**
- **File order is fixed**: `module`, then `import`s, then `class`es, then one `app` block. Inside a class: all fields, then all methods.
- **Imports**: one name per line, only from `kata.ui` (components) and `kata.sys` (`Store`, `Clock`); every such name used must be imported and every import must be used. `Int Float Bool String Unit Action Handler List` need no import.
- **`return`**: non-`Unit` methods must end every path with `return expr;` (loops do not count as ending a path). In `Unit` methods use bare `return;` to exit early; nothing may follow a `return` in the same block.
- A statement that is only an expression must be a call returning `Unit` (results cannot be discarded).
- Identifiers: values/methods `lowerCamel`, types `UpperCamel`, letters and digits only (no `_`). Comments: `//` only.
- Reserved words (cannot be names): `module import class implements let var fn self return if else while for in true false app interface enum match break continue`.

## Types and operators

| Type | Notes |
|---|---|
| `Int` | 64-bit, wraps on overflow. Literals `0`, `42`. `/` truncates toward zero; dividing by zero crashes the app. |
| `Float` | 64-bit IEEE. Literals need digits on both sides: `0.5`, `3.0`. Division by zero gives infinity, not a crash. |
| `Bool` | `true`, `false` |
| `String` | Literals `"..."` with `\" \\ \n \t`; any Unicode text is allowed inside. No interpolation. |
| `List<T>` | Immutable. Literals `[a, b]`, `[]` — only where the type is known from context (a typed variable, argument, field or return). |
| `Action` | A reference to a method `fn m(self) -> Unit`, written `obj.m` (no parentheses). |
| `Handler<T>` | A reference to a method `fn m(self, x: T) -> Unit`, written `obj.m`. |
| `Unit` | Return type only. |
| classes | Reference semantics. |

- Arithmetic `+ - * / %` and comparison `< <= > >=`: both sides `Int`, or both `Float`. Never mixed — convert with `toFloat()` / `toInt()`.
- `== !=`: both sides the same type among `Int Float Bool String` (value comparison). Objects and lists cannot be compared.
- `&& ||` short-circuit; unary `!` and `-`.
- `+` does not join strings; use `concat`.

Built-in methods (these are all of them):

| On | Method |
|---|---|
| `Int` | `toString() -> String`, `toFloat() -> Float` |
| `Float` | `toString() -> String` — a decimal that reads back as exactly the same number, never an exponent, always with a point (`3.0`, `0.1`; `0.1 + 0.2` prints `0.30000000000000004`, so `round` first when showing results); `toInt() -> Int` (truncates); `round(decimals: Int) -> Float` |
| `String` | `concat(other: String) -> String`, `length() -> Int`, `contains(part: String) -> Bool`, `startsWith(prefix: String) -> Bool`, `endsWith(suffix: String) -> Bool`, `indexOf(part: String) -> Int` (-1 if absent), `substring(start: Int, end: Int) -> String` (end exclusive; out of range crashes), `split(separator: String) -> List<String>`, `trim() -> String`, `toUpper() -> String`, `toLower() -> String`, `replace(part: String, with: String) -> String`, `toInt() -> Int` and `toFloat() -> Float` (text that is not a number crashes the app) |
| `List<T>` | `size() -> Int`, `get(index: Int) -> T` (out of range crashes), `append(item: T) -> List<T>`, `removeAt(index: Int) -> List<T>`, `set(index: Int, item: T) -> List<T>` — lists never change; each returns a new list |

Positions and lengths in strings count Unicode characters (code points).

## Classes

```
class Point {
  let x: Int;            // no initializer: must be given at construction
  var hits: Int = 0;     // initializer: cannot be given at construction; may not use self
  fn sum(self, extra: Int) -> Int {
    return self.x + extra;
  }
}
let p: Point = Point { x: 1 };
```

- `let` fields never change; `var` fields change only through `self.field = ...;` inside the class's own methods. Fields are readable from anywhere (`p.x`).
- Construction lists exactly the fields without initializers, in declaration order.
- Field and method names share one namespace per class.
- Locals: `let` (fixed) or `var` (reassignable); parameters and `for` variables are fixed.

## Control flow

```
if (a < b) { ... } else if (a == b) { ... } else { ... }
while (i < 10) { i = i + 1; }
for (item: Int in self.history) { total = total + item; }
```

No `break` / `continue`. To loop a number of times, use a `var` counter with `while`.

## UI

A screen is a class that `implements Screen` and defines `fn view(self) -> Ui`. State lives in its `var` fields. After every `onTap` / `onChange` the runtime calls `view` again and updates only what changed. Do not modify fields inside `view`. To reuse UI, write methods that return `Ui`.

Components (all in `kata.ui`; fields in this order, all required):

| Component | Fields |
|---|---|
| `Column`, `Row` | `children: List<Ui>` — stacked top-to-bottom / left-to-right |
| `Grid` | `columns: Int, children: List<Ui>` — equal-width columns, filled row by row, full width |
| `Stack` | `children: List<Ui>` — on top of each other, later ones in front |
| `Text` | `text: String` |
| `Button` | `label: String, onTap: Action` |
| `TextField` | `value: String, hint: String, onChange: Handler<String>` — single line; store the new text in `onChange` or the field reverts |
| `TextArea` | `value: String, hint: String, lines: Int, onChange: Handler<String>` — multi-line, `lines` ≥ 1 |
| `Checkbox` | `label: String, checked: Bool, onChange: Handler<Bool>` |
| `Switch` | `label: String, on: Bool, onChange: Handler<Bool>` |
| `Slider` | `value: Int, max: Int, onChange: Handler<Int>` — 0..`max`, `max` ≥ 1, full width; `onChange` fires continuously while dragging |
| `RadioGroup` | `options: List<String>, selected: Int, onChange: Handler<Int>` — `selected` is an index, -1 = none |
| `Dropdown` | `options: List<String>, selected: Int, onChange: Handler<Int>` — at least one option; `selected` is an index |
| `Rating` | `value: Int, max: Int, onChange: Handler<Int>` — stars |
| `Progress` | `value: Int, max: Int` — horizontal bar, full width |
| `Loading` | (no fields: `Loading {}`) — spinner |
| `Divider` | (no fields: `Divider {}`) — 1dp line, full width |
| `Tappable` | `onTap: Action, child: Ui` — makes any component respond to a tap |
| `Scroll`, `HorizontalScroll` | `child: Ui` |
| `StyledText` | `text: String, size: Int, color: Color` — size in sp; there is no default color |
| `StyledButton` | `label: String, onTap: Action, textColor: Color, background: Color` |
| `Color` | `red: Int, green: Int, blue: Int, alpha: Int` — each 0–255, alpha 255 = opaque. A value, not a `Ui`. |
| `Padding` | `all: Int, child: Ui` (dp) |
| `PaddingEdges` | `left: Int, top: Int, right: Int, bottom: Int, child: Ui` (dp) |
| `Background` | `color: Color, child: Ui` |
| `Border` | `width: Int, color: Color, radius: Int, child: Ui` — a line around the child (dp) |
| `Card` | `child: Ui` — raised white surface with rounded corners |
| `Rounded` | `radius: Int, color: Color, child: Ui` — background with rounded corners (dp); clips the child |
| `Width`, `Height` | `dp: Int, child: Ui` — fixed size |
| `FillWidth`, `FillHeight` | `child: Ui` — take all the space offered on that axis |
| `Expand` | `child: Ui` — as a direct child of a `Row`/`Column`, takes the remaining length along it; several `Expand`s share it equally, whatever their contents, and each child is stretched to its share |
| `Label` | `text: String, size: Int, color: Color, bold: Bool, italic: Bool, maxLines: Int` — full text control; `maxLines` ≥ 1 |
| `TopBar` | `title: String` — title bar, full width (apps have no title bar unless you add one) |
| `BackBar` | `title: String, onBack: Action` — title bar with a back arrow |
| `Tabs`, `BottomNav` | `labels: List<String>, selected: Int, onChange: Handler<Int>` — equal-width segments, full width |
| `BackHandler` | `onBack: Action, child: Ui` — while shown, the system back gesture runs `onBack` instead of closing the app |
| `Fab` | `label: String, onTap: Action` — round floating button; position it with `Stack` + `AlignBottom`/`AlignEnd` |
| `Chip` | `label: String, selected: Bool, onTap: Action` |
| `Badge` | `count: Int` |
| `Alert` | `title: String, message: String, confirmLabel: String, onConfirm: Action, cancelLabel: String, onCancel: Action` |
| `Modal`, `BottomSheet` | `child: Ui, onDismiss: Action` |
| `Snackbar` | `message: String, actionLabel: String, onAction: Action, onDismiss: Action` — `onDismiss` runs after 4 s |
| `LazyList` | `items: Items` — builds only the visible rows; full width, give it a height with `Expand` |
| `Image` | `name: String` — a file under `<project>/assets/` (PNG, JPEG, WebP); 1 image pixel = 1 dp, wrap in `Width`/`Height` to scale. A literal name that does not exist is a compile error. |
| `Icon` | `name: String, size: Int, color: Color` — a single-color asset drawn as a `size`-dp square in `color` |
| `Menu` | `label: String, options: List<String>, onSelect: Handler<Int>` — button that opens a pop-up menu |
| `DatePicker` | `value: Date, onChange: Handler<Date>` — `Date { year, month, day }`, month 1–12 |
| `TimePicker` | `value: Time, onChange: Handler<Time>` — `Time { hour, minute }`, hour 0–23 |
| `SecretField`, `NumberField` | `value: String, hint: String, onChange: Handler<String>` — password input / numeric keyboard (value stays text) |
| `Theme` | `accent: Color, child: Ui` — put it outermost; sets the color of controls, tabs, `Fab`, `Chip` |
| `Opacity` | `percent: Int, child: Ui` — 0–100 |
| `Rotate` | `degrees: Int, child: Ui` — clockwise, around the center; layout space unchanged |
| `Scale` | `percent: Int, child: Ui` — 100 = normal; layout space unchanged |
| `Gradient` | `from: Color, to: Color, child: Ui` — top-to-bottom background |
| `Disabled` | `child: Ui` — dimmed, ignores touches |
| `Swipeable` | `onSwipeLeft: Action, onSwipeRight: Action, child: Ui` — horizontal flick |
| `LongPress` | `onLongPress: Action, child: Ui` |
| `Animated` | `child: Ui` — changes inside it animate instead of jumping |
| `WebView` | `url: String` — only `http(s)://`, `data:` and `about:blank` load (anything else shows a blank page); full width, give it a height with `Expand`; adds the internet permission automatically |
| `Space` | `dp: Int` — an empty square, for gaps |
| `Center`, `CenterHorizontal`, `CenterVertical`, `AlignEnd` (right), `AlignBottom` | `child: Ui` |

Input controls (`TextField`, `TextArea`, `Checkbox`, `Switch`, `Slider`, `RadioGroup`, `Dropdown`, `Rating`) do not keep their own value: pass the current value from a field and store the new one in `onChange`, or the control snaps back.

Layout: a component is as big as its content unless told otherwise. A request to fill travels outward: a `Row` containing an `Expand`, or a `Column` containing a `FillWidth`/`AlignEnd`/`CenterHorizontal`, itself becomes full width (same for height). So equal-width buttons across the screen are simply `Row { children: [Expand {...}, Expand {...}] }`. Sizes cannot be negative and colors must be in range, or the app crashes.

**Overlays** (`Alert`, `Modal`, `BottomSheet`, `Snackbar`) are visible exactly while they are part of what `view` returns, and take no room where written. Build the child list conditionally and clear the flag in the dismiss callbacks:

```
var layers: List<Ui> = [self.page()];
if (self.asking) {
  layers = layers.append(item: Alert { title: "Delete?", message: "Sure?", confirmLabel: "Delete",
    onConfirm: self.confirm, cancelLabel: "Keep", onCancel: self.cancel });
}
return Stack { children: layers };
```

**Several pages**: there is no navigation system. Keep the current page in a field and return different `Ui` from `view`; wrap a sub-page in `BackHandler` so the back gesture returns instead of closing the app.

**Long lists**: a class may `implements Items` (besides `Screen`) by defining `fn size(self) -> Int` and `fn item(self, index: Int) -> Ui`; pass it as `LazyList { items: self }`. For a few dozen rows, `Scroll { child: Column {...} }` is enough.

## Beyond the screen

- **Saving** (`import kata.sys.Store;`): `Store {}.getInt(key: "count", fallback: 0)` / `.setInt(key: "count", value: n)`; also `getString/setString`, `getFloat/setFloat`, `getBool/setBool`, `remove(key:)`. Values survive restarts. Read in a field initializer, write in the method that changes the state.
- **Clock** (`import kata.sys.Clock;`): `Clock {}.now() -> Int` (ms since 1970), `.today() -> Date`, `.time() -> Time`.
- **Timer**: `Timer { millis: Int, onTick: Action, child: Ui }` runs `onTick` every `millis` while it is part of the view.
- **Network**: `Request { method: String, url: String, body: String, onResult: Handler<String>, onError: Handler<String>, child: Ui }` performs one HTTP exchange while it is part of the view (https only; method GET/POST/PUT/PATCH/DELETE). Show it while a `loading` field is true and clear that field in both callbacks. An error status arrives in `onError` as `"HTTP 404"`. There is no JSON parser; use the `String` methods.

`app { id: "...", name: "...", root: <expr of a Screen> }` — exactly these three, in this order. `id` is lowercase words joined by periods (`dev.example.app`).

## Passing a value to a button

Callbacks carry no arguments and there are no closures. Give each button a small object that holds the value, and pass its method (see `Step` in the example):

```
Button { label: label, onTap: Key { owner: self, label: label }.press }
```
