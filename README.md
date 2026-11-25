# Googlebook IPFSMedia

A music and video player for Googlebooks (Android 17 laptops with a touchscreen, a trackpad and a
keyboard). It plays uploads that were published to Filecoin, indexed on Arkiv, and gated by
DataDAO membership.

Built on Material 3 Expressive. Underneath it is haven-mobile's storage and unlock code.



## What it does

* **Library.** Every album and video the connected wallet can open, with its access state and
whether it is on the device. Collection, favourites, playlists, history and search are local.
* **Albums and songs.** An album is one audio file. Its songs are the chapters inside that file,
so they appear once the album has been fetched; after that a song can be played, queued,
favourited or put in a playlist on its own.
* **DataDAOs.** A directory, a page per DataDAO (Music, Video, Discussion, Uploaders, About), and
the membership rule in words. No prices, balances or places to buy.
* **Upload feed.** Home shows the newest uploads from your DataDAOs and the uploaders you follow.
It refreshes when the app opens and when you ask (pull down, the Refresh button, Ctrl+R). It
never polls.
* **Comments.** Read-only, and shown only where there are some.
* **Moving things around.** Long-press or right-click any item for one list of places it can go;
carry it onto a playlist, the queue or the player bar; reorder rows by their grip. Every move
can be undone.
* **Touch, mouse and keyboard** are all first-class. Every shortcut is listed in Settings and in
the system's Keyboard Shortcuts Helper.

## What it deliberately does not do

* **No local files.** Playback is only through the local content-addressed cache (`foc-cache`).
Nothing is opened from, or dragged in from, the file system.
* **Nothing about what you play leaves the device.** History is a local table. Following is a
local list. There are no accounts, analytics or ads.
* **No Cast.** A cast receiver would have to fetch from a server; this app has none.
* **No writing** of comments, threads or votes (the interface exists as a stub that says so).
* **No token launches, trading, prices or balances.**

## Building

Builds run in CI only (`.github/workflows/android.yml`): JDK 17, Android SDK platform 37, NDK
`29.0.14206865`, and a Rust toolchain for the key-unwrapping library.

The workflow reads these from the repository's settings:

|Name|Kind|What for|
|-|-|-|
|`WALLET\_PROJECT\_ID`|secret|Reown / WalletConnect project id. Without it the app builds, but no wallet can connect, and the app is wallet-first.|
|`WALLET\_APP\_URL`|variable, optional|The page wallets show as this app's home.|
|`ARKIV\_API\_KEY`|secret, optional|Raises the Arkiv read quota.|

The APK is uploaded as the `app-debug` artifact. It is debug-signed; there is no release signing.

The x86_64 APK, for an Android emulator or a ChromeOS device, is built with
`./gradlew :app:assembleDebug -Pabi=x86_64` and uploaded as the
`app-debug-x86_64` artifact. `-Pabi=arm64-v8a,x86_64` builds one APK per ABI;
without `-Pabi` the APK stays universal.

To build elsewhere, copy `local.properties.example` to `local.properties`, fill it in, build the
native library with `bash tools/build-vetkeys-android.sh`, then `./gradlew :app:assembleDebug`.

* Application id: `app.ipfsmedia.player`
* `minSdk` 29, `targetSdk` and `compileSdk` 37

## Where things are

|Path|What|
|-|-|
|`app/`|The activity, the shell (sidebar, content, queue, player bar) and the one action dispatcher|
|`feature-\*/`|One module per screen or group of screens|
|`core-design/`|Tokens, theme, shared components, the drag layer|
|`core-library/`|The product model: albums, songs, DataDAOs, the feed, search, drag payloads and their destinations|
|`core-player/`|Fetch-and-unlock staging, the playback service, the queue, downloads|
|`core-social/`|The refresh, and reading discussion|
|`core-userdata/`|Per-identity local data: playlists, favourites, history, follows, pins|
|`core-navigation/`|Routes and per-section history|
|the other `core-\*/`|Copied from haven-mobile; see `docs/UPSTREAM.md`|
|`docs/PLAN.md`|The design this implements|
|`docs/ARCHITECTURE.md`|How the pieces fit|
|`docs/WORKLOG.md`|Decisions made while implementing, and what is unverified|

## Licence

This repository's own licence is the one at its root in the hosting repository. Code copied from
other projects, and their licences, are listed in `NOTICE` and `docs/UPSTREAM.md`.


# Arkiv API key: now baked via secrets.ARKIV_API_KEY (rebuilt 2026-11-25)
