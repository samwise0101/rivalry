# Crown Domain Components

## PlayerStats And PlayerSnapshot

Files:

- `src/main/java/com/samwise0101/rivalry/PlayerStats.java`
- `src/main/java/com/samwise0101/rivalry/PlayerSnapshot.java`

`PlayerStats` is the interface consumed by `CrownCalculator`.

`PlayerSnapshot` adapts RuneLite `HiscoreResult` into `PlayerStats`:

- skills rank by experience;
- skills display by level;
- bosses and activities use the hiscore `level` field for KC/score;
- missing data returns `-1`;
- overall XP/level come from `HiscoreSkill.OVERALL`;
- total boss KC sums positive boss values and returns `-1` if no bosses are
  ranked.

The interface lets tests supply hand-built stats without constructing live
RuneLite objects.

## CrownCalculator

File: `src/main/java/com/samwise0101/rivalry/CrownCalculator.java`

`CrownCalculator` is pure. It takes:

- ordered roster;
- stats map keyed by lowercase player name;
- `CrownOptions`.

It returns `CrownResult` containing:

- `List<PlayerStanding>`;
- `Map<String, String>` holder map, where value is null for contested crowns.

Category construction:

- Adds `TOTAL_LEVEL` when skills are enabled.
- Adds `TOTAL_BOSS_KC` when bosses are enabled.
- Iterates RuneLite `HiscoreSkill.values()` for regular categories.
- Skips `OVERALL`.
- Filters by `HiscoreSkillType` and `CrownOptions`.
- Uses in-game skill-tab order for skills.
- Uses `HiscoreSkill.ordinal()` for bosses/activities.
- Uses RuneLite sprite ids or clue item ids for icon metadata.

Crown rules:

- Crown value decides the holder.
- Display value decides the visible diff.
- A strict single leader holds a crown.
- Ties for first are contested and have no holder.
- Unranked values are ignored for leadership.
- Nobody ranked means the category is omitted.

Diff rules:

- Holders show margin over runner-up.
- Non-holders show deficit to the holder by default.
- With `gapToNextPlayer`, non-holders show deficit to the player immediately
  above.
- `diff` is display units.
- `crownDiff` is crown-ranking units.

Important edge cases:

- Same skill level but less XP means `diff == 0` and `crownDiff < 0`; this is
  still behind.
- Contested crowns have `hasHolder == false`.
- Unknown/new skills not in `SKILL_ORDER` sort last.

Change guidance:

- Add aggregate categories in `categories()`.
- Add aggregate ids to `CrownStore.load()`.
- Add tests in `CrownCalculatorTest` for ranking-rule changes.
- Keep this class side-effect-free.

## CrownStore

File: `src/main/java/com/samwise0101/rivalry/CrownStore.java`

`CrownStore` persists current crown holders in RuneLite config.

Storage:

- Config group: `rivalry`.
- Key prefix: `crown_`.
- Examples: `crown_ATTACK`, `crown_ZULRAH`, `crown_TOTAL_LEVEL`.
- Blank means contested/no holder.

Lifecycle:

- `load()` runs on plugin startup.
- `setHolder()` runs after each computation.

Change guidance:

- Add new aggregate ids to `load()`.
- Preserve blank-as-contested unless adding a migration.

## Value Types

Files:

- `CategoryStat.java`
- `PlayerStanding.java`
- `CrownOptions.java`
- `CrownResult.java`

All are Lombok `@Value` immutable data carriers.

`CategoryStat` describes one category row/cell:

- name;
- sprite id;
- item id;
- hiscore type;
- display diff;
- crown diff;
- holder flags;
- aggregate flag;
- display order.

`PlayerStanding` describes one player:

- display name;
- crown count;
- category stats.

`CrownOptions` carries calculator settings.

`CrownResult` carries full calculator output.

