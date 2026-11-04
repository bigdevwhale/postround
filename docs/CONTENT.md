# Content of «До востребования» / Poste Restante

All game texts live in `app/src/main/assets/content/*.tsv`, Russian and English side by side.
Files are UTF-8, tab-separated, the first line is a header, lines starting with `#` are comments.
A cell never contains a tab or a newline. `tools/check_content.py` validates everything.

## The world

The player is the postman of their own real district. Every real house with an address on
OpenStreetMap gets fictional residents. Each morning a bag arrives; letters are handed over only
when the player physically stands within ~30 m of the house (GPS). Residents write to each other,
so the player carries correspondence between real houses — between them a red thread is drawn on the map.

**Tone.** Warm, quiet, a little funny, never saccharine. Small lives: old people, children, neighbours,
shopkeepers. No politics, no religion, no brands, no real people, no tragedy porn. Sadness is allowed
if it is light and dignified. Russian must read native (not a calque from English): short sentences,
spoken intonation, real everyday words. Residents speak to the postman with «вы» (never gendered
forms about the postman: no «пришёл/пришла», «молодой человек/девушка»). English is a natural
adaptation, not a literal translation (names and places may change: «каток на Елагином» → "the rink on the island").

**Places.** Never name a real city district, street or shop: the game runs in any town. Far-away
senders may be from real cities (Novosibirsk, Kazan, Murmansk…) — in English they may stay or become
neutral ("up north").

## Files

### names.tsv — ordinary residents
`sex  age  ru  dat_ru  en`
- sex `m|f`, age `old|young`. `ru` nominative («Тамара Ивановна», «Денис»), `dat_ru` dative («Тамаре Ивановне», «Денису»).
- Old people have name + patronymic, young ones a first name only. `en`: "Mrs Hollis", "Dan".

### cast.tsv — characters of the stories
`id  pick  name_ru  dat_ru  name_en  role_ru  role_en  look`
- `pick` — which real house they move into, among homes on the player's round:
  `any` · `water` (nearest to water) · `park` (by a park) · `far:<id>` (far from that character) ·
  `near:<id>` (100–250 m from that character) · `shop` (a shop) · `inst` (a school/office).
- `look` — comma list for the pixel portrait: `hair=grey|wood|dark|ginger|honey`, `cloth=cream|green|jam|roofB|navy|apple|lilac|oak|honey|enamel|autumn|red`,
  flags `glasses beard moustache long bun curls pigtails earrings apron cap`, `kerchief=<cloth colour>`.

### arcs_meta.tsv — the stories
`arc  order  months  title_ru  title_en`
- Stories start one at a time, in `order`, every ~5 days (the first three faster), at most three running at once.
- `months` — empty (any time) or e.g. `12,1` (a New Year story starts only in December or January). A seasonal story
  waits for its season; later stories start meanwhile. Write it so it fits the start months and the next 2–4 weeks.
- A year of play is about 60 stories.

### arcs.tsv — the steps of every story
`arc  n  from  to  kind  extra  next  text_ru  text_en  say_ru  say_en  gift_ru  gift_en`
- `n` — 1, 2, 3… in order; the last row of an arc has `n = end`: a letter to the postman, put in the
  «До востребования» window the day after the story ends (`from` = signature, `to` empty).
- `from`, `to` — a cast id, or `ext:Аня, Новосибирск|Annie, Novosibirsk` for someone outside the district.
  `to = post` means a parcel the postman carries to the post office.
- `kind` — `letter` · `card` (an open postcard) · `parcel` · `telegram` · `chess` (a move in a game by mail) · `pickup`
  (the postman calls at the house and is handed a parcel to send: `extra` = destination `ru|en`; the story goes
  on once that parcel is taken to the post office).
- `extra` — a chess move in coordinates (`e2e4`) for `chess`; destination for `pickup`.
- `next` — when the following step comes: `now` (the addressee replies at the door, the reply goes straight into the bag)
  or a number of days (`1` = tomorrow's bag or the day after; `3` = three or four days later).
- `text` — what is written (the addressee reads it aloud at the door). Up to ~350 characters.
- `say` — what the addressee says or does at the door, in a short line. May be a stage direction in third person.
- `gift` — optional, a small thing the postman is given («пирожок с капустой»).

### fillers.tsv — the everyday mail
`kind  where  months  from_ru  from_en  text_ru  text_en  say_ru  say_en  gift_ru  gift_en`
- `kind` — `card` (open postcard: `text` shown), `bill` (closed letter: `text` empty), `mag` (magazine/newspaper), `parcel`.
- `where` — `home` · `shop` · `any`. `months` — empty or e.g. `12,1` (New Year cards only then).
- `say` — the addressee's reaction at the door, the heart of the line.

### telegrams.tsv
`text_ru  text_en  say_ru  say_en  late_ru  late_en`
- Telegram style: CAPITALS, «ТЧК», no commas. `late` — what they say if it came late.

### riddles.tsv — letters with no address
`memory_ru  memory_en  say_ru  say_en`
- Written by an old postwoman who carried this round for decades (signed nowhere). She remembers
  something about the house. `say` — how today's resident reacts. The clue (colour, storeys, what's near)
  is generated from the map.

### paper.tsv — newspaper subscribers
`who  n  ru  en`
- `who = arist` — the crossword man's saga, in order of `n`; `any` — any subscriber, picked at random.
