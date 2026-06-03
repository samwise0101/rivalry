package com.samwise0101.rivalry;

import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Each value maps to a HiscoreSkill and represents one crown that can be held.
 */
public enum CrownCategory
{
	// Skills (XP)
	ATTACK("Attack XP", HiscoreSkill.ATTACK, CategoryType.SKILL),
	DEFENCE("Defence XP", HiscoreSkill.DEFENCE, CategoryType.SKILL),
	STRENGTH("Strength XP", HiscoreSkill.STRENGTH, CategoryType.SKILL),
	HITPOINTS("Hitpoints XP", HiscoreSkill.HITPOINTS, CategoryType.SKILL),
	RANGED("Ranged XP", HiscoreSkill.RANGED, CategoryType.SKILL),
	PRAYER("Prayer XP", HiscoreSkill.PRAYER, CategoryType.SKILL),
	MAGIC("Magic XP", HiscoreSkill.MAGIC, CategoryType.SKILL),
	COOKING("Cooking XP", HiscoreSkill.COOKING, CategoryType.SKILL),
	WOODCUTTING("Woodcutting XP", HiscoreSkill.WOODCUTTING, CategoryType.SKILL),
	FLETCHING("Fletching XP", HiscoreSkill.FLETCHING, CategoryType.SKILL),
	FISHING("Fishing XP", HiscoreSkill.FISHING, CategoryType.SKILL),
	FIREMAKING("Firemaking XP", HiscoreSkill.FIREMAKING, CategoryType.SKILL),
	CRAFTING("Crafting XP", HiscoreSkill.CRAFTING, CategoryType.SKILL),
	SMITHING("Smithing XP", HiscoreSkill.SMITHING, CategoryType.SKILL),
	MINING("Mining XP", HiscoreSkill.MINING, CategoryType.SKILL),
	HERBLORE("Herblore XP", HiscoreSkill.HERBLORE, CategoryType.SKILL),
	AGILITY("Agility XP", HiscoreSkill.AGILITY, CategoryType.SKILL),
	THIEVING("Thieving XP", HiscoreSkill.THIEVING, CategoryType.SKILL),
	SLAYER("Slayer XP", HiscoreSkill.SLAYER, CategoryType.SKILL),
	FARMING("Farming XP", HiscoreSkill.FARMING, CategoryType.SKILL),
	RUNECRAFT("Runecraft XP", HiscoreSkill.RUNECRAFT, CategoryType.SKILL),
	HUNTER("Hunter XP", HiscoreSkill.HUNTER, CategoryType.SKILL),
	CONSTRUCTION("Construction XP", HiscoreSkill.CONSTRUCTION, CategoryType.SKILL),
	OVERALL("Overall XP", HiscoreSkill.OVERALL, CategoryType.SKILL),

	// Clues
	CLUE_BEGINNER("Beginner Clues", HiscoreSkill.CLUE_SCROLL_BEGINNER, CategoryType.CLUE),
	CLUE_EASY("Easy Clues", HiscoreSkill.CLUE_SCROLL_EASY, CategoryType.CLUE),
	CLUE_MEDIUM("Medium Clues", HiscoreSkill.CLUE_SCROLL_MEDIUM, CategoryType.CLUE),
	CLUE_HARD("Hard Clues", HiscoreSkill.CLUE_SCROLL_HARD, CategoryType.CLUE),
	CLUE_ELITE("Elite Clues", HiscoreSkill.CLUE_SCROLL_ELITE, CategoryType.CLUE),
	CLUE_MASTER("Master Clues", HiscoreSkill.CLUE_SCROLL_MASTER, CategoryType.CLUE),
	CLUE_ALL("Total Clues", HiscoreSkill.CLUE_SCROLL_ALL, CategoryType.CLUE),

	// Bosses (total KC — one aggregate crown)
	TOTAL_BOSS_KC("Total Boss KC", HiscoreSkill.ABYSSAL_SIRE, CategoryType.BOSS);

	public enum CategoryType
	{
		SKILL, BOSS, CLUE
	}

	final String displayName;
	final HiscoreSkill hiscoreSkill;
	final CategoryType type;

	CrownCategory(String displayName, HiscoreSkill hiscoreSkill, CategoryType type)
	{
		this.displayName = displayName;
		this.hiscoreSkill = hiscoreSkill;
		this.type = type;
	}
}
