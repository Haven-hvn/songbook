# Work log

Working notes for whoever picks this repo up next (including me after a context reset).
The plan this implements is `docs/PLAN.md` (copied from `../MEDIA_APP_PLAN.md`, revision 13).

## Ground rules

- Nothing is built or run on the workstation. CI (`.github/workflows/android.yml`) is the only build.
- No access to the remote repository. Commits are local only.
- Reference folders live one level up (`../haven-mobile-main`, `../perfect-sound-master`, …) and are read-only.
- Library sources used to check API signatures are unpacked under `/tmp/ipfsmedia-ref/src`
  (material3 1.5.0-rc01, navigation3 1.2.1, media3 1.11.1, Compose 1.12.1, …). Re-download if missing.

## Status

Every module in `settings.gradle.kts` has its sources, every route has a page, and the CI
workflow is written. **Nothing has been compiled.** The next step is the first CI run, and
fixing what it finds.

- [x] Repo skeleton, build logic, CI workflow (written; never run)
- [x] Copied core modules adapted (`docs/UPSTREAM.md`)
- [x] core-navigation
- [x] core-library
- [x] core-userdata
- [x] core-social
- [x] core-player
- [x] core-design
- [x] app shell
- [x] features (14 modules)
- [x] unit tests written for the pure logic (never run)
- [x] docs (README, ARCHITECTURE, UPSTREAM, NOTICE)
- [ ] first green CI build
- [ ] first launch on a device; tuning of the gesture constants (`Gestures.kt`)
- [ ] instrumented tests

## What is unverified, most likely to break first

Written from reading library sources, not from a compiler. In rough order of risk:

1. **Compile errors in general.** Around 25,000 lines of Kotlin that have never met `kotlinc`.
   Two scripts were used in place of a compiler and are worth keeping in mind as *weaker* than
   one: every project import resolves to a declared symbol, and the drift rules pass on `:app`
   and the features. Neither checks types, overloads or named arguments.
2. **Version catalog and convention plugins.** `ipfsmedia.android.feature` applies KSP and Hilt
   itself and adds dependencies with `add("implementation", …)`; the Compose compiler plugin is
   applied per module. Accessor names in `gradle/libs.versions.toml` have not been resolved by
   Gradle.
3. **Experimental APIs.** Material 3 `1.5.0-rc01` Expressive components (`SplitButtonLayout`,
   `HorizontalFloatingToolbar`, `LoadingIndicator`, `PullToRefreshDefaults.LoadingIndicator`,
   `FilledIconButton(shapes = …)`), shared transitions, and Navigation 3's `NavDisplay(entries =
   …, sharedTransitionScope = …)`. Signatures were read from the sources of exactly those
   versions; a version bump can move them.
4. **Room and KSP** on the per-identity user database (`core-userdata`), including the embedded
   `EntrySnapshot` in three tables.
5. **Hilt multibindings**: `Set<EntryProviderInstaller>` (a function type) and
   `Set<IdentityStore>`.
6. **Media3**: the resolving data source blocking on the loader thread, clipped items sharing
   one staged file, and replacing a whole-album row with its songs while it is playing.
7. **JVM tests that need crypto providers** (Ed25519 in `MemberReceiptTest`).
8. **`core-haven-aol`** still applies `com.android.library` directly (compileSdk 36, minSdk 26).
   It is byte-identical to upstream and was left alone on purpose.
9. **The first-launch job** in CI uses an API 35 tablet emulator. It reports and does not gate.

## Decisions made while implementing (not in the plan)

1. **Module `core-navigation` added.** Route keys and the navigator must be visible to every
   feature module, and none of the planned modules is the right home.
2. **One catalogue scan per refresh.** haven-mobile's `refreshAccessible` scans the gate index twice,
   then pages every gate and resolves a block timestamp per block. With Arkiv's ~100 charged calls
   an hour that does not fit. The app instead scans the Haven groups once
   (`ArkivClient.scanCatalog`), writes every row to the mirror (`MediaRepository.replaceCatalog`)
   and derives the directory, DataDAO pages, uploader profiles, the feed and the Collection from
   that one result. Membership is kept beside it (`core-userdata`), so "what can I open" is a join.
3. **Dates are estimated from block height** (2 s blocks, as redhaven does) instead of one
   `eth_getBlockByNumber` per upload.
4. **Comments are read in the same refresh** (one paged query for `haven.board.thread` and
   `haven.board.post`), so "does this album have comments" is a local lookup and opening a page
   costs no Arkiv call. The entity-count probe in the plan is global, not per record, so it cannot
   answer that question.
5. **All Arkiv calls pass one gate** (`ArkivCallGate` in `core-arkiv`): two at a time, in-flight
   de-duplication, a short result cache, no retries, a cooldown after a 429.
6. **WalletConnect project id is not in the repo.** `core-wallet` reads `wallet.projectId` from
   `local.properties` / the `WALLET_PROJECT_ID` CI secret. haven-mobile's hardcoded id was removed.
7. **Queue entries play through a resolving data source**: the player holds `ipfsmedia://` item
   ids, and the file is staged (fetched, unlocked) when the player opens the entry.
