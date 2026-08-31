# AGENTS.md

## Project
Lexi-Kattam — Android crossword app (`org.anandram.xwordapp`).
Library module: `org.akop.ararat` (crossword data model + formatters).
App module: `xwordapp` (UI, subscriptions, Drive backup).

## Build & Test
```sh
./gradlew :library:testDebugUnitTest          # library unit tests
./gradlew :xwordapp:assembleDebug            # build APK
```
No custom lint or typecheck commands. Library tests cover formatters and word building.

## Device
- APK: `xwordapp/build/outputs/apk/debug/xwordapp-debug.apk`
- Install: `adb install -r <apk>`, launch: `adb shell monkey -p org.anandram.xwordapp.debug -c android.intent.category.LAUNCHER 1`
- Device sleeps often. Before UI actions: `adb shell input keyevent KEYCODE_WAKEUP; adb shell svc power stayon true; adb shell wm dismiss-keyguard`
- Use `adb shell uiautomator dump /sdcard/ui.xml` + python parse for finding tap targets.
- Use `/tmp` for temporary files (tool-output dir is off-limits).

## Architecture

### Puzzle Formats
- `.puz`, `.xd`, `guardian-json`, `wsj-json` (Everyman), `jsoup-html` (Irish News), `pml-json` (Metro), `amuse-json` (Hindu Sunday), `jpz` (Independent).
- All stored as verbatim files (`{id}.{format}`); no serialization step.
- `PuzzleManager.parse()` is the source of truth for the format registry.

### Key Classes
| File | Role |
|------|------|
| `library/.../core/Crossword.kt` | Data model. `Cell.attrFlags` stores bars/attributes. `ATTR_BAR_TOP/BOTTOM/LEFT/RIGHT = 4/8/16/32`, `ATTR_CIRCLED = 1`. |
| `library/.../core/WordBuilder.kt` | Bar-aware word boundary helpers. Word runs require length >= 2. |
| `library/.../io/XdFormatter.kt` | Generic XD parser (Metadata/Grid/Clues/Design/Start sections). |
| `library/.../io/PuzFormatter.kt` | Puz parser. Uses WordBuilder for word detection. |
| `xwordapp/.../PuzzleEntry.kt` | Has `format: String` field. Drives load path. |
| `xwordapp/.../PuzzleManager.kt` | Format-aware: `addPuzzle(source, format, ...)`, `addXdIfNew(xdText, ...)`, `puzzleFile(id, format)`, `parse(file, format)`. |
| `xwordapp/.../*Subscription.kt` | One object per source with scraping logic (`NewYorker`, `Guardian`, `Everyman`, `IrishNews`, `Metro`, `MyCrossword`, `Hindu`, `Independent`). |
| `xwordapp/.../SubscriptionsActivity.kt` | Dispatches downloads: one name-based branch first, then per-`puzzleFormat` branches; generic `.puz` link path is the fallback. |
| `xwordapp/.../DriveManager.kt` | Backup/restore zip. Uses `puzzleFile(id, format)` — format-aware. |
| `xwordapp/.../FirebaseStats.kt` | Guarded gateway to Crashlytics/Analytics/Performance/Remote Config. Never throws; falls back silently without a FirebaseApp. |

### Adding a New Subscription Source
1. Add a source class (e.g. `NewYorkerSubscription`) with scraping logic.
2. Add a `Subscription` entry with `puzzleFormat` matching what the source produces.
3. `SubscriptionsActivity` dispatches on `puzzleFormat`; generic `.puz` path needs no changes.

### Adding a New Puzzle Format
1. Add formatter in `library/.../io/` implementing `CrosswordFormatter`.
2. Add `"xyz"` branch in `PuzzleManager.parse()`.
3. Store files as `{id}.xyz`, set `format = "xyz"` on the entry.
4. Tests: `BaseTest` has `dumpMetadata()/dumpLayout()/dumpHints()`. Write the test with placeholder expected values plus a temporary test method calling `crossword.dumpAll()`; run, harvest actuals from `library/build/reports/tests/testDebugUnitTest/classes/<TestClass>.html`, then delete the dump method (report stdout may truncate — fall back to mirroring parse logic in python).

## Gotchas
- Library pinned to `compileSdk 31` (Kotlin 1.6.21). App uses `compileSdk 36`.
- `CrosswordState` constructor is `internal` — only the library module can build one.
- Gson uses Kotlin no-arg constructor for data classes with all-default params. Missing JSON fields get Kotlin defaults. `normalized()` handles blanks.
- `.puz` GEXT has no bar bits. `GEXT_CIRCLED = 0x80`. Bars only from XD Design section.
- New Yorker cryptics use per-cell numbering (not sequential). Single-barred isolated cells are not word starts.
- Format strings are not unique per source: `guardian-json` is emitted by both `GuardianSubscription` and `MyCrosswordSubscription`. Dispatch checks `subscription.name` (MyCrossword) *before* the format branches.
- Constant-URL sources ("always today's puzzle": Irish News, Metro) dedupe via `downloadUrl = "<page-url>#<yyyy-MM-dd>"` + date-suffixed `fallbackTitle`. Note `addPuzzleIfNew` prefers the parsed crossword title over `fallbackTitle`, so such formatters must leave title unset (see `PmlJsonFormatter`).
- thehindu.com sits behind Cloudflare: non-browser User-Agents get 403. All Jsoup fetches there need a browser UA. AmuseLabs prize puzzles (Hindu Sunday) withhold `placedWords[].word`; extents come from `nBoxes` and letters from the column-major `box` grid.
- Default subscriptions merge by name into existing installs (`DEFAULT_SUBSCRIPTIONS`); a fresh `subscriptions.json` is written only when the file is absent.
- All Firebase SDK calls go through `FirebaseStats` (guarded) so plain-JVM/Robolectric unit tests and no-Firebase builds degrade silently. Remote Config is fetched once per session: sweep caps `max_per_sweep_<source>` (four link-following scrapers), per-source kill switch `disabled_<source>` (inverted flag — absent always reads enabled), and `verbose_scrape_logs` (turns per-URL Crashlytics breadcrumbs on for debugging with a user). `firebase-perf` ships without its Gradle plugin so only manual traces exist.
- Analytics events: `subscription_download` (per source, from `SubscriptionsActivity`), `scraper_failure` (rate-limited once/day/source), `puzzle_completed` (format/source/time), `join_game_*` funnel (from `PuzzleListActivity`). Crashlytics catches subscription download failures (source/format keys) + Drive backup errors + CWF socket/import failures; breadcrumbs mark sweep/backup/open-puzzle/join boundaries; session keys (`num_subscriptions_enabled`, `num_puzzles`, `last_puzzle_format`, `has_drive_auth`, `cwf_games_joined`) tag every report.
- User tests manually after install. Never commit unless explicitly asked.
