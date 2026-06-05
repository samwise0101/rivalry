package com.samwise0101.rivalry;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Persists the current crown holder for each category in the plugin's config,
 * so standings survive restarts. A blank/empty holder means "contested / none".
 */
@Singleton
class CrownStore
{
	private static final String KEY_PREFIX = "crown_";

	private final ConfigManager configManager;
	private final Map<String, String> holders = new HashMap<>();

	@Inject
	CrownStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Loads stored holders into memory. Call on startup. */
	void load()
	{
		holders.clear();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			loadOne(skill.name());
		}
		loadOne(CrownCalculator.TOTAL_LEVEL_ID);
		loadOne(CrownCalculator.TOTAL_BOSS_KC_ID);
	}

	private void loadOne(String id)
	{
		String stored = configManager.getConfiguration(RivalryConfig.GROUP, KEY_PREFIX + id);
		if (stored != null && !stored.isBlank())
		{
			holders.put(id, stored);
		}
	}

	/** The stored holder for a category, or null if none/contested. */
	String getHolder(String id)
	{
		return holders.get(id);
	}

	/** Persists and caches the holder; null is stored as "" (contested). */
	void setHolder(String id, String holder)
	{
		String value = holder != null ? holder : "";
		holders.put(id, value);
		configManager.setConfiguration(RivalryConfig.GROUP, KEY_PREFIX + id, value);
	}
}
