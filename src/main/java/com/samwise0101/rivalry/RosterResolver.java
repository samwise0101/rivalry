package com.samwise0101.rivalry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Produces the player roster to track: either the manual rival list or the
 * members of a Wise Old Man group. The local player is always placed first.
 */
@Singleton
class RosterResolver
{
	private final RivalryConfig config;
	private final WomClient womClient;

	// Last successful WOM roster, used as a fallback when a fetch fails.
	private volatile List<String> lastWomMembers;

	@Inject
	RosterResolver(RivalryConfig config, WomClient womClient)
	{
		this.config = config;
		this.womClient = womClient;
	}

	/**
	 * Resolves the roster. Completes exceptionally with a {@link RosterException}
	 * whose message is suitable for display when the roster can't be determined.
	 */
	CompletableFuture<List<String>> resolve(String localName)
	{
		if (!config.useWomGroup())
		{
			return CompletableFuture.completedFuture(buildManualRoster(localName));
		}

		CompletableFuture<List<String>> future = new CompletableFuture<>();
		int groupId = config.womGroupId();
		if (groupId <= 0)
		{
			future.completeExceptionally(new RosterException("Set a WOM group ID in settings."));
			return future;
		}

		womClient.fetchGroupMembers(groupId, config.womMaxMembers(),
			members ->
			{
				lastWomMembers = members;
				future.complete(withLocalPlayer(localName, members));
			},
			error ->
			{
				if (lastWomMembers != null)
				{
					future.complete(withLocalPlayer(localName, lastWomMembers));
				}
				else
				{
					future.completeExceptionally(new RosterException("WOM group fetch failed — check the group ID."));
				}
			});
		return future;
	}

	private List<String> buildManualRoster(String localName)
	{
		List<String> players = new ArrayList<>();
		addIfNotBlank(players, localName);
		addIfNotBlank(players, config.rival1());
		addIfNotBlank(players, config.rival2());
		addIfNotBlank(players, config.rival3());
		addIfNotBlank(players, config.rival4());
		addIfNotBlank(players, config.rival5());
		return players;
	}

	private static List<String> withLocalPlayer(String localName, List<String> members)
	{
		List<String> players = new ArrayList<>();
		addIfNotBlank(players, localName);
		for (String m : members)
		{
			if (m != null && players.stream().noneMatch(p -> p.equalsIgnoreCase(m)))
			{
				players.add(m);
			}
		}
		return players;
	}

	private static void addIfNotBlank(List<String> list, String value)
	{
		if (value != null && !value.isBlank()
			&& list.stream().noneMatch(p -> p.equalsIgnoreCase(value.trim())))
		{
			list.add(value.trim());
		}
	}

	/** Thrown when the roster cannot be resolved; the message is user-facing. */
	static class RosterException extends RuntimeException
	{
		RosterException(String message)
		{
			super(message);
		}
	}
}
