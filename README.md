# かなぽん kanapon for Android

Handwriting practice for Japanese kana on genkō-yōshi ruling, as a native Android app.
It is the phone and tablet design from Claude Design (`Kanapon Mobile.dc.html`) built in
Kotlin and Jetpack Compose. Scoring, feedback, history and worksheets behave exactly as
they do in the web app, [adityasingh06-05/kanapon](https://github.com/adityasingh06-05/kanapon).

- **Practice**: four cells per character.
  - Two cells to trace, at full and then fainter guide strength.
  - Two cells to write from memory.
  - Each stroke is scored as the pen lifts. Notes cover stroke count, order, direction, size and placement.
  - The app supports stylus pressure and palm rejection.
- **Chart**: both syllabaries. Each character opens a detail view showing its counterpart in the other syllabary. From there you can save it as SVG or as a 2048px JPEG.
- **Progress**: streak, a 26-week heatmap, and your weakest characters. All of it is stored on the device only.
- **Worksheets**: printable A4 or Letter PDFs, with the guide fading across each row.

A phone gets one large cell at a time, bottom tabs and sliding sheets. A tablet in
landscape (840 × 600 dp and up) gets the web layout in columns. Everything comes in
three palettes (Light, Ink-stone and Sumi) or follows the system.

## Build

```sh
tools/setup-android-sdk.sh        # SDK into ~/android-sdk, writes local.properties
./gradlew assembleDebug           # app/build/outputs/apk/debug/
```

The toolchain is JDK 17+ (CI uses 21), Gradle 9.8 (wrapper), AGP 9.4 with built-in
Kotlin, compileSdk/targetSdk 37 and minSdk 29.

## Checks

```sh
./gradlew ktlintCheck verifyRoborazziDebug lintDebug   # what CI runs
./gradlew recordRoborazziDebug                         # after an intended UI change
./gradlew connectedDebugAndroidTest                    # on a device or emulator
```

- **Engine parity:** the matcher, feedback notes, ink outlines and statistics are line-for-line ports of the web
  app's `src/lib`. `tools/fixtures.mjs` runs the web code on the seeded synthetic ink from its
  `scripts/check-scoring.mjs` and records the results. The JVM tests require the Kotlin ports
  to agree to 1e-9.
- **Screenshots** (`app/src/test/screenshots/`): the design's four states (1a–1d) on every tab and sheet,
  rendered by Robolectric.
- **Device tests:** real PDF output and files saved through MediaStore, plus a stroke written with a finger.

## Data and fonts

All of it is generated from the web app, so the two never drift. To regenerate:

```sh
node tools/export-data.mjs ../kanapon      # kana tables, stroke data, glyphs, licences
(cd .cache/node && npm i perfect-freehand@1.2.3) && node tools/fixtures.mjs ../kanapon
pip install fonttools && tools/fonts.sh    # Klee One and Zen Kaku Gothic New subsets
```

## Licences

The app's code is the project's own. The stroke data is KanjiVG (CC BY-SA 3.0). The fonts
are Klee One and Zen Kaku Gothic New (SIL OFL 1.1). The ink outline is a port of
perfect-freehand (MIT). See [NOTICE](NOTICE) and Settings → Credits in the app.
