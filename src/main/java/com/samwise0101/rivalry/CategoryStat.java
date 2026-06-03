package com.samwise0101.rivalry;

import lombok.Value;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * One category (skill / boss / activity) as shown in a player's expanded view.
 */
@Value
public class CategoryStat
{
	String name;

	/** RuneLite sprite id for the icon, or -1 if none. */
	int spriteId;

	/** OSRS item id for the icon (used for clue tiers), or -1 if none. */
	int itemId;

	HiscoreSkillType type;

	/**
	 * This player's value relative to the local player (theirs - mine):
	 * levels for skills, score/KC for bosses and activities.
	 * Null if the comparison is unavailable (either side unranked, or no local player).
	 */
	Integer diff;

	/** True if this player currently leads the category (holds the crown). */
	boolean holdsCrown;

	/** True if some player strictly leads the category (the crown is not contested). */
	boolean hasHolder;

	/** True for aggregate categories (Total Level, Total Boss KC) — shown first with a trophy icon. */
	boolean aggregate;
}
