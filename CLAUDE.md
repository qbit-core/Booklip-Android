# CLAUDE.md — Booklip (Android)

Guidance for Claude Code when working in this repo.

## What this is

Android port of **Booklip**, an offline-first ebook/text reader. The source of
truth for behavior and feature scope is the iOS app at
`~/Workspace/iOS/ReaderApp` (see its `CLAUDE.md`). This port re-implements the
same features with Android-idiomatic tools; it is not a line-for-line
translation, but **positions, highlights and chapter offsets use the same
index space as iOS** (UTF-16 offsets into the rendered text).

- Package / applicationId: `com.qbitcore.booklip`
- Kotlin + Jetpack Compose (Material 3), min SDK 26, target SDK 36,
  **compile SDK 37** (needed for the PDF text APIs — see "PDF")
- Persistence: Room (books, folders, bookmarks, highlights) + DataStore
  Preferences (reading settings, library sort/view mode, TTS prefs, reading
  stats, cloud tokens)
- No backend, no analytics. Cloud import is opt-in, read-only OAuth.

## Build / run

```bash
cd ~/Workspace/Android/Booklip
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

AGP 9.4 / Kotlin 2.2 / Gradle 9.6, JDK 21 (JetBrains runtime via the
foojay toolchain resolver). Always build after changes; verify UI changes on
the emulator rather than by reading the code.

**Changelog is part of every change.** `README.md` holds the changelog twice —
under `## English` and `## 한국어` — newest first, as in the iOS repo: the
latest date is the first heading and the newest item is the first bullet under
it. Whenever you change the app, add the entry at the TOP of BOTH sections
(same date heading `#### YYYY-MM-DD`, same items, version bumps in bold)
without being asked, in the same commit as the change.

## Features (all implemented, matching the iOS app)

Formats **.txt .epub .pdf .md**. Library: All Books / Folders tabs, folder
detail screens (plus "Unfiled"), search (also on the Folders tab and inside
folders), sort + grid/list view modes (persisted), **language filter** (globe menu,
persisted), multi-select with All/None · Move · Delete and **drag-to-select**
(also in the cloud browser), long-press menu (move / delete), real covers.
Reader: font / size / line spacing / 6 colour themes, **Vertical Slide**
(continuous scroll) or **Paper Book** (pages) with edge-tap and swipe paging,
auto-scroll, nested table of contents (NCX / nav, anchor-accurate), bookmarks
(toggle, ticks on the progress bar), highlights (4 colours, character
ranges), in-book search with match stepping, Define, exact position restore,
"Page X / Y". **Language detection** per book (`LanguageDetector`, same codes as iOS).
**TTS**: the voice list holds only the book's language in its main region
(CLDR likely subtags: en → US, zh-Hant → TW; else the region with the most
voices) — English / Korean / Japanese / Chinese until the language is known.
"Automatic" = the best-rated of those; a pick is remembered per language;
speed, pitch, sentence highlight + follow, sleep timer, background playback
with media notification / lock-screen controls. **Reading stats**. **Cloud
import**: Dropbox, OneDrive, Google Drive with Select mode (files and whole
folders → library folder).

Not ported, on purpose: iCloud sync (off on iOS too), URL import (not
reachable in the iOS UI either).

## Architecture (`app/src/main/java/com/qbitcore/booklip/`)

- **`BooklipApplication`** — the service locator: `repository`,
  `settingsRepository`, `statsRepository`, `cloudRepository`, `tts`, and
  `appScope` (work that must outlive a screen: saving on close, imports,
  deletes).
