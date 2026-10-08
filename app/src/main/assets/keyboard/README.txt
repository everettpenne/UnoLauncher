Word data for Uno Keyboard's suggestions and autocorrect.

en_words.txt  "word score" per line: the 80,000 most common English words, score = round(10 * ln(frequency)).
en_next.txt   "word<TAB>next1 next2 ..." per line: up to five of the most common words that follow it.

Source: SymSpell frequency dictionaries (en-80k.txt and frequency_bigramdictionary_en_243_342.txt),
https://github.com/wolfgarbe/SymSpell, MIT License, Copyright (c) 2018 Wolf Garbe, whose frequencies come from the
Google Books Ngram data intersected with hunspell word lists. Filtered to lower-case letters, single letters other
than a and i dropped, bigrams kept only when both words are in the list.

Nothing here is ever written to by the keyboard; it never learns from what is typed.
