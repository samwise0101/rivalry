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
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
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

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private WomClient womClient;

	@Inject
	private CrownCalculator crownCalculator;

	private RivalryPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> pollTask;

	// Cached on the client thread (GameTick) so executor threads can read it safely.
	private volatile String localPlayerName;

	// Last successfully fetched WOM roster, for fallback when a fetch fails.
	private volatile List<String> lastWomMembers;

	// username -> last known snapshot
	private final Map<String, PlayerSnapshot> snapshots = new HashMap<>();
	// category id -> username of current holder (persisted key: "rivalry.crown_<id>")
	private final Map<String, String> crownHolders = new HashMap<>();

	private boolean seeded = false;

	@Override
	protected void startUp()
	{
		panel = new RivalryPanel(spriteManager, itemManager);
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

		// If the plugin is enabled while already logged in, no LOGGED_IN event fires,
		// so kick off an initial refresh ourselves.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			executor.schedule(this::refresh, 3, TimeUnit.SECONDS);
		}

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
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// Trigger a refresh shortly after login so data is fresh
			executor.schedule(this::refresh, 10, TimeUnit.SECONDS);
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			localPlayerName = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Runs on the client thread — safe to read the local player here.
		Player local = client.getLocalPlayer();
		String name = local != null ? local.getName() : null;
		if (name != null && !name.equals(localPlayerName))
		{
			localPlayerName = name;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"rivalry".equals(event.getGroup()))
		{
			return;
		}

		if ("pollIntervalMinutes".equals(event.getKey()))
		{
			schedulePoll();
		}

		// Re-run the leaderboard when the roster or group settings change.
		triggerRefresh();
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
		String localName = localPlayerName;

		if (config.useWomGroup())
		{
			resolveWomRoster(localName);
		}
		else
		{
			proceedRefresh(localName, buildPlayerList(localName));
		}
	}

	/** Fetches the WOM group roster (async), then continues the normal refresh. */
	private void resolveWomRoster(String localName)
	{
		int groupId = config.womGroupId();
		if (groupId <= 0)
		{
			panel.setStatus("Set a WOM group ID in settings.");
			return;
		}

		panel.setStatus("Fetching WOM group...");
		womClient.fetchGroupMembers(groupId, config.womMaxMembers(),
			members ->
			{
				lastWomMembers = members;
				executor.execute(() -> proceedRefresh(localName, withLocalPlayer(localName, members)));
			},
			error ->
			{
				log.debug("WOM group fetch failed: {}", error.getMessage());
				if (lastWomMembers != null)
				{
					// Fall back to the last known roster so a blip doesn't wipe standings.
					executor.execute(() -> proceedRefresh(localName, withLocalPlayer(localName, lastWomMembers)));
				}
				else
				{
					panel.setStatus("WOM group fetch failed — check the group ID.");
				}
			});
	}

	private void proceedRefresh(String localName, List<String> allPlayers)
	{
		if (allPlayers.isEmpty())
		{
			panel.setStatus(config.useWomGroup() ? "WOM group has no members." : "Configure rivals in settings.");
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

	/** Prepends the local player (if logged in and not already present) to a roster. */
	private static List<String> withLocalPlayer(String localName, List<String> members)
	{
		List<String> players = new ArrayList<>();
		if (localName != null && !localName.isEmpty())
		{
			players.add(localName);
		}
		for (String m : members)
		{
			if (players.stream().noneMatch(p -> p.equalsIgnoreCase(m)))
			{
				players.add(m);
			}
		}
		return players;
	}

	private void fetchPlayer(String username)
	{
		try
		{
			HiscoreResult result = hiscoreClient.lookup(username, HiscoreEndpoint.NORMAL);
			if (result != null)
			{
				snapshots.put(username.toLowerCase(), new PlayerSnapshot(username, result));
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
		try
		{
			doComputeAndUpdate(localName, allPlayers);
		}
		catch (Exception e)
		{
			// The executor swallows exceptions silently; log and clear the status
			// so the panel never gets stuck on "Refreshing...".
			log.warn("Failed to compute crown standings", e);
			panel.setStatus("Refresh failed — see logs");
		}
	}

	private void doComputeAndUpdate(String localName, List<String> allPlayers)
	{
		// Gather the freshly-fetched stats for the roster.
		Map<String, PlayerStats> stats = new HashMap<>();
		for (String p : allPlayers)
		{
			PlayerSnapshot snap = snapshots.get(p.toLowerCase());
			if (snap != null)
			{
				stats.put(p.toLowerCase(), snap);
			}
		}

		CrownOptions options = new CrownOptions(
			config.trackSkills(), config.trackBosses(), config.trackClues(), config.gapToNextPlayer());
		CrownResult result = crownCalculator.calculate(allPlayers, stats, options);

		applyHolderChanges(localName, result.getHolders());

		String timestamp = TIME_FMT.format(Instant.now());
		panel.updateStandings(result.getStandings(), localName != null ? localName : "", timestamp);
	}

	/**
	 * Compares the freshly-computed crown holders against the stored ones, firing
	 * gain/loss notifications for the local player and persisting the new holders.
	 */
	private void applyHolderChanges(String localName, Map<String, String> newHolders)
	{
		for (Map.Entry<String, String> entry : newHolders.entrySet())
		{
			String id = entry.getKey();
			String newHolder = entry.getValue();

			if (seeded && !sameHolder(newHolder, crownHolders.get(id)))
			{
				String prevHolder = crownHolders.get(id);
				boolean localHeld = localName != null && localName.equalsIgnoreCase(prevHolder);
				boolean localGained = localName != null && localName.equalsIgnoreCase(newHolder);
				String name = CrownCalculator.categoryDisplayName(id);

				if (localGained)
				{
					notify("You claimed the " + name + " crown!");
				}
				else if (localHeld)
				{
					notify(newHolder != null
						? "You lost the " + name + " crown to " + newHolder + "!"
						: "You lost the " + name + " crown!");
				}
			}

			persistAndStore(id, newHolder);
		}

		seeded = true;
	}

	private void persistAndStore(String id, String holder)
	{
		String value = holder != null ? holder : "";
		crownHolders.put(id, value);
		persistCrown(id, value);
	}

	private static boolean sameHolder(String a, String b)
	{
		return (a == null ? "" : a).equalsIgnoreCase(b == null ? "" : b);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

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
		if (config.notifyGameChat())
		{
			// Client calls must run on the client thread.
			clientThread.invoke(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
				}
			});
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

	private void persistCrown(String id, String holder)
	{
		configManager.setConfiguration("rivalry", CROWN_KEY_PREFIX + id, holder);
	}

	private void loadPersistedCrowns()
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			loadCrown(skill.name());
		}
		loadCrown("TOTAL_LEVEL");
		loadCrown("TOTAL_BOSS_KC");
	}

	private void loadCrown(String id)
	{
		String stored = configManager.getConfiguration("rivalry", CROWN_KEY_PREFIX + id);
		if (stored != null && !stored.isBlank())
		{
			crownHolders.put(id, stored);
		}
	}
}