8. **Staging never asks the wallet for anything on its own.** The queue advances in the
   background, where a signature prompt would arrive with no explanation. `MediaStager` throws
   `SIGNATURE_REQUIRED`; the service pauses on that row; the player bar says why and offers
   "Sign and play", which grants consent for that one upload.
9. **A wallet that is known not to be a member is refused before any fetch.** Unknown membership
   is still attempted: content already on the device plays with no network, and the signed
   unlock is the authority when there is one.
10. **Ungated uploads are stored with their CAR container stripped**, because the player needs a
    plain seekable file.
11. **Downloads in "keep unlocked" mode go through the stager**, since a plain `fetch` would put
    ciphertext into a store that mode expects to hold decrypted files.
12. **`WalletSession.restored`** was added so a wallet-first app can tell "still looking for a
    saved session" from "there is none", and not flash onboarding at a connected user.
13. **The feature convention plugin applies KSP and Hilt**, and experimental opt-ins are set once
    in the Compose convention plugin. The list there is the complete set the app may use.
14. **Input kind is read from `Configuration`**, not Compose's experimental `mediaQuery`. One
    wrapper (`InputKind`), so the source can be swapped in one file.
15. **Shortcuts are dispatched from `Activity.onKeyDown`**, which only sees keys nothing consumed.
    Focused items handle their own keys first, and text fields keep theirs.
16. **No reordering while shuffle is on.** The queue shows the order things will really play, and
    a move in that order would not mean what it looks like.
17. **The crash note is local.** An uncaught exception is written to a private file and shown
    once at the next start with a Copy button. There is no crash reporting service.
18. **Screens read the catalogue from `LocalLibraryState`**, provided once by the shell from the
    one shared flow. A screen has a view model only for what is its own.
19. **The notification permission is asked for at the first play**, not at launch.
20. **Card-to-hero transitions tag only the opened card.** The same upload can be on screen
    several times (the feed and a shelf); two pictures with one shared-element key in one page
    would both be drawn at the key's position. `ArtTransitions` remembers the card that was
    opened, by surface and record, and only that card and the hero share bounds.
21. **`ItemActions` has three actions that are not destinations**: `playFrom` (a tap on a song
    plays it with its album or playlist queued around it), `clearHistory`, and `removeDownloads`.
    All three are implemented by the same dispatcher, with the same notices and Undo.
22. **A mouse click on a song row selects it; a double-click plays.** A tap and Enter play. This
    is the plan's input table; rows for albums and videos (Collection's list view, search) open
    on a click, as their cards do.
23. **"New" in the feed is decided against what the feed had shown before the last refresh**, kept
    per identity (`FEED_SEEN_BLOCK`). Whoever starts a refresh, what was on screen stops being
    new when it starts. Nothing about plays enters into it.
24. **Home's shelves are local** (where a long item was left, played recently, on this device,
    your DataDAOs, people you follow) and sit above the feed, each with a grip. An empty shelf
    is not drawn but keeps its place in the saved order.
25. **A DataDAO page shows only the tabs that have something behind them.** About is always
    there. Tab names are part of saved navigation history (`DataDaoRoute.tab`).
26. **Settings offers fixed choices for sizes** (cache room, keep-for, staging room) instead of
    sliders: a choice is exact, reachable by keyboard, and says its value.
27. **Clearing storage never cuts the music.** `Downloads.clearAll` keeps the staged file of what
    is playing until it ends, then puts every item's offline state right.
28. **Multi-select with Ctrl or Shift click is not implemented.** Selecting several is done the
    touch way, which also works with a mouse: with the tray open, clicking other items adds them
    to what is held. The shortcut table has no "select all" behaviour behind it yet.
29. **No `LICENSE` file is added here.** The owner said the destination repository's licence is
    already set. `NOTICE` carries the attributions.

## Known gaps

- Selecting several rows with modifier keys (decision 28).
- Instrumented and screenshot tests (plan §13) are not written.
- Gesture timings and distances are first guesses, by design (plan §7).
