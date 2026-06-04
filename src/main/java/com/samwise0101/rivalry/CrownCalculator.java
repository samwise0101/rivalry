package com.samwise0101.rivalry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import lombok.Value;
import net.runelite.api.SpriteID;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * Pure crown computation: given a roster and each player's stats, works out who
 * holds each crown and builds the per-player comparison rows. No I/O, no config,
 * no UI — everything it needs is passed in, so it is fully unit testable.
 */
public class CrownCalculator
{
	static final String TOTAL_LEVEL_ID = "TOTAL_LEVEL";
	static final String TOTAL_BOSS_KC_ID = "TOTAL_BOSS_KC";

	// Skills in the order they appear in the in-game skills tab (3 columns, row by row).
	private static final List<HiscoreSkill> SKILL_ORDER = Arrays.asList(
		HiscoreSkill.ATTACK, HiscoreSkill.HITPOINTS, HiscoreSkill.MINING,
		HiscoreSkill.STRENGTH, HiscoreSkill.AGILITY, HiscoreSkill.SMITHING,
		HiscoreSkill.DEFENCE, HiscoreSkill.HERBLORE, HiscoreSkill.FISHING,
		HiscoreSkill.RANGED, HiscoreSkill.THIEVING, HiscoreSkill.COOKING,
		HiscoreSkill.PRAYER, HiscoreSkill.CRAFTING, HiscoreSkill.FIREMAKING,
		HiscoreSkill.MAGIC, HiscoreSkill.FLETCHING, HiscoreSkill.WOODCUTTING,
		HiscoreSkill.RUNECRAFT, HiscoreSkill.SLAYER, HiscoreSkill.FARMING,
		HiscoreSkill.CONSTRUCTION, HiscoreSkill.HUNTER);

	/**
	 * @param roster ordered player names (display case); the local player is expected first
	 * @param stats  player stats keyed by lowercase name
	 * @param options which category types to include and how to compute gaps
	 */
	public CrownResult calculate(List<String> roster, Map<String, PlayerStats> stats, CrownOptions options)
	{
		Map<String, List<CategoryStat>> statsByPlayer = new HashMap<>();
		Map<String, Integer> crownCount = new HashMap<>();
		for (String p : roster)
		{
			statsByPlayer.put(p, new ArrayList<>());
			crownCount.put(p, 0);
		}

		Map<String, String> holders = new HashMap<>();
		for (CategoryDef def : categories(options))
		{
			processCategory(def, roster, stats, options, statsByPlayer, crownCount, holders);
		}

		List<PlayerStanding> standings = new ArrayList<>();
		for (String p : roster)
		{
			standings.add(new PlayerStanding(p, crownCount.get(p), statsByPlayer.get(p)));
		}
		return new CrownResult(standings, holders);
	}

	/** Human-readable name for a category id (used for notifications etc.). */
	public static String categoryDisplayName(String id)
	{
		if (TOTAL_LEVEL_ID.equals(id))
		{
			return "Total Level";
		}
		if (TOTAL_BOSS_KC_ID.equals(id))
		{
			return "Total Boss KC";
		}
		try
		{
			return HiscoreSkill.valueOf(id).getName();
		}
		catch (IllegalArgumentException e)
		{
			return id;
		}
	}

