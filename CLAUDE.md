# CLAUDE.md — Booklip (Android)

Guidance for Claude Code when working in this repo.

## What this is

Android port of **Booklip**, an offline-first ebook/text reader. The source of
truth for behavior and feature scope is the iOS app at
`~/Workspace/iOS/ReaderApp` (see its `CLAUDE.md`) — this port re-implements
the same idea with Android-idiomatic tools, not a line-for-line translation.

- Package / applicationId: `com.qbitcore.booklip`
- Kotlin + Jetpack Compose (Material 3), min SDK 26, compile/target SDK 34
- Persistence: Room (books, folders, bookmarks, highlights) + DataStore
  Preferences (reading settings, reading stats, cloud tokens)
- No backend of its own, no analytics. Cloud import is opt-in, read-only
  OAuth to the user's own Dropbox/Google Drive/OneDrive — matches the iOS
  app's privacy stance (see its CLAUDE.md "Privacy" section).

## Scope vs. the iOS app

All originally-deferred features are now implemented: PDF import/reading,
text-to-speech, cloud import (Dropbox / Google Drive / OneDrive), bookmarks,
highlights, and reading stats. Two areas are deliberate, permanent
simplifications rather than gaps to fill in later:

- **EPUB embedded-font extraction/de-obfuscation and inline images** are not
  ported. Android renders with system fonts; this matters far less than on
  iOS, where a book's own font is sometimes required for correct Korean glyph
  coverage.
- **PDF text extraction** doesn't exist at all — Android's built-in
  `PdfRenderer` is raster-only (no text-layer API without a third-party PDF
  library), unlike iOS's PDFKit. The PDF reader shows real page images
  instead (see `PdfReaderScreen`), same as what the iOS PDFReaderView
  actually displays — its own extracted text there only feeds TTS/search,
  which Android's PDF path doesn't have either.

## Architecture (`app/src/main/java/com/qbitcore/booklip/`)

