# WA Backup (Personal)

Ekta **personal-use** WhatsApp backup system:

- **Android app** (`app/`) — phone er WhatsApp backup file gulo dhore (copy kore) tomar **nijer server**-e upload kore.
- **Server** (`server/server.py`) — encrypted backup receive, verify (token + SHA-256), ar store kore.

## ⚠️ Jeta age thik korte hobe (technical reality)

WhatsApp er live database (`msgstore.db`) WhatsApp er **private sandbox** e thake
(`/data/data/com.whatsapp/databases/`). **Kono normal Android app seta directly copy korte pare na** — eta Android security, amr app er limitation na. Tai app ti ei supported path gulo use kore:

| Source | Ki lage | Ki pao |
|---|---|---|
| 1. WhatsApp er nijer backup (Settings → Chats → Chat backup → Back Up) | "All files access" permission (ba folder pick) | `msgstore.crypt14` (fully encrypted DB!) |
| 2. Chat export (chat → ⋮ → More → Export chat) | Documents/Download access | `.zip`/`.txt` per-chat |
| 3. **Rooted phone** option (app e ON korte hoy) | root (`su`) | live `msgstore.db` direct |

Mane **root chara-o tumi WhatsApp er encrypted database backup ta server-e peye jao** (option 1) — setai full DB, shudhu WhatsApp encryption e locked (tar jonno WhatsApp backup password mone rakhbe!).

## Security / privacy notes (important)

- **Swechhe rakho:** onno karo chat copy kora = illegal & WhatsApp ToS violation. Eita shudhu **tomar nijer** data-r jonno, tomar **nijer** phone ar **nijer** server.
- Server e upload korar **ageo** file ta AES-256-CBC diye encrypt hoy (key = token theke PBKDF2). Tai server hack holeo plaintext chat ber hobey na. Decrypt: `server/decrypt_backup.py`.
- Token share keno koro na; HTTPS (TLS) chara server chalio na.

## Build (Android app)

```bash
# Android Studio open korle-i hobe; CLI:
cd wa-backup
./gradlew assembleDebug   # gradle wrapper add korle
# ba: gradle :app:assembleDebug  (system gradle 8.x + Android SDK thakle)
```
APK tarpor nijer phone e install koro (personal use — Play Store e dibe na, MANAGE_EXTERNAL_STORAGE er jonno allowed na).

## Server setup

```bash
export WA_BACKUP_TOKEN="$(python3 -c 'import secrets;print(secrets.token_hex(32))')"
python3 server/server.py --port 8443
# production: nginx/caddy reverse proxy with TLS, or --tls-cert/--tls-key
# systemd unit ready: server/wa-backup.service
curl -H "Authorization: Bearer $WA_BACKUP_TOKEN" https://your-server/api/health
```

Same token ta app er "Auth token" field e dao, ar server URL dao (https://...)।

## App flow

### Mode A — "Auto mode" (SOHOSU server chara, PC te chat) — recommended for you
1. APK install koro → **All files access** permission dao → **"Auto mode"** switch ON → **Save & schedule**.
2. WhatsApp e Settings → Chats → Chat backup → "Back up to device" ekbar chalu koro (daily auto-o korte paro).
3. App protok interval-e WhatsApp er backup file gulo phone er **`Documents/wa-auto/`** folder e collect korbe (notification dekhabe). Server, token, URL — kichui lagbe na.
4. PC te cable diye phone connect kore:
   ```bash
   python3 server/pc_puller.py            # ekbar pull (adb) -> server/wa_chats/<tarikh>/
   python3 server/pc_puller.py --watch    # protok 5 minik cholte thake
   ```
   (pc_puller khud-i `Documents/wa-auto` + direct WhatsApp Databases folder duitai dhore; chaile manually-o: `adb pull /sdcard/Documents/wa-auto ./`)
5. Optional live archive: pc_puller WhatsApp Web (Chrome remote debugging port 9222) theke message snapshot-o save kore — README er header note dekho. Na chaile `--no-web`.

### Mode A+ — Auto mode + Server (USB cable chara, internet diye PC te chat) — **recommended, no USB**
Ei mode e phone internet pele backup file-gulo server-e upload kore, ar PC-internet pele
server theke automatic niche name ney — konodin cable lagbe na।
1. Server chalu koro (Mode B er moto ekbar-i setup):
   ```bash
   export WA_BACKUP_TOKEN="$(python3 -c 'import secrets;print(secrets.token_hex(32))')"
   python3 server/server.py --port 8443        # TLS reverse proxy er pashhe rakhun
   ```
2. App e: **Auto mode ON** + server URL + same token dao → Save & schedule.
   (App tokhon `Documents/wa-auto/` e collect KOREO, server-e upload KOREO।)
3. PC te ekbar config dao:
   ```bash
   cp server/puller_config.example.json server/puller_config.json
   # ei duita value fill koro: server_url ar token
   ```
4. PC te puller chalu kore rakho (boot e start korte cron/systemd task banao):
   ```bash
   python3 server/pc_puller.py --watch --no-adb --no-web
   ```
   Protok 5 minute-e notun backup phone theke server diye tomar PC-r
   `server/wa_chats/<tarikh>/` e chole ashbe — USB chara, full over internet.
5. Pore decrypt korte: `python3 server/decrypt_backup.py <file> --token <TOKEN>`।

### Mode B — Server upload (internet-er onno machine theke-o access lagle)
1. App kholo → server URL + token dao → **Test connection** → **Save & schedule**.
2. **Grant all-files access** button → Settings theke allow koro (na chaile **Pick WhatsApp folder** — SAF alternative).
3. WhatsApp e ekbar manual **Chat backup** banao (button "How to create..." te step ache).
4. **Backup now** — immediate run; tarpor protidin/interval onujayi WorkManager periodic job cholbe (Wi-Fi only option ache), reboot korle-o auto re-arm hoy.
5. Server e file dekhte: `GET /api/list` (token lagbe) — files `backups/<device-id>/` e jam hobe.

## Restore

- `.crypt14` file ta phone e fire ene same path e rekhe WhatsApp reinstall → "Restore" khulle cholbe (same Google/backup password lagbe)।
- Nijer decryption: `python3 server/decrypt_backup.py stored_file.bin out.crypt14 --token "$WA_BACKUP_TOKEN"`

## Layout

```
wa-backup/
├── settings.gradle, build.gradle
├── app/                      # Android (Kotlin, minSdk 26)
│   └── src/main/java/com/personal/wabackup/
│       ├── BackupWorker.kt           # WorkManager job: find→stage→encrypt→upload
│       ├── BootReceiver.kt           # reboot e schedule re-arm
│       ├── ui/MainActivity.kt        # config UI
│       └── util/{BackupSourceFinder,UploadClient,SettingsStore,BackupScheduler,RootHelper}.kt
└── server/
    ├── server.py             # stdlib-only HTTPS receiver
    ├── decrypt_backup.py     # your side decryption tool
    └── wa-backup.service     # systemd unit
```
