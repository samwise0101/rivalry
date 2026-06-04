package com.samwise0101.rivalry;

import net.runelite.client.hiscore.HiscoreSkill;

/**
 * The subset of a player's hiscore data the crown logic needs. Abstracting this
 * (rather than passing a live {@link net.runelite.client.hiscore.HiscoreResult})
 * lets {@link CrownCalculator} be unit tested with hand-built data.
 */
public interface PlayerStats
{
	/** Value used to rank crowns: total XP for skills, score/KC otherwise. -1 if unranked. */
	long crownValue(HiscoreSkill skill);

	/** Value shown in the UI: level for skills, score/KC otherwise. -1 if unranked. */
	int displayValue(HiscoreSkill skill);

	/** Total XP (Overall experience), or -1 if unavailable. */
	long overallXp();

	/** Total skill level (Overall level), or -1 if unavailable. */
	int totalLevel();

	/** Sum of all boss kill counts, or -1 if ranked in no bosses. */
	int totalBossKc();
}
