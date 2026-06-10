package com.samwise0101.rivalry;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LocalPlayerStatsTest
{
	@Test
	public void localSkillValuesOverrideFetchedHiscores()
	{
		TestStats base = new TestStats()
			.skill(HiscoreSkill.ATTACK, 1_000, 10)
			.skill(HiscoreSkill.ZULRAH, 50, 50)
			.totals(10_000, 100, 50);
		Map<HiscoreSkill, Long> xp = new EnumMap<>(HiscoreSkill.class);
		Map<HiscoreSkill, Integer> levels = new EnumMap<>(HiscoreSkill.class);
		xp.put(HiscoreSkill.ATTACK, 2_000L);
		levels.put(HiscoreSkill.ATTACK, 20);

		LocalPlayerStats stats = new LocalPlayerStats(base, xp, levels, 20_000, 120);

		assertEquals(2_000L, stats.crownValue(HiscoreSkill.ATTACK));
		assertEquals(20, stats.displayValue(HiscoreSkill.ATTACK));
		assertEquals(20_000L, stats.overallXp());
		assertEquals(120, stats.totalLevel());
		assertEquals(50L, stats.crownValue(HiscoreSkill.ZULRAH));
		assertEquals(50, stats.totalBossKc());
	}

	@Test
	public void missingLocalValuesFallBackToFetchedHiscores()
	{
		TestStats base = new TestStats().skill(HiscoreSkill.DEFENCE, 1_500, 15);
		LocalPlayerStats stats = new LocalPlayerStats(base, new EnumMap<>(HiscoreSkill.class),
			new EnumMap<>(HiscoreSkill.class), -1, -1);

		assertEquals(1_500L, stats.crownValue(HiscoreSkill.DEFENCE));
		assertEquals(15, stats.displayValue(HiscoreSkill.DEFENCE));
	}

	private static final class TestStats implements PlayerStats
	{
		private final Map<HiscoreSkill, Long> crown = new EnumMap<>(HiscoreSkill.class);
		private final Map<HiscoreSkill, Integer> display = new EnumMap<>(HiscoreSkill.class);
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
