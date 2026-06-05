# UI Components

## RivalryPanel

File: `src/main/java/com/samwise0101/rivalry/RivalryPanel.java`

`RivalryPanel` is the Swing side panel shown in RuneLite.

External components:

- Swing: `JPanel`, `JLabel`, `JButton`, `BoxLayout`, `GridLayout`, etc.
- RuneLite UI: `PluginPanel`, `ColorScheme`, `FontManager`, `MaterialTab`,
  `MaterialTabGroup`.
- `IconLoader` for category icons.

Top-level structure:

- title;
- `Refresh Now` button;
- status label;
- crown summary;
- collapsible `Rivals` section;
- collapsible `Within Reach` section.

State retained across refreshes:

- expanded player rows;
- selected tab per player;
- top-level section expansion;
- selected within-reach tab;
- selected skill reach mode;
- last standings and local player name.

Rendering:

- `updateStandings()` stores latest data and rebuilds on the EDT.
- Player rows are sorted by crown count descending.
- Expanded rows show `Skills`, `Bosses`, and `Other` tabs.
- Regular categories render in a 3-column grid.
- Aggregate categories render in a full-width row.

Color semantics:

- holder: green;
- behind: red;
- unknown/unranked: light gray;
- contested `diff == 0`: light gray;
- same visible value but behind by crown metric: red because `hasHolder` is true.

Within Reach:

- Finds the local player's `PlayerStanding`.
- Shows closest crowns the local player does not hold.
- Skills have `By Level` and `By XP` modes.
- Bosses and activities sort by display gap.
- Lists are limited to 10 entries.

Threading:

- `setStatus()` uses `SwingUtilities.invokeLater()`.
- `updateStandings()` uses `SwingUtilities.invokeLater()`.
- Icon callbacks also switch to the EDT through `IconLoader`.

Change guidance:

- Keep calculation out of this class.
- Add source data through value types and `CrownCalculator` first.
- If view state grows further, extract a `PanelState`.

## IconLoader

File: `src/main/java/com/samwise0101/rivalry/IconLoader.java`

`IconLoader` resolves and applies icons to labels.

External components:

- RuneLite `SpriteManager`.
- RuneLite `ItemManager`.
- RuneLite `AsyncBufferedImage`.
- RuneLite `ImageUtil`.
- Swing `ImageIcon`, `JLabel`, `SwingUtilities`.

Icon priority:

1. `spriteId > 0`: load RuneLite sprite asynchronously.
2. `itemId > 0`: load OSRS item image asynchronously.
3. aggregate category: use bundled `/trophy.png`.

Icons are scaled to 18 px.

Resources:

- `/rivalry_icon.png`: toolbar icon, loaded by `RivalryPlugin`.
- `/trophy.png`: aggregate row icon, loaded by `IconLoader`.
- root `icon.png`: repository/package icon, not used by the runtime side panel.

Change guidance:

- Keep label mutation on the EDT.
- Prefer gameval constants for new item/sprite ids.
- Keep bundled PNGs small.

