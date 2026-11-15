# Googlebook IPFSMedia — plan

Status: revision 13, 2026-11-15, after your fourth round of decisions, the Navigation 3 recipes,
the Android UI survey and Google's Android skills. Nothing has been built or
installed, and nothing will be built on this laptop (§8).

## 1. What we are building

**Googlebook IPFSMedia**: a music and video player for Googlebooks (an Android app in a resizable
desktop window, used by touch, mouse and keyboard) that:

- looks and navigates like **Parachord**: left sidebar, gradient hero headers, card grids, filter
  chips, an always-dark player bar and a queue drawer;
- stores and reads media the way **haven-mobile** does: an Arkiv index mirrored into a per-identity
  Room database, content fetched by CID through `foc-cache`, decrypted on device, everything
  partitioned per wallet. This is the only way anything plays; there are no local files;
- plays **music and video** with one player and one queue;
- is organised around **DataDAOs**: every item belongs to one, membership decides what you can
  open, the feed is what DataDAOs and their members **upload**, and their discussion can be read;
- is built for **direct manipulation**: pick a song up with a finger or the mouse and put it in a
  playlist, the queue or your collection (§7).

## 2. Decisions (all settled)

| Topic | Decision |
|---|---|
| Name | Googlebook IPFSMedia |
| Package id | `app.ipfsmedia.player` (made up; free to change until the first public release) |
| Signing key | None. Builds are debug-signed and sideloaded; release signing is not planned (§14, row 9) |
| Wallet | Connect first, as in haven-mobile, to MetaMask installed on the Googlebook. The WalletConnect project id is yours to handle and is not a concern of this plan |
| Material Design | The latest: Material 3 Expressive components, adaptive layout and motion, carrying Parachord's palette and page structure (§6) |
| Navigation | Jetpack Navigation 3, following the patterns in `nav3-recipes-main` (§8) |
| State handling | Kotlin `StateFlow`, as the copied haven-mobile modules already use. Jetpack AppState is not adopted (§8) |
| Builds | None on this laptop. CI builds and tests everything and produces the APK |
| Spikes | None. The storage stack is taken as working on ARM and x86; the touch design is decided here (§7) |
| Platform | Googlebooks run Android 17; the app targets API 37 |
| Cast | Not a feature. It does not fit the content-addressed, local-first storage design: a Cast device cannot fetch or unlock that content itself (§15) |
| Local files | None. No file picker, folder import, file drops or "Open with". Playback is `foc-cache` only; offline means "already in the cache" |
| Listens | Never shared. No scrobbling, no presence, no Listen Along. A local History is fine |
| Following | Local. Followed addresses are stored on the device and published nowhere |
| Social feed | Uploads, DataDAO style. Not listening activity. It refreshes when the app opens and when you pull down or press Refresh, never on its own |
| Comments | Read-only, and not a focus: shown where they exist, absent where they do not. Writing (comments, threads, votes) is a stub only |
| Cover art | Not a concern. Embedded art when the file has it, a generated pattern otherwise |
| Design | Parachord's look. haven-mobile's design rules are ignored |
| haven-mobile code | `core-*` modules are copied into a new repo; the license is permissive |
| Tokens | No launches, trading, prices or balances. The design still shows that items belong to DataDAOs and that membership gates access |
| Interaction | Drag and drop **inside the interface** (a song into a playlist, and the like). A Googlebook has a touchscreen, so every interaction works by touch, and by mouse and keyboard as well |

## 3. What each reference contributes

| Reference | Used for | Not used |
|---|---|---|
| `perfect-sound-master` (MIT) | Googlebook target: resizable window with `<layout>` defaults, right-click menus, keyboard shortcuts, `onTopResumedActivityChanged`, Media3 `MediaSessionService`, queue persistence, its first-install check (install the way a user would) | Its local-file import, file drop target and "Open with"; its rule that only a mouse drags rows (here a finger drags too, after a long-press); its signing and Play release steps; Winamp-style UI; remote mode; audio capture; notification listener |
| `parachord-main` (MIT) | Visual design and information architecture: tokens, shell, heroes, cards, chips, player bar, queue drawer, skeletons, design-drift test; its drag-to-Collection and drag-to-reorder behaviour; a local History | All services, resolvers, plugins, marketplace, AI, scrobbling, friend activity, Listen Along, metadata providers, Discord, extension, DRM. Not its logo or wordmark |
| `haven-mobile-main` | Storage design and the storage code, copied: `core-domain`, `core-wallet`, `core-haven-aol`, `core-arkiv`, `core-collections`, `core-crypto`, `core-attestation`, `core-cache`, `core-cache-mirror`, `core-security`; playback service, chapters, identity switching | Phone UI, its design rules, launches and trading, image / PDF / file viewers |
| `foc-local-first-android-main` | The content cache itself (`foc-cache` 0.4.5, the same version haven-mobile uses from Maven Central): local-first reads, hedged fetch across providers, quota and TTL, `fetch` for downloading ahead, `isAvailable`, transform-on-store | Nothing left out. Read for what it can and cannot do (§9) |
| `nav3-recipes-main` (Apache 2.0) | Navigation patterns for the new shell: a history per sidebar section, the responsive bar / rail / sidebar wrapper, wallet-first gating, feature modules that register their own screens with Hilt, list-detail layouts, saved history | Deep links, dynamic feature modules, Koin and Metro variants, Fragment interop, the Navigation 2 migration recipes |
| `android-skills` (Apache 2.0) | Google's own guidance, written for coding agents. These of its 36 skills apply: `adaptive`, `navigation-3`, `navigation-event`, `edge-to-edge`, `jetpack-compose-performance`, `testing-setup`, and the two security audits plus the ANR audit at hardening (§8) | Camera, cars, TV, Wear, XR, on-device AI, ink, identity, Play billing / Engage / policy, home-screen widgets, Cast, the AGP 9 upgrade (haven-mobile is already on AGP 9), XML migration, R8 analysis (no release build), and the CLI and profiler skills (they run tools on a local machine) |
| `redhaven-main` (MIT) | The social model: a gate is a community (here: a DataDAO), identity is a wallet address, profile = what an address has published. Reading threads and comments (`haven.board.thread`, `haven.board.post`), verifying membership receipts (its `receipt-vectors.json` fixture), its Arkiv read budgeter | Writing posts, votes and vote ranking (Later); Reddit look; CoinGecko sidebar |
| `Dance-ignore` | Skipped because of its name; I did not read its contents | |

