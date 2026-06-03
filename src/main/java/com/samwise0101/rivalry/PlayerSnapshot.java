package com.samwise0101.rivalry;

import lombok.Value;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * A point-in-time snapshot of a player's hiscore data.
 */
@Value
public class PlayerSnapshot
{
	String username;
	HiscoreResult result;
	long fetchedAt;

	/**
	 * Value used to rank crowns: total XP for skills, score/KC for bosses and
	 * activities. Returns -1 if unavailable.
	 */
	long crownValue(HiscoreSkill skill)
	{
		if (result == null)
		{
			return -1;
		}
		var s = result.getSkill(skill);
		if (s == null)
		{
			return -1;
		}
		if (skill.getType() == HiscoreSkillType.SKILL)
		{
			return s.getExperience();
		}
		return s.getLevel(); // boss KC / activity score lives in the level field
	}

	/**
	 * Value shown in the UI: level for skills, score/KC for bosses and activities.
	 * Returns -1 if unranked/unavailable.
	 */
	int displayValue(HiscoreSkill skill)
	{
		if (result == null)
		{
			return -1;
		}
		var s = result.getSkill(skill);
		return s == null ? -1 : s.getLevel();
	}

	/** Total XP (Overall experience), or -1 if unavailable. Used to rank the Total Level crown. */
	long overallXp()
	{
		if (result == null)
		{
			return -1;
		}
		var s = result.getSkill(HiscoreSkill.OVERALL);
		return s == null ? -1 : s.getExperience();
	}

	/** Total skill level (Overall level), or -1 if unavailable. */
	int totalLevel()
	{
		if (result == null)
		{
			return -1;
		}
		var s = result.getSkill(HiscoreSkill.OVERALL);
		return s == null ? -1 : s.getLevel();
	}

	/** Sum of all boss kill counts, or -1 if the player is ranked in no bosses. */
	int totalBossKc()
	{
		if (result == null)
		{
			return -1;
		}
		long sum = 0;
		boolean any = false;
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() != HiscoreSkillType.BOSS)
			{
				continue;
			}
			var s = result.getSkill(skill);
			if (s != null && s.getLevel() > 0)
			{
				sum += s.getLevel();
				any = true;
			}
		}
		return any ? (int) sum : -1;
	}
}
