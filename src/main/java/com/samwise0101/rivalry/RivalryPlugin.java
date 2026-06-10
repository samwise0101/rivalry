package com.samwise0101.rivalry;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
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
	private static final String SUCCESS_SOUND = "/success.wav";
	private static final String FAILURE_SOUND = "/failure.wav";

	private static final String KEY_POLL_INTERVAL = "pollIntervalMinutes";
	// Config keys that change only how data is shown — recompute from cache, no re-fetch.
	private static final Set<String> DISPLAY_KEYS =
		Set.of("trackSkills", "trackBosses", "trackClues", "gapToNextPlayer");
	// Config keys that only affect notification delivery — no recompute needed.
	private static final Set<String> NOTIFICATION_KEYS =
		Set.of(
			RivalryConfig.CROWN_NOTIFICATIONS_ENABLED_KEY,
			RivalryConfig.CROWN_GAME_MESSAGE_KEY,
			RivalryConfig.CROWN_NOTIFICATION_SOUND_KEY,
			RivalryConfig.CROWN_NOTIFICATION_VOLUME_KEY,
			RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY,
			RivalryConfig.OLD_NOTIFY_DESKTOP_KEY);

	@Inject
	private Client client;

	@Inject
	private RivalryConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private AudioPlayer audioPlayer;

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
	private volatile LocalStatValues latestLocalStatValues;

	private boolean seeded = false;

	@Override
	protected void startUp()
	{
		migrateNotificationConfig();

		panel = new RivalryPanel(iconLoader);
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
			latestLocalStatValues = null;
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
	public void onStatChanged(StatChanged event)
	{
		HiscoreSkill hiscoreSkill = hiscoreSkillFor(event.getSkill());
		if (hiscoreSkill == null)
		{
			return;
		}

		String localName = localPlayerName;
		List<String> roster = lastRoster;
		Map<String, PlayerStats> stats = lastStats;
		if (localName == null || roster == null || stats == null || !containsPlayer(roster, localName))
		{
			return;
		}

		LocalStatValues localValues = captureLocalStatValues();
		latestLocalStatValues = localValues;
		Map<String, PlayerStats> updatedStats = withLocalStats(localName, stats, localValues);
		executor.execute(() -> computeAndUpdate(localName, roster, updatedStats));
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
		Notification oldNotification = getOldCrownNotification();
		String oldGameChat = configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY);

		if (configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATIONS_ENABLED_KEY) == null)
		{
			boolean enabled = oldNotification == null || oldNotification.isEnabled();
			configManager.setConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATIONS_ENABLED_KEY, enabled);
		}

		if (configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_GAME_MESSAGE_KEY) == null)
		{
			boolean gameMessage = oldNotification != null
				? oldNotification.isGameMessage()
				: oldGameChat == null || Boolean.parseBoolean(oldGameChat);
			configManager.setConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_GAME_MESSAGE_KEY, gameMessage);
		}

		if (oldNotification != null)
		{
			if (configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_SOUND_KEY) == null)
			{
				configManager.setConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_SOUND_KEY,
					oldNotification.isEnabled() && oldNotification.getSound() != NotificationSound.OFF);
			}

			if (configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_VOLUME_KEY) == null)
			{
				configManager.setConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_VOLUME_KEY,
					oldNotification.getVolume());
			}
		}

		configManager.unsetConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_KEY);
		configManager.unsetConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_GAME_CHAT_KEY);
		configManager.unsetConfiguration(RivalryConfig.GROUP, RivalryConfig.OLD_NOTIFY_DESKTOP_KEY);
		log.debug("Migrated Rivalry notification config");
	}

	private Notification getOldCrownNotification()
	{
		try
		{
			return configManager.getConfiguration(RivalryConfig.GROUP, RivalryConfig.CROWN_NOTIFICATION_KEY,
				Notification.class);
		}
		catch (Exception e)
		{
			log.debug("Could not read old Rivalry crown notification config", e);
			return null;
		}
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

	private LocalStatValues captureLocalStatValues()
	{
		Map<HiscoreSkill, Long> skillXp = new EnumMap<>(HiscoreSkill.class);
		Map<HiscoreSkill, Integer> skillLevels = new EnumMap<>(HiscoreSkill.class);
		for (Skill skill : Skill.values())
		{
			HiscoreSkill hiscoreSkill = hiscoreSkillFor(skill);
			if (hiscoreSkill == null)
			{
				continue;
			}
			skillXp.put(hiscoreSkill, (long) client.getSkillExperience(skill));
			skillLevels.put(hiscoreSkill, client.getRealSkillLevel(skill));
		}
		return new LocalStatValues(skillXp, skillLevels, client.getOverallExperience(), client.getTotalLevel());
	}

	private static HiscoreSkill hiscoreSkillFor(Skill skill)
	{
		if (skill == Skill.OVERALL)
		{
			return null;
		}
		try
		{
			HiscoreSkill hiscoreSkill = HiscoreSkill.valueOf(skill.name());
			return hiscoreSkill.getType() == HiscoreSkillType.SKILL ? hiscoreSkill : null;
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static boolean containsPlayer(List<String> roster, String player)
	{
		return roster.stream().anyMatch(p -> p.equalsIgnoreCase(player));
	}

	private static Map<String, PlayerStats> withLocalStats(String localName, Map<String, PlayerStats> stats,
		LocalStatValues localValues)
	{
		Map<String, PlayerStats> updated = new HashMap<>(stats);
		String key = localName.toLowerCase();
		updated.put(key, new LocalPlayerStats(updated.get(key), localValues.skillXp, localValues.skillLevels,
			localValues.overallXp, localValues.totalLevel));
		return updated;
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
		LocalStatValues localValues = latestLocalStatValues;
		if (localName != null && localValues != null && containsPlayer(roster, localName))
		{
			stats = withLocalStats(localName, stats, localValues);
		}

		List<String> previousRoster = lastRoster;
		boolean rosterChanged = previousRoster != null && !sameRoster(previousRoster, roster);

		// Remember the inputs so display-only config changes can recompute cheaply.
		lastRoster = roster;
		lastStats = stats;
		lastComputeLocalName = localName;

		try
		{
			CrownOptions options = new CrownOptions(
				config.trackSkills(), config.trackBosses(), config.trackClues(), config.gapToNextPlayer());
			CrownResult result = crownCalculator.calculate(roster, stats, options);

			applyHolderChanges(localName, result.getTierHolders(), rosterChanged);

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
	private void applyHolderChanges(String localName, Map<CrownTier, Map<String, String>> newHoldersByTier,
		boolean suppressNotifications)
	{
		for (CrownTier tier : CrownTier.values())
		{
			Map<String, String> newHolders = newHoldersByTier.get(tier);
			if (newHolders == null)
			{
				continue;
			}
			for (Map.Entry<String, String> entry : newHolders.entrySet())
			{
				String id = entry.getKey();
				String newHolder = entry.getValue();
				String prevHolder = crownStore.getHolder(tier, id);

				if (seeded && !suppressNotifications && !sameHolder(newHolder, prevHolder))
				{
					boolean localHeld = localName != null && holderContains(prevHolder, localName);
					boolean localGained = localName != null && holderContains(newHolder, localName);
					String name = CrownCalculator.categoryDisplayName(id);

					if (localGained && !localHeld)
					{
						notify("You gained the " + tierName(tier) + " crown for " + name + "!", SUCCESS_SOUND);
					}
					else if (localHeld && !localGained)
					{
						notify(newHolder != null
							? "You lost the " + tierName(tier) + " crown for " + name
								+ " to " + holderDisplayName(newHolder) + "!"
							: "You lost the " + tierName(tier) + " crown for " + name + "!", FAILURE_SOUND);
					}
				}

				crownStore.setHolder(tier, id, newHolder);
			}
		}

		seeded = true;
	}

	private static boolean sameRoster(List<String> a, List<String> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}

		for (int i = 0; i < a.size(); i++)
		{
			String left = a.get(i);
			String right = b.get(i);
			if (left == null ? right != null : !left.equalsIgnoreCase(right))
			{
				return false;
			}
		}

		return true;
	}

	private static boolean sameHolder(String a, String b)
	{
		return holderSet(a).equals(holderSet(b));
	}

	private static boolean holderContains(String holders, String player)
	{
		return holderSet(holders).contains(player.toLowerCase());
	}

	private static Set<String> holderSet(String holders)
	{
		Set<String> result = new HashSet<>();
		if (holders == null || holders.isBlank())
		{
			return result;
		}
		for (String holder : holders.split("\\|"))
		{
			if (!holder.isBlank())
			{
				result.add(holder.toLowerCase());
			}
		}
		return result;
	}

	private static String holderDisplayName(String holders)
	{
		return holders.replace("|", ", ");
	}

	private static String tierName(CrownTier tier)
	{
		return tier.name().toLowerCase();
	}

	private void notify(String message, String soundResource)
	{
		if (!config.crownNotificationsEnabled())
		{
			return;
		}

		notifier.notify(crownNotification(), message);
		playNotificationSound(soundResource);
	}

	private Notification crownNotification()
	{
		return new Notification(
			true,
			true,
			true,
			false,
			TrayIcon.MessageType.NONE,
			RequestFocusType.OFF,
			NotificationSound.OFF,
			null,
			100,
			0,
			config.crownGameMessage(),
			FlashNotification.DISABLED,
			new Color(255, 0, 0, 70),
			true);
	}

	private void playNotificationSound(String resourcePath)
	{
		if (!config.crownNotificationsEnabled() || !config.crownNotificationSound())
		{
			return;
		}

		int volume = Math.max(0, Math.min(100, config.crownNotificationVolume()));
		if (volume == 0)
		{
			return;
		}

		// Convert a 0-100 volume to a MASTER_GAIN value in decibels (0 dB at full volume).
		float gain = (float) (20.0 * Math.log10(volume / 100.0));
		executor.execute(() ->
		{
			try
			{
				audioPlayer.play(getClass(), resourcePath, gain);
			}
			catch (Exception e)
			{
				log.debug("Unable to play notification sound {}", resourcePath, e);
			}
		});
	}

	private static final class LocalStatValues
	{
		private final Map<HiscoreSkill, Long> skillXp;
		private final Map<HiscoreSkill, Integer> skillLevels;
		private final long overallXp;
		private final int totalLevel;

		private LocalStatValues(Map<HiscoreSkill, Long> skillXp, Map<HiscoreSkill, Integer> skillLevels,
			long overallXp, int totalLevel)
		{
			this.skillXp = skillXp;
			this.skillLevels = skillLevels;
			this.overallXp = overallXp;
			this.totalLevel = totalLevel;
		}
	}
}