## 4. Scope

**In (v1)**

- Library of everything the connected wallet can open, music and video, with offline state per item.
- Album page (songs from ID3 chapters), video page, queue, shuffle, repeat, resume position.
- Collection, favourites, playlists of songs / albums / videos, History, search. All local.
- DataDAOs: yours, a directory, a page per DataDAO, clear access state on every item.
- Upload feed on Home; uploader profiles; follow uploaders and pin DataDAOs (local).
- Download for offline, per item.
- Comments on an album or video and a DataDAO's discussion threads, shown when there are any.
- Touch, mouse and keyboard throughout; dragging items between parts of the interface;
  resizable panes; rearrangeable sidebar and Home.
- Multiple identities with full isolation, cache settings, wipe on disconnect.
- Desktop window behaviour, right-click and long-press menus, media keys, PiP for video.
- Light and dark themes.

**Out**

- Local files in any form. Dragging anything into or out of the window.
- Anything about what was played leaving the device.
- Cast.
- Writing comments, threads or votes (stubbed); vote-ranked ordering.
- Token launches, trading, prices, balances, buy links.
- From Parachord: every streaming service, the plugin system and marketplace, AI DJ and MCP,
  Last.fm / ListenBrainz / Libre.fm, MusicBrainz-family metadata, charts, concerts, Discord,
  browser and Raycast extensions, smart links, Widevine, auto-update, XSPF import and export.
- Publishing media from the app. haven-mobile has no upload code; uploads come from `haven-cli`.
- Release signing and store distribution.

**Network endpoints that remain:** the Arkiv RPC, Filecoin storage providers, the Haven-AOL
canister on ICP, public EVM RPCs for the wallet's membership checks, and the Reown / WalletConnect
relay (needs a project id). No accounts, analytics or ads.

## 5. Product model

| Parachord | In this app | Backed by |
|---|---|---|
| Album | One audio upload | `haven.audio.full` record; one file |
| Song | A chapter of that file (see "Songs" below) | ID3 `CHAP` / `CTOC`, parsed by haven-mobile's `Chapters.kt`, cached in `cached_chapters` |
| Artist | The uploader | `creator` handle when set (self-asserted), else the address |
| Video | One video upload | `haven.video.full` record |
| Cover art | Embedded picture if present, else a deterministic pattern from the item id | haven-mobile's embedded-art extraction |
| Collection | Everything you can open, plus favourites | Room mirror (`observeAccessible`) plus local tables |
| Source badge on a track | Offline state: on device, partly, not downloaded, expired | `ContentCacheStatus` |
| Playlists | Local playlists of songs, whole albums and videos | Local tables |
| History | Local play history, per identity, clearable, never leaves the device | Local table |
| Comments | Read-only: under an album or video, and in a DataDAO's Discussion tab | `haven.board.post` / `haven.board.thread` |
| Friends sidebar | Followed uploaders, each showing their latest upload | `listMediaForOwner`; local follow list |
| Friend page | Uploader profile: everything that address has live | `listMediaForOwner` |
| Home | Upload feed across your DataDAOs | Mirror, ordered by `createdAtBlock` |
| Artist page | DataDAO page: Music, Video, Discussion, Uploaders, About | `listMediaForCommunity` |
| Charts, recommendations, Listen Along | Not present | |

Entities carry a title, an uploader handle, a duration, a MIME type, a creation time and gate
fields. haven-mobile's audit found no artist, album, year or genre fields in practice, so the app
shows what the uploader put in the title and the file's own tags, and nothing else.

### Songs

Your guess is right: an album is one MP3, so a song is a pointer into it. haven-mobile already
models it that way (`QueueTrack`: album id, chapter index, start and end time) and plays it by
telling Media3 to clip the file to that range. A playlist entry stores:

| Field | Why |
|---|---|
| Album record key | Which upload |
| Piece CID | Which exact file the times were read from |
| Chapter index (-1 = the whole album or video) | Which song; -1 is haven-mobile's own marker for "whole file" |
| Start and end time | So the song plays even when the chapter cache has been dropped |
| Song title, album title, uploader, DataDAO (copies) | So the row still reads correctly when the record is gone |

Consequences:

- **Songs are known only after an album has been opened once.** The chapter list lives in the
  file's ID3 tag, and haven-mobile reads it from the staged file. Before that, only the whole
  album can be added to a playlist. There is no shortcut: `foc-cache` fetches whole pieces only
  (no ranged or partial read), so the song list cannot be had without the album. "Download"
  (§7) is therefore also the way to make an album's songs appear.
- Playing one song from a playlist stages the whole album file. The queue shows this as one
  download, not one per song.
- If the piece CID no longer matches (the album was re-uploaded), the entry is re-matched by
  chapter index and title; if that fails it is marked "changed" rather than played from the
  wrong spot.
- Arkiv records expire. An entry whose record is gone stays in the playlist, marked unavailable.
- A video is an entry with index -1. Video chapters are not in the references and not planned.

### DataDAOs and access

A DataDAO is a gate: `(chain, token, threshold)`. Members are the addresses that meet it. The app
shows membership and never shows a market.

- **Every item names its DataDAO**: a badge on cards, rows, the player bar and the queue.
- **Every item shows one access state**, checked by haven-mobile's existing gate code:
  *Member* (opens), *Not a member* (locked; says which DataDAO), *Can't check right now*
  (offline or the chain was unreachable), *Not open yet* (a market-cap gate that has not been
  reached; shown as a locked reason only).
- **Locked items are visible** on DataDAO pages and uploader profiles, and in the feed when they
  come from an uploader you follow, so the catalogue can be browsed. They cannot be queued;
  dragging one shows why on the drop target.
