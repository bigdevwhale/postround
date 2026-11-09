# Poste Restante — Privacy Policy

*Last updated: 9 November 2026*

Poste Restante is a walking game for Android: you deliver made-up mail to the real houses of your own neighbourhood.
It is published as open source at <https://github.com/bigdevwhale/postround>.

## What we collect

**Nothing.** Poste Restante has no accounts, no servers of its own, no analytics, no advertising and no crash reporting.
Nothing you do in the game reaches us.

## Location

The game uses your location while it is open, and during a round you start yourself (with a visible notification),
to know which house you stand at so mail can be handed over, and to draw your position on the map.
It does not ask for background location. Your positions and routes are stored only on the phone.

Two requests leave the phone, both over HTTPS:

- **The district map.** When you pick your home (or move house), the map tiles for the area
  around it, a few kilometres across, are downloaded once from OpenFreeMap (<https://openfreemap.org>, OpenStreetMap data). The tile server
  sees which area was requested, as any map does.
- **The weather.** Your home's coordinates, rounded to two decimal places (about a kilometre), are sent to MET Norway
  (<https://api.met.no>) for the forecast.

Neither service gets anything else, and neither request carries an identifier of you or your phone.

## Steps and Health Connect

The morning bag follows how much you walk. Steps come from one source you choose in Settings:

- **The phone's step counter** (the Physical activity permission). The daily totals are kept on the phone.
- **Health Connect**, only if you turn it on. The game asks only to **read steps** (`READ_STEPS`) and reads your
  daily step totals. It never writes to Health Connect, never reads any other data type, and never sends steps
  anywhere or shares them with anyone. Steps are used only to size the morning bag and to show how far you walked.
  You can revoke the permission in Health Connect or switch the source in Settings at any time.

Use of information received from Health Connect adheres to the Health Connect Permissions policy, including the
Limited Use requirements.

## What stays on your device

In the app's private storage: your home and the district map, the residents and their stories, the mail in the bag,
the delivery log and what people said, stamps, grades, daily walked distance and steps, and your settings.

## Sharing

- **Telegram links** in Settings (bug reports, wishes, the community) open Telegram or your browser only when you
  tap them; what you do there is governed by those services' own policies.
- **Backups.** Android may include the app's data in your own device backup to your Google account, if you have
  that turned on in system settings.

## Deleting your data

Uninstalling the app deletes everything it stored. Nothing is kept anywhere else.

## Children

Poste Restante is not directed at children under 13.

## Changes

Any change to this policy will be published on this page with a new date.

## Contact

Questions: open an issue at <https://github.com/bigdevwhale/postround/issues> or write to the StillCraft bot on
Telegram, <https://t.me/ArcaneWhisperBot>.

---

# До востребования — Политика конфиденциальности

*Обновлено: 9 ноября 2026*

«До востребования» — игра-прогулка для Android: вы разносите выдуманную почту по настоящим домам своего района.
Код открыт: <https://github.com/bigdevwhale/postround>.

## Что мы собираем

**Ничего.** В игре нет аккаунтов, собственных серверов, аналитики, рекламы и отчётов о сбоях.
Ничего из того, что вы делаете в игре, к нам не попадает.

## Местоположение

Игра узнаёт, где вы, пока она открыта и во время обхода, который вы начинаете сами (с видимым уведомлением):
чтобы понять, у какого дома вы стоите, вручить письмо и показать вас на карте. Фонового доступа к местоположению
она не запрашивает. Ваши координаты и маршруты хранятся только на телефоне.

С телефона уходят два запроса, оба по HTTPS:

- **Карта района.** Когда вы выбираете дом (или переезжаете), тайлы карты на несколько километров вокруг
  него один раз скачиваются с OpenFreeMap (<https://openfreemap.org>, данные OpenStreetMap). Сервер видит,
  какой участок карты запрошен, как и у любой карты.
- **Погода.** Координаты дома, округлённые до двух знаков после запятой (около километра), уходят в MET Norway
  (<https://api.met.no>) за прогнозом.

Больше эти сервисы ничего не получают, и ни в одном запросе нет идентификатора вас или телефона.

## Шаги и Health Connect

Утренняя сумка зависит от того, сколько вы ходите. Шаги берутся из одного источника, который вы выбираете в настройках:

- **Шагомер телефона** (разрешение «Физическая активность»). Суммы за день хранятся на телефоне.
- **Health Connect** — только если вы его включите. Игра просит только **чтение шагов** (`READ_STEPS`) и читает
  суммы шагов за день. Она ничего не записывает в Health Connect, не читает других данных и никуда не отправляет
  и ни с кем не делит шаги. Шаги нужны только для размера утренней сумки и чтобы показать, сколько вы прошли.
  Разрешение можно отозвать в Health Connect или сменить источник в настройках в любой момент.

Данные из Health Connect используются в соответствии с политикой разрешений Health Connect, включая требования
ограниченного использования (Limited Use).

## Что хранится на устройстве

В собственном хранилище приложения: дом и карта района, жильцы и их истории, письма в сумке, журнал вручений
и ответы жильцов, марки, разряды, пройденное за день и шаги, настройки.

## Ссылки и обмен

- **Ссылки на Telegram** в настройках (ошибки, пожелания, сообщество) открывают Telegram или браузер, только когда
  вы на них нажимаете; там действуют правила этих сервисов.
- **Резервная копия.** Android может включить данные приложения в резервную копию устройства в вашем
  аккаунте Google, если она включена в настройках.

## Удаление данных

При удалении приложения стирается всё, что оно хранило. Больше нигде ничего не остаётся.

## Дети

Игра не предназначена для детей младше 13 лет.

## Изменения

Изменения этой политики публикуются на этой странице с новой датой.

## Контакты

Вопросы — через issue: <https://github.com/bigdevwhale/postround/issues> или боту StillCraft в Telegram:
<https://t.me/ArcaneWhisperBot>.
