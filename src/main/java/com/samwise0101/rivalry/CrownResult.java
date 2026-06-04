package com.samwise0101.rivalry;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Output of {@link CrownCalculator}: the per-player standings and the current
 * crown holder for each category id (value is null when the crown is contested).
 */
@Value
public class CrownResult
{
	List<PlayerStanding> standings;
	Map<String, String> holders;
}
