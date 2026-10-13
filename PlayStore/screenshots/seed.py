#!/usr/bin/env python3
"""Seed the debug build on a running emulator with public-domain sample books.

usage: seed.py [books_dir]

books_dir holds the sample EPUBs (and unsu.txt). By default the library the
iOS screenshot run left behind is used. Everything in the app is replaced:
books, folders, bookmarks, highlights, reading settings and reading stats.
The app must be a debuggable build (run-as).
"""
import glob, os, sqlite3, struct, subprocess, sys, tempfile, time, uuid, zipfile, re
from datetime import datetime, timedelta

PKG = "com.qbitcore.booklip"
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
BOOKS = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/Library/Containers/qbit-core.Booklip.screenshots/Data/Documents")
TMP = "/data/local/tmp/booklip_seed"

# (title in the EPUB's OPF, title shown, author, progress, days ago) — the iOS seed's library.
SPEC = [
    ("Pride and Prejudice", "Pride and Prejudice", "Jane Austen", 0.42, 1),
    ("Alice's Adventures in Wonderland", "Alice's Adventures in Wonderland", "Lewis Carroll", 1.0, 30),
    ("Frankenstein", "Frankenstein", "Mary Wollstonecraft Shelley", 0.18, 6),
    ("The Adventures of Sherlock Holmes", "The Adventures of Sherlock Holmes", "Arthur Conan Doyle", 0.63, 3),
    ("Moby Dick", "Moby Dick", "Herman Melville", 0.07, 12),
    ("A Tale of Two Cities", "A Tale of Two Cities", "Charles Dickens", 0.0, 20),
    ("The Picture of Dorian Gray", "The Picture of Dorian Gray", "Oscar Wilde", 0.55, 8),
    ("Dracula", "Dracula", "Bram Stoker", 0.12, 15),
    ("Treasure Island", "Treasure Island", "Robert Louis Stevenson", 1.0, 40),
    ("Dr. Jekyll and Mr. Hyde", "The Strange Case of Dr. Jekyll and Mr. Hyde", "Robert Louis Stevenson", 0.0, 4),
    ("Great Expectations", "Great Expectations", "Charles Dickens", 0.27, 10),
]
KOREAN = ("운수 좋은 날", "현진건", 0.31, 2)

BOOKMARKS = [  # on the first book
    (0.12, "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.", 4),
    (0.27, "I could easily forgive his pride, if he had not mortified mine.", 2),
    (0.41, "Till this moment I never knew myself.", 1),
]


def adb(*args, **kw):
    return subprocess.run([ADB, *args], check=True, capture_output=True, **kw).stdout


def run_as(command):
    return adb("shell", f"run-as {PKG} sh -c '{command}'")


def ms(days_ago):
    return int((time.time() - days_ago * 86400) * 1000)


def opf_title(path):
    z = zipfile.ZipFile(path)
    opf = re.search(r'full-path="([^"]+)"', z.read("META-INF/container.xml").decode()).group(1)
    m = re.search(r"<dc:title[^>]*>([^<]*)<", z.read(opf).decode("utf-8", "replace"))
    return m.group(1) if m else ""


def find_books():
    """SPEC entry -> (book path, cover path or None)."""
    epubs = {opf_title(p).lower(): p for p in glob.glob(os.path.join(BOOKS, "*.epub"))}
    found = []
    for key, title, author, progress, days in SPEC:
        path = next((p for t, p in epubs.items() if key.lower() in t), None)
        if path is None:
            print("missing:", key)
            continue
        cover = os.path.splitext(path)[0] + "_cover.img"
        found.append((path, cover if os.path.exists(cover) else None, title, author, "EPUB", progress, days))
    txt = next(iter(glob.glob(os.path.join(BOOKS, "*.txt"))), None)
    if txt:
        found.insert(1, (txt, None, KOREAN[0], KOREAN[1], "TXT", KOREAN[2], KOREAN[3]))
    return found


# DataStore Preferences files are a protobuf map<string, Value>.
def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def field(number, wire, payload):
    return varint(number << 3 | wire) + (varint(len(payload)) + payload if wire == 2 else payload)


