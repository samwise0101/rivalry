package com.samwise0101.rivalry;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;

class LocalPlayerStats implements PlayerStats
{
	private final PlayerStats base;
	private final Map<HiscoreSkill, Long> skillXp;
	private final Map<HiscoreSkill, Integer> skillLevels;
	private final long overallXp;
	private final int totalLevel;

	LocalPlayerStats(PlayerStats base, Map<HiscoreSkill, Long> skillXp, Map<HiscoreSkill, Integer> skillLevels,
		long overallXp, int totalLevel)
	{
		this.base = base;
		this.skillXp = new EnumMap<>(skillXp);
		this.skillLevels = new EnumMap<>(skillLevels);
		this.overallXp = overallXp;
		this.totalLevel = totalLevel;
	}

	@Override
	public long crownValue(HiscoreSkill skill)
	{
		Long xp = skillXp.get(skill);
		return xp != null ? xp : baseCrownValue(skill);
	}

	@Override
	public int displayValue(HiscoreSkill skill)
	{
		Integer level = skillLevels.get(skill);
		return level != null ? level : baseDisplayValue(skill);
	}

	@Override
	public long overallXp()
	{
		return overallXp >= 0 ? overallXp : baseOverallXp();
	}

	@Override
	public int totalLevel()
	{
		return totalLevel >= 0 ? totalLevel : baseTotalLevel();
	}

	@Override
	public int totalBossKc()
	{
		return base != null ? base.totalBossKc() : -1;
	}

	private long baseCrownValue(HiscoreSkill skill)
	{
		return base != null ? base.crownValue(skill) : -1;
	}

	private int baseDisplayValue(HiscoreSkill skill)
	{
		return base != null ? base.displayValue(skill) : -1;
	}

	private long baseOverallXp()
	{
		return base != null ? base.overallXp() : -1;
	}

	private int baseTotalLevel()
	{
		return base != null ? base.totalLevel() : -1;
	}
}
