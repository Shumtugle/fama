# Fama

A board of meetings, gathered by itself from the public pages of channels.

Share a channel to Fama from the messenger it lives in, and whatever the
channel announces — a reading, a club, a talk — stands on the board by day
and by hour, with the book and the place. Nothing else has to be learned:
the board reads the channels again when it is opened and has not been read
for a while, and new meetings come in while it is looked at.

Fama, in the old poem, is Report: she has as many tongues as feathers, and
the painters give her a trumpet to carry what is said from one end of a city
to the other. Here she carries only what the channels themselves announce.

The page with the download: <https://shumtugle.github.io/fama/>

| Item | Value |
|---|---|
| Android | 8.0 and later (`minSdk 26`) |
| Package | `io.github.shumtugle.fama` |
| Licence | MIT |
| Dependencies | Android platform APIs only — no AndroidX, no Material Components, no Maven libraries |
| Build | `aapt`, `javac`, `dalvik-exchange`, `zipalign`, `apksigner`; one shell script |
| Network | the public pages of the channels you shared, and their pictures; nothing else |

## How it reads

**No server.** The board runs on the phone and reads only pages anyone can
open, on whatever network the phone has. There is no account, no list kept
anywhere but on the phone, and nobody's permission is asked or needed.

**A post is weighed, not matched.** A day still ahead is the spine of an
announcement; on it the other witnesses add their weight — a word that calls
people together, the channel's own tag, an hour, a place, a title in quotes.
A post that calls nobody and carries no tag must bring everything at once, so
a sale that ends on Friday is not a meeting.

**Dates the way people write them.** The twenty seventh of September, 27.09,
27/09/26, this Saturday, next Saturday, tomorrow, lists of days sharing one
month — all counted from the day the post was written.

**Forgiving words.** Case, the two spellings of one letter, grammatical
endings, a typo or two by length, two letters swapped, Latin look-alikes
inside a Cyrillic word, the wrong keyboard layout, a picture glued to a word,
a letter held down too long. Words of three letters or fewer are met only
whole.

**Paid posts and cancellations.** A paid post is thrown out by the mark the
law makes it wear. A meeting called off is kept, crossed through, and merged
with its announcement.

**Groups.** A group's talk has no public page, only its messages one by one:
the board tells a group from a channel, and takes a single message of a group
by its own link.

**Words of one's own.** Under Settings, Words, the words that call people
together and the words that turn a post away stand as a cloud, each as large
as the number of announcements it found at the last reading. A word of the
lexicon can be switched off, and stays off through updates; a word or a
phrase of one's own can be added, and is read strictly: by its start, with
its last letter or two dropped, never with a typo forgiven. After a change the
channels are read again, and the board says how many meetings came and went.
A report of something already past takes weight off: a post that says a
meeting took place is not an announcement for its sake alone.

**Tags.** A channel that marks its announcements with a tag of its own has it
noticed: only a tag that tells announcements from the rest is learned. A tag
can also be given by hand; then only posts that carry it are read.

## Carrying the board

The board travels as one text: a line that names it, then one line to a
thing — the look, each channel with its tag and name, each meeting taken off,
each meeting sent by hand. It can be shared to another phone or kept in a
file, and taking it in adds to the board already there without taking
anything away. Any other text that names several channels, a list pasted
from anywhere, adds them all at once.

## Beside the code

`assets/lexicon.txt` holds the words announcements are read by, one kind to a
line — months, days of the week, words that call people together, words for
places, for cancellations, for paid posts. `assets/ways.txt` holds the
addresses the board reads and the marks it finds its way through a page by.
Both are plain text: when a page changes or a new habit appears, the text is
mended, not the code.

## Language

The interface is English; every other language is a module — one text file,
filled in any editor and loaded in the settings. The Russian module travels
inside the package and is taken up by itself the first time the application
opens on a phone set to Russian. See `modules/`.

## Installing

Download the APK from the releases and open it on the phone. Every release is
signed with the same key; its certificate's SHA-256 is

```
CB:58:BA:68:06:24:A2:1D:C0:6C:8A:7E:6B:E8:78:C1:A2:0A:9A:72:27:F3:34:B7:0F:03:A3:79:38:14:AA:33
```

and `apksigner verify --print-certs fama-<version>.apk` shows it.

## Building

```sh
KEYSTORE=../keys/your.keystore KSPASS=<password> SDK=$HOME/sdk/android-33.jar \
  sh build.sh fama-<version>
```

`android-33.jar` is the platform's API stub, the one file the build needs
from outside.

`tools/words.py` makes `Words.java` and the empty module template from one
table of the interface's words, and says which words a module still lacks.
`tools/icon.py` draws the mark into `res/drawable`.
