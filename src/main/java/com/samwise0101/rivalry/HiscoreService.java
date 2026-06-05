package com.samwise0101.rivalry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;

/**
 * Fetches official hiscore data for a roster. Each player is looked up
 * asynchronously (staggered to be polite); the returned future completes once
 * every lookup has finished, with results collected into a fresh map — so there
 * is no shared mutable state and no fixed-delay guessing.
 */
@Slf4j
@Singleton
class HiscoreService
{
	private static final long STAGGER_MS = 500;

	private final HiscoreClient hiscoreClient;
	private final ScheduledExecutorService executor;

	@Inject
	HiscoreService(HiscoreClient hiscoreClient, ScheduledExecutorService executor)
	{
		this.hiscoreClient = hiscoreClient;
		this.executor = executor;
	}

	CompletableFuture<Map<String, PlayerStats>> fetch(List<String> roster)
	{
		Map<String, PlayerStats> results = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> lookups = new ArrayList<>();

		for (int i = 0; i < roster.size(); i++)
		{
			final String name = roster.get(i);
			final long delayMs = i * STAGGER_MS;
			final CompletableFuture<Void> done = new CompletableFuture<>();
			lookups.add(done);

			executor.schedule(() ->
				hiscoreClient.lookupAsync(name, HiscoreEndpoint.NORMAL).whenComplete((result, error) ->
				{
					if (error != null)
					{
						log.debug("Hiscore lookup failed for {}: {}", name, error.getMessage());
					}
					else if (result != null)
					{
						results.put(name.toLowerCase(), new PlayerSnapshot(name, result));
					}
					done.complete(null);
				}), delayMs, TimeUnit.MILLISECONDS);
		}

		return CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0]))
			.thenApply(v -> results);
	}
}
