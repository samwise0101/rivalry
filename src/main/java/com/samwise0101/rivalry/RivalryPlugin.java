package com.samwise0101.rivalry;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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

	private static final long LOGIN_REFRESH_DELAY_SECONDS = 10;
	private static final long STARTUP_REFRESH_DELAY_SECONDS = 3;

	private static final String KEY_POLL_INTERVAL = "pollIntervalMinutes";
	private static final List<String> DEMO_MESSAGES = List.of(
		"You claimed the Agility crown!",
		"You lost the Vorkath crown to Maple Sage!",
		"You claimed the Total Level crown!",
		"You lost the Clue Scrolls (hard) crown to Ashen Pike!");
	// Config keys that change only how data is shown — recompute from cache, no re-fetch.
	private static final Set<String> DISPLAY_KEYS =
		Set.of("trackSkills", "trackBosses", "trackClues", "gapToNextPlayer");
	// Config keys that only affect notification delivery — no recompute needed.
	private static final Set<String> NOTIFICATION_KEYS =
		Set.of(
			RivalryConfig.CROWN_NOTIFICATION_KEY,
			RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY,
			RivalryConfig.OLD_NOTIFY_DESKTOP_KEY);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RivalryConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private IconLoader iconLoader;

	@Inject
	private RosterResolver rosterResolver;

	@Inject
	private HiscoreService hiscoreService;

	@Inject
	private CrownCalculator crownCalculator;

	@Inject
	private CrownStore crownStore;

	private RivalryPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> pollTask;

	// Cached on the client thread (GameTick) so executor threads can read it safely.
	private volatile String localPlayerName;

	// Inputs from the last successful compute, so display-only changes can recompute
	// without re-fetching. The stats map is treated as immutable once stored.
	private volatile List<String> lastRoster;
	private volatile Map<String, PlayerStats> lastStats;
	private volatile String lastComputeLocalName;

	private boolean seeded = false;
	private int demoMessageIndex = 0;

	@Override
	protected void startUp()
	{
		migrateNotificationConfig();

		panel = new RivalryPanel(iconLoader);
		panel.setRefreshCallback(this::triggerRefresh);
		panel.setTestMessageCallback(this::fireDemoMessage);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/rivalry_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Rivalry")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		crownStore.load();
		schedulePoll();

		// If the plugin is enabled while already logged in, no LOGGED_IN event fires,
		// so kick off an initial refresh ourselves.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			executor.schedule(this::refresh, STARTUP_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
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
			executor.schedule(this::refresh, LOGIN_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
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
		if (!RivalryConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if (KEY_POLL_INTERVAL.equals(key))
		{
			schedulePoll();
		}
		else if (NOTIFICATION_KEYS.contains(key))
		{
			// Purely how we notify — nothing to recompute.
		}
		else if (DISPLAY_KEYS.contains(key))
		{
			// Category/display options don't change the fetched data, so recompute
			// from the cached stats instead of hitting the hiscores again.
			recomputeFromCache();
		}
		else
		{
			// Roster/group settings changed — need a fresh fetch.
			triggerRefresh();
		}
	}

	@Provides
	RivalryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RivalryConfig.class);
	}

	private void migrateNotificationConfig()
	{
		if (configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_KEY) != null)
		{
			return;
		}

		String oldGameChat = configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY);
		String oldDesktop = configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_DESKTOP_KEY);
		if (oldGameChat == null && oldDesktop == null)
		{
			return;
		}

		boolean gameChat = oldGameChat == null || Boolean.parseBoolean(oldGameChat);
		boolean desktop = oldDesktop != null && Boolean.parseBoolean(oldDesktop);
		configManager.setConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_KEY,
			RivalryConfig.crownNotification(gameChat, desktop));
		configManager.unsetConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY);
		configManager.unsetConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_DESKTOP_KEY);
		log.debug("Migrated Rivalry notification config");
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

	/** Recompute standings from the last fetched stats (for display-only changes), or refresh if none. */
	private void recomputeFromCache()
	{
		List<String> roster = lastRoster;
		Map<String, PlayerStats> stats = lastStats;
		if (roster == null || stats == null)
		{
			triggerRefresh();
			return;
		}
		executor.execute(() -> computeAndUpdate(lastComputeLocalName, roster, stats));
	}

	// -------------------------------------------------------------------------
	// Core refresh logic
	// -------------------------------------------------------------------------

	private void refresh()
	{
		final String localName = localPlayerName;

		rosterResolver.resolve(localName).whenComplete((roster, rosterErr) ->
		{
			if (rosterErr != null)
			{
				panel.setStatus(userMessage(rosterErr));
				return;
			}
			if (roster.isEmpty())
			{
				panel.setStatus(config.useWomGroup() ? "WOM group has no members." : "Configure rivals in settings.");
				return;
			}

			panel.setStatus("Refreshing...");
			hiscoreService.fetch(roster).whenComplete((stats, fetchErr) ->
			{
				if (fetchErr != null)
				{
					log.warn("Hiscore fetch failed", fetchErr);
					panel.setStatus("Refresh failed — see logs");
					return;
				}
				computeAndUpdate(localName, roster, stats);
			});
		});
	}

	private static String userMessage(Throwable t)
	{
		Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		String message = cause.getMessage();
		return message != null ? message : "Could not resolve rivals.";
	}

	private void computeAndUpdate(String localName, List<String> roster, Map<String, PlayerStats> stats)
	{
		// Remember the inputs so display-only config changes can recompute cheaply.
		lastRoster = roster;
		lastStats = stats;
		lastComputeLocalName = localName;

		try
		{
			CrownOptions options = new CrownOptions(
				config.trackSkills(), config.trackBosses(), config.trackClues(), config.gapToNextPlayer());
			CrownResult result = crownCalculator.calculate(roster, stats, options);

			applyHolderChanges(localName, result.getHolders());

			String timestamp = TIME_FMT.format(Instant.now());
			panel.updateStandings(result.getStandings(), localName != null ? localName : "", timestamp);
		}
		catch (Exception e)
		{
			// Async callbacks swallow exceptions; log and clear the status so the
			// panel never gets stuck on "Refreshing...".
			log.warn("Failed to compute crown standings", e);
			panel.setStatus("Refresh failed — see logs");
		}
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
			String prevHolder = crownStore.getHolder(id);

			if (seeded && !sameHolder(newHolder, prevHolder))
			{
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

			crownStore.setHolder(id, newHolder);
		}

		seeded = true;
	}

	private static boolean sameHolder(String a, String b)
	{
		return (a == null ? "" : a).equalsIgnoreCase(b == null ? "" : b);
	}

	private void notify(String message)
	{
		notifier.notify(config.crownNotification(), message);
	}

	private void fireDemoMessage()
	{
		String message = DEMO_MESSAGES.get(demoMessageIndex);
		demoMessageIndex = (demoMessageIndex + 1) % DEMO_MESSAGES.size();
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}
}
