# Architecture

How the pieces fit. The design this implements is `docs/PLAN.md`; decisions made while
implementing are in `docs/WORKLOG.md`; what was copied and changed is in `docs/UPSTREAM.md`.

## The shape of it

```
                         ┌────────────────────────────── :app ──────────────────────────────┐
                         │ MainActivity · AppRoot · MainShell (sidebar, content, queue, bar) │
                         │ ShellViewModel: the one action dispatcher, with undo             │
                         └───────────────┬───────────────────────────────────┬──────────────┘
                                         │ assembles                         │ provides
              ┌──────────────────────────┴─────────┐              ┌──────────┴───────────┐
              │ feature-*  (one per screen)        │              │ CompositionLocals    │
              │ home collection album video        │◄─────────────│ library state        │
              │ datadao uploader search playlists  │   read       │ item actions         │
              │ history nowplaying settings        │              │ navigator, stages…   │
              │ discussion identity onboarding     │              └──────────────────────┘
              └───────┬───────────────┬────────────┘
                      │               │
        ┌─────────────┴───┐   ┌───────┴─────────┐   ┌──────────────┐   ┌────────────────┐
        │ core-design     │   │ core-library    │   │ core-player  │   │ core-social    │
        │ tokens, theme,  │   │ product model,  │   │ staging,     │   │ refresh,       │
        │ components,     │   │ feed, search,   │   │ service,     │   │ discussion     │
        │ drag layer      │   │ drag rules      │   │ queue        │   │                │
        └─────────────────┘   └───────┬─────────┘   └──────┬───────┘   └───────┬────────┘
                                      │                    │                   │
                              ┌───────┴────────┐   ┌───────┴───────────────────┴────────┐
                              │ core-userdata  │   │ copied from haven-mobile           │
                              │ per-identity   │   │ arkiv · cache · cache-mirror ·     │
                              │ Room database  │   │ crypto · haven-aol · collections · │
                              └────────────────┘   │ wallet · security · attestation    │
                                                   └────────────────────────────────────┘
```

Feature modules never depend on `:app` or (with three stated exceptions) on each other. `:app`
never names a feature's screens: each feature provides an `EntryProviderInstaller` into a Hilt
set, and the shell runs them inside one `entryProvider` (the `modular/hilt` recipe). The
exceptions are small, one-way reuses of a composable: `album`, `video` and `datadao` use
`discussion`'s comment views; `nowplaying` uses `video`'s player surface; `:app` uses
`onboarding`, `identity` and `video` directly because the shell draws them itself.

## Data: one catalogue, derived once

- **The index is read in one place.** `RefreshCoordinator` (`core-social`) runs when the app
  opens and when the user asks. It walks the catalogue once, writes it to the mirror
  (`core-cache-mirror`), reads discussion once, and checks membership per DataDAO on the gate's
  own chain. Nothing else calls Arkiv, so moving around the app costs no index reads.
- **The catalogue is derived in one place.** `LibraryRepository` (`core-library`) combines the
  mirror, cached chapter lists and the identity's local data into one `LibraryState`: items with
  their access and offline state, DataDAOs, uploaders. It is one shared `StateFlow`.
- **Screens read it from a CompositionLocal.** The shell provides `LocalLibraryState`. A screen
  needs a view model only for what is its own (a preference, a list from `core-userdata`).
- **Local data is per identity.** `core-userdata` opens one Room database per wallet address:
  favourites, playlists, history, follows, pins, resume positions, preferences. Switching
  identity re-points every flow; disconnecting wipes it (`IdentityStore`).

## Playback: the queue holds references, files appear when needed

- A queue row is a Media3 item with the id `ipfsmedia://item/<recordKey>`. Its `PlayableEntry`
  (which upload, which song, start and end) rides in the item's extras, so the interface reads
  the queue back from the player and there is one source of truth.
- When ExoPlayer opens a row, a `ResolvingDataSource` asks `MediaStager` for the file:
  membership check, fetch through `foc-cache`, unlock through Haven-AOL, write to the plaintext
  spool. One fetch per upload, shared by every row of that album.
- A song is a clip of its album's file (`ClippingConfiguration`). An album queued before it was
  ever fetched is one row; when its file arrives the service replaces that row with its songs.
- **Staging never sends anything to the wallet on its own.** If a signature is needed it stops
  on that row and says so; the player bar offers "Sign and play".
- The player lives in `PlaybackService` (a `MediaSessionService`). The window can be resized,
  recreated or closed without touching playback. `PlayerConnection` is the window's view of it.

## Actions: one list, one dispatcher

- `destinationsFor(payload)` in `core-library` is the single definition of what can be done with
  an item. The tray, the right-click menu, keyboard commands and TalkBack's custom actions are
  all views of it.
- `dropVerdict(payload, target)` is the placement table for carrying an item somewhere.
- Every one of those ends at `ItemActions.perform(destination, payload, origin)`, implemented
  once by `ShellViewModel`. Anything that changes the user's data pushes an undo entry.
- The shared components (`MediaCard`, `MediaRow`, `SidebarItem`) carry the pick-up, drop, menu
  and keyboard behaviour. A screen built from them gets all of it.

## The drag layer

In-window only: Compose pointer input and an overlay, not Android's system drag and drop.
`DragController` holds what is held and where the pointer is; `DragHost` draws the carried card
and the tray; `Modifier.dropTarget` registers places that can take a drop. Timings and distances
are named constants in `Gestures.kt`, to be tuned on a device.

## Navigation

Navigation 3. Routes are serializable keys. `NavigationState` keeps one history per sidebar
section plus a forward list (Parachord has a forward button; the recipe does not), all as saved
state, so a window resize loses nothing. The content area is wrapped in a `SharedTransitionLayout`
so a card's picture travels to the hero of the page it opens.

## Design system

`core-design` is Parachord's tokens on Material 3 Expressive. Feature modules and `:app` declare
no colour, measurement, icon button or text style of their own; `DesignDriftTest` scans their
source and fails the build if one does.

## Tests

Pure logic is unit-tested where it lives: the feed, search, library derivation, destinations and
drop verdicts, playlist re-matching, ID3 chapters (`core-library`); queue model and persistence,
admission, staging states (`core-player`); receipts and comment nesting (`core-social`); the
drag controller, cover generation and the drift rules (`core-design`); undo (`:app`); shelf order
(`feature-home`). There are no instrumented tests yet.
