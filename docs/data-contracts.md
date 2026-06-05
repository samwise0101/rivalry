# Data Contracts And Invariants

These rules are assumed across components. Preserve them when changing behavior.

## Roster

- Player names are display-case strings.
- Duplicate names are removed case-insensitively.
- The local player is first when known.
- Manual roster entries are trimmed and blank entries are ignored.

## Stats Map

- `HiscoreService.fetch()` returns `Map<String, PlayerStats>`.
- Keys are lowercase player names.
- Missing key means lookup failed or no stats were available for that refresh.
- The map is treated as immutable once cached by `RivalryPlugin`.

## Unranked Values

- `-1` means unavailable or unranked in the data layer.
- `null` `diff` / `crownDiff` means the UI should show unknown (`?`).
- Skills are shown even when unranked.
- Bosses and activities are omitted for a player when that player is unranked.

## Crown Holders

- A non-null holder means exactly one player strictly leads the category.
- `null` means contested or no holder.
- Persisted blank string means contested/no holder.
- First compute after startup seeds stored holder state but does not notify.

## Category IDs

- Regular categories use `HiscoreSkill.name()`, such as `ATTACK` or `ZULRAH`.
- Aggregate categories use `TOTAL_LEVEL` and `TOTAL_BOSS_KC`.
- `CrownCalculator.categoryDisplayName(id)` maps ids to notification-friendly
  names.

## Ranking Values

- Skills rank by XP.
- Skills display by visible level.
- Bosses and activities rank and display by the hiscore `level` field, which
  RuneLite uses for KC/score.
- `TOTAL_LEVEL` ranks by overall XP and displays by total level.
- `TOTAL_BOSS_KC` ranks/displays by summed boss KC.

## Threading

- WOM network I/O runs on OkHttp callbacks.
- Hiscore lookups are asynchronous and staggered through
  `ScheduledExecutorService`.
- Swing mutations happen on the EDT through `SwingUtilities.invokeLater()`.
- RuneLite `Client` reads happen on RuneLite event/client-thread paths.
- If a future async callback must call back into `Client`, use
  `clientThread.invoke()`.