- **model/** — `Book`, `BookFolder`, `Bookmark`, `Highlight` (all Room
  entities) + `Chapter` (in-memory TOC entry, not persisted). `HighlightColor`
  is a 4-color enum, same set as iOS.
- **parser/** — `BookParser` interface + `ParserFactory` (static dispatch,
  mirrors iOS `ParserFactory`), `PlainTextParser` (encoding fallback chain:
  UTF-8 → EUC-KR → MS949(CP949) → UTF-16 → windows-1252 → ISO-8859-1, each
  tried with a **strict** decoder — see its doc comment for why), `MarkdownParser`,
  `EpubParser` (unzip via `java.util.zip.ZipFile`, OPF/NCX parsing via
  `android.util.Xml`'s pull parser, regex-based HTML stripping — a simplified
  port, see "Scope vs. the iOS app"), `PdfParser` (page count + a page-0
  thumbnail for the library cover only — no text).
- **data/** — `BookDao` / `FolderDao` / `BookmarkDao` / `HighlightDao` (Room),
  `Converters` (Room `TypeConverter`s for the `BookFormat` and
  `HighlightColor` enums — Room has no native enum support, don't remove
  these without swapping in another conversion), `BooklipDatabase` (version 2;
  `fallbackToDestructiveMigration()` is on in `BooklipApplication` since this
  is pre-release — write a real migration before shipping to real users),
  `FileStore` (owns everything on disk: imported book files, an extracted
  **plain-text cache** + **chapters cache** written once at import time so
  the reader never has to re-run `EpubParser` just to open a book, and cover
  images), `BookRepository` (the single entry point UI code talks to; wraps
  Room + FileStore + ParserFactory), `ReadingStatsRepository` (DataStore:
  total seconds read + a set of "read today" day keys, mirrors iOS
  `ReadingStats`).
- **data/cloud/** — `CloudConfig` (client IDs/redirect URIs — placeholders,
  see "Cloud OAuth" below), `CloudProvider` (enum of the 3 providers + their
  endpoints/scopes), `PkceUtil`, `OAuthToken`, `OAuthClient` (PKCE authorize
  via Chrome Custom Tabs + token exchange/refresh via plain
  `HttpURLConnection` — no networking library dependency), `OAuthRedirectActivity`
  + `OAuthRedirectBridge` (catch the `booklip://auth/*` redirect and hand it
  back to the suspended `authorize()` call), `CloudFile` + `CloudService`
  interface, `DropboxService` / `GoogleDriveService` / `OneDriveService`
  (each a direct, from-scratch REST client against that provider's real API —
  no shared abstraction beyond the `CloudService` interface, since the three
  APIs' request shapes genuinely differ), `CloudTokenStore` (DataStore),
  `CloudRepository` (orchestrates auth + listing + download-then-import).
- **settings/** — `ReadingSettings` (font family from a small fixed list of
  Android system font families — Serif/Sans/Monospace, not the iOS app's
  named-font list, since Android doesn't ship those fonts — font size, line
  spacing, color preset) + `SettingsRepository` (DataStore-backed).
- **tts/** — `BooklipTts` wraps `android.speech.tts.TextToSpeech`. See "TTS"
  below for how it differs from the iOS `TTSManager`.
- **ui/library/** — `LibraryViewModel`, `LibraryScreen`, `BookCard`,
  `StatsScreen` (mirrors iOS `StatsView`).
- **ui/reader/** — `ReaderViewModel` (text books: cached plain text +
  chapters + bookmarks/highlights + TTS, all per book id), `ReaderScreen` (a
  `LazyColumn` of paragraphs — **not** the iOS app's char-based TextKit
  pager; progress is a paragraph index, not a character offset),
  `AppearanceSheet`, `NavigateDialog` (tabbed Contents/Bookmarks/Highlights,
  mirrors iOS `ContentsPanel`), `TtsSheet` (mirrors iOS `TTSPanel`),
  `PdfReaderViewModel` + `PdfReaderScreen` (separate ViewModel/screen for PDF
  — see "Scope vs. the iOS app").
- **ui/cloud/** — `CloudViewModel`, `CloudConnectScreen` (provider picker,
  mirrors iOS `CloudConnectView`), `CloudFileBrowserScreen` (breadcrumb +
  folder navigation + tap-to-import, mirrors iOS `CloudFileBrowserView`).
- **navigation/** — `BooklipNavHost`: `library`, `stats`, `cloud`,
  `cloud/browse`, `reader/{bookId}`, `pdfreader/{bookId}`. `LibraryScreen`
  picks `reader` vs. `pdfreader` from `book.format` at the call site (it
  already has the `Book` object when the user taps it).

## Key decisions & gotchas

- **`Book.charIndex` means different things per format.** For text formats
  it's a paragraph index into `ReaderScreen`'s `LazyColumn` (the iOS field of
  the same name is a UTF-16 TextKit character index — no equivalent here).
  For PDF it's a page index. Don't try to make stored progress values
  interoperate across formats, or with the iOS app, without a conversion layer.
- **A book's `LazyListState` is created with `remember(uiState.book?.id) {
  LazyListState(...) }`, not `rememberLazyListState(initialFirstVisibleItemIndex
  = ...)`** (both `ReaderScreen` and `PdfReaderScreen`). The book loads
  asynchronously (`uiState.book` is null on first composition), so a plain
  `rememberLazyListState` would freeze its initial scroll index at 0 forever —
  `remember` only re-runs its initializer when the key changes, and keying on
  the book id makes it re-run exactly once, right when the loaded book (and
  its saved position) becomes available. The same pattern bit
  `derivedStateOf { listState.firstVisibleItemIndex }` in `ReaderScreen` —
  that one needs `remember(listState)`, not a bare unkeyed `remember`, or it
  permanently captures the first (pre-load placeholder) `LazyListState`.
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
- **A Room entity field of enum type needs a `Converters` entry, or the build
  fails at annotation-processing time**, not silently. Both `BookFormat`
  (`Book`) and `HighlightColor` (`Highlight`) have one — keep that pairing in
  mind before adding another enum-typed entity field.
- **A highlight is per-paragraph, not per-substring.** The iOS Highlight model
  stores an arbitrary NSRange because it renders one continuous
  NSTextStorage; this reader is a list of discrete paragraph `Text`
  composables, so `Highlight.paragraphIndex` covers the whole paragraph.
  Picking a new color for an already-highlighted paragraph replaces the
  existing `Highlight` row (see `ReaderViewModel.setHighlight`) rather than
  stacking a second one — don't reintroduce a bare `addHighlight` call there
  without that replace-first step.
- **TTS has no true pause/resume.** `android.speech.tts.TextToSpeech` has no
  public "pause in place" API (unlike iOS's `AVSpeechSynthesizer.pauseSpeaking`),
  so `BooklipTts.pause()` stops the engine and remembers the current
  paragraph, and `resume()` re-speaks that paragraph from its start. Word/
  chunk-level position within a paragraph is lost on pause.
- **`ReaderViewModel.onCleared()` cannot use `viewModelScope`** to save the
  reading-stats session length — `viewModelScope` is already cancelled by the
  time `onCleared()` runs, so a coroutine launched on it there silently never
  executes. It uses a short-lived scope of its own instead.
- **`OAuthClient.authorize()` has no true cancel signal.** Chrome Custom Tabs
  gives no callback when a user just closes the tab without finishing sign-in
  (unlike iOS's `ASWebAuthenticationSession`, which reports cancellation
  directly) — it times out after 5 minutes instead. A user who backs out
  waits for that timeout before seeing an error, unless a future pass adds
  lifecycle-based cancellation detection.
- **No Gradle build has been run in this environment** (no Android SDK, and
  system Java is 11 — AGP 8.5.x requires JDK 17 to run, independent of the
  app's own `compileSdk`/`targetSdk`). Open the project in Android Studio,
  which bundles a compatible JDK and can fetch missing SDK platforms — that's
  the first real compiler feedback this code will get. Every file here has
  had a manual review pass looking for exactly the kind of mistake a compiler
  would catch (see the git history for several real ones that were found and
  fixed this way — a Room enum converter, an icon-name/`List<T>` collision, a
  stale `remember` capture, a `download(): Unit` override whose expression
  body actually inferred `Long`), but that is not a substitute for actually
  building it.

## Cloud OAuth

`CloudConfig.kt` ships with placeholder client IDs (`YOUR_...`), exactly like
the iOS app's `CloudConfig.swift` did before it was configured — `OAuthClient`
refuses to start a flow while a client ID still has that placeholder prefix,
failing with a clear message instead of a confusing provider-side error.
Registration notes (also in `CloudConfig.kt`'s header comment):

- **Dropbox** is the easy one: a custom URL scheme redirect isn't
  OS-specific, so the exact same Dropbox app the iOS build uses can serve
  Android too — just confirm `booklip://auth/dropbox` is one of its
  registered redirect URIs.
- **Google Drive** needs a *new* OAuth client — the iOS app's client is an
  "iOS" type tied to its bundle id/redirect format and won't validate a
  different platform's redirect. Register an Android or Desktop-app client
  and put its ID + a matching redirect in `CloudConfig.kt`.
- **OneDrive** can likely reuse the iOS app's Application (client) ID — Azure
  app registrations support multiple redirect URIs per client — after adding
  an Android/native redirect URI to that same registration in the Azure portal.

## Build / run

Requires Android Studio (or a standalone JDK 17 + Android SDK with
`compileSdk 34` installed). From the command line once those are set up:

```bash
cd ~/Workspace/Android/Booklip
./gradlew assembleDebug
```
