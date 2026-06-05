# Rivalry Technical Documentation

This directory explains how the Rivalry RuneLite plugin works and where to make
changes safely.

Start here if you are new to the codebase. The files are split by concern so you
can read only the part you need.

## Documentation Map

```text
docs/
  README.md
  architecture.md
  runtime-flow.md
  data-contracts.md
  development.md
  components/
    plugin-and-config.md
    data-loading.md
    crown-domain.md
    ui.md
```

## What To Read

- Need the big picture? Read [architecture.md](architecture.md).
- Need to understand refreshes, polling, startup, shutdown, or config changes?
  Read [runtime-flow.md](runtime-flow.md).
- Need to change plugin lifecycle, settings, notifications, or scheduling? Read
  [components/plugin-and-config.md](components/plugin-and-config.md).
- Need to change manual rivals, Wise Old Man groups, or hiscore fetching? Read
  [components/data-loading.md](components/data-loading.md).
- Need to change crown ranking, ties, aggregate categories, or persistence? Read
  [components/crown-domain.md](components/crown-domain.md).
- Need to change the side panel or category icons? Read
  [components/ui.md](components/ui.md).
- Need invariants for names, category ids, unranked values, or threading? Read
  [data-contracts.md](data-contracts.md).
- Need test commands and common change recipes? Read
  [development.md](development.md).

## One-Screen Summary

Rivalry tracks a roster of OSRS players, fetches their official hiscore data,
computes "crowns" for category leaders, renders the comparison in a RuneLite side
panel, and notifies the local player when they gain or lose a crown.

```text
RuneLite events/config
        |
        v
RivalryPlugin
        |
        +-- RosterResolver -- WomClient -- Wise Old Man API
        |
        +-- HiscoreService -- HiscoreClient -- official OSRS hiscores
        |
        +-- CrownCalculator -- PlayerStats/PlayerSnapshot -- CrownResult
        |
        +-- CrownStore -- ConfigManager
        |
        +-- RivalryPanel -- IconLoader -- SpriteManager/ItemManager/resources
        |
        +-- Notifier
```

Core principles:

- `RivalryPlugin` coordinates lifecycle, events, scheduling, refreshes,
  persistence, and notifications.
- `CrownCalculator` is pure computation. It has no RuneLite client, config,
  network, disk, or Swing dependencies.
- `RosterResolver`, `WomClient`, and `HiscoreService` isolate asynchronous data
  loading.
- `RivalryPanel` owns Swing view state and rendering.
- `CrownStore` persists crown holders through RuneLite `ConfigManager`.
- Value types carry data between layers and stay deliberately simple.