- **DataDAO page**: hero with name and your standing, tabs for Music, Video, Discussion,
  Uploaders and About.
  About states the membership rule in words (chain, token address, amount). No price, balance,
  chart or buy action anywhere.
- **Sidebar**: *Your DataDAOs* lists the ones you are a member of; *Browse* lists the rest.
- Membership is read from the chain when the library refreshes and when an item is opened. It
  does not revoke instantly: content already opened stays until trimmed or the identity leaves
  (haven-mobile's accepted behaviour; the app says so in Settings).

## 6. Design: Parachord in Compose

**Shell**

- Left sidebar: back / forward, search, Home, *Your media* (Collection, Playlists, History), *Your
  DataDAOs* (pinned, then Browse), *Following* (uploaders, hexagonal avatars), Settings.
- Content area: hero header, then a filter and sort bar, then a grid or list. One 24dp gutter.
  Card grids take their column count from a minimum card width, so they refill as the window
  is resized.
- Player bar: always dark, fixed at the bottom: transport, queue button with count, art, title,
  uploader, DataDAO badge, offline state, favourite, position, shuffle, repeat, volume.
- Queue drawer: slides in from the right, always dark, virtualised.
- Video: plays in the content area with its own hover controls; the player bar stays.

**Tokens** (from Parachord's `docs/DESIGN_SYSTEM.md` and `CLAUDE.md`)

- Accent `#7c3aed` light, `#a78bfa` dark; filled accent buttons always use `#7c3aed` so white text
  stays readable. Grounds `#ffffff` / `#161616`, elevated `#ffffff` / `#252525`. Text `#111827` /
  `#f3f4f6`. Success `#10b981`, warning `#f59e0b`, error `#ef4444`.
- Type sizes 11, 12, 13, 14, 16, 20, 24, 28, 48. Spacing on a 4dp scale. Radius 4, 6, 8, 10, 12,
  16, full. Motion uses Material's expressive spring scheme rather than
  Parachord's fixed 100 to 400 ms durations.
- Hero titles: uppercase, wide tracking, light weight, on a gradient with a faint repeated pattern.
- Cards lift on hover (6dp) and settle on press (2dp); a picked-up card lifts further and tilts
  slightly. Skeletons match the box they stand in for.
- Access and offline states are drawn in Parachord's style (chips and badges from the same token
  set), with a fixed meaning per colour.

**Built on Material 3 Expressive**

Material supplies the components, adaptive layout, motion and accessibility. Parachord supplies
the palette, the page structure and the signature pieces. Nothing is hand-built where a Material
component exists.

| Part | From Material | From Parachord |
|---|---|---|
| Theme | Expressive theme: colour roles, type scale, shape scale | Its purple palette mapped onto the colour roles; fixed, not derived from the wallpaper |
| Navigation | Navigation suite: bar, rail or drawer chosen by window size class | Section headings, hexagonal avatars |
| Queue | A pane of the shell, built by us; it is not a navigation destination | Always dark |
| Chips, cards, menus, search, sliders, tooltips, "Undo" notices | The Material components, themed | Card lift on hover |
| Feed refresh, row swipes | Pull-to-refresh, swipe-to-dismiss | — |
| Fetching and unlocking | Expressive loading indicator and wavy progress bar | — |
| Movement | Expressive spring motion scheme | — |
| The tray | Material's floating toolbar as the base, with our drop handling added | — |
| Main action on an album, video or playlist page | Split button: "Play", with Play next / Add to queue / Download behind the arrow | Pink call-to-action colour |
| All / Music / Video switch | Connected button group | — |
| Song rows, settings | Expressive list items | — |
| Icons | Material Symbols, shipped as vector drawables (the old `material-icons` library is not used) | — |
| Going back | Predictive back: the previous screen shows through as the gesture progresses | — |
| No Material equivalent | — | Gradient hero headers with wide-tracked titles, the always-dark player bar, the drag layer |

Shape-morphing and bounce are used where they explain something (a button being pressed, an
item being lifted, a download in progress) and nowhere else. Hero gradients and generated
covers use a mesh gradient seeded from the item's id if the Compose version in use has one,
and an ordinary gradient otherwise.

**Rules from Parachord's design audit**, enforced by a drift test like its `design-drift.test.js`:
no hex colours or raw dp in feature modules; one shared component per element; every clickable
thing is a real button with a label; a layout change ships with its skeleton; sentence case in
running UI; errors say what happened and what to do.

**Window sizes** (proposed, to confirm on a device)

| Material window size class | Layout |
|---|---|
| Compact (under 600dp) | Bottom bar, single column; queue as a full-height sheet |
| Medium (600 to 839dp) | Navigation rail; queue as an overlay |
| Expanded (840 to 1199dp) | Full sidebar (256dp, resizable); queue as an overlay |
| Large and up (1200dp and over) | Full sidebar; queue docked beside the content |

Manifest `<layout>`: default about 1180 x 760dp, minimum 480 x 560dp, `resizeableActivity`. Check
every screen at the minimum, the default and maximised, in both themes.

**Input: touch, mouse and keyboard, all first-class**

| Action | Touch | Mouse | Keyboard |
|---|---|---|---|
| Open | Tap | Click | Enter |
| Play | Tap the play button on the item | Double-click, or the play button | Space on a focused item |
| Actions for an item | Long-press and let go: the tray stays open | Right-click | Menu key / Shift+F10 |
| Move an item | Long-press, then carry | Press and carry | Ctrl+X / Ctrl+V, or pick from the actions |
| Reorder a row | Drag its grip (no long-press) | Drag the row | Alt+up / down |
| Select several | Long-press, then tap others | Ctrl / Shift click | Shift+arrows, Ctrl+A |
| Scroll | Finger drag, fling | Wheel, trackpad | Arrows, Page Up / Down |
| Add a row to the queue | Swipe it right | Actions, or carry | Q on a focused item |
| Remove from queue or playlist | Swipe it left | Actions, or carry out | Delete |
| Seek, volume | Drag the bar | Drag or wheel on the bar | Left / right, up / down |
| Refresh the feed | Pull down, or the Refresh button | Refresh button | Ctrl+R |

- Touch targets are at least 48dp. Rows get taller in the under-600dp layout, not smaller.
- **Nothing depends on hover.** The play and queue buttons Parachord reveals on hover are always
  present, quieter at rest and brighter on hover or focus.
- Video: tap toggles the controls, double-tap either side seeks 10 seconds, F or a button for
  fullscreen, PiP when the window loses focus if enabled.
- Other shortcuts, following Android's desktop conventions: Space play / pause, Ctrl+F search,
  Ctrl+N new playlist, Ctrl+left / right previous / next, M mute, S shuffle, R repeat,
  Ctrl+1 to 9 for the sidebar's sections, Ctrl+Z / Ctrl+Shift+Z undo and redo, Escape to close
  temporary things or cancel a move (not as a general Back). They do not fire while a text
  field has focus. Every shortcut is published to the system's Keyboard Shortcuts Helper.
- Icon-only buttons have tooltips, with the shortcut where there is one ("Queue (Q)").
- The mouse pointer says what will happen: an open hand over anything that can be picked up, a
  closed hand while carrying, a double arrow over the pane dividers.
- Where behaviour should differ by input (shortcut hints only with a keyboard attached, roomier
  rows with touch only), it is read from Compose's `mediaQuery` (pointer precision, keyboard
  kind). That API is experimental, so it sits behind one small wrapper of our own.
