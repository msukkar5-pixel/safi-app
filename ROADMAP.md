# صافي — الشغل الجاي (محفوظ علشان نكمل بعدين)

## اتعمل
- ترجمة التطبيق (عربي/إنجليزي/أوردو)، ثيم الخشوع، رئيسية جديدة، القرآن المسموع، كتب إسلامية مسموعة، إصلاح فتح الكتب، مراجعة وإصلاح باجات كتير.

## لسه
1. الحسابات في مكان واحد (FinanceScreen): المصاريف، تحويلات مصر، الالتزامات، السلف، الادخار، الدروس، التقارير، الزكاة + سعر العملة + زرار "+" واحد للإدخال.
2. السيارة في مكان واحد (VehicleScreen): دور السواقة + العربية + الصيانة الدورية (الزيت بالكيلو والتاريخ) + خطط وتعديلات. DB: safi_car.db
3. المستندات: أنواع قابلة للتعديل، صفحات متعددة، سكانر ML Kit، كاميرا، تنزيل PDF. DB: safi_docs.db
4. المكتبة: البداية والنهاية + القصص والسير + الأحاديث + الكتب المسموعة جوه المكتبة.
5. تنبيهات قابلة للضبط: الأذكار (وقت ثابت أو بعد الصلاة)، الورد لو ماخلصش، قبل الأذان. AlertsScreen.
6. الصحة: دقات القلب والضغط والأكسجين (Health Connect + يدوي) + نصايح مربوطة بالحالة الصحية. DB: safi_vitals.db
7. إضافات: متابعة الصلوات، التقويم الهجري والمناسبات والصيام، أسماء الله الحسنى. DB: safi_faith.db
8. لعبة مسابقة (عامة + دينية) احترافية: مراحل، تايمر، مساعدات، تحدي يومي، XP.
9. بعد كل ده: ترجمة النصوص الجديدة، اختبار كل الشاشات على الإيموليتر، دمج dev في main ونشر النسخة.

المرجع الهندسي للتعليمات: brief في المحادثة (Room بدون migrations — أي جدول جديد في DB جديدة).

## Session of 2026-10-05 — state when handed over

Done and pushed on dev:
- i18n: 364 new strings translated (en/ur).
- Quiz: 490 questions (29 more hard ones).
- Content in `app/src/main/assets/deen/`: umrah.json, hajj.json, ruqyah.json, hisn.json (133 chapters, audio per chapter), media.json (videos, books, checklists, Hajj day plan).
  - Built by `tools/deen/build.py`; every hadith quote is checked against the six books (fawazahmed0/hadith-api, raw.githubusercontent.com) and every ayah is pulled from quran-api `ara-quransimple`. Sources: put them in a folder and set `DEEN_SRC` (see build.py docstring). The build fails if a quote isn't found.
- Library: 8 new OpenITI books (cat `hajj`, `adhkar`) in tools/books.json; categories added in faith/Books.kt.

WIP in this commit (NOT yet compiled — check CI first):
- `ui/Reading.kt`: eye-comfort reading mode (ReadPrefs, ReadingTheme, ReadingSettingsButton, ReadingSheet, ImmersiveEffect).
- QuranScreen: 4 display modes (mushaf pages / continuous / ayah-by-ayah / with tafsir), auto-scroll, double-tap focus mode. Quran.kt: viewMode, quranFont, autoSpeed.
- ReadingTheme applied to Book reader, Hadith section/favourites, Story detail, Zikr reader.
  - Still to do: Book/Azkar text sizes should read `rememberReadStyle().size(...)`/`lineH(...)` so they recompose on change.

Next (user requests, in order):
1. Screens for the deen content: Hajj & Umrah hub (`hajj`, `umrah` routes), guide renderer for all block types (p,h,steps,list,ayah,hadith,dua,note,warn,tip,diagram,qa,table,video,link,longtext), diagrams in Canvas (umrah_flow, miqat, ihram, tawaf, sai, hajj_days, nusuk_types, arafah, day10, jamarat, ruqyah_how, tahseen_day), tools (tawaf/sai/rami counters, hajjplan checklist), Hisn al-Muslim screen (groups, search, counters, chapter audio), Ruqyah screen. Add to More, Library and Assistant open_screen; uiplan steps.
2. Sleep section: sleep adhkar, audio duas and ruqyah, calm recitation, sleep timer — rich.
3. Global mini player (home + all screens) whenever any audio plays (audiobook, Quran, duas, radio): play/pause/stop.
4. Sunni Islamic radio — as MANY stations as available (mp3quran.net radios API + official Quran radios: Cairo, Saudi, Sharjah…).
5. Free Sunni Islamic TV channels — as MANY as available (e.g., Saudi Quran & Sunnah channels; verify each stream).
6. Merge dev → main to release.

