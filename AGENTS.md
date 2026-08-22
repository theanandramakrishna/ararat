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
- `.puz` — raw Puzzler's Assistant bytes, parsed by `PuzFormatter` on load.
- `.xd` — raw XD text (kotwords-compatible), parsed by `XdFormatter` on load.
- Both stored as verbatim files (`{id}.puz` / `{id}.xd`); no serialization step.

### Key Classes
| File | Role |
|------|------|
| `library/.../core/Crossword.kt` | Data model. `Cell.attrFlags` stores bars/attributes. `ATTR_BAR_TOP/BOTTOM/LEFT/RIGHT = 4/8/16/32`, `ATTR_CIRCLED = 1`. |
| `library/.../core/WordBuilder.kt` | Bar-aware word boundary helpers. Word runs require length >= 2. |
| `library/.../io/XdFormatter.kt` | Generic XD parser (Metadata/Grid/Clues/Design/Start sections). |
| `library/.../io/PuzFormatter.kt` | Puz parser. Uses WordBuilder for word detection. |
| `xwordapp/.../PuzzleEntry.kt` | Has `format: String` field — `"puz"` or `"xd"`. Drives load path. |
| `xwordapp/.../PuzzleManager.kt` | Format-aware: `addPuzzle(source, format, ...)`, `addXdIfNew(xdText, ...)`, `puzzleFile(id, format)`, `parse(file, format)`. |
| `xwordapp/.../NewYorkerSubscription.kt` | NY-specific scraping (listing -> page -> UUID -> API -> XD). |
| `xwordapp/.../SubscriptionsActivity.kt` | Generic: dispatches on `subscription.puzzleFormat`. No site-specific logic. |
| `xwordapp/.../DriveManager.kt` | Backup/restore zip. Uses `puzzleFile(id, format)` — format-aware. |

### Adding a New Subscription Source
1. Add a source class (e.g. `NewYorkerSubscription`) with scraping logic.
2. Add a `Subscription` entry with `puzzleFormat` matching what the source produces.
3. `SubscriptionsActivity` dispatches on `puzzleFormat`; generic `.puz` path needs no changes.

### Adding a New Puzzle Format
1. Add formatter in `library/.../io/` implementing `CrosswordFormatter`.
2. Add `"xyz"` branch in `PuzzleManager.parse()`.
3. Store files as `{id}.xyz`, set `format = "xyz"` on the entry.

## Gotchas
- Library pinned to `compileSdk 31` (Kotlin 1.6.21). App uses `compileSdk 36`.
- `CrosswordState` constructor is `internal` — only the library module can build one.
- Gson uses Kotlin no-arg constructor for data classes with all-default params. Missing JSON fields get Kotlin defaults. `normalized()` handles blanks.
- `.puz` GEXT has no bar bits. `GEXT_CIRCLED = 0x80`. Bars only from XD Design section.
- New Yorker cryptics use per-cell numbering (not sequential). Single-barred isolated cells are not word starts.
- User tests manually after install. Never commit unless explicitly asked.