- **model/** — Room entities `Book`, `BookFolder`, `Bookmark`, `Highlight`
  (`location` + `length`), plus `Chapter` (TOC entry, not persisted).
- **parser/** — `PlainTextParser` (strict-decoder fallback chain UTF-8 →
  EUC-KR → MS949 → UTF-16 → windows-1252 → ISO-8859-1; BOM + CR
  normalised), `MarkdownParser` + `MarkdownRenderer` (raw text is cached;
  rendering to text + `StyleRun`s happens on load), `EpubParser` (port of the
  iOS parser: OPF via pull parser, NCX/nav TOC with fragment splitting,
  inline images, embedded fonts incl. IDPF/Adobe de-obfuscation, cover),
  `PdfParser` (page-0 cover; `sampleText` for language detection on Android
  15+), `LanguageDetector` (see below).
- **data/** — DAOs, `BooklipDatabase` (**version 4**, `MIGRATION_2_3`,
  `MIGRATION_3_4` adds `books.language`),
  `FileStore` (book files, covers, and the per-book parsed cache
  `files/parsed-v3/<fileName>/`: `text.txt`, `chapters.json`, `img_N`,
  `font_N`, `meta.json` written last as the "complete" marker),
  `BookRepository` (the single entry point for UI code),
  `ReadingStatsRepository`.
- **data/cloud/** — `CloudConfig`, `CloudProvider`, `OAuthClient` (PKCE via
  Custom Tabs), `OAuthRedirectActivity` + `OAuthRedirectBridge`,
  `DropboxService` / `GoogleDriveService` / `OneDriveService` (plain
  `HttpURLConnection`, paginated listings), `CloudTokenStore`,
  `CloudRepository`.
- **settings/** — `ReadingSettings` (font, size, line spacing, theme,
  `pageEffect`, `useEmbeddedFont`, `autoScrollSpeed`), `SettingsRepository`.
- **tts/** — `TtsController` (app-wide player over
  `android.speech.tts.TextToSpeech`), `TtsPlaybackService` (foreground
  service + `MediaSessionCompat`).
- **ui/** — `DragSelection` (`Modifier.dragSelection` on the lazy container +
  `Modifier.dragSelectItem` per cell), shared by the library and the cloud
  browser.
- **ui/library/** — `LibraryViewModel`, `LibraryScreen`, `FolderViews`
  (`FolderDetailScreen`, folder cards), `LibraryComponents` (collection,
  selection bar, dialogs, view/sort menu), `BookCard`, `StatsScreen`.
- **ui/reader/** — see "The text reader". `PdfReaderViewModel` /
  `PdfReaderScreen` for PDFs; `ReaderChrome` (bars, progress bar, search
  bar, gestures), `ContentsPanel`, `AppearanceSheet`, `TtsSheet` are shared
  by both readers.
- **ui/cloud/** — `CloudViewModel`, `CloudConnectScreen`,
  `CloudFileBrowserScreen`.
- **navigation/** — `BooklipNavHost`: `library`, `unfiled`,
  `folder/{id}`, `stats`, `cloud`, `cloud/browse`, `reader/{bookId}`,
  `pdfreader/{bookId}`. Reader view models are scoped to their back-stack
  entry, so closing a book clears them (and stops speech).

## The text reader

Everything is a **UTF-16 offset into `ReaderDocument.text`**: the reading
position (`Book.charIndex`), chapters and bookmarks (stored as a 0…1
fraction, converted with `offsetOf` / `progressOf` — `offsetOf` **rounds**,
so the round trip is exact), highlights, search matches, the spoken sentence.
An EPUB image is one `U+FFFC` + `"\n\n"` in the text, as on iOS.

- **`ReaderLayout`** decides lines and pages. `LayoutSpec` holds every
  metric-affecting value; pages are measured with a `StaticLayout` built from
  it and displayed in a `TextView` configured by `LayoutSpec.applyTo` with the
  *same* values (no font padding, `BREAK_STRATEGY_SIMPLE`, no hyphenation,
  fallback line spacing). **Change one side and you must change the other**,
  or pages show cut lines. `pageEnd` ends a page at the last line that fits
  completely; `pageStartBefore` builds the previous page when the position is
  off the page grid; `paginate` computes every page start in the background
  (cached per book + spec in `ReaderViewModel.pageCache`).
- **`PaperReader`** (Paper Book) — one `TextView` per page inside
  `AnimatedContent`. Holds a `PageRange`; turning never depends on the full
  page table, which is only used for "Page X / Y" and for exact backward
  turns when the current page is on the grid.
- **`ScrollReader`** (Vertical Slide) — a `LazyColumn` with one `TextView`
  per block (`ReaderDocument.segmentStarts`: ~1200 chars, cut only at line
  breaks so wrapping is unaffected). The position is the first fully visible
  line below the top inset. `settled` gates position reporting: the list's
  provisional position during a seek or a re-layout must not be written back.
- **`ReaderTextViews`** — `TextView.bind` (rebinding the same block is a
  no-op so selection survives), background decorations (`Decorations`:
  highlights, search matches, spoken sentence — none move a line), and the
  selection action mode (Highlight → colour dialog, Remove Highlight, Define).
- **Gestures** (`Modifier.readerGestures`): taps and swipes are read at the
  Compose level. A `TextView` is selectable **only in highlight mode**; then
  it consumes touches, so taps don't turn pages (swipes still do) and the bars
  stay up. Scrolling over a selectable `TextView` still works because Compose
  delivers moves to the `LazyColumn` before the interop view.
- A seek is a `SeekRequest(offset, token, exact)`: `exact` puts that offset
  at the top (restore, chapters, bookmarks); otherwise the page / line that
  contains it (search, progress bar, highlights).

## Key decisions & gotchas

- **Fonts.** Android has font families, not the iOS named faces:
  `ReaderFont` lists the system families (Korean falls back to Noto Serif /
  Sans CJK). EPUB embedded fonts are loaded with `Typeface.createFromFile`
  (TTF/OTF only — no WOFF) and the first one that covers the text is used
  when "Use book's original font" is on.
- **Import titles.** Parsers without metadata return the *storage* file name
  (a UUID). `BookRepository.finishImport` replaces a blank or UUID title
  with the name the user's file had — don't remove that check.
- **Room.** Enum columns need `Converters`. Progress, folder and cover
  updates are column-level `@Query` updates, never a whole-row `@Update`
  from a possibly stale `Book`. Schema changes need a real `Migration`
  (destructive fallback only covers pre-v2 development builds).
- **Cache versioning.** A parser output change means bumping the
  `parsed-vN` directory name in `FileStore`; books re-parse lazily on open.
- **TTS.** `TextToSpeech` has no pause: `pause()` stops and `resume()`
  restarts at the sentence that was being spoken. Chunks are whole sentences
  packed to 240 chars, 3 queued ahead; utterance ids carry a `generation` so
  callbacks from a flushed queue are ignored. The manifest `<queries>` entry
  for `TTS_SERVICE` is required on Android 11+ or no engine can be bound.
  Speech stops when the reader closes, never just because the app went to the
  background. The readers call `setBookLanguage(book.language)` on open; the
  per-language pick lives in DataStore `ttsVoice.<code>`, the old global
  `ttsVoice` only applies to books of unknown language.
- **PDF.** Pages are rendered with `PdfRenderer` (one open page at a time,
  all access under `rendererMutex`; bitmaps are dropped, never recycled,
  because a frame may still be drawing them). The text layer
  (`Page.getTextContents` / `searchText`) exists only on **Android 15+**, so
  PDF speech and search are offered only there.
- **Language detection.** `LanguageDetector` decides CJK by Unicode script
  counts exactly as iOS (Hangul → ko, kana → ja, Han → zh-Hans / zh-Hant by
  characters unique to one script). Android has no `NLLanguageRecognizer`, so
  other scripts map to their main language and Latin text is scored against
  common-word lists (unknown → "und"). `Book.language` is set on import;
  `BookRepository.detectMissingLanguages` (launched from `BooklipApplication`)
  tags older books once. PDFs before Android 15 have no text and stay `null`.
- **Drag selection.** Mirrors iOS `DragSelection.swift`, Photos-style: a drag
  that starts horizontally, or a long press (any direction), sweeps; the first
  cell decides select vs deselect and the whole index range anchor…finger gets
  it, with edge autoscroll. It reads pointers in the `Initial` pass and
  consumes them once engaged, so the list doesn't scroll and taps don't fire;
  in select mode `BookItem` has no `onLongClick`, so the long press is free.
- **`Book.charIndex`** is a character offset for text formats, a page index
  for PDF, and `Book.UNKNOWN_POSITION` (-1) when unknown → fall back to
  `progress`.
- **Compose / lifecycle versions.** Compose BOM 2024.06 (UI 1.6) with
  lifecycle 2.8: use `androidx.compose.ui.platform.LocalLifecycleOwner`
  (see `ReadingSession`), not `androidx.lifecycle.compose.*` effects — those
  crash with "LocalLifecycleOwner not present" on this combination.
- **Name clashes seen here.** A `var x` with `private set` plus a
  `fun setX()` is a JVM signature clash; inside `MediaSessionCompat.apply {}`
  `controller` means the session's own controller; importing the `List` icon
  unaliased shadows `kotlin.collections.List`.

## Cloud OAuth

`CloudConfig.kt` holds the client IDs and redirects. Dropbox uses the iOS
app key with its own redirect (`booklip://auth/dropbox/android`). OneDrive
uses the iOS Azure registration with an "Android" platform redirect
(`msauth://com.qbitcore.booklip/<signature hash>`; only the string has to
match what Azure lists, the app does not use MSAL). Google uses the project's
"Android" OAuth client with its reversed-client-id redirect, which needs
"Custom URI scheme" enabled in the client's advanced settings. Redirect
schemes are declared twice — `CloudConfig.kt` and the intent-filters of
`OAuthRedirectActivity` in the manifest (`booklip`, the reversed Google
client ID, `msauth`) — and the Google scheme must be re-derived whenever
`GOOGLE_CLIENT_ID` changes.

`OAuthRedirectActivity` hands the redirect to `OAuthRedirectBridge` and
brings `MainActivity` back with `CLEAR_TOP` to close the sign-in tab. Closing
the tab without signing in is detected by `MainActivity.onResume` →
`OAuthRedirectBridge.onHostResumed` (a flow still pending when the app is
back in front = cancelled), reported as `OAuthCancelledException`, which the
UI does not show as an error. A token that cannot be refreshed signs the
provider out.

## App icon

`res/mipmap-xxxhdpi/ic_launcher_foreground.png` and
`ic_launcher_monochrome.png` are generated from the iOS artwork
(`Assets.xcassets/iOS.appiconset/app_icon_light.png`), scaled to 70 % so the
book sits inside the adaptive-icon safe zone.

## Play Store screenshots

`PlayStore/screenshots/` mirrors the iOS `AppStore/screenshots/` pipeline:

- `seed.py` — replaces the debug app's data on the running emulator with the
  sample library (12 public-domain books, bookmarks, stats, sepia / Paper
  Book settings) by writing the Room database and DataStore files through
  `run-as`. Cloud sign-ins are left alone.
- `tools.sh` — `demo_on` / `demo_off` (clean 9:41 status bar),
  `tablet_on` / `tablet_off` (the phone emulator at 1600×2560, 320 dpi),
  `shot <name>` (writes `raw/<name>.png`), `ui`, `tapd`.
- `raw/{phone,tablet}_<slide>.png` — the nine captures per device.
- `compose.py [phone|tablet]` — headline + framed screenshot →
  `{ko,en}/{phone,tablet}/NN_<slide>.png`. Phone 1440×2880, tablet
  1600×2560 (Play allows a long side of at most 2× the short side).
