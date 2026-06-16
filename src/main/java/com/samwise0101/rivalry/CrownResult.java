package com.samwise0101.rivalry;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Output of {@link CrownCalculator}: per-player standings, gold crown holders,
 * and holder strings per crown tier. Multiple tied holders are pipe-delimited;
 * value is null when nobody holds that tier.
 */
@Value
public class CrownResult
{
	List<PlayerStanding> standings;
	Map<String, String> holders;
	Map<CrownTier, Map<String, String>> tierHolders;
}
