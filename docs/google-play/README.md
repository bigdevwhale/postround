# Google Play — всё для ручной публикации

Тексты лежат в `listing/<язык>/*.txt` (копируйте как есть), графика — в `graphics/`.
Ниже — что вписать в каждое поле Play Console и в каком порядке.

## 0. Перед стартом

- **Нужен AAB, не APK.** Релизный workflow кладёт в GitHub Release ещё и `PosteRestante-v<версия>.aab`.
  Первая версия для Play — **v1.2.1**. Секреты `SIGNING_*` в репозитории заданы; без них сборка
  подписывается debug-ключом, а такой файл Play отклонит.
- **Языки.** AAB собирается без разбиения по языкам (`bundle.language.enableSplit = false`), иначе Play
  ставит только язык телефона и переключение языка в настройках игры перестаёт работать.
  Не удаляйте этот блок.
- **Регистрация имени пакета.** Play Console при создании приложения пишет «You can't use this package
  name because it hasn't been registered». Порядок
  ([справка Google](https://support.google.com/googleplay/android-developer/answer/16761053)):
  1. Play Console → **Android developer verification** → **Package names** → **Register package name** → `app.postround`.
  2. **Select key** → ключ с SHA-256 `b305e866ff15118c15fc329d813595ac878150355c5398430625e7402997287d`
     (сертификат CN=Poste Restante, O=Stillcraft — им подписаны все релизы).
  3. **Upload APK** → скопировать сниппет, собрать релизный APK с файлом `assets/adi-registration.properties`,
     в котором этот сниппет, подписать тем же ключом и загрузить. Ключ есть только в секретах GitHub,
     так что APK собирает workflow **Package registration proof** (Actions → Run workflow → вставить сниппет),
     APK — в артефактах запуска.
  4. Дождаться письма о регистрации, потом создавать приложение.
- **Подпись (Play App Signing).** При первой загрузке выберите **«Использовать свой ключ»** и загрузите ключ
  из того же keystore через PEPK (инструкция в самом диалоге). Так `app.postround` остаётся за одним
  зарегистрированным ключом, а APK из GitHub и версия из Play обновляют друг друга.
  Если оставить «Google создаст ключ подписи», версии из Play и GitHub друг на друга не встанут.
- **versionCode** берётся из номера запуска workflow (`github.run_number`), он только растёт — Play это устраивает.
- **Новый личный аккаунт разработчика** (создан после ноября 2023): перед выходом в production нужен
  закрытый тест — минимум 12 тестировщиков, которые не выходят из теста 14 дней подряд.
- **Политика конфиденциальности** — `PRIVACY.md` в корне репозитория (на двух языках, с разделом
  про Health Connect). Ссылка: `https://github.com/bigdevwhale/postround/blob/main/PRIVACY.md` —
  та же, что открывает Health Connect из игры (`HealthPrivacy.kt`), так что не переносите файл.
- **Видео для фоновой службы.** Play требует ролик (YouTube, можно «доступ по ссылке») для разрешения
  `FOREGROUND_SERVICE_LOCATION`, см. раздел 4. Снимите его заранее: 20–40 секунд записи экрана —
  нажать «Выйти на обход», показать уведомление в шторке, выключить экран, пройти мимо дома с письмом,
  включить — письмо вручено, в уведомлении и журнале видно, что сказал жилец.

## 1. Создание приложения

| Поле | Значение |
|---|---|
| App name | `Poste Restante: GPS Postman` |
| Default language | English (United States) – en-US |
| App or game | **App** (см. «Категория» в разделе 3) |
| Free or paid | Free |
| Declarations | обе галочки (Developer Program Policies, US export laws) |

## 2. Store listing → Main store listing

| Поле | en-US | ru-RU (Add translations → Russian) |
|---|---|---|
| App name (≤30) | `listing/en-US/title.txt` | `listing/ru-RU/title.txt` |
| Short description (≤80) | `listing/en-US/short_description.txt` | `listing/ru-RU/short_description.txt` |
| Full description (≤4000) | `listing/en-US/full_description.txt` | `listing/ru-RU/full_description.txt` |
| App icon 512×512 | `graphics/icon-512.png` | та же |
| Feature graphic 1024×500 | `graphics/feature-graphic-en.png` | `graphics/feature-graphic-ru.png` |
| Phone screenshots (2–8) | `graphics/screenshots-en/1…8.png` | `graphics/screenshots-ru/1…8.png` |
| Tablet screenshots | можно не загружать | — |
| Video | пусто | — |

Скриншоты 1080×1920 собраны `tools/play.py` из рендеров README (`docs/screenshots/<язык>`, демо —
Петроградка): Play не берёт картинки длиннее двух ширин, поэтому экраны карты уходят за нижний край
под подписью, а у листов (дверь, письмо, утро) оставлена только карточка. Как пересобрать:

```bash
./gradlew testDebugUnitTest -Prender --tests '*RenderArtScreens*' && python tools/shots.py
python tools/play.py   # карта, дверь, сумка, окошко, письмо без адреса, письмо почтальону, марки, утро
```

## 3. Store settings

| Поле | Значение |
|---|---|
| App category | Application → **Health & Fitness** |
| Tags (до 5) | Activity tracker, Health & fitness, Workout, Maps & navigation, Entertainment |
| Email address | *ваш публичный email* — обязательно, его увидят все |
| Website | `https://bigdevwhale.github.io/stillcraft/apps/postround/` |
| Phone | пусто |
| External marketing | можно оставить включённым |

**Категория.** «До востребования» — игра, но приходят в неё за прогулками, как в Fogwalk и Липовку:
в Health & Fitness её найдут по «ходьба», «шагомер», «гулять». Альтернатива — Game → **Adventure**
(рядом с GPS-играми) или **Casual**; тогда в разделе 1 выберите Game, анкета IARC будет игровой, ответы те же.
Категорию можно сменить позже в Store settings.

**Теги** — из списка Play для приложений. Activity tracker и Health & fitness — шаги и пройденные километры;
Workout — туда Play относит приложения для ходьбы; Maps & navigation — настоящая карта района с маршрутом;
Entertainment — истории жильцов. Running и Weight loss не подходят: про бег и похудение игра не говорит.

**Имя разработчика** (Settings → Developer account) — **Stillcraft**, одинаковое для всех приложений:
тогда Play сам покажет под карточкой «Другие приложения этого разработчика».

## 4. App content (Policy → App content)

**Privacy policy** — URL выше.

**App access** — *All functionality is available without any access restrictions.*

**Ads** — *No, my app does not contain ads.*

**Content rating** (анкета IARC)
- Email — ваш; категория — **All Other App Types** (для Game — игровая).
- На все вопросы — **No**: насилия, страха, сексуального контента, ненормативной лексики нет; в письмах
  бытовое — соседи, грибы, шахматы, ёлка на балконе; алкоголь, табак и наркотики не показаны; азартных
  игр и покупок нет; общения между пользователями и обмена контентом нет (жильцы выдуманные);
  местоположение другим пользователям не передаётся; браузера и поиска нет.
- Ожидаемый результат: Everyone / PEGI 3 / 0+.

**Target audience and content**
- Target age: **13–15, 16–17, 18 and over** (политика: игра не для детей младше 13).
  Младше 13 не отмечать: иначе действует политика Families, а игра водит гулять одного с GPS.
- *Could your store listing unintentionally appeal to children?* → **No**: пиксельная графика, но это
  прогулочная игра с письмами для взрослых, без детских героев в центре.

**News apps** — **No.**

**COVID-19 contact tracing and status apps** — My app is not a publicly available COVID-19 app.

**Data safety**
- Разработчику не уходит ничего: нет серверов, аналитики, отчётов о сбоях. Координаты, маршруты, шаги
  и игра лежат в локальной базе. С телефона уходят два запроса: тайлы карты района с OpenFreeMap
  (один раз при выборе дома или переезде) и погода из MET Norway по координатам дома, округлённым
  до 0,01° (около километра).
- По определению Google «сбор» — любая передача данных с устройства, поэтому честнее задекларировать:
  - *Does your app collect or share…?* → **Yes**.
  - *Is all of the user data collected by your app encrypted in transit?* → **Yes** (только HTTPS).
  - *Do you provide a way for users to request that their data is deleted?* → **No** (на сервере
    ничего не хранится; всё на телефоне удаляется вместе с приложением).
  - Data types: **Location → Approximate location** — *Collected*: Yes, *Shared*: No;
    *Processed ephemerally*: **Yes**; **Required** (без карты района игры нет);
    purpose: **App functionality**.
  - Больше ничего не отмечать: точное местоположение, шаги («Health and fitness → Fitness info»),
    маршруты и игра с устройства не уходят. Системное резервное копирование Android делает система,
    а не приложение, его не декларируем.
  - Итог в карточке: «Collects: Location», «No data shared with third parties».

**Government apps** — No.

**Financial features** — *My app doesn't provide any financial features.*

**Health apps** — отметьте **Activity and fitness** (шаги и прогулки). Медицинские функции и прочее — не отмечать.

**Health Connect** (появится после загрузки AAB: в манифесте `android.permission.health.READ_STEPS`)
- Разрешение: только **Steps — Read**. Записи нет, фонового чтения (`READ_HEALTH_DATA_IN_BACKGROUND`)
  в манифесте нет.
- Назначение (use case): **Fitness / Activity tracking**. Описание, если просят:
  > Poste Restante is a walking game: the player delivers made-up mail to real houses of their neighbourhood.
  > If the user chooses Health Connect as the step source in Settings, the app reads their daily step totals
  > to size the next morning's bag of mail and to show how far they walked. Steps are only read, never written,
  > stay on the device and are never shared or sent anywhere. The default source is the phone's own step counter.
- Политика конфиденциальности (раздел «Steps and Health Connect» в `PRIVACY.md`) это подтверждает,
  включая фразу про Limited Use. Экран с политикой Health Connect открывает сам (`HealthPrivacy`).

**Advertising ID** — *No* (рекламы и SDK аналитики нет, разрешения `AD_ID` в сборке нет).

**Foreground service permissions** (появится после загрузки AAB: targetSdk 36 и `FOREGROUND_SERVICE_LOCATION`)
- *What tasks require your app to use the FOREGROUND_SERVICE_LOCATION permission?* — в группе
  **Background location updates** четыре галочки: User-initiated location sharing, Navigation, Geofencing, Other.
  Отметить только **Other** в этой группе (с ⓘ): sharing — передача координат другим, Navigation — пошаговый
  маршрут, Geofencing — системные зоны. Нижнюю группу «Other tasks → Other» (задачи не про местоположение)
  не отмечать. Описание:
  > Poste Restante is a walking game in which mail is handed over only when the user stands at the house.
  > When the user taps "Start the round", a foreground service with a visible, ongoing notification keeps
  > receiving GPS positions, so letters are handed over at the doors while the phone is in a pocket and the
  > screen is off. The service only starts on the user's tap, stops when they end the round from the app or
  > the notification, and positions never leave the device.
- Ссылка на видео: `https://github.com/bigdevwhale/postround/blob/main/docs/google-play/foreground-service.mp4`
  (ролик `foreground-service.mp4` в этой папке: старт обхода, уведомление в шторке, игра свёрнута, остановка).

**Location permissions** — отдельной декларации не нужно: `ACCESS_BACKGROUND_LOCATION` не запрашивается,
местоположение берётся только при открытой игре или в начатом пользователем обходе с уведомлением.

**Прочие разрешения** — особых деклараций не нужно: `ACTIVITY_RECOGNITION` (шагомер телефона),
`POST_NOTIFICATIONS`, `INTERNET` не относятся к чувствительным, как и `RECEIVE_BOOT_COMPLETED`,
`WAKE_LOCK` и `ACCESS_NETWORK_STATE`, которые добавляет WorkManager. Точных будильников (exact alarm)
нет — шаги раз в 15 минут считывает WorkManager.

## 5. Pricing & distribution

- Countries / regions: все доступные.
- Free, без встроенных покупок.

## 6. Релиз

1. Testing → **Internal testing** (или Closed testing, если аккаунт новый) → Create new release.
2. Загрузить `PosteRestante-v1.2.1.aab` из GitHub Release.
3. Release name: `1.2.1`. Release notes:

```
<en-US>
(listing/en-US/release_notes.txt)
</en-US>
<ru-RU>
(listing/ru-RU/release_notes.txt)
</ru-RU>
```

4. Review release → Start rollout. Первая проверка Google обычно занимает от нескольких дней до недели;
   с фоновой службой по местоположению и Health Connect — может дольше.
5. После установки из Play проверьте: Настройки → Язык → Русский/English — экран должен
   переключиться сразу; «Шаги» → Health Connect открывает диалог разрешения; «Выйти на обход» —
   в шторке появляется уведомление.

## Следующие версии

- `release_notes.txt` переписать под новую версию (для первой там «on Google Play»).
- Скриншоты пересобирать, только если поменялись показанные экраны.

## Риск: OpenFreeMap и MET Norway

Оба сервиса бесплатные и разрешают приложения с собственным User-Agent (он задан). Карта качается один раз
на игрока, так что нагрузка маленькая. Если игра разойдётся, MET Norway попросит соблюдать кеш
(`Expires`/`If-Modified-Since`), а OpenFreeMap — не превышать разумный объём; ссылки «© OpenStreetMap
contributors», OpenFreeMap и MET Norway есть в настройках.

## После публикации

Добавьте в README бейдж и ссылку `https://play.google.com/store/apps/details?id=app.postround`.