## Session of 2026-10-05 (cont.) — branch `claude/kind-cannon-ljnz50` (based on dev)

Done (each pushed and built on CI):
- WIP from the previous session compiles (dev run 35 green). Book/Azkar text sizes now use `rememberReadStyle()`.
- Next 1 — Deen screens: `manasik` hub, `umrah` / `hajj` / `ruqyah` guides (all block types, search, continue-where-you-left, books/videos/surahs), 12 diagrams in `ui/DeenDiagrams.kt`, tools `tool/{tawaf,sai,rami,hajjplan,checklist}`, `hisn` + `hisn/{i}` (groups, search, favourites, counters, chapter audio). Code: `faith/Deen.kt`, `ui/screens/DeenScreens.kt`.
- Next 2 — `sleep`: Hisn sleep chapters (+ audio playlist), Sunnah-before-sleep hadiths taken from the verified ruqyah guide, ruqyah surahs + calm recitation (mp3quran reciters), sleep timer (`Library.sleepAt`).
- Next 3 — global mini player: `audio/NowPlaying.kt` (one app-wide MediaController) + `ui/MiniPlayer.kt` above the bottom bar on every screen.
- Next 4 — `radio`: official stations in `assets/media/radio.json` + every mp3quran.net radio (cached a day).
- Next 5 — `tv`: Saudi Quran & Sunnah channels in-app (HLS via media3-exoplayer-hls / media3-ui), YouTube fallbacks.
  - `tools/check_streams.py` runs in CI before the build and drops dead radio/TV links from the APK (the dev container can't reach the streams).
- Diagrams/tool tips quote only text that already exists in the verified `assets/deen` content; everything else is described, not quoted.
- en/ur translations added for all the new strings (and the reading-mode / Quran-mode WIP strings).

Still to do:
- More TV channels: only add a stream after CI shows it alive (check_streams output in the build log).
- Look at the emulator screenshots of the new screens (uiplan has steps for them) and fix anything off.
- Next 6 — merge into dev, then dev → main to publish the release (needs Mohamed's go-ahead).

## Session of 2026-10-06 — user feedback round (branch `claude/kind-cannon-ljnz50`, based on dev after v1.0.42)

Done (all built on CI, quick emulator plan passed with no crashes):
- Stories/Seerah tabs show their own books (BookList was cached per first tab); one catalog refresh per run, atomic write.
- Books: each section keeps its books; Library shows only the rest. +59 OpenITI books (paths verified) in aqeedah, tafsir, hadith, fiqh, tazkiya, family, thought, adab, history, seerah, sahaba. books.yml timeout 150 min.
- Audiobooks: topics in all fields of life + a strict blocklist (unbelief, other scriptures/sects, magic, romance, music).
- Voice: Google recognition service preferred, mic level, fallback to the system speech screen.
- Prayer: auto location → country calculation method + place time zone; re-scheduled on app start and TIMEZONE_CHANGED.
- Home: finance quick actions moved into Finance; prayer strip, round shortcuts, verse of the day, Ramadan card.
- Languages: 21; non-shipped ones translated on the phone (ML Kit, from en.json). APK ships arm64 + x86_64 only.
- Bottom bar: 6 places, Home/Safi/More fixed + 3 user-chosen (More → tune icon).
- Radio: tools/radios.py checks every mp3quran station in CI (173/177 live) + 5 official; TV: 16 live Sunni channels.
- Quiz: 2104 questions (gen_verified.py from Quran/Nawawi/Asma data, gen_world.py), balanced picks, 3 lives, 60-second race, ladder, challenge a friend, 12 badges, confetti, sounds.
- Kids "مدينة الخير" (all year), Ramadan layer (auto in Ramadan, countdown otherwise), family linking without a server (QR + AES-GCM cards, home Wi-Fi or any messenger).

Not done on purpose: the schools/teachers/admin platform from the Manus document (needs a server and multi-tenant accounts).
Next: merge into dev → main to release (with Mohamed's go-ahead).

### Same branch — follow-up (family roles, friends challenge, kids, more Manus ideas)
- Family: roles زوج/زوجة/أب/أم/ابن/ابنة/أخ/أخت/جد/جدة; the invite QR carries the inviter's intro, the joiner shows a reply QR (or same Wi-Fi does it) so each appears on the other's phone at once; per-member local label ("صفته عندي"); family khatma (30 juz claimed/marked, merged via cards, rounds).
- Friends challenge from anywhere (`quiz/Challenge.kt`): code `SAFI-CH1:` with seed + question ids, sent by any messenger; shared into Safi or pasted/clipboard; reply code records W/D/L per friend. No server (phones behind mobile NAT can't serve each other).
- Kids: 6 new games (`KidsGames.kt`: lanterns catch, maze, colour by number, Arabic letters, prayers order + rak'ahs, counting); stories (`assets/kids/stories.json`, ar/en/ur): 8 value stories with a question + 3 choose-your-path. Original educational fiction, no hadith or attributions.
- Ramadan screen: daily good-deed challenge, khatma plan 30/15/10 days (sets Wird.pagesPerDay), private notebook with prompts, charity log, share card (verse of the day + Maghrib, `ui/ShareCard.kt`), suhoor/iftar alerts (FaithAlerts "suhoor"/"iftar", Ramadan days only; also in Alerts screen).

### Same branch — later additions (all pushed on `claude/kind-cannon-ljnz50`, not merged yet)
Built and emulator-tested OK up to c871afd; later commits were pushed and CI was running (check the latest run on the branch).
- App guide (`assets/guide.json` ar/en/ur, `AppGuideScreen`, route `app_guide`, Settings top card + More).
- Kids: 32 games (`KidsGames.kt` engines + `assets/kids/games.json` packs), 30 stories (`assets/kids/stories.json`: 23 value + 7 choose-your-path), "game & story of the day" (Ramadan calendar: day n).
- Daily social post (`social/Social.kt`, `SocialScreen`, route `social`): verified ayah / Bukhari-Muslim hadith (only "قال رسول الله ﷺ" sayings) / Hisn dua as an image card; every 1/2/3/6/24 h; auto to Telegram channel, Facebook Page, X (user's own keys, stored as key_* so backups strip them); one-tap share elsewhere; log.
- Share sheet for any verse/hadith/dua (`ui/ShareSheet.kt`, `ShareBus.open`): story (1080x1920) / post image / text to WhatsApp, Facebook, Instagram, Snapchat, Telegram, TikTok; optional Meta App ID for direct story intents.
- Kid mode (`kids/KidMode.kt`, `KidModeScreens.kt`, routes `kidhome`/`kidsetup`): child's phone shows only parent-chosen sections; PIN (salted hash); setup on the phone or by QR/text code from the parent's phone (also joins the family and links the same study student); route guard in MainActivity; screen time (daily limit, bedtime, Quran stays open), reward requests approved by parent.
- Lessons & homework (`study/Study.kt`, `StudyScreen`, route `study`): timetable, homework with notes/due + evening "did you do it?" with Done action, study timer, exams/grades; merges (newest wins) inside the family card; weekly report for parents (Fri 8 pm).
- Kids TV (`assets/media/kidstv.json`, `TvScreen(kids=true)`, route `kidstv`): Arabic family-content channels only, CI-pruned, parent can hide channels; cleartext only for the Ajyal host (`res/xml/network_security_config.xml`).
- Quran memorization (`faith/Hifz.kt`, `HifzScreen`, route `hifz`): everyayah.com verse audio with repeats, hidden-word test, spaced review.
- Family list (`family/FamilyLists.kt`, `FamilyListsScreen`, route `familylists`): shared shopping + dates with reminders, synced in the family card.
- Nearby mosques (`faith/Mosques.kt`, Overpass/OSM, route `mosques`) and SOS (`safety/Sos.kt`, route `sos`, SEND_SMS optional, app shortcut).
- Fixed: LazyColumn lists not refreshing after Family/KidMode/Study changes (read the version state inside the list lambda).

- Kids' library: 9 authentic books in `tools/books.json` cat `kids` (Nawawi 40, Tuhfat al-Atfal, Jazariyya, Thalathat al-Usul, Shurut al-Salat, Adab al-Mashy, Mukhtasar al-Shamail, Mukhtasar al-Sira, Fada'il al-Quran); shown only in Kids → «مكتبة الأطفال». The `books` release is rebuilt by books.yml on push to dev/main (or dispatch) — needed before they appear in the app.
- Arab countries' history: 41 more OpenITI books (cats saudi, sham, palestine, lebanon, iraq, yemen, sudan, maghrib, tunisia, mauritania, andalus) in History (route `history`) as country tabs; Algeria/Libya/Jordan/Gulf have no verified free book yet ("others" tab shows general history).
- Picture-book stories: every story page / choice node has a `scene` ("background|emojis") drawn by `StoryScene` (Canvas backgrounds + animated emoji figures).

Next: check the latest CI run on the branch, fix any compile error, look at the screenshots; then release (PR branch → dev, then dev → main; Mohamed merges PRs himself; books.yml runs on the dev merge).
