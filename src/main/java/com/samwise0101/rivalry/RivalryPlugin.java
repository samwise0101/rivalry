package com.samwise0101.rivalry;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.Notifier;

@Slf4j
@PluginDescriptor(
	name = "Rivalry",
	description = "Track yourself and rivals across the OSRS hiscores and compete for crowns.",
	tags = {"hiscore", "rivalry", "crown", "rivals", "competition", "skilling", "pvm"}
)
public class RivalryPlugin extends Plugin
{
	private static final DateTimeFormatter TIME_FMT =
		DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	@Inject
	private Client client;

	@Inject
	private RivalryConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ScheduledExecutorService executor;

	private RivalryPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> pollTask;

	// username -> last known snapshot
	private final Map<String, PlayerSnapshot> snapshots = new HashMap<>();
	// category -> username of current holder (persisted key: "rivalry.crown.<category.name>")
	private final Map<CrownCategory, String> crownHolders = new HashMap<>();

	private boolean seeded = false;

	@Override
	protected void startUp()
	{
		panel = new RivalryPanel();
		panel.setRefreshCallback(this::triggerRefresh);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/rivalry_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Rivalry")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		loadPersistedCrowns();
		schedulePoll();
		log.info("Rivalry started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		if (pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
		snapshots.clear();
		seeded = false;
		log.info("Rivalry stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Trigger a refresh shortly after login so data is fresh
			executor.schedule(this::refresh, 10, TimeUnit.SECONDS);
		}
	}

	@Provides
	RivalryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RivalryConfig.class);
	}

	// -------------------------------------------------------------------------
	// Scheduling
	// -------------------------------------------------------------------------

	private void schedulePoll()
	{
		if (pollTask != null)
		{
			pollTask.cancel(false);
		}
		long intervalMinutes = config.pollIntervalMinutes();
		pollTask = executor.scheduleAtFixedRate(this::refresh, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
	}

	private void triggerRefresh()
	{
		executor.execute(this::refresh);
	}

	// -------------------------------------------------------------------------
	// Core refresh logic
	// -------------------------------------------------------------------------

	private void refresh()
	{
		String localName = getLocalPlayerName();
		List<String> allPlayers = buildPlayerList(localName);

		if (allPlayers.isEmpty())
		{
			panel.setStatus("Configure rivals in settings.");
			return;
		}

		panel.setStatus("Refreshing...");

		// Fetch all players' hiscores — stagger requests 500ms apart to be polite
		for (int i = 0; i < allPlayers.size(); i++)
		{
			final String name = allPlayers.get(i);
			final long delayMs = i * 500L;
			executor.schedule(() -> fetchPlayer(name), delayMs, TimeUnit.MILLISECONDS);
		}

		// After all fetches should be done, compute crowns and update UI
		long totalDelayMs = allPlayers.size() * 500L + 3000L;
		executor.schedule(() -> computeAndUpdate(localName, allPlayers), totalDelayMs, TimeUnit.MILLISECONDS);
	}

	private void fetchPlayer(String username)
	{
		try
		{
			HiscoreResult result = hiscoreClient.lookup(username, HiscoreEndpoint.NORMAL);
			if (result != null)
			{
				snapshots.put(username.toLowerCase(), new PlayerSnapshot(username, result, System.currentTimeMillis()));
				log.debug("Fetched hiscores for {}", username);
			}
			else
			{
				log.debug("No hiscore result for {}", username);
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to fetch hiscores for {}: {}", username, e.getMessage());
		}
	}

	private void computeAndUpdate(String localName, List<String> allPlayers)
	{
		// Keyed by display-case username so RivalryPanel lookups match.
		Map<String, Integer> crownCounts = new HashMap<>();
		for (String p : allPlayers)
		{
			crownCounts.put(p, 0);
		}

		for (CrownCategory category : CrownCategory.values())
		{
			if (!isCategoryEnabled(category))
			{
				continue;
			}

			String newHolder = findLeader(category, allPlayers);
			if (newHolder == null)
			{
				continue;
			}

			String prevHolder = crownHolders.get(category);

			if (!seeded)
			{
				// First run: just record, don't notify
				crownHolders.put(category, newHolder);
				persistCrown(category, newHolder);
			}
			else if (!newHolder.equalsIgnoreCase(prevHolder))
			{
				boolean localHeld = localName != null && localName.equalsIgnoreCase(prevHolder);
				boolean localGained = localName != null && localName.equalsIgnoreCase(newHolder);

				if (localGained)
				{
					notify("You claimed the " + category.displayName + " crown!");
				}
				else if (localHeld)
				{
					notify("You lost the " + category.displayName + " crown to " + newHolder + "!");
				}

				crownHolders.put(category, newHolder);
				persistCrown(category, newHolder);
			}

			// newHolder is a display-case name from allPlayers, matching the map keys.
			crownCounts.merge(newHolder, 1, Integer::sum);
		}

		seeded = true;

		String timestamp = TIME_FMT.format(Instant.now());
		panel.updateStandings(allPlayers, crownCounts, localName != null ? localName : "", timestamp);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private String findLeader(CrownCategory category, List<String> players)
	{
		String leader = null;
		long best = -1;
		for (String name : players)
		{
			PlayerSnapshot snap = snapshots.get(name.toLowerCase());
			if (snap == null)
			{
				continue;
			}
			long val = snap.getValue(category);
			if (val > best)
			{
				best = val;
				leader = name;
			}
		}
		return leader;
	}

	private boolean isCategoryEnabled(CrownCategory category)
	{
		switch (category.type)
		{
			case SKILL:
				return config.trackSkills();
			case BOSS:
				return config.trackBosses();
			case CLUE:
				return config.trackClues();
			default:
				return false;
		}
	}

	private String getLocalPlayerName()
	{
		if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
		{
			return client.getLocalPlayer().getName();
		}
		return null;
	}

	private List<String> buildPlayerList(String localName)
	{
		List<String> players = new ArrayList<>();
		if (localName != null && !localName.isEmpty())
		{
			players.add(localName);
		}
		addIfNotBlank(players, config.rival1());
		addIfNotBlank(players, config.rival2());
		addIfNotBlank(players, config.rival3());
		addIfNotBlank(players, config.rival4());
		addIfNotBlank(players, config.rival5());
		return players;
	}

	private static void addIfNotBlank(List<String> list, String value)
	{
		if (value != null && !value.isBlank())
		{
			list.add(value.trim());
		}
	}

	private void notify(String message)
	{
		if (config.notifyGameChat() && client.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}
		if (config.notifyDesktop())
		{
			notifier.notify(message);
		}
	}

	// -------------------------------------------------------------------------
	// Crown persistence
	// -------------------------------------------------------------------------

	private static final String CROWN_KEY_PREFIX = "crown_";

	private void persistCrown(CrownCategory category, String holder)
	{
		configManager.setConfiguration("rivalry", CROWN_KEY_PREFIX + category.name(), holder);
	}

	private void loadPersistedCrowns()
	{
		for (CrownCategory category : CrownCategory.values())
		{
			String stored = configManager.getConfiguration("rivalry", CROWN_KEY_PREFIX + category.name());
			if (stored != null && !stored.isBlank())
			{
				crownHolders.put(category, stored);
			}
		}
	}
}
