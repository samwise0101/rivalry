# Architecture

Rivalry is a RuneLite side-panel plugin built around a narrow orchestration layer
and several focused services.

## Packages And Files

All production Java code lives in:

```text
src/main/java/com/samwise0101/rivalry/
```

The main components are:

- `RivalryPlugin`: lifecycle, RuneLite events, refresh pipeline, polling,
  notifications, persistence coordination.
- `RivalryConfig`: RuneLite config group and settings.
- `RosterResolver`: manual/WOM roster selection.
- `WomClient`: asynchronous Wise Old Man API client.
- `HiscoreService`: asynchronous official hiscore fetching.
- `PlayerStats` and `PlayerSnapshot`: abstraction over RuneLite hiscore data.
- `CrownCalculator`: pure crown computation.
- `CrownStore`: persisted crown holder cache.
- `RivalryPanel`: Swing side-panel renderer.
- `IconLoader`: category icon loading and scaling.
- Value types: `CategoryStat`, `PlayerStanding`, `CrownOptions`,
  `CrownResult`.

## Data Flow

```text
Config + local player name
        |
        v
RosterResolver
        |
        v
ordered roster
        |
        v
HiscoreService
        |
        v
Map<lowercaseName, PlayerStats>
        |
        v
CrownCalculator
        |
        v
CrownResult
        |
        +-- CrownStore + Notifier
        |
        +-- RivalryPanel
```

The local player name is captured from RuneLite game ticks. Manual rivals come
from config. WOM group rosters come from the Wise Old Man API. Hiscores always
come from RuneLite's official hiscore client using `HiscoreEndpoint.NORMAL`.

## Dependency Boundaries

`RivalryPlugin` is allowed to know about most services because it coordinates the
whole plugin.

`CrownCalculator` deliberately knows only about:

- Java collections/functions.
- `HiscoreSkill` and `HiscoreSkillType` category identity.
- gameval item/sprite constants used for display metadata.
- `PlayerStats`, `CrownOptions`, `CrownResult`, and `CategoryStat`.

It does not know about:

- RuneLite `Client`.
- RuneLite config.
- OkHttp/Gson.
- Swing.
- `ConfigManager`.

This boundary is what makes crown behavior easy to unit test.

`RivalryPanel` renders already-computed data. It should not fetch hiscores, read
config, or decide crown rules.

`WomClient` owns WOM HTTP and JSON parsing. It should not call RuneLite `Client`.

`HiscoreService` owns official hiscore fetching and maps successful lookups to
`PlayerSnapshot`.

## External Components

RuneLite APIs used:

- Plugin lifecycle: `Plugin`, `PluginDescriptor`.
- Event bus: `@Subscribe`, `GameStateChanged`, `GameTick`, `ConfigChanged`.
- Game client: `Client`, `GameState`, `Player`.
- UI: `ClientToolbar`, `NavigationButton`, `PluginPanel`, `ColorScheme`,
  `FontManager`, `MaterialTab`, `MaterialTabGroup`.
- Images: `ImageUtil`, `SpriteManager`, `ItemManager`, `AsyncBufferedImage`.
- Hiscores: `HiscoreClient`, `HiscoreEndpoint`, `HiscoreResult`,
  `HiscoreSkill`, `HiscoreSkillType`, `Skill`.
- Config: `ConfigManager`, `Config`, `ConfigGroup`, `ConfigItem`,
  `ConfigSection`, `Range`, `Notification`.
- Notifications: `Notifier`.
- Constants: `net.runelite.api.gameval.ItemID` and
  `net.runelite.api.gameval.SpriteID`.

Third-party/runtime libraries:

- OkHttp for asynchronous WOM HTTP requests.
- Gson for WOM JSON parsing.
- Guice/JSR-330 injection through `@Inject`, `@Singleton`, `@Provides`.
- Lombok `@Value` and `@Slf4j`.
- Swing/AWT for the side panel.
- Java concurrency through `CompletableFuture`, `ScheduledExecutorService`,
  `ScheduledFuture`, and `ConcurrentHashMap`.

