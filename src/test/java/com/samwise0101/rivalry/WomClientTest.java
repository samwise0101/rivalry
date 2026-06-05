package com.samwise0101.rivalry;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WomClientTest
{
	@Test
	public void sortsByLastChangedDescending()
	{
		WomClient.WomGroup group = group(
			member("old", "Old", "2026-06-01T00:00:00.000Z"),
			member("new", "New", "2026-06-05T00:00:00.000Z"),
			member("mid", "Mid", "2026-06-03T00:00:00.000Z"));

		assertEquals(Arrays.asList("New", "Mid", "Old"), WomClient.selectMembers(group, 10));
	}

	@Test
	public void appliesMaxMembersLimit()
	{
		WomClient.WomGroup group = group(
			member("a", "A", "2026-06-01T00:00:00.000Z"),
			member("b", "B", "2026-06-05T00:00:00.000Z"),
			member("c", "C", "2026-06-03T00:00:00.000Z"));

		assertEquals(Arrays.asList("B", "C"), WomClient.selectMembers(group, 2));
	}

	@Test
	public void prefersDisplayNameFallsBackToUsername()
	{
		WomClient.WomGroup group = group(
			member("withdisplay", "DisplayName", "2026-06-02T00:00:00.000Z"),
			member("usernameonly", null, "2026-06-01T00:00:00.000Z"));

		assertEquals(Arrays.asList("DisplayName", "usernameonly"), WomClient.selectMembers(group, 10));
	}

	@Test
	public void nullTimestampsSortLast()
	{
		WomClient.WomGroup group = group(
			member("noDate", "NoDate", null),
			member("dated", "Dated", "2026-06-01T00:00:00.000Z"));

		List<String> result = WomClient.selectMembers(group, 10);
		assertEquals("Dated", result.get(0));
		assertEquals("NoDate", result.get(1));
	}

	@Test
	public void maxMembersIsAtLeastOne()
	{
		WomClient.WomGroup group = group(member("a", "A", "2026-06-01T00:00:00.000Z"));
		assertEquals(1, WomClient.selectMembers(group, 0).size());
	}

	@Test
	public void nullGroupOrMembershipsYieldsEmpty()
	{
		assertTrue(WomClient.selectMembers(null, 10).isEmpty());
		assertTrue(WomClient.selectMembers(new WomClient.WomGroup(), 10).isEmpty());
	}

	// --- helpers ---

	private static WomClient.WomGroup group(WomClient.WomMembership... members)
	{
		WomClient.WomGroup g = new WomClient.WomGroup();
		g.memberships = Arrays.asList(members);
		return g;
	}

	private static WomClient.WomMembership member(String username, String displayName, String lastChangedAt)
	{
		WomClient.WomPlayer p = new WomClient.WomPlayer();
		p.username = username;
		p.displayName = displayName;
		p.lastChangedAt = lastChangedAt;
		WomClient.WomMembership m = new WomClient.WomMembership();
		m.player = p;
		return m;
	}
}
