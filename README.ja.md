# kata

AI のコーディングエージェントが読み書きすることを前提に設計した、Android 向けの実験的なプログラミング言語です。

[English](README.md)

構文は Rust と Swift に似ています（`fn`、`let` / `var`、`Point { x: 1 }`、名前付き引数）。考え方は Go に近く（機能を絞り、書き方を1通りにする）、動く場所は Kotlin と同じです。Kotlin 風の言語ではありません。型推論も `this` の省略もなく、関数リテラルは型をすべて書き、書き換えられない名前だけを取り込めます。

## 状態

実験段階です。1画面で完結する小さなアプリを作れます（`examples/` に複数ファイルの ToDo リスト、Material Design に沿った単位換算、カウンター、見た目の指定、入力欄とスクロール、入力部品、選択と配置の部品、タブやナビゲーションを持つ画面の骨組み、ダイアログと長い一覧、画像や日付の選択、効果とジェスチャー、四則演算の電卓があります）。値の保存、時刻の取得、タイマー、HTTP の通信、JSON の読み書きもできます。動画、カメラ、地図、Android の API の直接呼び出しはまだできません。言語と出力の形式は予告なく変わることがあります。

## 特徴

- **短さより明示性。** すべての宣言に型を書き、引数はすべて名前付きで、`self.` は必須です。書き方は1通りで、オーバーロード、既定引数、暗黙の型変換、シャドーイング、継承、null、例外がありません。`enum` と、全部の場合を書かないとコンパイルできない `match` があり、場合分けの書き漏らしはコンパイル時に見つかります。
- **読む範囲が閉じている。** フィールドを書き換えられるのはそのクラスの中だけで、ファイルが使う外部の名前は先頭の import にすべて現れます。
- **機械向けの診断。** `kata check` は、固定のエラーコード、正確な範囲、機械的に決まる修正案を JSON で返します。
- **アプリ側に Kotlin も Gradle も要らない。** コンパイラが JVM バイトコードを直接出力し、Android SDK の build-tools で APK を組み立てます。カウンターの APK は 25 KB です。

## 試す

必要なもの：JDK 17、`platforms/android-35` と `build-tools/35.0.0` を含む Android SDK（`ANDROID_HOME` を設定）。

```
./gradlew installDist
compiler/build/install/kata/bin/kata check examples/counter
compiler/build/install/kata/bin/kata build examples/counter    # examples/counter/build/app.apk ができる
adb install examples/counter/build/app.apk
```

アプリを書くには [`docs/kata-guide.md`](docs/kata-guide.md)（英語、1ファイル）を読んでください。仕様の全体は [`docs/spec/`](docs/spec/index.html) にあり、これが正です。

## AI エージェントにとって本当に有利か

計測しました。結論は「言語としては有利ではない。ただし、ビルドの仕組みは速くて小さい」です。

新しく起動したエージェントに、同じ要件の3つのアプリ（電卓、単位換算、支出の記録）をゼロから作らせました。kata では手引きだけを渡し、Kotlin では Gradle を使わせています。できたものは、同じスクリプトで端末上の動作を確かめました。全部で12回、言語ごとに6回です。

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

- **正しさは、ほぼ同じです。** 12回のうち11回が、すべての確認項目を通りました。通らなかった1回は Kotlin で、ボタンがシステムバーの下に隠れていました。
- **kata は毎回、1回目のビルドで通りました。Kotlin は6回のうち1回です。** Kotlin の失敗のうち2回はコードの名前の衝突で、少なくとも2回はコードではなく Gradle やライブラリのバージョンが原因でした。
- **kata の所要時間は Kotlin の 21〜52% で、APK は 13〜180分の1 です。** どちらも文法ではなく、Gradle と依存ライブラリがないことから来ています。
- **トークンは、毎回 Kotlin のほうが 7〜18% 少なくなりました。** エージェントは先に kata の手引きを読む必要があり、この費用はアプリを大きくしても薄まりませんでした。
- 安いモデル（Sonnet）でも、大きいアプリでも、結果は kata に傾きませんでした。

各条件1回ずつの計測なので、数字は目安です。データ、要件、確認スクリプト、図を描くスクリプトは [`docs/benchmarks/`](docs/benchmarks/) にあります（図の文字は英語です）。

## 作り方について

言語の設計、仕様書、コンパイラ、ランタイム、テスト、この README は、人間の指示のもとで、ほぼすべて AI エージェント（Claude）が書きました。「エージェントのための言語を、エージェントが作る」ことも、この実験の一部です。

個人の実験であり、サポートのある製品ではありません。Issue と Pull Request は歓迎しますが、返事が遅くなることがあり、今後の開発計画は約束しません。

## 構成

```
docs/spec/         仕様書（正）
docs/kata-guide.md アプリを書くための手引き
compiler/          字句解析、構文解析、検査、バイトコード生成、APK の組み立て、CLI（Java 17、ASM）
runtime-core/      UI の値、配置の規則、差分反映（Android に依存しない）
runtime-android/   Activity と View への描画
examples/          サンプルアプリ
scripts/e2e.sh     端末上の動作確認（エミュレータか実機が1台必要）
```

## テスト

```
./gradlew test      # 端末を使わないテストすべて
scripts/e2e.sh      # サンプルをビルドして、接続中の端末で確認
```

## ライセンス

Apache License 2.0。[LICENSE](LICENSE) を参照してください。