	private List<CategoryDef> categories(CrownOptions options)
	{
		List<CategoryDef> defs = new ArrayList<>();

		if (options.isTrackSkills())
		{
			defs.add(new CategoryDef(TOTAL_LEVEL_ID, "Total Level", HiscoreSkillType.SKILL,
				SpriteID.TAB_STATS, -1, true, -1,
				PlayerStats::overallXp, PlayerStats::totalLevel));
		}
		if (options.isTrackBosses())
		{
			defs.add(new CategoryDef(TOTAL_BOSS_KC_ID, "Total Boss KC", HiscoreSkillType.BOSS,
				-1, -1, true, -1,
				PlayerStats::totalBossKc, PlayerStats::totalBossKc));
		}

		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			HiscoreSkillType type = skill.getType();
			if (type == HiscoreSkillType.OVERALL || !typeEnabled(type, options))
			{
				continue;
			}
			int order = type == HiscoreSkillType.SKILL ? skillOrder(skill) : skill.ordinal();
			defs.add(new CategoryDef(skill.name(), skill.getName(), type,
				skill.getSpriteId(), clueItemId(skill), false, order,
				s -> s.crownValue(skill), s -> s.displayValue(skill)));
		}
		return defs;
	}

	private void processCategory(CategoryDef def, List<String> roster, Map<String, PlayerStats> stats,
		CrownOptions options, Map<String, List<CategoryStat>> statsByPlayer,
		Map<String, Integer> crownCount, Map<String, String> holders)
	{
		// Pass 1: find the leading crown value, count ties, gather per-player values.
		long best = -1;
		int topCount = 0;
		int topDisplay = -1;
		String topPlayer = null;
		Map<String, Integer> displays = new HashMap<>();
		Map<String, Long> crownValues = new HashMap<>();
		for (String name : roster)
		{
			PlayerStats ps = stats.get(name.toLowerCase());
			long crownVal = ps != null ? def.crownValueFn.applyAsLong(ps) : -1;
			int displayVal = ps != null ? def.displayValueFn.applyAsInt(ps) : -1;
			displays.put(name, displayVal);
			crownValues.put(name, crownVal);

			if (crownVal < 0)
			{
				continue;
			}
			if (crownVal > best)
			{
				best = crownVal;
				topCount = 1;
				topPlayer = name;
				topDisplay = displayVal;
			}
			else if (crownVal == best)
			{
				topCount++;
			}
		}

		if (best < 0)
		{
			return; // nobody ranked
		}

		// A crown is only held when a single player strictly leads; a tie is contested.
		String holder = topCount == 1 ? topPlayer : null;
		holders.put(def.id, holder);
		if (holder != null)
		{
			crownCount.merge(holder, 1, Integer::sum);
		}

		// Best display/crown value among players other than the holder (for the holder's margin).
		int runnerUp = 0;
		long runnerUpCrown = 0;
		for (String name : roster)
		{
			if (holder != null && name.equals(holder))
			{
				continue;
			}
			runnerUp = Math.max(runnerUp, displays.get(name));
			runnerUpCrown = Math.max(runnerUpCrown, crownValues.get(name));
		}

		for (String p : roster)
		{
			int displayVal = displays.get(p);
			long crownVal = crownValues.get(p);

			// Skills are always listed; bosses/activities only when this player is ranked.
			if (def.type != HiscoreSkillType.SKILL && displayVal < 0)
			{
				continue;
			}

			boolean holds = holder != null && p.equalsIgnoreCase(holder);
			Integer diff;
			Long crownDiff;
			if (displayVal < 0)
			{
				diff = null;
				crownDiff = null;
			}
			else if (holds)
			{
				diff = topDisplay - runnerUp;
				crownDiff = best - runnerUpCrown;
			}
			else if (options.isGapToNextPlayer())
			{
				long aboveCrown = Long.MAX_VALUE;
				int aboveDisplay = displayVal;
				boolean found = false;
				for (String q : roster)
				{
					long qc = crownValues.get(q);
					if (qc >= 0 && qc > crownVal && qc < aboveCrown)
					{
						aboveCrown = qc;
						aboveDisplay = displays.get(q);
						found = true;
					}
				}
				diff = found ? displayVal - aboveDisplay : 0;
				crownDiff = found ? crownVal - aboveCrown : 0L;
			}
			else
			{
				diff = displayVal - topDisplay;
				crownDiff = crownVal - best;
			}

			statsByPlayer.get(p).add(new CategoryStat(def.displayName, def.spriteId, def.itemId,
				def.type, diff, crownDiff, holds, holder != null, def.aggregate, def.order));
		}
	}

	private static boolean typeEnabled(HiscoreSkillType type, CrownOptions options)
	{
		switch (type)
		{
			case SKILL:
				return options.isTrackSkills();
			case BOSS:
				return options.isTrackBosses();
			case ACTIVITY:
				return options.isTrackActivities();
			default:
				return false;
		}
	}

	private static int skillOrder(HiscoreSkill skill)
	{
		int i = SKILL_ORDER.indexOf(skill);
		return i < 0 ? Integer.MAX_VALUE : i; // unknown/new skills sort last
	}

	/** Maps clue scroll tiers (which lack a sprite) to a representative clue-scroll item id, else -1. */
	private static int clueItemId(HiscoreSkill skill)
	{
		// TODO (improvement plan §2.1): replace these literals with gameval ItemID constants.
		switch (skill)
		{
			case CLUE_SCROLL_BEGINNER:
				return 23182; // Clue scroll (beginner)
			case CLUE_SCROLL_EASY:
				return 2677;  // Clue scroll (easy)
			case CLUE_SCROLL_MEDIUM:
				return 2801;  // Clue scroll (medium)
			case CLUE_SCROLL_HARD:
				return 2722;  // Clue scroll (hard)
			case CLUE_SCROLL_ELITE:
				return 12073; // Clue scroll (elite)
			case CLUE_SCROLL_MASTER:
				return 19835; // Clue scroll (master)
			default:
				return -1;
		}
	}

	/** Immutable description of one tracked category. */
	@Value
	private static class CategoryDef
	{
		String id;
		String displayName;
		HiscoreSkillType type;
		int spriteId;
		int itemId;
		boolean aggregate;
		int order;
		ToLongFunction<PlayerStats> crownValueFn;
		ToIntFunction<PlayerStats> displayValueFn;
	}
}
