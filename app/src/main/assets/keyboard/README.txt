Word data for Uno Keyboard's suggestions and autocorrect.

en_words.txt  "word score" per line: the 80,000 most common English words, score = round(10 * ln(frequency)).
en_next.txt   "word<TAB>next1 next2 ..." per line: up to five of the most common words that follow it.

Source: SymSpell frequency dictionaries (en-80k.txt and frequency_bigramdictionary_en_243_342.txt),
https://github.com/wolfgarbe/SymSpell, MIT License, Copyright (c) 2018 Wolf Garbe, whose frequencies come from the
Google Books Ngram data intersected with hunspell word lists. Filtered to lower-case letters, single letters other
than a and i dropped, bigrams kept only when both words are in the list.

emoji.txt        every fully-qualified emoji of Unicode Emoji 16.0 without a skin tone (1,906), in Unicode's order, from emoji-test.txt
                 (https://unicode.org/Public/emoji/16.0/emoji-test.txt, Unicode License v3); the panel hides any the phone cannot draw.
emoji_words.txt  "emoji<TAB>name<TAB>keywords<TAB>t" per line, for every emoji in emoji.txt: its English name and keywords, and "t"
when it takes a skin tone. From the Unicode CLDR English annotations (cldr-json annotations and annotationsDerived),
https://github.com/unicode-org/cldr-json, Unicode License v3, Copyright (c) Unicode, Inc.

Nothing here is ever written to by the keyboard. What it learns from your typing is kept separately, on the phone only, in the app's
no-backup storage (see keyboard/PersonalStore.kt).
