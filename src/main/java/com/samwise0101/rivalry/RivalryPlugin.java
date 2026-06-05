package com.samwise0101.rivalry;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
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

	@Inject
	private Client client;

	@Inject
	private RivalryConfig config;

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

		crownStore.load();
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
		if (!RivalryConfig.GROUP.equals(event.getGroup()))
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
}
