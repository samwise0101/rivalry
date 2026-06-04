package com.samwise0101.rivalry;

import lombok.Value;

/**
 * The display/category options that influence crown calculation, decoupled from
 * the live {@link RivalryConfig} so {@link CrownCalculator} can be unit tested.
 */
@Value
public class CrownOptions
{
	boolean trackSkills;
	boolean trackBosses;
	boolean trackActivities;

	/** When true, non-holders are compared to the player immediately above them. */
	boolean gapToNextPlayer;
}
