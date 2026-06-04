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

		CategoryStat aliceAtk = stat(r, "Alice", "Attack");
		assertTrue(aliceAtk.isHoldsCrown());
		assertEquals(Integer.valueOf(2), aliceAtk.getDiff());        // 10 - runner-up 8
		assertEquals(Long.valueOf(500), aliceAtk.getCrownDiff());     // 1000 - 500

		CategoryStat bobAtk = stat(r, "Bob", "Attack");
		assertFalse(bobAtk.isHoldsCrown());
		assertTrue(bobAtk.isHasHolder());
		assertEquals(Integer.valueOf(-2), bobAtk.getDiff());          // 8 - top 10
	}

	@Test
	public void tieForLeadIsContested()
	{
		TestStats alice = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);
		TestStats bob = new TestStats().skill(HiscoreSkill.ATTACK, 1000, 10);

		CrownResult r = calc(SKILLS_ONLY, roster("Alice", "Bob"), alice, bob);

		assertNull("contested crown has no holder", r.getHolders().get("ATTACK"));
		assertEquals(0, crownCount(r, "Alice"));
		assertEquals(0, crownCount(r, "Bob"));

		CategoryStat aliceAtk = stat(r, "Alice", "Attack");
		assertFalse(aliceAtk.isHoldsCrown());
		assertFalse(aliceAtk.isHasHolder());
		assertEquals(Integer.valueOf(0), aliceAtk.getDiff());
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
	public void aggregateTotalLevelAndBossKc()
	{
		CrownOptions skillsAndBosses = new CrownOptions(true, true, false, false);
		TestStats alice = new TestStats().totals(1_000_000, 500, 300);
		TestStats bob = new TestStats().totals(800_000, 450, 200);

		CrownResult r = calc(skillsAndBosses, roster("Alice", "Bob"), alice, bob);

		assertEquals("Alice", r.getHolders().get(CrownCalculator.TOTAL_LEVEL_ID));
		assertEquals("Alice", r.getHolders().get(CrownCalculator.TOTAL_BOSS_KC_ID));

		CategoryStat total = stat(r, "Alice", "Total Level");
		assertTrue(total.isAggregate());
		assertEquals(Integer.valueOf(50), total.getDiff());            // 500 - 450
		assertEquals(Long.valueOf(200_000), total.getCrownDiff());     // 1,000,000 - 800,000
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
		return r.getStandings().stream()
			.filter(s -> s.getName().equals(player))
			.findFirst().orElseThrow(AssertionError::new)
			.getCrownCount();
	}

	private static CategoryStat stat(CrownResult r, String player, String categoryName)
	{
		return r.getStandings().stream()
			.filter(s -> s.getName().equals(player))
			.findFirst().orElseThrow(AssertionError::new)
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
