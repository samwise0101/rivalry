package com.samwise0101.rivalry;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Persists the current crown holder for each category in the plugin's config,
 * so standings survive restarts. Tied holders are pipe-delimited. A blank/empty
 * holder means none.
 */
@Singleton
class CrownStore
{
	private static final String KEY_PREFIX = "crown_";

	private final ConfigManager configManager;
	private final Map<String, String> holders = new HashMap<>();
	private final Map<CrownTier, Map<String, String>> tierHolders = new HashMap<>();

	@Inject
	CrownStore(ConfigManager configManager)
	{
		this.configManager = configManager;
		for (CrownTier tier : CrownTier.values())
		{
			tierHolders.put(tier, new HashMap<>());
		}
	}

	/** Loads stored holders into memory. Call on startup. */
	void load()
	{
		holders.clear();
		for (Map<String, String> holderMap : tierHolders.values())
		{
			holderMap.clear();
		}
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			loadOne(skill.name());
		}
		loadOne(CrownCalculator.TOTAL_LEVEL_ID);
		loadOne(CrownCalculator.TOTAL_XP_ID);
		loadOne(CrownCalculator.TOTAL_BOSS_KC_ID);
	}

	private void loadOne(String id)
	{
		String stored = configManager.getConfiguration(RivalryConfig.GROUP, KEY_PREFIX + id);
		if (stored != null && !stored.isBlank())
		{
			holders.put(id, stored);
			tierHolders.get(CrownTier.GOLD).put(id, stored);
		}
		for (CrownTier tier : CrownTier.values())
		{
			if (tier == CrownTier.GOLD)
			{
				continue;
			}
			String tierStored = configManager.getConfiguration(RivalryConfig.GROUP, keyFor(tier, id));
			if (tierStored != null && !tierStored.isBlank())
			{
				tierHolders.get(tier).put(id, tierStored);
			}
		}
	}

	/** The stored holder string for a category, or null if none. */
	String getHolder(String id)
	{
		return holders.get(id);
	}

	String getHolder(CrownTier tier, String id)
	{
		if (tier == CrownTier.GOLD)
		{
			return getHolder(id);
		}
		return tierHolders.get(tier).get(id);
	}

	/** Persists and caches the holder string; null is stored as "". */
	void setHolder(String id, String holder)
	{
		String value = holder != null ? holder : "";
		holders.put(id, value);
		tierHolders.get(CrownTier.GOLD).put(id, value);
		configManager.setConfiguration(RivalryConfig.GROUP, KEY_PREFIX + id, value);
	}

	void setHolder(CrownTier tier, String id, String holder)
	{
		if (tier == CrownTier.GOLD)
		{
			setHolder(id, holder);
			return;
		}
		String value = holder != null ? holder : "";
		tierHolders.get(tier).put(id, value);
		configManager.setConfiguration(RivalryConfig.GROUP, keyFor(tier, id), value);
	}

	private static String keyFor(CrownTier tier, String id)
	{
		return KEY_PREFIX + tier.name().toLowerCase() + "_" + id;
	}
}
