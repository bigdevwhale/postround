# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

## [1.3.0] - 2026-11-20

### Added
- **The save as a file:** Settings → the save → "Save" and "Load". All the progress in one plain JSON file, for a new phone or to mend by hand; on another phone the district map is downloaded again by itself (#23, #16).
- **The keepsake box** in the poste restante window: the gifts people gave the postman, who gave them and when, repeats counted (#11).
- **Turning a telegram down:** one that isn't on your way can go back to the day shift; it counts neither way (#12).

### Changed
- **The letter with no address:** not found in two days or after five doors, Galina finds Mrs Somers' mark and names the very house, with "On the map" and "No house there" for a house the map has wrong (the letter then finds another) (#20).
- **The delivery log** keeps three months instead of a week (#17).
- New telegrams pick a house on the way home rather than the other way (#12).

### Fixed
- A telegram from a story no longer comes in the night bag: it waits for the day shift, 8:00–21:00. Mail promised for the day comes only while the bag has room; the rest waits until there is some (#24).

<!-- tg
Прогресс можно сохранить в файл и загрузить на другом телефоне: настройки → «Сохранение»
Письмо без адреса больше не мучает: через пару дней Галина назовёт точный дом, а если его нет на месте – письмо найдёт другой адрес
Телеграмму не по пути можно отдать дневной смене, в «Окошке» появилась шкатулка подарков, журнал вручений хранит три месяца
Исправлено: телеграммы из историй не приходят ночью и в полную сумку
-->

## [1.2.3] - 2026-11-16

### Fixed
- A sudden power-off (a flat battery, a frozen phone) could leave the save empty, and the game then started over. The save is now forced to the disk before it replaces the old one, the previous save is kept beside it as a spare, and a damaged save falls back to that spare instead of a new game. The district map is written the same way.

<!-- tg
Поправили сохранение: если телефон внезапно выключится (сел аккумулятор, завис), игра больше не может начаться заново.
Прогресс записывается на диск надёжно, а рядом хранится прошлое сохранение на всякий случай.
-->

## [1.2.2] - 2026-11-13

### Fixed
- After moving house the map kept the picture of the old district until the app was restarted, while the houses, their outlines and taps already followed the new one: the map looked stitched from several districts (#13), and the lit windows and street lamps glowed in the wrong places (#9). The map is now drawn again for the new district.

<!-- tg
Исправили карту после переезда: она больше не выглядит склеенной из разных районов, дома стоят там, где их подсвечивает нажатие.
Свет окон и фонарей снова на своих местах.
-->

## [1.2.1] - 2026-11-09

### Added
- Each release also carries PosteRestante-v<version>.aab for Google Play, with every language inside so the in-app switch keeps working.
- Settings → Community has a "Support Poste Restante" line: it opens the StillCraft support page.
- The privacy policy in English and Russian, with what leaves the phone and how steps from Health Connect are used.

<!-- tg skip -->

## [1.2.0] - 2026-11-08

- **«Can't get to this house»** on a house's card: no more mail goes there, and what was due goes to a neighbour. Houses inside works, railway yards, military land and garage rows are left out of the round by themselves (#4).
- **Moving house:** Settings → «Move here» draws the round again around where you stand. Stories, street stamps, the journal and the Poste Restante letters stay; the people of the stories, the paper's subscribers and the mail in the bag find new houses (#5).

<!-- tg
Если к дому не подойти, его можно отметить «Сюда не пройти» – письма туда больше не пойдут, а заводы и закрытые территории исключаются сами.
Переехали? Настройки → «Переехать сюда»: участок нарисуется заново, а истории, марки и журнал останутся.
-->

## [1.1.0]

- The panel under the map folds away with a button at the top left of the map, and stays folded between launches; folded, it shows only the hand-over buttons, a telegram and a house you tap (#1).
- The delivery log: every handing-over of the last seven days, house by house and with the time, with what the mail said and what was said at the door (#3). Open it from the map panel, the poste restante window or the morning sheet ("what was delivered yesterday").
- When a round ends, by the button or from the notification, a summary of it: how much, to how many houses, how far, new stamps, and every house to read again (#2).

<!-- tg
Панель под картой теперь сворачивается: кнопка «Свернуть» в левом верхнем углу карты.
Журнал вручений: кому и что вы вручили за последнюю неделю, с текстами писем и ответами жильцов.
После обхода — короткая сводка: сколько разнесли, куда и что вам сказали.
-->

## [1.0.0]

The first version.

- Your real district from OpenStreetMap with its house numbers; made-up residents in every house; a round that grows with the letters you deliver.
- The morning bag: letters, postcards, bills, magazines, parcels and papers, sized by how much you've walked lately.
- Delivery by GPS within 30 metres of the house, never from a vehicle; the round in a pocket hands mail over by itself and keeps what people said for later.
- 60 stories between houses for a year of play, seasonal ones in their season; red threads on the map; replies written at the door; parcels to take to the post office; a game of chess by post.
- Letters with no address found by the house's signs and by asking; telegrams with a deadline; a stamp drawn for every street; grades; the poste restante window.
- The map: tags over houses with mail, glowing outlines, today's route, arrows to mail beyond the screen, zoom, and a "?" legend of every icon.
- Steps from the phone's own counter or from Health Connect; Russian and English.

<!-- tg
«До востребования» — первая версия. Вы почтальон своего района: улицы и номера домов настоящие, жильцы выдуманные, а письмо вручается, только когда вы стоите у дома.
Жильцы переписываются между собой, и вы носите их письма туда и обратно. Шестьдесят историй на год, письма без адреса, телеграммы со сроком, марки улиц.
Это первая версия: если найдёте ошибку или придумаете, что улучшить, напишите — кнопки в настройках игры.
-->
