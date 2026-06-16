package com.samwise0101.rivalry;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RivalryPluginNotificationTest
{
	@Test
	public void sameLocalAccountAndRosterDoesNotSuppressNotifications()
	{
		List<String> roster = roster("Alice", "Bob");

		assertFalse(RivalryPlugin.shouldSuppressHolderNotifications("Alice", "alice", roster,
			roster("Alice", "Bob")));
	}

	@Test
	public void localAccountChangeSuppressesNotifications()
	{
		List<String> previousRoster = roster("Alice", "Bob");
		List<String> currentRoster = roster("Bob", "Alice");

		assertTrue(RivalryPlugin.shouldSuppressHolderNotifications("Alice", "Bob", previousRoster, currentRoster));
	}

	@Test
	public void loginAfterAnonymousComputeSuppressesNotifications()
	{
		List<String> previousRoster = roster("Bob");
		List<String> currentRoster = roster("Alice", "Bob");

		assertTrue(RivalryPlugin.shouldSuppressHolderNotifications(null, "Alice", previousRoster, currentRoster));
	}

	@Test
	public void rosterChangeSuppressesNotifications()
	{
		assertTrue(RivalryPlugin.shouldSuppressHolderNotifications("Alice", "Alice", roster("Alice", "Bob"),
			roster("Alice", "Carol")));
	}

	@Test
	public void holderChangesCreateDetailedGainAndLossNotifications()
	{
		Map<CrownTier, Map<String, String>> previous = holders(
			"ATTACK", "Bob",
			"STRENGTH", "Alice");
		Map<CrownTier, Map<String, String>> current = holders(
			"ATTACK", "Alice",
			"STRENGTH", "Bob");

		List<RivalryPlugin.CrownNotificationChange> changes =
			RivalryPlugin.crownNotificationChanges("Alice", previous, current);

		assertEquals(Arrays.asList(
			"You gained the gold crown for Attack!",
			"You lost the gold crown for Strength to Bob!"),
			RivalryPlugin.crownNotificationMessages(changes));
		assertTrue(changes.get(0).gained);
		assertFalse(changes.get(1).gained);
	}

	@Test
	public void exactlyThreeCrownChangesStayDetailed()
	{
		Map<CrownTier, Map<String, String>> previous = holders(
			"ATTACK", "Bob",
			"STRENGTH", "Bob",
			"DEFENCE", "Bob");
		Map<CrownTier, Map<String, String>> current = holders(
			"ATTACK", "Alice",
			"STRENGTH", "Alice",
			"DEFENCE", "Alice");

		List<RivalryPlugin.CrownNotificationChange> changes =
			RivalryPlugin.crownNotificationChanges("Alice", previous, current);
		List<String> messages = RivalryPlugin.crownNotificationMessages(changes);

		assertEquals(3, messages.size());
		assertTrue(messages.contains("You gained the gold crown for Attack!"));
		assertTrue(messages.contains("You gained the gold crown for Strength!"));
		assertTrue(messages.contains("You gained the gold crown for Defence!"));
	}

	@Test
	public void moreThanThreeCrownChangesUseAggregateNotification()
	{
		Map<CrownTier, Map<String, String>> previous = holders(
			"ATTACK", "Bob",
			"STRENGTH", "Bob",
			"DEFENCE", "Bob",
			"RANGED", "Bob",
			"PRAYER", "Alice");
		Map<CrownTier, Map<String, String>> current = holders(
			"ATTACK", "Alice",
			"STRENGTH", "Alice",
			"DEFENCE", "Alice",
			"RANGED", "Alice",
			"PRAYER", "Bob");

		List<RivalryPlugin.CrownNotificationChange> changes =
			RivalryPlugin.crownNotificationChanges("Alice", previous, current);

		assertEquals(Arrays.asList("You gained 4 crowns and lost 1 crown."),
			RivalryPlugin.crownNotificationMessages(changes));
	}

	@Test
	public void nonLocalHolderChangesDoNotNotify()
	{
		Map<CrownTier, Map<String, String>> previous = holders("ATTACK", "Bob");
		Map<CrownTier, Map<String, String>> current = holders("ATTACK", "Carol");

		List<RivalryPlugin.CrownNotificationChange> changes =
			RivalryPlugin.crownNotificationChanges("Alice", previous, current);

		assertTrue(changes.isEmpty());
		assertTrue(RivalryPlugin.crownNotificationMessages(changes).isEmpty());
	}

	private static List<String> roster(String... players)
	{
		return Arrays.asList(players);
	}

	private static Map<CrownTier, Map<String, String>> holders(String... idHolderPairs)
	{
		Map<CrownTier, Map<String, String>> holders = new EnumMap<>(CrownTier.class);
		holders.put(CrownTier.GOLD, new HashMap<>());
		for (int i = 0; i < idHolderPairs.length; i += 2)
		{
			holders.get(CrownTier.GOLD).put(idHolderPairs[i], idHolderPairs[i + 1]);
		}
		return holders;
	}
}
