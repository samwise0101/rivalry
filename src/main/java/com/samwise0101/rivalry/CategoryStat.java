package com.samwise0101.rivalry;

import java.util.List;
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
	 * This player's value relative to the crown holder (or the contested top):
	 * levels for skills, score/KC for bosses and activities. Holder shows their
	 * margin (>= 0); everyone else shows their deficit (<= 0). Null if unranked.
	 */
	Integer diff;

	/**
	 * Same comparison as {@link #diff} but in the crown-ranking metric: XP for
	 * skills (so it differs from the level diff), score/KC for everything else.
	 * Null if unranked.
	 */
	Long crownDiff;

	/** True if this player currently leads the category (holds the crown). */
	boolean holdsCrown;

	/** The crown tier this player holds in this category, or null if none. */
	CrownTier crownTier;

	/** True if some player strictly leads the category (the crown is not contested). */
	boolean hasHolder;

	/** True when the displayed gap is to the next player above, not the crown holder. */
	boolean comparesToNextPlayer;

	/** Player name(s) this stat is compared against, or null when not applicable. */
	String comparisonPlayerName;

	/** Tooltip comparison rows, usually crown holder and next player above. */
	List<CategoryComparison> comparisons;

	/** True for aggregate categories (Total Level, Total Boss KC) — shown in their own row. */
	boolean aggregate;

	/** Display order within a tab (e.g. in-game skill order). Lower comes first. */
	int order;
}
