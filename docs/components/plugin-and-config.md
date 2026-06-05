# Plugin And Config Components

## RivalryPlugin

File: `src/main/java/com/samwise0101/rivalry/RivalryPlugin.java`

Responsibilities:

- RuneLite plugin lifecycle.
- Event subscriptions.
- Poll scheduling.
- Refresh orchestration.
- Config migration.
- Holder-change detection.
- Notification delivery.
- Panel creation and updates.

Injected dependencies:

- `Client`: game state and local player name.
- `RivalryConfig`: typed config.
- `ConfigManager`: raw config access for migration and provider creation.
- `Notifier`: RuneLite notification subsystem.
- `ClientToolbar`: side-panel navigation.
- `ScheduledExecutorService`: delayed and periodic work.
- `IconLoader`, `RosterResolver`, `HiscoreService`, `CrownCalculator`,
  `CrownStore`: plugin services.

Important fields:

- `pollTask`: current fixed-rate polling task.
- `localPlayerName`: volatile local player name captured on game ticks.
- `lastRoster`, `lastStats`, `lastComputeLocalName`: cached successful compute
  inputs.
- `seeded`: suppresses notifications for the first holder pass.

Config key routing:

- Poll key: reschedule.
- Notification keys: no data work.
- Display keys: recompute from cache.
- Roster/group keys: full refresh.

Change guidance:

- Put new roster sources in `RosterResolver`.
- Put new data-fetch behavior in `HiscoreService` or a focused service.
- Put ranking behavior in `CrownCalculator`.
- Put rendering behavior in `RivalryPanel`.
- Do not block in startup or shutdown.

## RivalryConfig

File: `src/main/java/com/samwise0101/rivalry/RivalryConfig.java`

Config group: `rivalry`.

Sections:

- `Group`: Wise Old Man group mode.
- `Rivals`: manual rival names.
- `Crown Categories`: category inclusion toggles.
- `Display`: comparison mode.
- `Polling`: refresh interval.
- `Notifications`: crown gain/loss notification.

Important settings:

- `useWomGroup()`: false by default, opt-in because it calls a third-party
  server.
- `womGroupId()`: WOM group id.
- `womMaxMembers()`: capped 1 to 50.
- `rival1()` through `rival5()`: manual rival names.
- `trackSkills()`, `trackBosses()`, `trackClues()`: included category types.
- `gapToNextPlayer()`: compare non-holders to the player immediately above.
- `pollIntervalMinutes()`: capped 5 to 60.
- `crownNotification()`: RuneLite `Notification`.

Notification config:

- Current key: `crownNotification`.
- Legacy keys: `notifyGameChat`, `notifyDesktop`.
- `RivalryPlugin.migrateNotificationConfig()` converts legacy keys when the new
  key is absent.
- Default behavior is game-message on, tray off, sound off.

Change guidance:

- Never rename a config key without migration.
- Add display-only settings to `RivalryPlugin.DISPLAY_KEYS`.
- Add warning text for any new opt-in third-party server feature.

