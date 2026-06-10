package com.samwise0101.rivalry;

import java.util.List;
import lombok.Value;

/**
 * Display data for one player in the standings panel.
 */
@Value
public class PlayerStanding
{
	String name;
	int crownCount;
	int silverCrownCount;
	int bronzeCrownCount;
	long totalXp;
	int totalLevel;

	/** All categories relevant to this player (skills always; bosses/activities only if ranked). */
	List<CategoryStat> stats;
}
