package com.samwise0101.rivalry;

import lombok.Value;
import net.runelite.client.hiscore.HiscoreResult;

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
	 * Returns the value for a given category, or -1 if unavailable.
	 * For skills: returns XP. For clues/bosses: returns score.
	 */
	long getValue(CrownCategory category)
	{
		if (result == null)
		{
			return -1;
		}

		var skill = result.getSkill(category.hiscoreSkill);
		if (skill == null)
		{
			return -1;
		}

		if (category.type == CrownCategory.CategoryType.SKILL)
		{
			return skill.getExperience();
		}
		else
		{
			// clue scrolls and boss KC stored in score/rank — score is the count
			return skill.getLevel(); // HiscoreResult uses level field for activity scores
		}
	}
}
