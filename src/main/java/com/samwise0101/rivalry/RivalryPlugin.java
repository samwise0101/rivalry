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
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
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

	private RivalryPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> pollTask;

	// Cached on the client thread (GameTick) so executor threads can read it safely.
	private volatile String localPlayerName;

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
		Map<String, List<CategoryStat>> statsByPlayer = new HashMap<>();
		Map<String, Integer> crownCount = new HashMap<>();
		for (String p : allPlayers)
		{
			statsByPlayer.put(p, new ArrayList<>());
			crownCount.put(p, 0);
		}

		// Aggregate crowns shown at the top of their tab.
		processCategory("TOTAL_LEVEL", "Total Level", HiscoreSkillType.SKILL, -1, -1, true,
			PlayerSnapshot::overallXp, PlayerSnapshot::totalLevel,
			allPlayers, localName, statsByPlayer, crownCount);
		processCategory("TOTAL_BOSS_KC", "Total Boss KC", HiscoreSkillType.BOSS, -1, -1, true,
			s -> s.totalBossKc(), PlayerSnapshot::totalBossKc,
			allPlayers, localName, statsByPlayer, crownCount);

		// One crown per individual hiscore entry.
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			HiscoreSkillType type = skill.getType();
			if (type == HiscoreSkillType.OVERALL || !isTypeEnabled(type))
			{
				continue;
			}
			processCategory(skill.name(), skill.getName(), type, skill.getSpriteId(), clueItemId(skill), false,
				s -> s.crownValue(skill), s -> s.displayValue(skill),
				allPlayers, localName, statsByPlayer, crownCount);
		}

		seeded = true;

		List<PlayerStanding> standings = new ArrayList<>();
		for (String p : allPlayers)
		{
			standings.add(new PlayerStanding(p, crownCount.get(p), statsByPlayer.get(p)));
		}

		String timestamp = TIME_FMT.format(Instant.now());
		panel.updateStandings(standings, localName != null ? localName : "", timestamp);
	}

	/**
	 * Determines the crown holder for one category, fires gain/loss notifications,
	 * and records a per-player comparison stat.
	 */
	private void processCategory(String id, String displayName, HiscoreSkillType type,
		int spriteId, int itemId, boolean aggregate,
		ToLongFunction<PlayerSnapshot> crownValueFn, ToIntFunction<PlayerSnapshot> displayValueFn,
		List<String> allPlayers, String localName,
		Map<String, List<CategoryStat>> statsByPlayer, Map<String, Integer> crownCount)
	{
		if (!isTypeEnabled(type))
		{
			return;
		}

		// Pass 1: find the crown holder (highest crown value) and gather display values.
		String newHolder = null;
		long best = -1;
		Map<String, Integer> displays = new HashMap<>();
		for (String name : allPlayers)
		{
			PlayerSnapshot snap = snapshots.get(name.toLowerCase());
			long crownVal = snap != null ? crownValueFn.applyAsLong(snap) : -1;
			int displayVal = snap != null ? displayValueFn.applyAsInt(snap) : -1;
			displays.put(name, displayVal);
			if (crownVal > best)
			{
				best = crownVal;
				newHolder = name;
			}
		}

		if (newHolder == null)
		{
			return;
		}

		String prevHolder = crownHolders.get(id);

		if (!seeded)
		{
			// First run: just record, don't notify
			crownHolders.put(id, newHolder);
			persistCrown(id, newHolder);
		}
		else if (!newHolder.equalsIgnoreCase(prevHolder))
		{
			boolean localHeld = localName != null && localName.equalsIgnoreCase(prevHolder);
			boolean localGained = localName != null && localName.equalsIgnoreCase(newHolder);

			if (localGained)
			{
				notify("You claimed the " + displayName + " crown!");
			}
			else if (localHeld)
			{
				notify("You lost the " + displayName + " crown to " + newHolder + "!");
			}

			crownHolders.put(id, newHolder);
			persistCrown(id, newHolder);
		}

		crownCount.merge(newHolder, 1, Integer::sum);

		// Values are relative to the crown holder:
		//   holder    -> +(holder's value minus the best challenger)  [their margin]
		//   non-holder-> (their value minus holder's value)           [their deficit, <= 0]
		int holderDisplay = displays.get(newHolder);
		int runnerUp = 0;
		for (String name : allPlayers)
		{
			if (name.equals(newHolder))
			{
				continue;
			}
			int d = displays.get(name);
			if (d > runnerUp)
			{
				runnerUp = d;
			}
		}

		for (String p : allPlayers)
		{
			int displayVal = displays.get(p);

			// Skills are always listed; bosses/activities only when this player is ranked.
			if (type != HiscoreSkillType.SKILL && displayVal < 0)
			{
				continue;
			}

			boolean holds = p.equalsIgnoreCase(newHolder);
			Integer diff;
			if (displayVal < 0)
			{
				diff = null; // unranked skill
			}
			else if (holds)
			{
				diff = holderDisplay - runnerUp;
			}
			else
			{
				diff = displayVal - holderDisplay;
			}

			statsByPlayer.get(p).add(new CategoryStat(displayName, spriteId, itemId, type, diff, holds, aggregate));
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private boolean isTypeEnabled(HiscoreSkillType type)
	{
		switch (type)
		{
			case SKILL:
				return config.trackSkills();
			case BOSS:
				return config.trackBosses();
			case ACTIVITY:
				return config.trackClues();
			default:
				return false;
		}
	}

	/** Maps clue scroll tiers (which lack a sprite) to a representative clue-scroll item id, else -1. */
	private static int clueItemId(HiscoreSkill skill)
	{
		switch (skill)
		{
			case CLUE_SCROLL_BEGINNER:
				return 23182; // Clue scroll (beginner)
			case CLUE_SCROLL_EASY:
				return 2677;  // Clue scroll (easy)
			case CLUE_SCROLL_MEDIUM:
				return 2801;  // Clue scroll (medium)
			case CLUE_SCROLL_HARD:
				return 2722;  // Clue scroll (hard)
			case CLUE_SCROLL_ELITE:
				return 12073; // Clue scroll (elite)
			case CLUE_SCROLL_MASTER:
				return 19835; // Clue scroll (master)
			default:
				return -1;
		}
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
