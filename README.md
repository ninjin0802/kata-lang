# kata

An experimental programming language for Android, designed to be written and read by AI coding agents rather than by people.

[日本語の説明はこちら](README.ja.md)

```
module app.counter;

import kata.ui.Button;
import kata.ui.Column;
import kata.ui.Screen;
import kata.ui.Text;
import kata.ui.Ui;

class Counter implements Screen {
  var count: Int = 0;

  fn view(self) -> Ui {
    return Column {
      children: [
        Text { text: self.count.toString() },
        Button { label: "+1", onTap: self.increment },
      ],
    };
  }

  fn increment(self) -> Unit {
    self.count = self.count + 1;
  }
}

app {
  id: "dev.kata.counter",
  name: "Counter",
  root: Counter {},
}
```

The syntax looks like Rust and Swift (`fn`, `let`/`var`, `Point { x: 1 }`, named arguments), the philosophy is closer to Go (few features, one way to do things), and it runs where Kotlin runs. It is not Kotlin-like: there is no type inference and no implicit `this`, and function literals spell out their types and may only capture names that cannot change.

## Status

Experimental. It can build small single-screen apps (see `examples/`): a multi-file todo list, a Material-styled unit converter, a counter, a styling demo, a form with text input and a scrolling list, input controls, choice and container widgets, an app shell with tabs and navigation, dialogs and long lists, images and pickers, effects and gestures, and a four-function calculator. Apps can save values, read the clock, run timers, make HTTP requests and read and write JSON. It cannot yet play video, use the camera or maps, or call Android APIs directly. The language and its output formats may change without notice.

## What it is

- **Explicit over short.** Every declaration is typed, every argument is named, `self.` is mandatory, and there is one way to write each thing: no overloading, default arguments, implicit conversions, shadowing, inheritance, null or exceptions. Enums with an exhaustive `match` make "forgot a case" a compile error.
- **Local reasoning.** A field changes only inside its own class, and a file's imports list every outside name it uses.
- **Diagnostics for machines.** `kata check` prints JSON with stable error codes, exact ranges, and a mechanical `fix` where one exists.
- **No Kotlin, no Gradle in your app.** The compiler emits JVM bytecode directly and assembles the APK with the Android SDK build-tools. A counter app is a 25 KB APK.

## Try it

Requirements: JDK 17 and an Android SDK with `platforms/android-35` and `build-tools/35.0.0` (`ANDROID_HOME` set).

```
./gradlew installDist
compiler/build/install/kata/bin/kata check examples/counter
compiler/build/install/kata/bin/kata build examples/counter    # writes examples/counter/build/app.apk
adb install examples/counter/build/app.apk
```

To write an app, read [`docs/kata-guide.md`](docs/kata-guide.md) — one file with everything an author (human or agent) needs. The full specification is in [`docs/spec/`](docs/spec/index.html) (Japanese) and is the source of truth.

## Does it actually help an AI agent?

We measured it once, on one small task, and the honest answer is: not on token cost, yet.

The same model built the same calculator from scratch, with the same requirements, in kata (given only the guide) and in Kotlin (plain Views, Gradle). All four results passed the same on-device check.

| | kata (full spec) | kata (guide) | Kotlin, run 1 | Kotlin, run 2 |
|---|---|---|---|---|
| Total tokens | 62,054 | 46,131 | 43,349 | 42,896 |
| Wall time | 54 s | 39 s | 88 s | 81 s |
| Builds (failed) | 1 (0) | 1 (0) | 2 (1) | 2 (1) |
| Source size | 4,542 chars | 3,476 chars | 4,660 chars | 4,622 chars |
| APK size | 25 KB | 25 KB | 845 KB | 855 KB |

- kata built on the first try both times; Kotlin failed once both times, on the same implicit name clash (`display` vs `View.getDisplay()`).
- kata's source was shorter, but the agent has to read the guide first, which Kotlin does not need. On a small app that fixed cost outweighs the saving.
- This is one task with one or two runs per column. Treat it as an anecdote, not a result.

## How this was made

The language design, specification, compiler, runtime, tests and this README were written almost entirely by an AI agent (Claude), directed by a human. That is part of the experiment: a language for agents, built by one.

This is a personal experiment, not a supported product. Issues and pull requests are welcome, but responses may be slow and there is no roadmap commitment.

## Layout

```
docs/spec/         specification (source of truth)
docs/kata-guide.md authoring guide
compiler/          lexer, parser, checker, bytecode generator, APK builder, CLI (Java 17, ASM)
runtime-core/      UI values, layout rules, reconciler (no Android dependency)
runtime-android/   Activity and View renderer
examples/          sample apps
scripts/e2e.sh     on-device checks (needs one connected emulator or device)
```

## Tests

```
./gradlew test      # everything that does not need a device
scripts/e2e.sh      # builds the examples and checks them on a connected emulator or device
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
