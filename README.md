# Booklip for Android

[English](#english) · [한국어](#한국어)

## English

The Android version of Booklip, an offline-first ebook and text reader. Users
bring their own files (**.txt · .epub · .pdf · .md**). Written in Kotlin with
Jetpack Compose (Material 3); it uses only AndroidX libraries and the platform
SDK — no third-party dependencies. Behaviour follows the iOS app
([Booklip](https://github.com/qbit-core/Booklip)), and reading positions and
highlights use the same character offsets.

- Library: folders, sorting, grid/list views, multi-select with drag-to-select,
  search, language filter, covers
- Reader: font, size, line spacing and colour themes, vertical-slide or
  paper-book page turns, auto-scroll, table of contents, bookmarks, highlights,
  in-book search, dictionary lookup, reading-position restore
- TTS: voices in the book's language (English, Korean, Japanese, Chinese, …),
  speed and pitch, sentence highlight, sleep timer, background playback with
  lock-screen controls
- Cloud import: Dropbox, Google Drive, OneDrive
- Reading stats: time read, day streak

See [CLAUDE.md](CLAUDE.md) for build instructions, architecture and the cloud
sign-in setup, [PLAY_STORE.md](PLAY_STORE.md) for the Google Play listing, and
[WHATS_NEW.md](WHATS_NEW.md) for the next release's notes.

### Changelog

Compiled from the commit history, by date, newest first.

#### 2026-10-17
- TTS voice list shows only the book's language in its main region (an
  English book lists en-US voices, not every English-speaking country), and
  no longer adds other languages' voices once the language is known — as on
  iOS. "Automatic" uses the best-rated of those voices.

#### 2026-10-16
- **Version 0.9.3 (versionCode 6).**
- The app no longer ships native code: DataStore's
  `libdatastore_shared_counter.so`, used only by multi-process DataStore
  (Booklip's stores are single-process), is left out of the build. Google
  Play no longer asks for native debug symbols, which that prebuilt, stripped
  library could not provide anyway.
- **Version 0.9.2 (versionCode 5).**
- Books are tagged with their language when imported (English, Korean,
  Japanese, Simplified/Traditional Chinese, …), detected on-device from the
  text; books already in the library are detected once in the background.
  PDFs are detected on Android 15 and later, which have a PDF text layer.
- Library: a globe menu filters books by language, in All Books, Folders and
  folder screens.
- TTS picks the best voice in the book's language when a book opens (Japanese
  and Chinese voices are now offered too); a voice you choose is remembered
  for every book in that language.
- Multi-select: sweep across books to select or deselect a whole range — start
  with a sideways drag or a long press; the list scrolls at the top and bottom
  edges. Also in the cloud browser's Select mode.
- Added `WHATS_NEW.md`: Google Play release notes for this release in Korean,
  English, Simplified Chinese, Traditional Chinese and Japanese; the Play
  listing copy mentions the new features.

#### 2026-10-13
- **Version 0.9.1 (versionCode 4)**, target SDK 36.
- Release builds shrink code and resources with R8.
- Added the 512×512 store icon and 1024×500 feature graphics (Korean and
  English); `compose.py` gained a mode for landscape banners.

#### 2026-10-12
- Added the Google Play listing copy in English and Korean (`PLAY_STORE.md`,
  `PLAY_STORE.ko.md`): name, descriptions, release notes, and draft answers for
  the store settings, data safety form, permissions and the Google Drive OAuth
  justification.
- Cloud sign-in finished for Android: Google Drive uses the project's Android
  OAuth client, OneDrive the Azure Android redirect, Dropbox its own Android
  redirect.
- Added Play Store screenshots for phone (1440×2880) and tablet (1600×2560),
  nine slides in Korean and English, with the `seed.py` / `tools.sh` /
  `compose.py` pipeline.
- TTS voices are listed by name and language ("Yuna (ko-KR)", "Flo (en-US)")
  instead of engine ids.

#### 2026-10-09
- Define opens a dictionary dialog over the page instead of a web search,
  with entries from Korean or English Wiktionary; a trailing Korean particle is
  stripped before the lookup.

#### 2026-10-07
- Reader rebuilt to match iOS: positions, bookmarks, highlights and chapters
  are character offsets into the text; Paper Book pages never cut a line;
  Vertical Slide lays the book out in blocks. Added page numbers, in-book
  search, auto-scroll, range highlights, Define and exact position restore.
- EPUB: port of the iOS parser — nested table of contents, inline images,
  embedded fonts.
- TTS: sentence highlight that the page follows, automatic Korean/English
  voice, sleep timer, media notification and lock-screen controls.
- Library: All Books / Folders tabs, folder screens, search, saved sort and
  view mode, multi-select move/delete; imported books keep the file's name as
  their title.
- Cloud: Select mode for files and whole folders, sign-in cancel detection.
- PDF: paged and scrolling modes, bookmarks, and speech and search on
  Android 15+.
- App icon from the iOS artwork.

#### 2026-09-14
- First versions of bookmarks, highlights, reading stats, TTS, the PDF reader
  and cloud import (Dropbox, Google Drive, OneDrive).

#### 2026-09-12
- **Version 0.1.0.** Initial Kotlin / Jetpack Compose app: library (grid/list,
  sort, folders), TXT / Markdown / EPUB import, a scrolling reader with font
  and theme settings, Room and DataStore storage.

## 한국어

오프라인 우선 전자책·텍스트 리더 Booklip의 Android 버전입니다. 사용자가 가진
파일(**.txt · .epub · .pdf · .md**)을 가져와 읽습니다. Kotlin과 Jetpack
Compose(Material 3)로 작성했고 AndroidX 라이브러리와 플랫폼 SDK만 사용하며
외부 의존성은 없습니다. 동작은 iOS 앱([Booklip](https://github.com/qbit-core/Booklip))을
따르고, 읽던 위치와 하이라이트는 같은 문자 오프셋을 씁니다.

- 서재: 폴더, 정렬, 그리드/리스트 보기, 드래그 선택이 되는 다중 선택, 검색,
  언어 필터, 표지
- 리더: 글꼴·크기·줄 간격·색 테마, 세로 슬라이드 / 종이책 넘김, 자동 스크롤,
  목차, 책갈피, 하이라이트, 본문 검색, 사전 찾기, 읽던 위치 복원
- TTS: 책 언어에 맞는 음성(영어, 한국어, 일본어, 중국어 등), 속도·음높이,
  문장 하이라이트, 취침 타이머, 잠금 화면 컨트롤이 있는 백그라운드 재생
- 클라우드 가져오기: Dropbox, Google Drive, OneDrive
- 읽기 통계: 읽은 시간, 연속 일수

빌드 방법, 구조, 클라우드 로그인 설정은 [CLAUDE.md](CLAUDE.md), Google Play
등록 정보는 [PLAY_STORE.ko.md](PLAY_STORE.ko.md), 다음 릴리스 내역은
[WHATS_NEW.md](WHATS_NEW.md)를 참고하세요.

### 변경 이력

커밋 기록을 날짜별로 정리한 것입니다(최신 날짜부터).

#### 2026-10-17
- TTS 음성 목록에 책 언어의 대표 지역 음성만 표시합니다 (영어 책이면 모든
  영어권 국가가 아니라 en-US만). 언어를 감지한 뒤에는 다른 언어의 음성도
  더 이상 함께 표시하지 않습니다 (iOS와 동일). "자동"은 그중 품질이 가장 좋은
  음성을 씁니다.

#### 2026-10-16
- **버전 0.9.3 (versionCode 6).**
- 앱에 네이티브 코드가 더 이상 포함되지 않습니다: 다중 프로세스 DataStore에서만
  쓰는 DataStore의 `libdatastore_shared_counter.so`를 빌드에서 뺐습니다(Booklip의
  저장소는 모두 단일 프로세스). Google Play가 네이티브 디버그 기호를 요구하지
  않으며, 이미 기호가 제거된 채 배포되는 라이브러리라 어차피 제공할 수 없었습니다.
- **버전 0.9.2 (versionCode 5).**
- 책을 가져올 때 본문으로 언어(영어, 한국어, 일본어, 중국어 간체/번체 등)를
  기기에서 감지해 기록합니다. 이미 서재에 있는 책은 백그라운드에서 한 번
  감지합니다. PDF는 PDF 텍스트를 읽을 수 있는 Android 15 이상에서 감지합니다.
- 서재: 지구본 메뉴로 언어별 책 필터링 (전체 책·폴더·폴더 화면 모두).
- TTS가 책을 열 때 책 언어에 맞는 가장 좋은 음성을 자동 선택합니다
  (일본어·중국어 음성도 추가). 직접 고른 음성은 그 언어의 모든 책에 기억됩니다.
- 다중 선택: 책 위를 쓸어 범위 전체를 선택하거나 해제합니다. 옆으로 드래그하거나
  길게 눌러 시작하고, 위·아래 가장자리에서는 목록이 자동으로 스크롤됩니다.
  클라우드 브라우저의 선택 모드에서도 됩니다.
- `WHATS_NEW.md` 추가: 이번 릴리스의 Google Play 업데이트 내역을 한국어, 영어,
  중국어(간체), 중국어(번체), 일본어로 정리했습니다. Play 등록 정보에도 새
  기능을 반영했습니다.

#### 2026-10-13
- **버전 0.9.1 (versionCode 4)**, target SDK 36.
- 릴리스 빌드에서 R8로 코드와 리소스를 축소합니다.
- 512×512 스토어 아이콘과 1024×500 그래픽 이미지(한국어·영어) 추가.
  `compose.py`에 가로 배너 모드 추가.

#### 2026-10-12
- Google Play 등록 정보 문안을 영어·한국어로 추가(`PLAY_STORE.md`,
  `PLAY_STORE.ko.md`): 이름, 설명, 출시 노트, 스토어 설정·데이터 보안
  양식·권한·Google Drive OAuth 사유 답변 초안.
- Android용 클라우드 로그인 설정 완료: Google Drive는 프로젝트의 Android OAuth
  클라이언트, OneDrive는 Azure Android 리디렉션, Dropbox는 전용 Android
  리디렉션을 씁니다.
- 휴대폰(1440×2880)·태블릿(1600×2560) Play 스토어 스크린샷 추가: 한국어·영어
  9장씩, `seed.py` / `tools.sh` / `compose.py` 파이프라인.
- TTS 음성을 엔진 ID 대신 이름과 언어로 표시("Yuna (ko-KR)", "Flo (en-US)").

#### 2026-10-09
- 뜻 찾기가 웹 검색 대신 페이지 위 사전 대화상자로 열립니다. 한국어 또는 영어
  위키낱말사전 항목을 보여 주며, 끝의 한국어 조사는 떼고 찾습니다.

#### 2026-10-07
- iOS에 맞춰 리더를 다시 만들었습니다: 위치·책갈피·하이라이트·목차가 본문의
  문자 오프셋이고, 종이책 페이지는 줄을 자르지 않으며, 세로 슬라이드는 블록
  단위로 배치합니다. 쪽 번호, 본문 검색, 자동 스크롤, 범위 하이라이트, 뜻 찾기,
  정확한 위치 복원 추가.
- EPUB: iOS 파서 이식 — 중첩 목차, 본문 이미지, 내장 글꼴.
- TTS: 페이지가 따라가는 문장 하이라이트, 한국어/영어 자동 음성, 취침 타이머,
  미디어 알림과 잠금 화면 컨트롤.
- 서재: 전체 책 / 폴더 탭, 폴더 화면, 검색, 정렬·보기 방식 저장, 다중 선택
  이동·삭제. 가져온 책은 파일 이름을 제목으로 씁니다.
- 클라우드: 파일과 폴더를 고르는 선택 모드, 로그인 취소 감지.
- PDF: 페이지·스크롤 모드, 책갈피, Android 15 이상에서 음성 읽기와 검색.
- iOS 아트워크로 앱 아이콘 제작.

#### 2026-09-14
- 책갈피, 하이라이트, 읽기 통계, TTS, PDF 리더, 클라우드 가져오기(Dropbox,
  Google Drive, OneDrive) 첫 버전.

#### 2026-09-12
- **버전 0.1.0.** 최초 Kotlin / Jetpack Compose 앱: 서재(그리드/리스트, 정렬,
  폴더), TXT / Markdown / EPUB 가져오기, 글꼴·테마 설정이 있는 스크롤 리더,
  Room과 DataStore 저장.
