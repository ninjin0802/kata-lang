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

We measured it, and the honest answer is: not as a language, but the toolchain is fast and small.

Fresh agents built three apps from scratch (a calculator, a unit converter, an expense tracker), once in kata with only the guide and once in Kotlin with Gradle, from the same requirements. Each result was checked on a device by the same script. Twelve runs in all, six per language.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/img/reliability-dark.svg">
  <img src="docs/benchmarks/img/reliability-light.svg" alt="Runs that worked without a retry">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/img/time-dark.svg">
  <img src="docs/benchmarks/img/time-light.svg" alt="Time the agent took">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/img/tokens-dark.svg">
  <img src="docs/benchmarks/img/tokens-light.svg" alt="Tokens the agent used">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/img/apk-dark.svg">
  <img src="docs/benchmarks/img/apk-light.svg" alt="Size of the APK">
</picture>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/benchmarks/img/build-dark.svg">
  <img src="docs/benchmarks/img/build-light.svg" alt="Build time for the expense tracker">
</picture>

- **Correctness is about equal.** Eleven of twelve runs passed every check. The one that did not was a Kotlin run that drew its buttons under the system bar.
- **kata built on the first attempt every time; Kotlin did once in six.** Two of Kotlin's failures were a name clash in the code, and at least two were Gradle or dependency versions rather than the code.
- **kata took about half the time or less (21 to 52% of Kotlin's), and its APK is 13 to 180 times smaller.** Both come from having no Gradle and no dependencies, not from the syntax.
- **Kotlin used 7 to 18% fewer tokens every time.** The agent has to read the kata guide first, and that cost did not shrink on the larger app.
- A cheaper model (Sonnet) and a larger app did not tip the result toward kata.

Each condition was run once, so treat the numbers as indications. The data, the requirements, the check scripts and the script that draws these figures are in [`docs/benchmarks/`](docs/benchmarks/).

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
