package com.samwise0101.rivalry;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("rivalry")
public interface RivalryConfig extends Config
{
	@ConfigSection(
		name = "Rivals",
		description = "Usernames of your rivals (up to 5)",
		position = 0
	)
	String rivalsSection = "rivals";

	@ConfigItem(
		keyName = "rival1",
		name = "Rival 1",
		description = "OSRS username of rival 1",
		section = rivalsSection,
		position = 1
	)
	default String rival1()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rival2",
		name = "Rival 2",
		description = "OSRS username of rival 2",
		section = rivalsSection,
		position = 2
	)
	default String rival2()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rival3",
		name = "Rival 3",
		description = "OSRS username of rival 3",
		section = rivalsSection,
		position = 3
	)
	default String rival3()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rival4",
		name = "Rival 4",
		description = "OSRS username of rival 4",
		section = rivalsSection,
		position = 4
	)
	default String rival4()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rival5",
		name = "Rival 5",
		description = "OSRS username of rival 5",
		section = rivalsSection,
		position = 5
	)
	default String rival5()
	{
		return "";
	}

	@ConfigSection(
		name = "Crown Categories",
		description = "Which categories to award crowns for",
		position = 10
	)
	String categoriesSection = "categories";

	@ConfigItem(
		keyName = "trackSkills",
		name = "Track Skills",
		description = "Award crowns for individual skill XP",
		section = categoriesSection,
		position = 11
	)
	default boolean trackSkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackBosses",
		name = "Track Bosses",
		description = "Award a crown for each boss (by kill count)",
		section = categoriesSection,
		position = 12
	)
	default boolean trackBosses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackClues",
		name = "Track Activities",
		description = "Award crowns for clue scrolls and other activities (LMS, minigames, etc.)",
		section = categoriesSection,
		position = 13
	)
	default boolean trackClues()
	{
		return true;
	}

	@ConfigSection(
		name = "Polling",
		description = "How often to check the hiscores",
		position = 20
	)
	String pollingSection = "polling";

	@ConfigItem(
		keyName = "pollIntervalMinutes",
		name = "Poll interval (minutes)",
		description = "How often to refresh hiscores from all players",
		section = pollingSection,
		position = 21
	)
	@Range(min = 5, max = 60)
	default int pollIntervalMinutes()
	{
		return 15;
	}

	@ConfigSection(
		name = "Notifications",
		description = "How to notify you of crown changes",
		position = 30
	)
	String notificationsSection = "notifications";

	@ConfigItem(
		keyName = "notifyGameChat",
		name = "Game chat message",
		description = "Show crown gain/loss in the game chat",
		section = notificationsSection,
		position = 31
	)
	default boolean notifyGameChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyDesktop",
		name = "Desktop notification",
		description = "Show a desktop notification on crown gain/loss",
		section = notificationsSection,
		position = 32
	)
	default boolean notifyDesktop()
	{
		return false;
	}
}
