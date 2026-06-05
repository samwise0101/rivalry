# Runtime Flow

This file describes how the plugin starts, refreshes data, reacts to config
changes, sends notifications, and shuts down.

## Startup

`RivalryPlugin.startUp()`:

1. Runs `migrateNotificationConfig()` to convert old notification booleans into
   the current RuneLite `Notification` config object.
2. Creates `RivalryPanel` and wires its refresh callback to `triggerRefresh()`.
3. Loads `/rivalry_icon.png` via RuneLite `ImageUtil`.
4. Creates a `NavigationButton` and registers it with `ClientToolbar`.
5. Loads persisted holders with `CrownStore.load()`.
6. Calls `schedulePoll()`.
7. If already logged in, schedules a startup refresh after
   `STARTUP_REFRESH_DELAY_SECONDS`.

Startup does not block.

## Local Player Tracking

The local player name is needed for roster ordering, the crown summary, "Within
Reach", and local-player notifications.

- `onGameStateChanged(LOGGED_IN)` schedules a refresh after
  `LOGIN_REFRESH_DELAY_SECONDS`.
- `onGameStateChanged(LOGIN_SCREEN/HOPPING)` clears `localPlayerName`.
- `onGameTick()` reads `client.getLocalPlayer().getName()` and writes it to a
  `volatile` field.

The field is `volatile` because executor refreshes read it outside the game tick
callback.

## Refresh Pipeline

`triggerRefresh()` submits `refresh()` to the injected
`ScheduledExecutorService`.

`refresh()`:

1. Captures the current `localPlayerName`.
2. Calls `RosterResolver.resolve(localName)`.
3. Displays a user-facing status if roster resolution fails.
4. Displays an empty-roster status if no players are available.
5. Sets status to `Refreshing...`.
6. Calls `HiscoreService.fetch(roster)`.
7. Calls `computeAndUpdate(localName, roster, stats)` when lookups finish.

`computeAndUpdate()`:

1. Caches `lastRoster`, `lastStats`, and `lastComputeLocalName`.
2. Builds `CrownOptions` from `RivalryConfig`.
3. Calls `CrownCalculator.calculate(...)`.
4. Calls `applyHolderChanges(...)`.
5. Updates `RivalryPanel` with standings and timestamp.

Exceptions during compute are logged at `warn`, and the panel status is set to
`Refresh failed - see logs`.

## Config Changes

`RivalryPlugin.onConfigChanged()` classifies config keys:

- `pollIntervalMinutes`: reschedule polling.
- notification keys: no recompute needed.
- `trackSkills`, `trackBosses`, `trackClues`, `gapToNextPlayer`: recompute from
  cached stats.
- everything else in the `rivalry` group: trigger a full refresh.

The cached recompute path avoids re-fetching hiscores when only display/category
presentation changes.

## Notifications

`applyHolderChanges()` compares freshly computed holders to values stored in
`CrownStore`.

Notifications are suppressed until after the first compute by the `seeded` flag.
Once seeded:

- if the local player becomes a holder, notify "You claimed the X crown!"
- if the local player was the previous holder and no longer is, notify loss
  text.

The plugin delegates notification delivery to RuneLite:

```java
notifier.notify(config.crownNotification(), message);
```

The configured `Notification` decides game message, tray, sound, focus, flash,
and focused-client behavior.

## Shutdown

`RivalryPlugin.shutDown()`:

- removes the `NavigationButton`;
- cancels `pollTask` if present;
- clears `seeded`;
- logs a one-time shutdown message.

It does not wait for executor termination.