- **A resize must lose nothing.** Resizing the window recreates the screen. Scroll position,
  search text, selection, the open tray and navigation history are all saved state, and the
  manifest does not suppress the recreation (haven-mobile's manifest does; ours will not).
  Playback is unaffected because the player lives in the service.
- The app draws edge to edge, which is mandatory at the API level it targets, and keeps clear
  of the window's caption bar, which in a desktop window is where the system puts the title
  and window buttons.
- No gestures start at the window's edges: those belong to the system's back gesture. The
  queue opens from its button or Q.
- Media keys and the system media notification work as on any Android player.

## 7. Moving things around, and a live interface

All of this happens inside the app's own window. Nothing is dragged in or out.

**The tray: one list of destinations, reached three ways**

A long-press normally has to mean either "show me the menu" or "let me carry this". Here they
are the same thing. When an item is long-pressed, it lifts and a **tray** rises just above the
player bar, holding the places that item can go as large slots:

> Play now · Play next · Add to queue · Favourite · Download · *your three most recent
> playlists* · New playlist

(For an uploader the tray holds Follow; for a DataDAO, Pin.)

- **Let go without moving** and the tray stays open. Tap a slot. That is the menu.
- **Keep holding and move** and the item comes with you. Drop it on a slot. The tray is a short
  trip from anywhere on a laptop-sized touchscreen, so nothing has to be dragged across the
  whole display.
- **Right-click or the Menu key** shows the same list as an ordinary menu at the pointer.

One list, defined once, so touch, mouse and keyboard can never offer different things.

**Precise placement** is still there for when order matters: carry an item past the tray to the
sidebar, the open queue drawer or an open playlist page.

| Put it on | Song, album, video, or a selection of them | Playlist | Uploader | DataDAO |
|---|---|---|---|---|
| A playlist in the sidebar | Add to it | Merge into it | — | — |
| "New playlist" in the sidebar | Create one from it | Duplicate | — | — |
| An open playlist page | Insert at that position | — | — | — |
| Queue button | Add to the end; holding over it opens the queue | Add all | — | — |
| Queue drawer | Insert at that position | Insert all there | — | — |
| Player bar | Play now | Play now | — | — |
| Collection in the sidebar | Favourite | — | — | — |
| Following in the sidebar | — | — | Follow | — |
| Your DataDAOs in the sidebar | — | — | — | Pin |

Also movable in place: queue rows and playlist rows (reorder by their grip), sidebar pins and
playlists (reorder), Home shelves (reorder), the sidebar and queue dividers (resize), the seek
and volume controls.

**Behaviour**

- The long-press uses the system's own duration, so the accessibility setting for it applies.
  The item lifts with a short vibration. A finger that moves before then scrolls, as usual.
- With a mouse, press-and-move lifts at once; there is no wait.
- Rows that reorder (queue, playlists) have a grip at one end. Touching the grip moves the row
  immediately, so reordering never waits on a long-press and never fights scrolling.
- Swiping a row is the quick path: right adds it to the queue, left removes it from a queue or
  playlist. Both show "Undo".
- With one item lifted, tapping others adds them to the stack, which shows a count.
- While something is held, every place that can take it lights up and the rest dims.
- The held item follows the finger as a small card, offset so the finger does not cover it.
- Lists open a gap at the insertion point and the other rows slide aside.
- Collapsed things open when held over: a sidebar section, the queue drawer.
- Lists scroll when the held item nears their top or bottom edge.
- A locked item's tray shows the play and queue slots struck through, with the DataDAO
  membership that is missing. It can still go into a playlist.
- An album that has not been opened yet moves as a whole album (§5, Songs).
- Letting go outside any target, or Escape, cancels and the item glides back. Every move can be
  undone (an "Undo" notice, and Ctrl+Z).
- TalkBack users get the same list as custom actions on each item; dragging is never required.

**Live interface**

- Press feedback on everything; hover feedback as an extra where there is a pointer.
- Card-to-hero transitions when opening an album, video or DataDAO.
- After a refresh, new uploads animate into the feed; nothing moves on its own between refreshes.
- An item being fetched shows its stage on the item itself, wherever that item appears (§10).
- Layout animates between window-size classes instead of snapping.
- Pane widths, pin order and Home shelf order are remembered per identity.
- The system's "remove animations" setting is honoured.

**How it is built**

- One typed payload (`DragPayload`: songs, album, video, playlist, uploader, DataDAO) and one
  function from payload to its list of destinations, in `core-library`. The tray, the right-click
  menu, the keyboard commands, TalkBack actions and the table above are all views of that one
  list, so it can be unit-tested as plain data.
- One in-window drag layer in `core-design`, driven by Compose pointer input, drawing the held
  item and the tray in an overlay above the shell. Since nothing crosses the window edge,
  Android's system drag-and-drop is not used.
- The shared `MediaCard`, `MediaRow`, `SidebarItem` and `QueueRow` carry the pick-up and drop
  behaviour, so a screen built from them gets it without extra code.
- perfect-sound's playlist panel shows how to tell a mouse press from a finger press (it checks
  the pointer type); it lets only the mouse drag, and we add the long-press and grip paths.
