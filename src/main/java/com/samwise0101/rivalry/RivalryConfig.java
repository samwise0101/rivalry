package com.samwise0101.rivalry;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(RivalryConfig.GROUP)
public interface RivalryConfig extends Config
{
	String GROUP = "rivalry";

	@ConfigSection(
		name = "Group",
		description = "Populate rivals automatically from a Wise Old Man group",
		position = 0
	)
	String groupSection = "group";

	@ConfigItem(
		keyName = "useWomGroup",
		name = "Use Wise Old Man group",
		description = "Populate the rival list from a Wise Old Man group instead of entering names manually",
		section = groupSection,
		position = 0,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean useWomGroup()
	{
		return false;
	}

	@ConfigItem(
		keyName = "womGroupId",
		name = "WOM group ID",
		description = "The numeric ID from your Wise Old Man group's URL (wiseoldman.net/groups/<id>). Only used when the toggle above is enabled.",
		section = groupSection,
		position = 1
	)
	default int womGroupId()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "womMaxMembers",
		name = "Max members",
		description = "How many of the most recently active group members to track. Only used when the toggle above is enabled.",
		section = groupSection,
		position = 2
	)
	@Range(min = 1, max = 50)
	default int womMaxMembers()
	{
		return 10;
	}

	@ConfigSection(
		name = "Rivals",
		description = "Usernames of your rivals (up to 5). Ignored while 'Use Wise Old Man group' is enabled.",
		position = 5
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
		name = "Display",
		description = "How comparisons are shown in the panel",
		position = 15
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "gapToNextPlayer",
		name = "Gap to next player",
		description = "For categories you don't lead, show how far you are behind the player immediately above you, instead of behind the crown holder",
		section = displaySection,
		position = 16
	)
	default boolean gapToNextPlayer()
	{
		return false;
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
