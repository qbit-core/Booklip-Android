# Google Play Listing (English)

Play shows the full description as plain text (no Markdown), so the blocks
below are ready to paste as they are.

## App name (30 char max)
```
Booklip — eBook Reader
```

Alternatives: `Booklip: EPUB & PDF Reader` / `Booklip — Book Reader`

## Short description (80 char max)
```
Read EPUB, PDF, TXT and Markdown your way — or listen with text-to-speech.
```

Alternatives:
- `A clean reader for your own books: EPUB, PDF, TXT. No account, no ads.`
- `Your books, your fonts, your themes. Read or listen. Nothing leaves your phone.`

## Full description (4000 char max)
```
Booklip is a clean, distraction-free reader for the books you already own. Open your own files, make every page look the way you like, and listen hands-free with built-in text-to-speech. No account, no subscription, no ads.

READ ANY FORMAT
Open EPUB, PDF, TXT and Markdown files. EPUB books keep their embedded fonts, illustrations and cover, so even specially typeset books look the way their makers intended. Korean, English and mixed-language books all read well, and text files saved in older Korean encodings (EUC-KR, CP949) are detected automatically.

MAKE IT YOURS
Choose the font, size and line spacing, and pick one of six themes — Default, Sepia, Dark, Forest, Ocean, Rose. Turn pages like a paper book with a tap or a swipe, or scroll the whole book in one continuous column.

LISTEN INSTEAD
Text-to-speech reads your books aloud in English and Korean, choosing the voice by the language of the text or using the one you pick. The sentence being read is highlighted and the page follows along. Adjust speed and pitch, set a sleep timer, and keep listening with the screen off — playback controls stay in the notification shade and on the lock screen. Auto-scroll keeps the page moving when you would rather read.

NEVER LOSE YOUR PLACE
Jump to any chapter from the table of contents, drop bookmarks, and highlight passages in four colors. Search inside the book and step through every match. Select a word to look it up in the dictionary without leaving the page. Your reading position is saved automatically, and the page number is always in view.

ORGANIZE YOUR LIBRARY
Group books into folders, sort them, switch between grid and list layouts, search by title or author, and select several books to move or delete at once. Real covers make every book easy to spot.

BRING BOOKS FROM THE CLOUD
Connect Dropbox, Google Drive or OneDrive and import straight from your folders. Pick single files, several at once, or a whole folder — it arrives as a folder in your library. Access is read-only: nothing in your cloud is ever changed or deleted, and imported books read offline.

TRACK YOUR READING
See your total time read, your day streak, and how many books you are reading or have finished.

EVERYTHING STAYS ON YOUR DEVICE
Books, settings, bookmarks, highlights and progress are stored only on your device. No account, no data collection, no ads. The network is used only when you connect a cloud service or look up a word.

Note: text-to-speech and search inside PDF files require Android 15 or later. Every other feature works on Android 8.0 and up.
```

## What's new (500 char max — first release)
```
First release on Android.
• EPUB, PDF, TXT and Markdown
• Paper-book pages or continuous scroll, six themes
• Text-to-speech with sentence highlighting, sleep timer and background playback
• Table of contents, bookmarks, highlights, search, dictionary
• Import from Dropbox, Google Drive and OneDrive
• Folders, sorting and reading stats
```

---

## Store settings

- **Category:** Books & Reference
- **Tags:** Ebook reader, PDF reader, Text to speech
- **Contains ads:** No
- **In-app purchases:** No
- **Target audience:** 13+ (a general reading tool; not directed at children)
- **Content rating questionnaire:** Reference / utility app, no user-generated
  or shared content, no restricted content.

## Data safety form

- **Does the app collect or share user data?** No.
  - Books, settings, bookmarks, highlights, progress and reading stats are
    stored only on the device.
  - Cloud sign-in tokens are stored only on the device and sent only to the
    provider the user connected (Dropbox / Google / Microsoft).
  - A word the user chooses to define is sent to Wiktionary to fetch its
    entry; nothing identifying the user is attached and nothing is stored.
- **Is data encrypted in transit?** Yes (HTTPS only).
- **Account creation:** None. **Data deletion:** uninstalling the app (or
  deleting a book) removes its data; there is nothing held on a server.

## Permissions (for the review notes)

- **Internet** — cloud import (only after the user connects a service) and
  dictionary look-ups.
- **Foreground service (media playback)** and **notifications** — keep
  text-to-speech playing with the screen off and show its play / pause / stop
  controls. The notification permission is requested the first time speech is
  started; speech works without it.

## Google Drive sign-in (OAuth verification)

The app requests `drive.readonly`, a restricted scope: it lists the user's
folders and downloads the files they pick, nothing else. If Google asks for a
justification: "Booklip lets the user browse their own Drive and import ebook
files (.epub, .pdf, .txt, .md) to read offline. Files are only read; nothing
is uploaded, modified or deleted, and no Drive data leaves the device."