- The timings and distances (how far a finger may drift during a long-press, how close to an
  edge scrolling starts) are named constants in one file, to be adjusted once the first build
  is on a Googlebook. That is tuning, not a redesign: the grip and the tray both work without
  the carry gesture.

## 8. Architecture

Kotlin, Jetpack Compose, Media3, Hilt, Room, DataStore. haven-mobile's toolchain, because its
modules need it (AGP 9.3.3, Kotlin 2.3.21, Media3 1.11.1, Room 2.8.5, Hilt 2.60.1, JDK 17, core
library desugaring for `foc-cache`). Googlebooks run Android 17, so the app targets it: `compileSdk` and `targetSdk` 37 (haven-mobile
already compiles against 37 but targets 34, so the behaviour changes between 34 and 37 need
reading when the manifest is written). `minSdk` stays 29 so the app still installs on other
Android devices, with plain fallbacks there.
Application id `app.ipfsmedia.player`; debug signing only.

**Material.** haven-mobile's Compose BOM (2026.09.00, the newest) pins `material3` 1.4.0. The
Expressive components are in the 1.5 line, whose newest release is 1.5.0-rc01, so the app
overrides the BOM for `material3` and `material3-adaptive-navigation-suite` and moves to 1.5.0
when it is final. `material3` 1.5.0-rc01 is itself built against Compose 1.12.0, so the rest of
Compose stays on the BOM's stable 1.12.1; the app does not move to the Compose 1.13 release
candidate to get newer layout or graphics APIs. The adaptive library stays at the BOM's 1.3.0. The copied `core-*` modules do
not draw UI, so this affects only new code.

**Navigation.** Navigation 3 (`androidx.navigation3`, stable 1.2.1), not the Navigation 2 that
haven-mobile uses. haven-mobile's navigation lives in its `app` module, which is not copied, so
nothing has to be migrated. In Navigation 3 the history is a plain list the app owns, and one
window can show more than one destination, which is what a resizable desktop window needs.

| Need | Recipe in `nav3-recipes-main` |
|---|---|
| Each sidebar section (Home, Collection, a DataDAO…) keeps its own history | `multiplestacks` |
| Sidebar, rail or bottom bar wrapped around every screen, by window size | `navscenedecorator` |
| Nothing but onboarding until a wallet is connected | `conditional` |
| Each `feature-*` module registers its own screens; the `app` module only assembles them | `modular/hilt` |
| Screen arguments (a record key, an address) reach a Hilt view model | `passingarguments/viewmodels/hilt` |
| Thread list beside the open thread, and Settings, in wide windows | `material/listdetail` |
| History survives a restart | `basicsaveable` |
| "Add to playlist… → New playlist" as a dialog that returns a value | `dialog`, `results` |
| Per-screen transitions, alongside Compose shared transitions for card-to-hero | `animations` |

Two things the recipes do not cover and the app adds itself: the sidebar's **forward** button
(a second list beside the history, cleared on a new navigation), and the queue, which stays a
shell-level pane outside navigation so that opening it never changes the history.

The recipes' main branch builds against a snapshot (1.3.0-SNAPSHOT). The app uses released
versions: `navigation3` 1.2.1, `adaptive-navigation3` 1.3.0, `lifecycle-viewmodel-navigation3`
2.11.0. A recipe that needs something newer is adapted or waits.

**Google's Android skills** (`android-skills`) are written for coding agents and are followed
when the matching work is done:

| Skill | Used for | Phase |
|---|---|---|
| `adaptive` | Navigation that changes form with the window, multi-pane layouts through Navigation 3 scenes (it rules out the older pane scaffolds), adaptive grids, state across resize, shortcuts, tooltips, cursors | 1 to 3 |
| `navigation-3` | The same recipes as `nav3-recipes-main`, as documentation | 1 |
| `navigation-event` | Predictive back, and the order in which Back and Escape dismiss the tray, the queue overlay and dialogs | 1 |
| `edge-to-edge` | Insets, including the caption bar | 1 |
| `jetpack-compose-performance` | Large lazy grids (stable keys and content types, shared transitions only on visible items) and the drag overlay (animated values read at draw time, not on every recomposition) | 2 |
| `testing-setup` | Screenshot tests at fixed window sizes, fakes, window-size overrides in tests | 1 onward |
| `android-intent-security`, `android-permissions-security`, `anr-audit-and-fix` | Audits of the exported wallet return link, notification intents, the media service, the permission list, and main-thread work | 4 |

**State.** `core-*` modules expose `StateFlow`; screens collect it. Playback state crosses from
the service to the window through Media3's session, which is built for that. Jetpack AppState
(Transform and StateStore, 1.0.0-alpha02) is not used: the copied modules are already written
on Flow, the one piece of state shared between app components is playback, and an alpha API
would be a second state system beside the first. To keep the door open, new state holders sit
behind interfaces, so a later move touches one module at a time.

**Builds happen in CI only.** Nothing is compiled on this laptop. haven-mobile's workflow
(`.github/workflows/android.yml`) already does what is needed on a Linux runner: installs the
NDK, builds the vetkeys native library, runs the tests, assembles a debug APK and uploads it.
The new repo starts from that workflow. This also settles the native-library problem: its build
script only works on Linux, which is where CI runs.

**Wallet.** `core-wallet` arrives with a WalletConnect project id set in code, the app name
"Haven" and the return link `haven://connect`. The project id is yours to set. Change the name to
Googlebook IPFSMedia and the return link to `ipfsmedia://connect`, so this app and haven-mobile
can sit on the same device without both claiming `haven://`. Connecting works as in
haven-mobile: the app hands a `wc:` link to MetaMask, MetaMask opens in its own window, the
user approves, and the return link brings them back. haven-mobile's copy-link fallback stays.

