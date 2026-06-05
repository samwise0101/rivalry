# Development And Testing

## Build And Test Commands

On this Windows workspace, refresh PATH before running Gradle:

```powershell
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
```

Run unit tests:

```powershell
.\gradlew.bat test
```

Run the development RuneLite client:

```powershell
.\gradlew.bat run
```

The `run` task starts `com.samwise0101.rivalry.RivalryPluginTest`, which loads
the plugin as a built-in external plugin and launches RuneLite in developer mode.

Do not automate RuneScape gameplay. In-game behavior must be confirmed manually.

## Tests

`CrownCalculatorTest` covers:

- strict leader crown ownership;
- contested ties;
- same visible level but lower XP;
- gap-to-holder vs gap-to-next-player;
- unranked skill display;
- unranked boss omission;
- aggregate total level and boss KC.

`PlayerSnapshotTest` covers:

- XP vs display-level mapping;
- boss KC in RuneLite hiscore `level` field;
- missing/unranked values;
- overall XP/level;
- total boss KC aggregation;
- null hiscore result handling.

`WomClientTest` covers:

- member sorting by activity timestamp;
- max member limit;
- display-name fallback behavior;
- null timestamp handling;
- empty/null WOM response handling.

`RivalryPluginTest` is a manual development-client entrypoint.

## Common Change Recipes

### Add A New Aggregate Crown

1. Add a category id constant to `CrownCalculator`.
2. Add a `CategoryDef` in `CrownCalculator.categories()`.
3. Add the aggregate id to `CrownStore.load()`.
4. Add tests in `CrownCalculatorTest`.
5. Confirm `RivalryPanel` renders its type and aggregate row correctly.

### Change Crown Ranking Rules

1. Update `PlayerStats` if a new source value is needed.
2. Update `PlayerSnapshot` to map RuneLite hiscore data.
3. Update `CrownCalculator`.
4. Add or update `CrownCalculatorTest` and `PlayerSnapshotTest`.
5. Verify in game because hiscore freshness depends on logout state.

### Add A New Roster Source

1. Add config keys to `RivalryConfig`.
2. Add a source-specific client/service if external data is needed.
3. Extend `RosterResolver.resolve()`.
4. Preserve local-player-first and case-insensitive dedupe.
5. Add tests for pure selection/filtering logic.
6. Ensure third-party network toggles are opt-in and have the required warning.

### Add Or Change UI Sections

1. Add data to `CategoryStat`, `PlayerStanding`, or `CrownResult` if needed.
2. Populate it in `CrownCalculator`.
3. Render it in `RivalryPanel`.
4. Keep Swing mutation on the EDT.
5. Avoid fetching data directly from the panel.

### Add A Config Setting

1. Add the `@ConfigItem` in `RivalryConfig`.
2. Decide how `RivalryPlugin.onConfigChanged()` should respond:
   - no action for notification-only settings;
   - recompute for display-only settings;
   - refresh for roster/fetch-affecting settings;
   - reschedule for polling settings.
3. If replacing or renaming an existing key, add a migration.

## Known Limitations

- Hiscore data is only as current as a player's last logout.
- A transient hiscore lookup failure drops that player for one refresh cycle.
- WOM fallback only exists after one successful WOM fetch in the current plugin
  session.
- `trackClues()` is named for clues but enables all
  `HiscoreSkillType.ACTIVITY` categories.
- `RivalryPanel` still mixes view state and rendering; a future `PanelState`
  extraction would make UI changes easier.
- The `crownNotification` migration needs human in-game verification.