def value(v):
    if isinstance(v, bool):
        return field(1, 0, varint(int(v)))
    if isinstance(v, float):
        return field(2, 5, struct.pack("<f", v))
    if isinstance(v, str):
        return field(5, 2, v.encode())
    if isinstance(v, set):
        return field(6, 2, b"".join(field(1, 2, s.encode()) for s in sorted(v)))
    if isinstance(v, tuple):  # ("double", x)
        return field(7, 1, struct.pack("<d", v[1]))
    raise TypeError(v)


def preferences(values):
    return b"".join(field(1, 2, field(1, 2, k.encode()) + field(2, 2, value(v))) for k, v in values.items())


def push_private(local, remote):
    """Copy a host file into the app's private storage."""
    name = os.path.basename(remote)
    adb("push", local, f"{TMP}/{name}")
    run_as(f"cp {TMP}/{name} {remote}")
    adb("shell", f"rm {TMP}/{name}")


def main():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", f"rm -rf {TMP}; mkdir -p {TMP}; chmod 777 {TMP}")
    work = tempfile.mkdtemp()

    # The database: pulled with its WAL so the schema Room created is intact.
    for suffix in ("", "-wal", "-shm"):
        data = adb("exec-out", f"run-as {PKG} cat databases/booklip.db{suffix} 2>/dev/null || true")
        if data:
            open(os.path.join(work, "booklip.db" + suffix), "wb").write(data)
    db = sqlite3.connect(os.path.join(work, "booklip.db"))
    for table in ("books", "folders", "bookmarks", "highlights"):
        db.execute(f"DELETE FROM {table}")

    run_as("rm -rf files/books files/covers files/parsed-v3; mkdir -p files/books files/covers files/datastore")
    first_id = None
    for path, cover, title, author, fmt, progress, days in find_books():
        book_id = str(uuid.uuid4())
        first_id = first_id or book_id
        file_name = f"{uuid.uuid4()}.{'epub' if fmt == 'EPUB' else 'txt'}"
        push_private(path, f"files/books/{file_name}")
        cover_name = None
        if cover:
            cover_name = file_name + ".cover"
            push_private(cover, f"files/covers/{cover_name}")
        db.execute(
            "INSERT INTO books (id, title, author, format, fileName, progress, dateAdded, wordCount, folderId,"
            " coverFileName, progressUpdated, charIndex) VALUES (?,?,?,?,?,?,?,?,NULL,?,?,-1)",
            (book_id, title, author, fmt, file_name, progress, ms(days), os.path.getsize(path) // 40, cover_name, ms(days - 0.5)),
        )
        print(f"{title}  ({'cover' if cover else 'no cover'})")
    for progress, snippet, days in BOOKMARKS:
        db.execute("INSERT INTO bookmarks (id, bookId, progress, snippet, date) VALUES (?,?,?,?,?)",
                   (str(uuid.uuid4()), first_id, progress, snippet, ms(days)))
    db.commit()
    db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    db.close()
    run_as("rm -f databases/booklip.db-wal databases/booklip.db-shm")
    push_private(os.path.join(work, "booklip.db"), "databases/booklip.db")

    settings = preferences({
        "font": "SERIF", "fontSize": 19.0, "lineSpacing": 8.0, "presetId": "sepia", "pageEffect": "PAPER",
        "useEmbeddedFont": True, "autoScrollSpeed": 40.0, "sortOption": "DATE_ADDED", "viewMode": "MEDIUM_GRID",
    })
    today = datetime.now()
    days = {(today - timedelta(days=i)).strftime("%Y-%m-%d") for i in list(range(14)) + [17, 18, 21, 25, 26, 30, 33]}
    stats = preferences({"totalSeconds": ("double", 47 * 3600 + 29 * 60.0), "readingDays": days})
    for name, blob in (("reading_settings", settings), ("reading_stats", stats)):
        local = os.path.join(work, name + ".preferences_pb")
        open(local, "wb").write(blob)
        push_private(local, f"files/datastore/{name}.preferences_pb")

    # So starting speech for the screenshot does not stop at a permission prompt.
    subprocess.run([ADB, "shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS"])
    adb("shell", f"rm -rf {TMP}")
    print("seeded")


main()