```
app              shell, navigation, DI, manifest (desktop window)
core-design      tokens, shared components, drag layer, drift test                new
core-player      playback service, queue, persistence, shortcuts                  new, from perfect-sound + haven feature-watch
core-library     album / track / video / uploader / DataDAO model, search,
                 drag payload                                                     new
core-userdata    per-identity database: favourites, playlists, history,
                 follows, pins, resume positions, layout                          new
core-social      upload feed, DataDAO directory, uploader profiles, reading
                 threads and comments, receipt check, Arkiv read budgeter,
                 stub for writing comments and votes                              new
core-domain, core-wallet, core-haven-aol, core-arkiv, core-collections,
core-crypto, core-attestation, core-cache, core-cache-mirror, core-security      copied from haven-mobile
feature-home, -collection, -album, -video, -datadao, -discussion, -uploader,
-search, -playlists, -history, -nowplaying, -identity, -onboarding, -settings     new
```

Rules: `core-*` never depends on `feature-*`; feature modules declare no colours, type or
spacing; anything drawn twice is a `core-design` component; pure logic is testable without
Android.

Copying the `core-*` modules: record which haven-mobile commit they came from (the zip here has
no commit id) and add the license text to the new repo (the zip has no LICENSE file). Launch and
trading code inside the copied modules is left unreferenced at first and removed once the build
is green, so the first diff against upstream stays small.

Published and resolvable today (checked on Maven Central): `io.github.haven-hvn:foc-cache:0.4.5`,
`ic-agent`, `ic-kotlin`. `foc-cache` is used as the published artifact, not copied; the source in
`foc-local-first-android-main` is the same version.

## 9. Storage design (haven-mobile's)

**Layers, all per identity (lowercased address)**

| Layer | Where | Holds | Lifetime |
|---|---|---|---|
| Index | Arkiv chain, read with `arkiv_query` | Records: what exists, who uploaded it, which DataDAO | Until the record expires on Arkiv |
| Mirror | Room, `haven-mirror-<address>.db` | Record metadata, cached chapters | A cache: dropped and refetched on schema change |
| Ciphertext cache | `cacheDir/foc/<address>/` via `foc-cache` | Encrypted pieces | Quota (automatic by default) and TTL (30 days), user-set |
| Unlocked store | `filesDir/foc-unlocked/<address>/` | Decrypted pieces when "keep unlocked" is on | Until turned off or the identity leaves |
| Plaintext spool | `cacheDir/plaintext/<address>/<cid>.bin` | Staged decrypted files for playback | Budget, oldest first; always wiped on disconnect |
| Keys | Memory, plus an encrypted epoch-unlock file per identity | Unwrapped AES keys, epoch unlocks | Session; always wiped on disconnect |
| Settings | DataStore, keys scoped `id/<address>/…` | Quota, TTL, chains, keep-unlocked | Until the identity is removed |

