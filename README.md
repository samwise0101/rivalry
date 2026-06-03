# Rivalry

Track yourself and your rivals across the Old School RuneScape hiscores and compete for **crowns**.

Rivalry pulls hiscore data for you and a group of rivals, awards a crown to whoever leads each category, and shows the standings in a side panel. When a rival overtakes you — or you overtake them — you get an in-game notification.

---

## Testing this plugin (before it's on the Plugin Hub)

Rivalry isn't on the RuneLite Plugin Hub yet, so it can't be installed from the in-client browser. To try it, you build and run it from source. This launches a **development RuneLite client** with the plugin already loaded. It takes about 10 minutes the first time.

> You'll need access to this repository to clone it. If you can read this on GitHub, you already have access.

### 1. Install the required tools

You need **Git** and a **Java 11 JDK**. You do **not** need to install Gradle — the project downloads it automatically.

**Windows** (in PowerShell):
```powershell
winget install --id Git.Git -e
winget install --id EclipseAdoptium.Temurin.11.JDK -e
```

**macOS** (with [Homebrew](https://brew.sh)):
```bash
brew install git
brew install --cask temurin@11
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt update
sudo apt install -y git openjdk-11-jdk
```

If you'd rather not use a package manager, download the JDK 11 installer directly from [adoptium.net](https://adoptium.net/temurin/releases/?version=11).

**After installing, close and reopen your terminal**, then verify both tools are available:
```bash
git --version
java -version      # should report version "11.x"
```

### 2. Download the plugin

```bash
git clone https://github.com/samwise0101/rivalry.git
cd rivalry
```

### 3. Run it

**Windows:**
```powershell
.\gradlew.bat run
```

**macOS / Linux:**
```bash
./gradlew run
```

The first run downloads RuneLite and its dependencies, so give it a few minutes. A RuneLite client window will open with Rivalry loaded.

### 4. Log in and use it

1. Log into the dev client with your Jagex account. If it won't let you log in, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide once (it has you tick a box in the official launcher to generate a credentials file the dev client reuses).
2. Click the **crown icon** in the right-hand toolbar to open the Rivalry panel.
3. Open the plugin's settings (wrench icon → **Rivalry**) and add rivals — see [Setup](#setup) below.
4. Click **Refresh Now**.

To stop, just close the client window.

> **Note:** hiscores only update after a player logs out, so a freshly-logged-in account may show slightly stale numbers. That's a limitation of the OSRS hiscores, not the plugin.

---

## Features

- **Crowns for every hiscore category** — one crown per skill (by XP), per boss (by kill count), and per activity (clues, minigames, etc.), plus aggregate **Total Level** and **Total Boss KC** crowns.
- **Your Crowns summary** — see how many of the crowns in play you currently hold (e.g. `Your Crowns: 53/113`).
- **Rivals leaderboard** — players ranked by crown count. Click a player to expand a **Skills / Bosses / Other** breakdown showing how they compare to the current crown holder in each category.
- **Within Reach** — the crowns you don't hold yet, grouped into Skills / Bosses / Other tabs and ordered by the smallest gap first, so you can see what's easiest to take next.
- **In-game notifications** — get a message when you claim a crown or lose one to a rival.
- **Two ways to set your rivals:**
  - **Manual** — enter up to 5 usernames.
  - **Wise Old Man group** — point the plugin at a WOM group ID and it tracks the most recently active members automatically.

## Setup

1. Install **Rivalry** from the RuneLite Plugin Hub and enable it.
2. Open the **Rivalry** side panel (crown icon in the toolbar).
3. Open the plugin settings and choose how to set your rivals:

### Manual rivals
Enter up to five OSRS usernames in the **Rivals** section.

### Wise Old Man group
In the **Group** section:
1. Enable **Use Wise Old Man group**.
2. Enter your **WOM group ID** (the number in your group's URL, `wiseoldman.net/groups/<id>`).
3. Optionally set **Max members** — how many of the group's most recently active players to track (default 10).

> The Wise Old Man option contacts a third-party server (wiseoldman.net) to read your group's membership. It is disabled by default and opt-in.

## How it works

- Hiscores are read from the **official OSRS hiscores**. They only update after you log out, so standings reflect your last logged-out state, and lookups are polled on an interval (configurable, default 15 minutes).
- A crown is awarded to whichever tracked player ranks highest in a category. Comparisons in the panel are shown **relative to the crown holder** — the holder's number is their lead over the runner-up, and everyone else's is their deficit to the holder.
- Names from Wise Old Man use that service's stored casing.

## Settings

| Section | Setting | Description |
|---|---|---|
| Group | Use Wise Old Man group | Populate rivals from a WOM group instead of manual entry |
| Group | WOM group ID | The numeric ID from your group's URL |
| Group | Max members | How many of the most recently active members to track |
| Rivals | Rival 1–5 | OSRS usernames (used when WOM group is disabled) |
| Crown Categories | Track Skills / Bosses / Activities | Which category types award crowns |
| Polling | Poll interval | How often to refresh the hiscores (5–60 min) |
| Notifications | Game chat message / Desktop notification | How you're told about crown changes |

## Notes & limitations

- Hiscore data is only as fresh as the player's last logout, so changes can lag real progress.
- "Within Reach" orders by the raw gap in each category's own units (levels, kill count, completions) — an intuitive proxy, not a precise time-to-complete estimate.
- Only players who appear on the normal hiscores can be tracked.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
