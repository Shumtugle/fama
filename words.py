# The interface's words, in English, keyed by a stable id.
# This one table makes the English inside the source (Words.java, from Words.tmpl)
# and the empty template a new language starts from (modules/template.txt).
# Every language module in modules/ is filled by hand against that template;
# this script says which keys a module still lacks.
import os
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

W = [
 ('board', "what's on"),
 ('empty_title', 'nothing is announced yet'),
 ('empty_what', 'share a channel here from the messenger, and whatever it announces will stand on this board by itself'),
 ('quiet_title', 'no meetings ahead'),
 ('quiet_what', 'the channels are read, and none of them names a day still to come'),
 ('first_read', 'reading the channels for the first time'),
 ('ahead_n', 'ahead: {n}'),
 ('read_at', 'read at {t}'),
 ('reading_n', 'reading {n} of {m}'),
 ('today', 'today'),
 ('tomorrow', 'tomorrow'),
 ('day_1', 'monday'),
 ('day_2', 'tuesday'),
 ('day_3', 'wednesday'),
 ('day_4', 'thursday'),
 ('day_5', 'friday'),
 ('day_6', 'saturday'),
 ('day_7', 'sunday'),
 ('month_1', 'january'),
 ('month_2', 'february'),
 ('month_3', 'march'),
 ('month_4', 'april'),
 ('month_5', 'may'),
 ('month_6', 'june'),
 ('month_7', 'july'),
 ('month_8', 'august'),
 ('month_9', 'september'),
 ('month_10', 'october'),
 ('month_11', 'november'),
 ('month_12', 'december'),
 ('cancelled', 'called off'),
 ('moved', 'moved'),
 ('open_post', 'open the announcement'),
 ('open_channel', 'open the channel'),
 ('share', 'share'),
 ('hide', 'take off the board'),
 ('hidden', 'taken off the board'),
 ('post', 'the announcement'),
 ('posted_on', 'posted {d}'),
 ('to_calendar', 'to the calendar'),
 ('links', 'links'),
 ('no_words', 'the words of this announcement come with the next reading'),
 ('settings', 'settings'),
 ('sources', 'channels'),
 ('sources_what', 'where the board is gathered from; a tag keeps only the posts that carry it'),
 ('no_sources', 'no channels yet: share one from the messenger'),
 ('tag_none', 'no tag: every post is weighed'),
 ('tag_is', 'tag: {t}'),
 ('tag_learned', 'noticed by itself: {t}'),
 ('tag_hint', 'a tag, like #announcement'),
 ('tag_set', 'the tag is set'),
 ('tag_off', 'the tag is taken off'),
 ('found_n', '{n} ahead'),
 ('state_closed', 'cannot be read: the channel has no public page'),
 ('state_silent', 'did not answer: check the network or the tunnel'),
 ('state_wait', 'not read yet'),
 ('state_group', 'a group, not a channel'),
 ('group_hint', 'a group has no open wall: its announcements come here one by one, shared as links to their messages'),
 ('remove', 'remove'),
 ('remove_sure', 'remove?'),
 ('removed', 'the channel is removed'),
 ('lexicon', 'words'),
 ('lexicon_what', 'what makes a post an announcement, and what turns it away'),
 ('lexicon_about', 'A word is as large as the number of announcements it found at the last reading. Touch a word to bring it forward; touch it again to change it.'),
 ('lexicon_calls', 'they call'),
 ('lexicon_calls_what', 'they add weight, as the word for a meeting does'),
 ('lexicon_away', 'they turn away'),
 ('lexicon_away_what', 'they take weight off: reports of what has already been'),
 ('word_add', 'add a word'),
 ('word_hint', 'a word or a phrase: master class'),
 ('word_on', 'switch on'),
 ('word_off', 'switch off'),
 ('word_remove', 'take away'),
 ('word_removed', 'the word is taken away; reading again'),
 ('word_switched_on', 'the word is on; reading again'),
 ('word_switched_off', 'the word is off; reading again'),
 ('word_added', 'the word is added; reading again'),
 ('word_short', 'too short: three letters at least'),
 ('word_there', 'this word is already there'),
 ('words_diff', 'meetings: +{a}, −{r}'),
 ('words_same', 'the board is the same'),
 ('look', 'colour'),
 ('look_what', 'one seed, and every colour grows from it'),
 ('hue', 'hue'),
 ('richness', 'richness'),
 ('wallpaper', 'from the wallpaper'),
 ('sw_primary', 'primary'),
 ('sw_secondary', 'secondary'),
 ('sw_tertiary', 'tertiary'),
 ('sw_ground', 'ground'),
 ('language', 'language'),
 ('language_what', 'english lives inside; every other language is a module'),
 ('english', 'english'),
 ('words_of', '{n} of {m} words'),
 ('module_load', 'load a module'),
 ('module_save', 'save the template'),
 ('use_english', 'use english'),
 ('module_taken', 'the language is taken'),
 ('module_bad', 'this is not a language module'),
 ('module_saved', 'the template is saved'),
 ('unsaved', 'it could not be saved'),
 ('stale', 'words whose english has changed since the module was made: {n}'),
 ('carry', 'carry over'),
 ('carry_what', 'channels, meetings sent by hand, what was taken off, and the colour, as one text'),
 ('carry_about', 'Everything the board knows by itself: the channels with their tags, the meetings sent by hand, the meetings taken off, and the colour. Meetings from channels are not carried: the channels will tell them again.'),
 ('carry_n', 'channels: {c}  ·  by hand: {b}  ·  taken off: {h}'),
 ('carry_save', 'save to a file'),
 ('carry_load', 'load from a file'),
 ('carry_saved', 'the board is saved'),
 ('carry_sheet', 'channels not yet on the board come onto it and are read; what is already there stays as it is'),
 ('carry_take', 'carry over'),
 ('carry_done', 'channels carried over: {c}'),
 ('carry_bad', 'this file holds neither a carried board nor a channel'),
 ('many_n', 'channels in the text: {n}'),
 ('many_there', 'already on the board: {n}'),
 ('add_all', 'add them all'),
 ('about', 'about'),
 ('about_what', 'what the name means, how the board works, what it understands'),
 ('about_version', 'version {v}  ·  MIT licence'),
 ('about_name_h', 'the name'),
 ('about_name', 'Fama is Latin for what is said of a thing: report, rumour, and fame. In Virgil she has as many tongues as feathers and runs through the city telling what has happened, and the painters give her a trumpet. Here she tells only what the channels themselves announce.'),
 ('about_how_h', 'how it works'),
 ('about_how', "There is no server and no account. The phone itself opens the public page of each channel, over whatever network it has, and weighs every post: a day still ahead is the spine of an announcement, and words that call people together, the channel's tag, an hour, a place and a title in quotes add their weight. A meeting called off is crossed out, a moved one is marked. The board reads the channels again when it is opened after half an hour, and keeps an announcement's words only until its day has passed."),
 ('about_links_h', 'what it understands'),
 ('link_channel', 'a public channel: read whole, and read again'),
 ('link_handle', 'the same, by its short name'),
 ('link_message', 'one message; from a group, the only way in'),
 ('link_closed', 'invitations and closed chats: they have no public page and are not read'),
 ('link_folder', 'a folder: not read whole; its channels one by one'),
 ('link_list', 'any text that names several channels: all of them at once'),
 ('link_carry', 'a carried board: channels, meetings sent by hand, what was taken off, the colour'),
 ('about_lang_h', 'languages'),
 ('about_lang', 'The interface speaks English by itself; Russian travels inside as a module and is taken up on a phone set to Russian; any other language is a module filled in by hand, under Settings, Language. Announcements are read in Russian: the words for meetings, days, months and places lie in a file beside the code. English announcements are next.'),
 ('about_not_h', 'what it does not do'),
 ('about_not', "It signs in nowhere, reads no closed channel and no group's talk, and reads no network that shows its pages only to those who have signed in."),
 ('about_home', 'the source'),
 ('log', 'journal'),
 ('log_what', 'what the application did, to send when something goes wrong'),
 ('log_copy', 'copy the journal'),
 ('log_before', 'the run before'),
 ('copied', 'copied'),
 ('take_reading', 'reading the channel'),
 ('take_add', 'to the board'),
 ('take_there', 'already on the board'),
 ('take_added', 'on the board'),
 ('take_next', 'the nearest'),
 ('take_none', 'no meetings ahead yet; it will be read again'),
 ('take_closed', 'a closed channel has no public page; share its announcements one by one'),
 ('take_folder', 'a folder is not read whole yet; share its channels one by one'),
 ('take_group', "this is a group, not a channel: a group's talk has no open wall. Share its announcements here one by one, as links to their messages"),
 ('take_group_none', 'this is a group, not a channel, and the message did not read or names no day ahead'),
 ('take_silent', 'the channel did not answer; check the network or the tunnel'),
 ('take_no_day', 'no day ahead is named in this text'),
 ('take_note', 'an announcement'),
 ('by_hand', 'by hand'),
 ('close', 'close'),
 ('add_channel', 'add a channel'),
 ('add_hint', "a channel's link or @name"),
 ('added', 'the channel is added'),
 ('not_channel', "this is neither a channel's link nor its name"),
 ('take_unread', "the channel's page did not read just now; it can go on the board, and will be read again"),
]