**The one playback path** (haven-mobile's `WatchViewModel`)

```
spool hit ─────────────────────────────────────────────▶ play
availability check ─▶ key (cached, or one wallet signature + canister call)
  ─▶ foc-cache stream ─▶ strip CAR container ─▶ AES-GCM chunked decrypt ─▶ spool file ─▶ play
```

Peak memory is one chunk. Access is the intersection of what the wallet holds and the gates Arkiv
records, across Haven-AOL's supported chains, with no chain picker. A key is never requested for
a piece no provider can serve. Once a piece is in the cache it plays with no network.

**What `foc-cache` does, from its source**

- Local first: a read is served from disk if the piece is there; only a miss touches the network.
- On a miss it races, staggered 200 ms apart and up to three at once: the FilBeam CDN (if the
  upload enabled it), each storage provider's piece endpoint, and, for uploads indexed for IPFS,
  the providers' IPFS endpoints and IPFS gateways. First to answer wins. All of it is HTTP.
- If a record names no providers, it finds them from Filecoin's on-chain provider registry.
- It always fetches a **whole piece**, to a temp file, then installs it. There is no partial or
  ranged read, and no progress callback while the download runs.
- `fetch(ref)` downloads ahead of time; `exists` is a local check; `isAvailable` asks the network
  without downloading; `remove` evicts; `space` reports quota and use.
- Concurrent requests for one piece collapse into one download.
- It does not yet verify downloaded bytes against the piece CID (its README lists this as
  queued). Gated content is still authenticated by AES-GCM when decrypted; clear content is not.

**What this app adds or changes**

- **Download** on any item calls `foc-cache`'s `fetch`, so it is in the cache before it is
  wanted. Offline state on every item comes from `exists`.

- `core-userdata`: its own database per identity, because the mirror is dropped on schema change
  and this data must survive. Favourites, playlists and their entries (§5, Songs), play
  history, followed uploaders, pinned DataDAOs, resume positions, layout. Migrations required.
  It is wiped when the identity is removed, and History has its own "Clear" action.
- The queue is saved per identity and stores item ids (record key plus chapter index), not file
  paths, because a staged file can be trimmed; resume re-stages when needed.
- The spool trimmer must never evict a file the current queue is about to play.
- The spool budget is a constant 1 GiB in haven-mobile (listed there as not done). For video on a
  laptop it becomes a setting with a larger default.
- Extracted artwork becomes per identity and is wiped with it. In haven-mobile it goes to a
  shared `cacheDir/haven-artwork/` that no disconnect step deletes, so art from gated content
  outlives the identity.
- The mirror stores titles and CIDs in plaintext (haven-mobile's own notes flag it). Decide in
  Phase 1 whether to encrypt it with a Keystore key.

## 10. Playback

- One `MediaSessionService` owning one ExoPlayer; the UI holds a `MediaController`. Audio focus,
  noisy-audio pause, wake lock, playback resumption, skip-on-error (perfect-sound's service).
- Music: an album's chapters become clipped media items in one queue (haven-mobile's
  `queueMediaItems`), so next / previous / shuffle work per track.
- Video: same player and queue, rendered in the content area; Compose controls for touch, mouse
  and keyboard, fullscreen, resume position. Picture-in-picture uses Android 17's interactive
  desktop form, an always-on-top window whose controls work, which is what a Googlebook has;
  older devices get ordinary picture-in-picture.
- Mixed queues of tracks and videos are allowed.
- The whole file is fetched before it plays; `foc-cache` works in whole pieces. A long video
  therefore waits for the full download on first open. Playing while downloading would need a
  change in `foc-cache` and is Later.
- **Progress shown honestly.** `foc-cache` reports nothing while a download runs, so during the
  download the item shows the stage ("Fetching…") with a moving indicator and the total size,
  not an invented percentage. Byte counts appear in the decrypt stage, where haven-mobile
  already has them. A progress callback in `foc-cache` would make the first stage exact (§15).
- To verify, not assume: gapless playback across clipped chapters of one MP3.

## 11. Social: the upload feed, and comments where they exist

Read-only. The feed is built on queries haven-mobile's Arkiv client already has
(`listMediaForCommunity`, `listMediaForOwner`, `discoverGates`). Comments read the records
redhaven and Fairhaven write. No new record types.

**Uploads (the main thing)**

- **Home feed**: newest uploads across your DataDAOs and from uploaders you follow, ordered by
  creation block. Each entry: title, kind, uploader, DataDAO, age, access state, offline state.
  Filter chips: All, Music, Video, one per DataDAO. Grouped by day.
- **Refresh.** The feed loads when the app opens, and again only when asked: pull down, the
  Refresh button, or Ctrl+R. It never polls or updates on its own. The header shows when it was
  last refreshed. Between refreshes everything is read from the local mirror, so moving around
  the app costs no Arkiv calls.
- **DataDAO page**: its uploads and its uploaders (derived from who has published under the gate).
- **Uploader profile**: everything that address has live, grouped by DataDAO.
- **Following** is a local list of addresses. The sidebar shows each with their latest upload and
  an unseen mark, computed at refresh from the last block the feed showed, not from plays.

**Comments (secondary; present only when there is something to show)**

- An album or video page checks, when it opens, how many comments exist under that record. If
  none, the page shows no comments area at all. If some, a "Comments (n)" section appears below
  the songs or the video, collapsed, and loads when opened.
- A DataDAO page gets a Discussion tab only if that DataDAO has threads.
- The count check uses the entity-count probe that redhaven's budgeter relies on as uncharged.
  If it turns out to be charged, the check moves behind the page's own Refresh.
- Threads are a public subject line plus text; comments nest by `parent_ref`. Oldest first
  within a thread, newest first for the thread list. No vote ranking.
- **Member mark.** Every post carries the author's membership receipt, signed by the Haven-AOL
  canister. The app checks it offline and marks posts whose receipt does not verify. The check
  is pinned by redhaven's `receipt-vectors.json`.
- **Sealed text.** A post's text is either clear or sealed to members. Clear text is shown.
  Sealed text is shown to members if haven-mobile's unlock path opens redhaven's small-text
  framing; otherwise it appears as "sealed comment".
- Threads last 14 days and comments 7 on Arkiv; each shows its remaining life.
- Local only: mute an address, hide a thread.

**Arkiv calls are spent deliberately.** From redhaven's budgeter, keep: no automatic retries,
in-flight de-duplication, at most two calls at once, cached results, and a visible "rate
limited" state that says when to try again. Drop its watch subscription and polling; this app
has neither.

**Stub.** `core-social` declares a `SocialActions` interface (comment, reply, vote) with a single
implementation that returns "not available". No visible control calls it.

## 12. Phases

Sizes are relative (S, M, L), not estimates. There is no spike phase. Every build comes from CI;
"on the device" below means the CI-built debug APK installed on a Googlebook.

**Phase 1 — repo, storage, shell and player (L)**
- New repo with the license, and CI adapted from haven-mobile's workflow.
- Copy the `core-*` modules. Rename the wallet's app name and return link (§8). Onboarding
  (connect MetaMask first), identity switcher.
- `core-design`: tokens, shell, shared components, the drag layer and the tray, both themes,
  skeletons, the drift test.
- `core-player`: queue, persistence by item id, shortcuts, media keys.
- A plain list of what the wallet can open, enough to open, fetch and play audio and video.
- *Exit:* on the device, from a fresh install, MetaMask connects and returns to the app; a gated
  album and a gated video play at minimum, default and maximised window sizes, once by touch
  alone and once by keyboard alone; replay works in airplane mode; the queue survives a restart;
  a song reaches the queue by the tray, by carrying, by swipe and by right-click, and can be
  reordered there by its grip; resizing the window during playback, mid-scroll and with the
  tray open loses nothing.

**Phase 2 — library (L)**
- Collection (albums, videos, uploaders), album page with songs, video page, favourites,
  playlists with song entries (§5), History, search, shuffle and repeat, resume position,
  Download. `core-userdata`.
- Cache and spool settings, offline state on items, spool pinning, per-identity artwork.
- Every row of the §7 table that involves these screens.
- *Exit:* a song carried from an album into a playlist plays from that playlist after a restart
  and after the spool has been cleared; a downloaded album plays in airplane mode on first
  open; each screen has empty, loading, error and populated states; a 2,000-item library
  scrolls without dropped frames; switching identity shows no trace of the other; disconnect
  leaves no plaintext, keys or artwork on disk (checked by listing the app's directories).

**Phase 3 — DataDAOs, feed and comments (L)**
- DataDAO directory and page, access states everywhere, Home feed with refresh, uploader
  profiles, follow and pin, the Arkiv call rules. The remaining rows of the §7 table.
- Comments where they exist, the member mark, mute and hide.
- Resizable panes, reorderable sidebar and Home shelves, card-to-hero transitions.
- *Exit:* an item uploaded with `haven-cli` appears after a refresh and not before; an hour of
  browsing with no refresh makes no Arkiv calls; a page with no comments shows no comments
  area, and a thread written in redhaven renders with correct nesting and remaining life; a
  post with a forged receipt is marked; a locked item explains itself and cannot be queued; a
  429 shows as rate limited; every cell of the §7 table works by touch, by mouse and by keyboard.

**Phase 4 — hardening (M)**
- TalkBack labels, custom actions and focus order; performance; crash handling; privacy and
  licence notices.
- The intent, permission and ANR audits from Google's Android skills.
- First-install check: the CI APK installed from the file manager, not adb.

**Later**
- Writing comments and threads; votes and vote ranking. Play while downloading. Publishing. A
  visualizer. A release key and store distribution.

## 13. Testing

- **Everything runs in CI.** No test or build is run on this laptop.
- **Unit, no Android:** library grouping, queue and shuffle, chapter parsing, playlist-entry
  re-matching when a piece changes, shortcut mapping, window-size rules, feed ordering and
  "unseen", that nothing calls Arkiv without a refresh, comment nesting, receipt verification
  against redhaven's fixture, and the destination list of §7 as data (including refusals for
  locked items).
- **Carried over:** haven-mobile's tests for crypto, CAR stripping, gate parsing, attestation and
  identity isolation keep running against the copied modules.
- **Emulator in CI:** Room and cache paths; Maestro flows for library, play audio, play video,
  carry a song to a playlist, reorder the queue, refresh the feed, disconnect, at three window
  sizes. Touch gestures are scripted.
- **By hand on a Googlebook:** connecting MetaMask, mouse and trackpad paths, and how the
  long-press and carry feel.
- **Design:** the drift test, plus a check that feature modules build cards and rows only from
  the shared components. Screenshot tests, generated in CI from previews: every screen at the
  minimum window, each Material size class and a desktop-sized window, in both themes and at
  1.5x font size; every state (empty, loading, error, populated). New reference images are
  accepted only after you have looked at them.

## 14. Risks and findings

| # | Finding | Effect | Response |
|---|---|---|---|
| 1 | Songs are known only after an album has been fetched once, and `foc-cache` cannot fetch part of a piece | An unfetched album cannot be split into songs for a playlist | Add the whole album; Download reveals the songs |
| 2 | Nothing is built locally, so the first sight of the touch behaviour is a CI build on a Googlebook | Slower loop for tuning gestures | Timings in one constants file; the grip, swipe and tray work without the carry gesture |
| 3 | Whole-piece fetch before playback, with no download progress from `foc-cache` | Slow first open for long video, with a stage label instead of a percentage | Download ahead; honest stage labels; a progress callback upstream would fix it |
| 4 | Material 3 Expressive needs `material3` 1.5.0, which is at release candidate | An API could still shift before final | Low: a release candidate is close to final, and the app is not shipping yet |
| 5 | haven-mobile is a phone UI with no window, keyboard or pointer handling | All desktop behaviour is new work on perfect-sound's patterns | Phases 1 to 3 |
| 6 | Arkiv rate limits | A refresh can be refused | Refresh only on open and on request; show "rate limited" and when to retry |
| 7 | Arkiv is on a testnet (Rabdi); redhaven's friction log names a different one (Tiramisu) | Content and endpoints may change or reset | Keep endpoints configurable |
| 8 | `haven-mobile-main/README.md` and `MOBILE_V1_REQUIREMENTS.md` are macOS metadata stubs in this download, not the documents | The requirements its code cites were not available to me; §9 is from code and planning notes | Re-check §9 against the real files when copying from the repo |
| 9 | Debug-signed builds only | Android will not install a later release-signed build over a debug-signed one; the app must be uninstalled first, which deletes playlists, history and follows | Acceptable while testing; add an export of local data before a release key is introduced |
| 10 | `foc-cache` does not yet check downloaded bytes against the piece CID | Clear (ungated) content is trusted as delivered; gated content is still authenticated on decrypt | Note it; fixed upstream when verification lands |
| 11 | redhaven's README says its code was written without being installed or run | Comment records in the wild may differ from its schema | Read defensively: a record that does not parse is skipped, never a crash |
| 12 | Access does not revoke instantly | Opened content stays until trimmed or the identity leaves | State it in Settings |
| 13 | "Googlebook" in the app name | A store may object to a device brand in a listing name | Only matters if the app is ever listed; the label is one string |

## 15. Not verified, and two things worth asking of `foc-cache`

- Nothing was built, installed or run, here or anywhere.
- Which `material3` line carries the Expressive components. I checked that 1.5.0-rc01 is the
  newest release and that the BOM pins 1.4.0; that Expressive sits in 1.5 and not 1.4 is from
  memory, and CI will settle it on the first build.
- `ic-kotlin`, `haven-cli`, `haven-dapp` and Fairhaven are not in this workspace; what I say
  about them comes from how the code here uses or describes them.
- Whether haven-mobile's unlock path opens redhaven's sealed comment text unchanged.
- Whether Arkiv's entity-count probe is uncharged, as redhaven's budgeter assumes.
- Whether every recipe listed in §8 works on Navigation 3 1.2.1. The recipes target a 1.3
  snapshot; the scene-decorator one (`navscenedecorator`) is the most likely to need 1.3.
- The Android 17 features named here (interactive desktop picture-in-picture, the resizability
  rule at API 37) come from the survey you pasted; I did not check them against Google's pages.
- Which Compose release has mesh gradients. If it is 1.13, the app uses ordinary gradients
  until 1.13 is stable.
- Window breakpoints and default sizes in §6 are proposals.

**Looked at and left out:** Compose's `Grid` and `FlexBox` (in Compose since 1.11 but
experimental, and not lazy; library grids hold thousands of items and stay on lazy grids);
Cast (the content is addressed by CID, fetched through `foc-cache` and unlocked on the device;
a Cast device can do none of that, so casting would mean re-serving unlocked files from the
Googlebook over plain HTTP, outside the storage design; the system's own screen mirroring
still exists for anyone who wants a bigger screen);
progressive blur (Android 13 and up only, and the
player bar is solid by design); App Bubbles; Continue On (it passes what is playing to another
device, and there is no companion app to receive it).

Two additions to `foc-cache` would each remove a limit above. Neither is needed for v1.

1. **A progress callback on fetch** (bytes received, total). Gives a real percentage while a
   video downloads.
2. **A ranged read of the start of a piece.** Lets the app read an album's song list before
   downloading the album. It needs providers to honour Range requests, which the library's own
   notes say is not guaranteed, and for gated albums it needs the first cipher chunks decrypted,
   so this is the larger of the two.
