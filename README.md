<div align="center" style="max-width: 760px; margin: 0 auto;">

<h1 align="center">
  <img src="icon.png" alt="Crown" width="32" height="32" />
  RIVALRY
  <img src="icon.png" alt="Crown" width="32" height="32" />
</h1>

Turn the hiscores into a competition.

Rivalry tracks you and your chosen rivals across Old School RuneScape hiscore
categories, awards crowns to the leaders, and shows who is ahead, who is
catching up, and which crowns are closest for you to steal next.

Whether you are racing friends, comparing clanmates, or just want a little more
motivation to send one more boss trip, Rivalry turns passive hiscore numbers into
a live scoreboard.

![Rivalry side panel showing the crown summary, rivals leaderboard, and expanded player breakdown](src/main/resources/Browse_Rival_Leaderboard.gif)

## Why Use Rivalry?

OSRS progress is more fun when there is someone to chase.

Rivalry gives every tracked category a crown. If you have the most Attack XP, you
hold the Attack crown. If your friend has the most Vorkath KC, they hold the
Vorkath crown. The panel rolls those crowns into a simple leaderboard so you can
see who is winning overall, then lets you drill into the details.

Instead of asking "who has better stats?", Rivalry answers better questions:

<p>
Which crowns do I currently hold?<br>
Which rival is beating me overall?<br>
What are they beating me in?<br>
Which crowns am I closest to taking?<br>
Did someone just overtake me?
</p>

## Features

<p>
<strong>Crown leaderboard:</strong> players are ranked by how many category crowns they currently hold.<br><br>
<strong>Crowns across the hiscores:</strong> track skills, bosses, clue scrolls, minigames, and other hiscore activities.<br><br>
<strong>Aggregate crowns:</strong> compete for Total Level and Total Boss KC in addition to individual categories.<br><br>
<strong>Detailed breakdowns:</strong> expand any player to see Skills, Bosses, and Other tabs with category-by-category gaps.<br><br>
<strong>Within Reach:</strong> see the crowns you do not hold yet, sorted by the smallest gap first.
</p>

![Within Reach section showing closest crowns by level and XP](src/main/resources/Browse_Within_Reach.gif)

<p>
<strong>Skill XP mode:</strong> for skills, compare by visible level or by XP so level-99 rivalries still matter.<br><br>
<strong>Crown notifications:</strong> get notified when you claim a crown or lose one.
</p>

![Crown chat notifications when claiming or losing a crown](src/main/resources/Crown_Chat_Notifications.gif)

<p>
<strong>Manual rivals:</strong> enter up to five usernames directly.<br><br>
<strong>Wise Old Man groups:</strong> optionally populate rivals from a Wise Old Man group and track the most recently active members.
</p>

## How It Works

Add rivals manually or connect a Wise Old Man group. Rivalry fetches normal
account hiscores from the official OSRS hiscores, compares every tracked player,
and awards each crown to the single player who leads that category.

Tied categories are contested, so nobody gets the crown until someone pulls
ahead.

For skills, crowns are awarded by XP, not just level. That means two players can
both be level 99, but the player with more XP still holds the crown. Bosses and
activities use their hiscore score, kill count, or completion count.

## The Panel

The side panel is built for quick checks:

<p>
<strong>Your Crowns</strong> shows how many crowns you hold out of all crowns currently in play.<br><br>
<strong>Rivals</strong> ranks everyone by crown count.<br><br>
<strong>Expanded player rows</strong> show category gaps with compact icons and color-coded numbers.<br><br>
<strong>Within Reach</strong> highlights your closest targets so you can decide what to do next.
</p>

## Setup

<p>
<strong>1.</strong> Install <strong>Rivalry</strong> from the RuneLite Plugin Hub.<br>
<strong>2.</strong> Enable the plugin.<br>
<strong>3.</strong> Open the Rivalry panel from the crown icon in the side toolbar.<br>
<strong>4.</strong> Add rivals manually, or enable Wise Old Man group mode in the plugin settings.<br>
<strong>5.</strong> Click <strong>Refresh Now</strong> or let the plugin refresh on its polling interval.
</p>

### Manual Rivals

Use the **Rivals** settings to enter up to five OSRS usernames.

![Adding rivals manually via the settings panel](src/main/resources/Add_Rivals_Manual.gif)

### Wise Old Man Group

If your friends or clan already use Wise Old Man, Rivalry can populate the rival
list from a group:

<p>
<strong>1.</strong> Enable <strong>Use Wise Old Man group</strong>.<br>
<strong>2.</strong> Enter the numeric group ID from <code>wiseoldman.net/groups/&lt;id&gt;</code>.<br>
<strong>3.</strong> Choose how many recently active members to track.
</p>

This option contacts Wise Old Man, a third-party service, to read group
membership. It is disabled by default and must be enabled manually.

![Adding rivals from a Wise Old Man group](src/main/resources/Add_Rivals_WOM.gif)

## Settings At A Glance

<div align="center">
<div align="left" style="display: inline-block;">

| Section | Setting | What it does |
|---|---|---|
| Group | Use Wise Old Man group | Use a WOM group instead of manual rivals |
| Group | WOM group ID | Selects the group to read |
| Group | Max members | Limits how many recently active group members are tracked |
| Rivals | Rival 1-5 | Manual usernames |
| Crown Categories | Track Skills / Bosses / Activities | Chooses which hiscore categories award crowns |
| Display | Gap to next player | Compares you to the next player above you instead of only the crown holder |
| Polling | Poll interval | Controls automatic hiscore refresh timing |
| Notifications | Crown notification | Controls how crown gain/loss alerts are delivered |

</div>
</div>

## Notes

<p>
Hiscores update when players log out, so very recent progress may not appear immediately.<br><br>
Rivalry uses the normal OSRS hiscores.<br><br>
Only players who appear on the normal hiscores can be tracked.<br><br>
Wise Old Man is used only to find group members. Hiscore comparison still uses the official OSRS hiscores.
</p>

## Maintainers

Technical documentation lives in [docs/](docs/OVERVIEW.md).
Release notes live in [CHANGELOG.md](CHANGELOG.md).

## License

BSD 2-Clause. See [LICENSE](LICENSE).

</div>