ids = [w[0] for w in W]
assert len(ids) == len(set(ids))


def jq(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def rows(items):
    out, line = [], "       "
    for it in items:
        piece = " " + it + ","
        if len(line) + len(piece) > 98:
            out.append(line)
            line = "       "
        line += piece
    out.append(line)
    return "\n".join(out)


java = open(os.path.join(HERE, 'Words.tmpl')).read()
java = java.replace('@@IDS@@', rows([jq(i) for i in ids]))
java = java.replace('@@EN@@', "\n".join("        " + jq(w[1]) + "," for w in W))
open(os.path.join(ROOT, 'src/io/github/shumtugle/fama/Words.java'), 'w').write(java)

head = "# a language for this board\n# fill the empty lines and load the file back\n\n"
template = head + "module untitled\n\n" + "".join("# %s\n%s\t\n\n" % (e, k) for k, e in W)
open(os.path.join(ROOT, 'modules/template.txt'), 'w').write(template)

for name in sorted(os.listdir(os.path.join(ROOT, 'modules'))):
    if not name.endswith('.txt') or name == 'template.txt':
        continue
    have = set()
    for line in open(os.path.join(ROOT, 'modules', name), encoding='utf-8'):
        if '\t' in line and not line.startswith('#'):
            key, value = line.rstrip('\n').split('\t', 1)
            if value.strip():
                have.add(key.strip())
    missing = [i for i in ids if i not in have]
    print("%s: %d of %d%s" % (name, len(ids) - len(missing), len(ids),
                              "" if not missing else ", missing " + " ".join(missing)))
