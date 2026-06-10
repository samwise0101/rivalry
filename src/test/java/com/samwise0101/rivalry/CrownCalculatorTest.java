package com.samwise0101.rivalry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CrownCalculatorTest
{
	private final CrownCalculator calculator = new CrownCalculator();

	private static final CrownOptions SKILLS_ONLY = new CrownOptions(true, false, false, false);
	private static final CrownOptions SKILLS_GAP_TO_NEXT = new CrownOptions(true, false, false, true);

	@Test
	public void strictLeaderHoldsCrown()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 500, 8);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice", r.getHolders().get("ATTACK"));
		assertEquals(1, crownCount(r, "Alice"));
		assertEquals(0, crownCount(r, "Bob"));
		assertEquals(1, silverCrownCount(r, "Bob"));

		CategoryStat aliceAtk = stat(r, "Alice", "Attack");
		assertTrue(aliceAtk.isHoldsCrown());
		assertEquals(CrownTier.GOLD, aliceAtk.getCrownTier());
		assertEquals(Integer.valueOf(2), aliceAtk.getDiff());        // 10 - runner-up 8
		assertEquals(Long.valueOf(500), aliceAtk.getCrownDiff());     // 1000 - 500

		CategoryStat bobAtk = stat(r, "Bob", "Attack");
		assertFalse(bobAtk.isHoldsCrown());
		assertEquals(CrownTier.SILVER, bobAtk.getCrownTier());
		assertTrue(bobAtk.isHasHolder());
		assertEquals(Integer.valueOf(-2), bobAtk.getDiff());          // 8 - top 10
	}

	@Test
	public void topThreeUniquePlayersHoldGoldSilverAndBronze()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 3000, 30);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 2000, 20);
		TestStats carol = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob", "Carol"), alice, bob, carol);

		assertEquals(1, crownCount(r, "Alice"));
		assertEquals(1, silverCrownCount(r, "Bob"));
		assertEquals(1, bronzeCrownCount(r, "Carol"));
		assertEquals(CrownTier.GOLD, stat(r, "Alice", "Attack").getCrownTier());
		assertEquals(CrownTier.SILVER, stat(r, "Bob", "Attack").getCrownTier());
		assertEquals(CrownTier.BRONZE, stat(r, "Carol", "Attack").getCrownTier());
	}

	@Test
	public void tieForLeadAwardsGoldToAllTiedPlayers()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice|Bob", r.getHolders().get("ATTACK"));
		assertEquals(1, crownCount(r, "Alice"));
		assertEquals(1, crownCount(r, "Bob"));

		CategoryStat aliceAtk = stat(r, "Alice", "Attack");
		assertTrue(aliceAtk.isHoldsCrown());
		assertEquals(CrownTier.GOLD, aliceAtk.getCrownTier());
		assertTrue(aliceAtk.isHasHolder());
		assertEquals(Integer.valueOf(0), aliceAtk.getDiff());
	}

	@Test
	public void tiedRanksAwardTierToAllPlayers()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 3000, 30);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 3000, 30);
		TestStats carol = new TestStats().skill(HiscoreSkill.ATTACK, 2000, 20);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob", "Carol"), alice, bob, carol);

		assertEquals(1, crownCount(r, "Alice"));
		assertEquals(1, crownCount(r, "Bob"));
		assertEquals(1, silverCrownCount(r, "Carol"));
		assertEquals(CrownTier.GOLD, stat(r, "Alice", "Attack").getCrownTier());
		assertEquals(CrownTier.GOLD, stat(r, "Bob", "Attack").getCrownTier());
		assertEquals(CrownTier.SILVER, stat(r, "Carol", "Attack").getCrownTier());
	}

	@Test
	public void tiedZeroValuesDoNotAwardCrowns()
	{
		CrownOptions bossesOnly = new CrownOptions(false, true, false, false);
		TestStats alice = new TestStats().skill(HiscoreSkill.ZULRAH, 0, 0);
		TestStats bob = new TestStats().skill(HiscoreSkill.ZULRAH, 0, 0);

		CrownResult r = calc(bossesOnly, roster("Alice", "Bob"), alice, bob);

		assertNull(r.getHolders().get("ZULRAH"));
		assertEquals(0, crownCount(r, "Alice"));
		assertEquals(0, crownCount(r, "Bob"));
		assertNull(stat(r, "Alice", "Zulrah").getCrownTier());
		assertNull(stat(r, "Bob", "Zulrah").getCrownTier());
	}

	@Test
	public void sameLevelButLessXpIsBehind()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 14_000_000, 99);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 13_000_000, 99);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice", r.getHolders().get("ATTACK"));
		CategoryStat bobAtk = stat(r, "Bob", "Attack");
		assertFalse(bobAtk.isHoldsCrown());
		assertTrue(bobAtk.isHasHolder());          // drives the red-zero UI rule
		assertEquals(Integer.valueOf(0), bobAtk.getDiff());
		assertTrue(bobAtk.getCrownDiff() < 0);     // behind on XP
	}

	@Test
	public void gapToNextPlayerComparesToPlayerAbove()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 300, 30);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 200, 20);
		TestStats carol = new TestStats().skill(HiscoreSkill.ATTACK, 100, 10);

		CrownResult dflt = calc(SKILLS_ONLY, roster("Alice", "Bob", "Carol"), alice, bob, carol);
		assertEquals(Integer.valueOf(-20), stat(dflt, "Carol", "Attack").getDiff()); // vs top (30)

		CrownResult gap = calc(SKILLS_GAP_TO_NEXT, roster("Alice", "Bob", "Carol"), alice, bob, carol);
		assertEquals(Integer.valueOf(-10), stat(gap, "Carol", "Attack").getDiff());  // vs Bob (20)
		assertEquals("Bob", stat(gap, "Carol", "Attack").getComparisonPlayerName());
		assertEquals(2, stat(gap, "Carol", "Attack").getComparisons().size());
		assertEquals("Alice", stat(gap, "Carol", "Attack").getComparisons().get(0).getPlayerNames());
		assertEquals(CrownTier.GOLD, stat(gap, "Carol", "Attack").getComparisons().get(0).getCrownTier());
		assertEquals(Long.valueOf(-200), stat(gap, "Carol", "Attack").getComparisons().get(0).getCrownDiff());
		assertEquals("Bob", stat(gap, "Carol", "Attack").getComparisons().get(1).getPlayerNames());
		assertEquals(CrownTier.SILVER, stat(gap, "Carol", "Attack").getComparisons().get(1).getCrownTier());
		assertEquals(Long.valueOf(-100), stat(gap, "Carol", "Attack").getComparisons().get(1).getCrownDiff());
	}

	@Test
	public void comparisonPlayerNamesIncludeTiedHolders()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 300, 30);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 300, 30);
		TestStats carol = new TestStats().skill(HiscoreSkill.ATTACK, 100, 10);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob", "Carol"), alice, bob, carol);

		assertEquals("Alice|Bob", stat(r, "Carol", "Attack").getComparisonPlayerName());
	}

	@Test
	public void unrankedSkillShownAsNullDiff()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 500, 8);
		TestStats bob = new TestStats(); // unranked in everything

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob"), alice, bob);

		CategoryStat bobAtk = stat(r, "Bob", "Attack");
		assertNotNull("skills are always listed", bobAtk);
		assertNull(bobAtk.getDiff());
	}

	@Test
	public void unrankedBossIsOmitted()
	{
		CrownOptions bossesOnly = new CrownOptions(false, true, false, false);
		TestStats alice = new TestStats().skill(HiscoreSkill.ZULRAH, 500, 500);
		TestStats bob = new TestStats(); // no Zulrah KC

		CrownResult r = calc(bossesOnly, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice", r.getHolders().get("ZULRAH"));
		assertNotNull(stat(r, "Alice", "Zulrah"));
		assertNull("unranked boss not listed for that player", stat(r, "Bob", "Zulrah"));
	}

	@Test
	public void aggregateTotalLevelTotalXpAndBossKc()
	{
		CrownOptions skillsAndBosses = new CrownOptions(true, true, false, false);
		TestStats alice = new TestStats().totals(1_000_000, 500, 300);
		TestStats bob = new TestStats().totals(800_000, 450, 200);

		CrownResult r = calc(skillsAndBosses, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice", r.getHolders().get(CrownCalculator.TOTAL_LEVEL_ID));
		assertEquals("Alice", r.getHolders().get(CrownCalculator.TOTAL_XP_ID));
		assertEquals("Alice", r.getHolders().get(CrownCalculator.TOTAL_BOSS_KC_ID));

		CategoryStat total = stat(r, "Alice", "Total Level");
		assertTrue(total.isAggregate());
		assertEquals(Integer.valueOf(50), total.getDiff());            // 500 - 450
		assertEquals(Long.valueOf(200_000), total.getCrownDiff());     // 1,000,000 - 800,000

		CategoryStat totalXp = stat(r, "Alice", "Total XP");
		assertTrue(totalXp.isAggregate());
		assertEquals(Integer.valueOf(200_000), totalXp.getDiff());
		assertEquals(Long.valueOf(200_000), totalXp.getCrownDiff());
	}

	@Test
	public void standingsIncludeTotalXpAndTotalLevelForLeaderboardTieBreaks()
	{
		CrownOptions bossesOnly = new CrownOptions(false, true, false, false);
		TestStats alice = new TestStats().totals(1_000_000, 1200, -1);
		TestStats bob = new TestStats().totals(800_000, 1500, -1);

		CrownResult r = calc(bossesOnly, roster("Alice", "Bob"), alice, bob);

		assertEquals(1_000_000L, standing(r, "Alice").getTotalXp());
		assertEquals(800_000L, standing(r, "Bob").getTotalXp());
		assertEquals(1200, standing(r, "Alice").getTotalLevel());
		assertEquals(1500, standing(r, "Bob").getTotalLevel());
	}

	// --- helpers ---

	private CrownResult calc(CrownOptions options, List<String> roster, TestStats... players)
	{
		Map<String, PlayerStats> stats = new HashMap<>();
		for (int i = 0; i < roster.size(); i++)
		{
			stats.put(roster.get(i).toLowerCase(), players[i]);
		}
		return calculator.calculate(roster, stats, options);
	}

	private static List<String> roster(String... names)
	{
		return Arrays.asList(names);
	}

	private static int crownCount(CrownResult r, String player)
	{
		return standing(r, player).getCrownCount();
	}

	private static int silverCrownCount(CrownResult r, String player)
	{
		return standing(r, player).getSilverCrownCount();
	}

	private static int bronzeCrownCount(CrownResult r, String player)
	{
		return standing(r, player).getBronzeCrownCount();
	}

	private static PlayerStanding standing(CrownResult r, String player)
	{
		return r.getStandings().stream()
			.filter(s -> s.getName().equals(player))
			.findFirst().orElseThrow(AssertionError::new);
	}

	private static CategoryStat stat(CrownResult r, String player, String categoryName)
	{
		return standing(r, player)
			.getStats().stream()
			.filter(c -> c.getName().equals(categoryName))
			.findFirst().orElse(null);
	}

	/** A hand-built {@link PlayerStats} for tests. */
	private static final class TestStats implements PlayerStats
	{
		private final Map<HiscoreSkill, Long> crown = new HashMap<>();
		private final Map<HiscoreSkill, Integer> display = new HashMap<>();
		private long overallXp = -1;
		private int totalLevel = -1;
		private int totalBossKc = -1;

		TestStats skill(HiscoreSkill skill, long crownValue, int displayValue)
		{
			crown.put(skill, crownValue);
			display.put(skill, displayValue);
			return this;
		}

		TestStats totals(long overallXp, int totalLevel, int totalBossKc)
		{
			this.overallXp = overallXp;
			this.totalLevel = totalLevel;
			this.totalBossKc = totalBossKc;
			return this;
		}

		@Override
		public long crownValue(HiscoreSkill skill)
		{
			return crown.getOrDefault(skill, -1L);
		}

		@Override
		public int displayValue(HiscoreSkill skill)
		{
			return display.getOrDefault(skill, -1);
		}

		@Override
		public long overallXp()
		{
			return overallXp;
		}

		@Override
		public int totalLevel()
		{
			return totalLevel;
		}

		@Override
		public int totalBossKc()
		{
			return totalBossKc;
		}
	}
}
