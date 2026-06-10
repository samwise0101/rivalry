package com.samwise0101.rivalry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
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
	static final String TOTAL_XP_ID = "TOTAL_XP";
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
		Map<String, Integer> goldCrownCount = new HashMap<>();
		Map<String, Integer> silverCrownCount = new HashMap<>();
		Map<String, Integer> bronzeCrownCount = new HashMap<>();
		for (String p : roster)
		{
			statsByPlayer.put(p, new ArrayList<>());
			goldCrownCount.put(p, 0);
			silverCrownCount.put(p, 0);
			bronzeCrownCount.put(p, 0);
		}

		Map<String, String> holders = new HashMap<>();
		Map<CrownTier, Map<String, String>> tierHoldersByCategory = new HashMap<>();
		for (CrownTier tier : CrownTier.values())
		{
			tierHoldersByCategory.put(tier, new HashMap<>());
		}
		for (CategoryDef def : categories(options))
		{
			processCategory(def, roster, stats, options, statsByPlayer,
				goldCrownCount, silverCrownCount, bronzeCrownCount, holders, tierHoldersByCategory);
		}

		List<PlayerStanding> standings = new ArrayList<>();
		for (String p : roster)
		{
			PlayerStats playerStats = stats.get(p.toLowerCase());
			long totalXp = playerStats != null ? playerStats.overallXp() : -1;
			int totalLevel = playerStats != null ? playerStats.totalLevel() : -1;
			standings.add(new PlayerStanding(p, goldCrownCount.get(p), silverCrownCount.get(p),
				bronzeCrownCount.get(p), totalXp, totalLevel, statsByPlayer.get(p)));
		}
		return new CrownResult(standings, holders, tierHoldersByCategory);
	}

	/** Human-readable name for a category id (used for notifications etc.). */
	public static String categoryDisplayName(String id)
	{
		if (TOTAL_LEVEL_ID.equals(id))
		{
			return "Total Level";
		}
		if (TOTAL_XP_ID.equals(id))
		{
			return "Total XP";
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
				net.runelite.api.gameval.SpriteID.SideIcons.STATS, -1, true, -1,
				PlayerStats::overallXp, PlayerStats::totalLevel));
			defs.add(new CategoryDef(TOTAL_XP_ID, "Total XP", HiscoreSkillType.SKILL,
				net.runelite.api.gameval.SpriteID.SideIcons.STATS, -1, true, 0,
				PlayerStats::overallXp, s -> safeLongToInt(s.overallXp())));
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
		Map<String, Integer> goldCrownCount, Map<String, Integer> silverCrownCount,
		Map<String, Integer> bronzeCrownCount, Map<String, String> holders,
		Map<CrownTier, Map<String, String>> tierHoldersByCategory)
	{
		// Pass 1: find the leading crown value and gather per-player values.
		long best = -1;
		int topDisplay = -1;
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
				topDisplay = displayVal;
			}
		}

		if (best < 0)
		{
			return; // nobody ranked
		}

		Map<CrownTier, List<String>> tierHolders = crownTierHolders(roster, crownValues);
		List<String> goldHolders = tierHolders.getOrDefault(CrownTier.GOLD, Collections.emptyList());
		String holder = holderString(goldHolders);
		holders.put(def.id, holder);
		for (CrownTier tier : CrownTier.values())
		{
			tierHoldersByCategory.get(tier).put(def.id,
				holderString(tierHolders.getOrDefault(tier, Collections.emptyList())));
		}
		mergeTierHolders(goldCrownCount, goldHolders);
		mergeTierHolders(silverCrownCount, tierHolders.get(CrownTier.SILVER));
		mergeTierHolders(bronzeCrownCount, tierHolders.get(CrownTier.BRONZE));

		// Best display/crown value among players not holding gold (for gold holder margins).
		int runnerUp = 0;
		long runnerUpCrown = 0;
		boolean runnerUpFound = false;
		for (String name : roster)
		{
			if (goldHolders.stream().anyMatch(name::equalsIgnoreCase))
			{
				continue;
			}
			runnerUp = Math.max(runnerUp, displays.get(name));
			runnerUpCrown = Math.max(runnerUpCrown, crownValues.get(name));
			runnerUpFound = true;
		}
		if (!runnerUpFound)
		{
			runnerUp = topDisplay;
			runnerUpCrown = best;
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

			CrownTier tier = tierForPlayer(tierHolders, p);
			boolean holds = tier == CrownTier.GOLD;
			Integer diff;
			Long crownDiff;
			boolean comparesToNextPlayer = false;
			String comparisonPlayerName = null;
			List<CategoryComparison> comparisons = Collections.emptyList();
			long aboveCrown = Long.MAX_VALUE;
			int aboveDisplay = displayVal;
			boolean foundAbove = false;
			if (displayVal >= 0 && !holds)
			{
				for (String q : roster)
				{
					long qc = crownValues.get(q);
					if (qc >= 0 && qc > crownVal && qc < aboveCrown)
					{
						aboveCrown = qc;
						aboveDisplay = displays.get(q);
						foundAbove = true;
					}
				}
				comparisons = comparisonsFor(roster, crownValues, tierHolders, crownVal, best, holder, foundAbove, aboveCrown);
			}

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
				diff = foundAbove ? displayVal - aboveDisplay : 0;
				crownDiff = foundAbove ? crownVal - aboveCrown : 0L;
				comparesToNextPlayer = foundAbove && aboveCrown < best;
				comparisonPlayerName = foundAbove ? holderString(playersWithValue(roster, crownValues, aboveCrown)) : null;
			}
			else
			{
				diff = displayVal - topDisplay;
				crownDiff = crownVal - best;
				comparisonPlayerName = holder;
			}

			statsByPlayer.get(p).add(new CategoryStat(def.displayName, def.spriteId, def.itemId,
				def.type, diff, crownDiff, holds, tier, holder != null, comparesToNextPlayer,
				comparisonPlayerName, comparisons, def.aggregate, def.order));
		}
	}

	private static void mergeTierHolders(Map<String, Integer> counts, List<String> holders)
	{
		if (holders == null)
		{
			return;
		}
		for (String holder : holders)
		{
			counts.merge(holder, 1, Integer::sum);
		}
	}

	private static CrownTier tierForPlayer(Map<CrownTier, List<String>> tierHolders, String player)
	{
		for (Map.Entry<CrownTier, List<String>> entry : tierHolders.entrySet())
		{
			if (entry.getValue().stream().anyMatch(player::equalsIgnoreCase))
			{
				return entry.getKey();
			}
		}
		return null;
	}

	private static Map<CrownTier, List<String>> crownTierHolders(List<String> roster, Map<String, Long> crownValues)
	{
		Map<CrownTier, List<String>> tierHolders = new HashMap<>();
		List<Long> rankedValues = new ArrayList<>();
		for (long value : crownValues.values())
		{
			if (value > 0 && !rankedValues.contains(value))
			{
				rankedValues.add(value);
			}
		}
		rankedValues.sort((a, b) -> Long.compare(b, a));

		CrownTier[] tiers = CrownTier.values();
		for (int i = 0; i < rankedValues.size() && i < tiers.length; i++)
		{
			long rankedValue = rankedValues.get(i);
			List<String> holders = new ArrayList<>();
			for (String player : roster)
			{
				if (crownValues.get(player) == rankedValue)
				{
					holders.add(player);
				}
			}
			if (!holders.isEmpty())
			{
				tierHolders.put(tiers[i], holders);
			}
		}
		return tierHolders;
	}

	private static String holderString(List<String> holders)
	{
		if (holders.isEmpty())
		{
			return null;
		}
		return String.join("|", holders);
	}

	private static List<String> playersWithValue(List<String> roster, Map<String, Long> crownValues, long value)
	{
		List<String> players = new ArrayList<>();
		for (String player : roster)
		{
			if (crownValues.get(player) == value)
			{
				players.add(player);
			}
		}
		return players;
	}

	private static List<CategoryComparison> comparisonsFor(List<String> roster, Map<String, Long> crownValues,
		Map<CrownTier, List<String>> tierHolders, long crownVal, long best, String holder,
		boolean foundAbove, long aboveCrown)
	{
		List<CategoryComparison> comparisons = new ArrayList<>();
		if (holder != null && crownVal < best)
		{
			comparisons.add(new CategoryComparison(holder, CrownTier.GOLD, crownVal - best));
		}
		if (foundAbove && aboveCrown < best)
		{
			List<String> players = playersWithValue(roster, crownValues, aboveCrown);
			comparisons.add(new CategoryComparison(holderString(players), tierForValue(tierHolders, players),
				crownVal - aboveCrown));
		}
		return comparisons;
	}

	private static CrownTier tierForValue(Map<CrownTier, List<String>> tierHolders, List<String> players)
	{
		if (players.isEmpty())
		{
			return null;
		}
		for (CrownTier tier : CrownTier.values())
		{
			List<String> holders = tierHolders.getOrDefault(tier, Collections.emptyList());
			for (String player : players)
			{
				if (holders.stream().anyMatch(player::equalsIgnoreCase))
				{
					return tier;
				}
			}
		}
		return null;
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

	private static int safeLongToInt(long value)
	{
		if (value < 0)
		{
			return -1;
		}
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
	}

	private static int skillOrder(HiscoreSkill skill)
	{
		int i = SKILL_ORDER.indexOf(skill);
		return i < 0 ? Integer.MAX_VALUE : i; // unknown/new skills sort last
	}

	/** Maps clue scroll tiers (which lack a sprite) to a representative clue-scroll item id, else -1. */
	private static int clueItemId(HiscoreSkill skill)
	{
		switch (skill)
		{
			case CLUE_SCROLL_BEGINNER:
				return ItemID.TRAIL_CLUE_BEGINNER;
			case CLUE_SCROLL_EASY:
				return ItemID.TRAIL_CLUE_EASY_SIMPLE001;
			case CLUE_SCROLL_MEDIUM:
				return ItemID.TRAIL_CLUE_MEDIUM_SEXTANT001;
			case CLUE_SCROLL_HARD:
				return ItemID.TRAIL_CLUE_HARD_MAP001;
			case CLUE_SCROLL_ELITE:
				return ItemID.TRAIL_ELITE_EMOTE_EXP1;
			case CLUE_SCROLL_MASTER:
				return ItemID.TRAIL_CLUE_MASTER;
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
