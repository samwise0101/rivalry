# Changelog

All notable changes to Rivalry will be documented in this file.

## 1.1 - Unreleased

### Added

- Added gold, silver, and bronze crown tiers for each tracked category.
- Award tied ranks to all tied players, as long as the tied category value is greater than zero.
- Added tier-specific crown counts to the top panel summary.
- Added gold, silver, and bronze crown counts beside each player in the leaderboard.
- Added a Total XP crown to the Skills panel.
- Added tier-specific crown gain/loss notifications, such as "You gained the silver crown for Cooking!".
- Added aggregate crown notifications when more than three crowns change at once.
- Added immediate local skill and Total Level crown detection from RuneLite stat changes, without waiting for the next hiscore poll.
- Added richer category tooltips that show both crown-holder and next-player gaps with player names and crown tiers.

### Changed

- Changed the crown summary and leaderboard crown count order to gold, silver, bronze.
- Changed leaderboard sorting so equal crown counts are broken by Total XP, then Total Level, then player name.
- Changed category value colors to use the held crown tier color; categories with no held crown are red.
- Changed leading tooltip copy from "You lead by..." to "Leading by...".
- Changed tied crown holder storage and comparisons to support multiple holders per tier.

### Fixed

- Fixed zero-value ties incorrectly awarding crowns.
- Fixed stale hiscore refreshes overwriting local skill progress before logout.
- Fixed crown notification spam when switching between multiple accounts.
- Fixed pronoun-specific tooltip copy when viewing another player's category breakdown.

## 1.0

### Added

- Initial Rivalry side-panel plugin.
- Manual rival tracking for up to five players.
- Optional Wise Old Man group roster support.
- Hiscore-based crown calculation across skills, bosses, clue scrolls, minigames, and other activities.
- Aggregate crowns for Total Level and Total Boss KC.
- Crown leaderboard ranked by crown count.
- Expandable player breakdowns with Skills, Bosses, and Other tabs.
- Category icons for skills, bosses, activities, clue tiers, and aggregate rows.
- Gap display against the crown holder, with optional gap-to-next-player mode.
- Within Reach view for closest crowns, including skill level and XP modes.
- Crown gain/loss notifications with optional chat messages and sounds.
- Configurable crown categories, polling interval, notification options, manual rivals, and Wise Old Man group settings.
- Unit coverage for crown calculation, hiscore snapshot mapping, roster resolution, and Wise Old Man parsing.
