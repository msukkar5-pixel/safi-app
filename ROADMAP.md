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
