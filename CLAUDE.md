# CLAUDE.md — Booklip (Android)

Guidance for Claude Code when working in this repo.

## What this is

Android port of **Booklip**, an offline-first ebook/text reader. The source of
truth for behavior and feature scope is the iOS app at
`~/Workspace/iOS/ReaderApp` (see its `CLAUDE.md`) — this port re-implements
the same idea with Android-idiomatic tools, not a line-for-line translation.

- Package / applicationId: `com.qbitcore.booklip`
- Kotlin + Jetpack Compose (Material 3), min SDK 26, compile/target SDK 34
- Persistence: Room (books, folders) + DataStore Preferences (reading settings)
- No external services, no analytics, no accounts — everything is local,
  matching the iOS app's privacy stance.

## Current scope (MVP — first milestone)

Implemented: library (grid/list view modes, sort, folders), import via SAF
(`.txt`, `.md`, `.epub`), TXT/Markdown/EPUB parsers, a scrolling reader with
font/size/line-spacing/color-theme settings, progress save/restore, and a
basic table-of-contents dialog for EPUBs.

**Deliberately not yet implemented** (each is a separate milestone):
- PDF import (`BookFormat.PDF` exists but `ParserFactory` throws for it)
- Text-to-speech
- Cloud import (Dropbox / Google Drive / OneDrive OAuth)
- Bookmarks, highlights, reading stats
- EPUB embedded-font extraction/de-obfuscation and inline images (Android
  renders with system fonts; this matters far less than on iOS, where a
  book's own font is sometimes required for correct Korean glyph coverage)

## Architecture (`app/src/main/java/com/qbitcore/booklip/`)

- **model/** — `Book` (Room entity), `BookFolder` (Room entity), `Chapter`
  (in-memory TOC entry, not persisted).
- **parser/** — `BookParser` interface + `ParserFactory` (static dispatch,
  mirrors iOS `ParserFactory`), `PlainTextParser` (encoding fallback chain:
  UTF-8 → EUC-KR → MS949(CP949) → UTF-16 → windows-1252 → ISO-8859-1, each
  tried with a **strict** decoder — see PlainTextParser's doc comment for why),
  `MarkdownParser`, `EpubParser` (unzip via `java.util.zip.ZipFile`, OPF/NCX
  parsing via `android.util.Xml`'s pull parser, regex-based HTML stripping;
  a deliberately simplified port of the iOS EPUBParser — no embedded fonts,
  no inline images, no font de-obfuscation).
- **data/** — `BookDao` / `FolderDao` (Room), `Converters` (Room
  `TypeConverter` for the `BookFormat` enum — Room has no native enum
  support, don't remove this without swapping in another conversion),
  `BooklipDatabase`, `FileStore` (owns everything on disk: imported book
  files, an extracted **plain-text cache** + **chapters cache** written once
  at import time so the reader never has to re-run `EpubParser` just to open
  a book, and cover images), `BookRepository` (the single entry point UI code
  talks to; wraps Room + FileStore + ParserFactory).
- **settings/** — `ReadingSettings` (font family from a small fixed list of
  Android system font families — Serif/Sans/Monospace, not the iOS app's
  named-font list, since Android doesn't ship those fonts — font size, line
  spacing, color preset) + `SettingsRepository` (DataStore-backed).
- **ui/library/** — `LibraryViewModel` (Flow-based, combines books/folders/
  sort/view-mode/import-state into one `LibraryUiState`), `LibraryScreen`,
  `BookCard`.
- **ui/reader/** — `ReaderViewModel` (loads the cached plain text + chapters
  once per book id), `ReaderScreen` (a `LazyColumn` of paragraphs — **not**
  the iOS app's char-based TextKit pager; progress is a paragraph index, not
  a character offset), `AppearanceSheet`.
- **navigation/** — `BooklipNavHost` (two routes: `library`, `reader/{bookId}`).

## Key decisions & gotchas

- **`Book.charIndex` is a paragraph index, not a character offset.** The iOS
  field of the same name is a UTF-16 character index into TextKit's storage;
  that has no equivalent here since the reader is a plain `LazyColumn` of
  paragraphs. Don't try to make the two apps' stored progress values
  interoperate without a conversion layer.
- **`ReaderScreen`'s `LazyListState` is created with
  `remember(uiState.book?.id) { LazyListState(...) }`, not
  `rememberLazyListState(initialFirstVisibleItemIndex = ...)`.** The book
  loads asynchronously (`uiState.book` is null on first composition), so a
  plain `rememberLazyListState` would freeze its initial scroll index at 0
  forever — `remember` only re-runs its initializer when the key changes, and
  keying on the book id makes it re-run exactly once, right when the loaded
  book (and its saved `charIndex`) becomes available.
- **The `List` icon (`androidx.compose.material.icons.filled.List`) must be
  imported with an alias** (`import ... as ListIcon` in `ReaderScreen.kt`).
  Importing it under its own name shadows `kotlin.collections.List` for every
  bare `List<T>` type reference in that file — a real compile error, not a
  style nit.
- **`FileStore` caches parsed plain text + chapters at import time**
  (`<fileName>.txt` / `<fileName>.json` next to the original file). The
  reader always reads from that cache, never re-parsing the original EPUB —
  if you change `EpubParser`'s output shape, existing imported books' caches
  go stale until re-imported (there's no cache-invalidation/versioning yet).
- **No Gradle build has been run in this environment** (no Android SDK, and
  system Java is 11 — AGP 8.5.x requires JDK 17 to run, independent of the
  app's own `compileSdk`/`targetSdk`). Open the project in Android Studio,
  which bundles a compatible JDK and can fetch missing SDK platforms — that's
  the first real compiler feedback this code will get.

## Build / run

Requires Android Studio (or a standalone JDK 17 + Android SDK with
`compileSdk 34` installed). From the command line once those are set up:

```bash
cd ~/Workspace/Android/Booklip
./gradlew assembleDebug
```
