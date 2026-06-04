package com.samwise0101.rivalry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

public class RivalryPanel extends PluginPanel
{
	private static final Color CROWN_COLOR = new Color(255, 215, 0);
	private static final Color HEADER_COLOR = ColorScheme.BRAND_ORANGE;
	private static final Color AHEAD_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;   // green
	private static final Color BEHIND_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;     // red
	private static final int ICON_SIZE = 18;
	private static final int GRID_COLUMNS = 3;
	private static final int REACH_LIMIT = 10;

	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ImageIcon trophyIcon;
	private final JPanel body = new JPanel();
	private final JLabel statusLabel = new JLabel("Not refreshed yet", SwingConstants.CENTER);
	private Runnable onRefresh;

	// Per-player expand state and last-open tab.
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, String> selectedTab = new HashMap<>();

	// Top-level collapsible section state (retained across refreshes).
	private boolean rivalsExpanded = true;
	private boolean reachExpanded = true;
	private String reachSelectedTab = "Skills";
	private String reachSkillsMode = "By Level";

	// Last data received, so section toggles can re-render without a refresh.
	private List<PlayerStanding> lastStandings = Collections.emptyList();
	private String lastLocalPlayer = "";

	RivalryPanel(SpriteManager spriteManager, ItemManager itemManager)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;

		BufferedImage trophy = ImageUtil.loadImageResource(getClass(), "/trophy.png");
		this.trophyIcon = new ImageIcon(trophy.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));

		JLabel title = new JLabel("Rivalry", SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		title.setForeground(HEADER_COLOR);
		add(title);

		JButton refreshBtn = new JButton("Refresh Now");
		refreshBtn.addActionListener(e -> { if (onRefresh != null) onRefresh.run(); });
		add(refreshBtn);

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		add(statusLabel);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(body);
	}

	void setRefreshCallback(Runnable callback)
	{
		this.onRefresh = callback;
	}

	void setStatus(String message)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(message));
	}

	/** Stores the latest data and rebuilds the panel. Callable from any thread. */
	void updateStandings(List<PlayerStanding> standings, String localPlayer, String lastUpdated)
	{
		SwingUtilities.invokeLater(() ->
		{
			lastStandings = standings;
			lastLocalPlayer = localPlayer;
			statusLabel.setText("Updated: " + lastUpdated);
			rebuild();
		});
	}

	// -------------------------------------------------------------------------
	// Layout
	// -------------------------------------------------------------------------

	private void rebuild()
	{
		body.removeAll();

		// --- Your crowns summary ---
		if (!lastStandings.isEmpty())
		{
			body.add(buildCrownSummary());
		}

		// --- Rivals section ---
		body.add(sectionHeader("Rivals", rivalsExpanded, () ->
		{
			rivalsExpanded = !rivalsExpanded;
			rebuild();
		}));

		if (rivalsExpanded)
		{
			if (lastStandings.isEmpty())
			{
				body.add(note("No rivals configured."));
			}
			else
			{
				lastStandings.stream()
					.sorted((a, b) -> b.getCrownCount() - a.getCrownCount())
					.forEach(s ->
					{
						boolean isLocal = s.getName().equalsIgnoreCase(lastLocalPlayer);
						body.add(buildPlayerRow(s, isLocal));
						if (expanded.contains(s.getName().toLowerCase()))
						{
							body.add(buildExpandSection(s));
						}
					});
			}
		}

		// --- Within Reach section ---
		body.add(sectionHeader("Within Reach", reachExpanded, () ->
		{
			reachExpanded = !reachExpanded;
			rebuild();
		}));

		if (reachExpanded)
		{
			body.add(buildReachContent());
		}

		body.add(Box.createVerticalGlue());
		body.revalidate();
		body.repaint();
	}

	private JComponent buildCrownSummary()
	{
		int total = lastStandings.stream().mapToInt(PlayerStanding::getCrownCount).sum();
		int mine = lastStandings.stream()
			.filter(s -> s.getName().equalsIgnoreCase(lastLocalPlayer))
			.mapToInt(PlayerStanding::getCrownCount)
			.findFirst()
			.orElse(0);

		JLabel summary = new JLabel("Your Crowns: " + mine + "/" + total + " 👑", SwingConstants.CENTER);
		summary.setFont(FontManager.getRunescapeBoldFont());
		summary.setForeground(CROWN_COLOR);
		summary.setHorizontalAlignment(SwingConstants.CENTER);
		summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		summary.setBorder(BorderFactory.createEmptyBorder(2, 4, 8, 4));
		summary.setAlignmentX(LEFT_ALIGNMENT);
		return summary;
	}

	private JComponent sectionHeader(String title, boolean isExpanded, Runnable onToggle)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 4, 5, 4)
		));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel label = new JLabel((isExpanded ? "▼ " : "▶ ") + title);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(HEADER_COLOR);
		header.add(label, BorderLayout.WEST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onToggle.run();
			}
		});
		return header;
	}

	private JComponent note(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	// -------------------------------------------------------------------------
	// Rivals section
	// -------------------------------------------------------------------------

	private JComponent buildPlayerRow(PlayerStanding standing, boolean isLocal)
	{
		final String name = standing.getName();
		boolean isExpanded = expanded.contains(name.toLowerCase());

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(isLocal ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)
		));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		String arrow = isExpanded ? "▼ " : "▶ ";
		JLabel nameLabel = new JLabel(arrow + (isLocal ? name + " (you)" : name));
		nameLabel.setForeground(isLocal ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());

		int crowns = standing.getCrownCount();
		JLabel crownLabel = new JLabel(crowns + " 👑");
		crownLabel.setForeground(crowns > 0 ? CROWN_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		crownLabel.setFont(FontManager.getRunescapeSmallFont());
		crownLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(nameLabel, BorderLayout.WEST);
		row.add(crownLabel, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				String key = name.toLowerCase();
				if (!expanded.remove(key))
				{
					expanded.add(key);
				}
				rebuild();
			}
		});

		return row;
	}

	private JComponent buildExpandSection(PlayerStanding standing)
	{
		final String key = standing.getName().toLowerCase();

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
		wrapper.setAlignmentX(LEFT_ALIGNMENT);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

		MaterialTab skillsTab = new MaterialTab("Skills", tabGroup, buildGrid(standing, HiscoreSkillType.SKILL));
		MaterialTab bossesTab = new MaterialTab("Bosses", tabGroup, buildGrid(standing, HiscoreSkillType.BOSS));
		MaterialTab otherTab = new MaterialTab("Other", tabGroup, buildGrid(standing, HiscoreSkillType.ACTIVITY));

		skillsTab.setOnSelectEvent(() -> { selectedTab.put(key, "Skills"); return true; });
		bossesTab.setOnSelectEvent(() -> { selectedTab.put(key, "Bosses"); return true; });
		otherTab.setOnSelectEvent(() -> { selectedTab.put(key, "Other"); return true; });

		tabGroup.addTab(skillsTab);
		tabGroup.addTab(bossesTab);
		tabGroup.addTab(otherTab);
		selectTab(tabGroup, selectedTab.getOrDefault(key, "Skills"), skillsTab, bossesTab, otherTab);

		wrapper.add(tabGroup, BorderLayout.NORTH);
		wrapper.add(display, BorderLayout.CENTER);
		return wrapper;
	}

	private JComponent buildGrid(PlayerStanding standing, HiscoreSkillType type)
	{
		List<CategoryStat> all = standing.getStats().stream()
			.filter(c -> c.getType() == type)
			.collect(Collectors.toList());

		CategoryStat aggregate = all.stream().filter(CategoryStat::isAggregate).findFirst().orElse(null);
		List<CategoryStat> regular = all.stream()
			.filter(c -> !c.isAggregate())
			.sorted(Comparator.comparingInt(CategoryStat::getOrder))
			.collect(Collectors.toList());

		if (aggregate == null && regular.isEmpty())
		{
			return emptyTabPanel("Nothing ranked");
		}

		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (!regular.isEmpty())
		{
			JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 4, 2));
			grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			grid.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
			// Centre the grid block so left/right margins are even.
			grid.setAlignmentX(CENTER_ALIGNMENT);
			for (CategoryStat stat : regular)
			{
				grid.add(buildCell(stat));
			}
			container.add(grid);
		}

		// Aggregate (e.g. Total Level) gets its own centered full-width row below the grid.
		if (aggregate != null)
		{
			container.add(buildTotalRow(aggregate));
		}

		return container;
	}

	private JComponent buildTotalRow(CategoryStat aggregate)
	{
		JLabel row = new JLabel(aggregate.getName() + ": " + formatValue(aggregate), SwingConstants.CENTER);
		row.setFont(FontManager.getRunescapeSmallFont());
		row.setForeground(colorFor(aggregate));
		row.setHorizontalAlignment(SwingConstants.CENTER);
		row.setIconTextGap(4);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		row.setAlignmentX(CENTER_ALIGNMENT);
		applyIcon(row, aggregate);
		return row;
	}

	// -------------------------------------------------------------------------
	// Within Reach section
	// -------------------------------------------------------------------------

	private JComponent buildReachContent()
	{
		PlayerStanding me = lastStandings.stream()
			.filter(s -> s.getName().equalsIgnoreCase(lastLocalPlayer))
			.findFirst()
			.orElse(null);

		if (me == null)
		{
			return note("Log in to see crowns within your reach.");
		}

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
		wrapper.setAlignmentX(LEFT_ALIGNMENT);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

		MaterialTab skillsTab = new MaterialTab("Skills", tabGroup, buildReachList(me, HiscoreSkillType.SKILL));
		MaterialTab bossesTab = new MaterialTab("Bosses", tabGroup, buildReachList(me, HiscoreSkillType.BOSS));
		MaterialTab otherTab = new MaterialTab("Other", tabGroup, buildReachList(me, HiscoreSkillType.ACTIVITY));

		skillsTab.setOnSelectEvent(() -> { reachSelectedTab = "Skills"; return true; });
		bossesTab.setOnSelectEvent(() -> { reachSelectedTab = "Bosses"; return true; });
		otherTab.setOnSelectEvent(() -> { reachSelectedTab = "Other"; return true; });

		tabGroup.addTab(skillsTab);
		tabGroup.addTab(bossesTab);
		tabGroup.addTab(otherTab);
		selectTab(tabGroup, reachSelectedTab, skillsTab, bossesTab, otherTab);

		wrapper.add(tabGroup, BorderLayout.NORTH);
		wrapper.add(display, BorderLayout.CENTER);
		return wrapper;
	}

	/** The closest crowns of a given type the local player doesn't hold. Skills get By Level / By XP sub-tabs. */
	private JComponent buildReachList(PlayerStanding me, HiscoreSkillType type)
	{
		if (type != HiscoreSkillType.SKILL)
		{
			return buildReachRows(me, type, false);
		}

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		MaterialTabGroup group = new MaterialTabGroup(display);
		group.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		MaterialTab byLevel = new MaterialTab("By Level", group, buildReachRows(me, type, false));
		MaterialTab byXp = new MaterialTab("By XP", group, buildReachRows(me, type, true));
		byLevel.setOnSelectEvent(() -> { reachSkillsMode = "By Level"; return true; });
		byXp.setOnSelectEvent(() -> { reachSkillsMode = "By XP"; return true; });
		group.addTab(byLevel);
		group.addTab(byXp);
		group.select("By XP".equals(reachSkillsMode) ? byXp : byLevel);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(group, BorderLayout.NORTH);
		wrapper.add(display, BorderLayout.CENTER);
		return wrapper;
	}

	private JComponent buildReachRows(PlayerStanding me, HiscoreSkillType type, boolean xpMode)
	{
		List<CategoryStat> reachable;
		if (xpMode)
		{
			reachable = me.getStats().stream()
				.filter(s -> s.getType() == type && !s.isHoldsCrown() && s.getCrownDiff() != null && s.getCrownDiff() < 0)
				.sorted(Comparator.comparingLong(s -> -s.getCrownDiff())) // smallest XP deficit first
				.limit(REACH_LIMIT)
				.collect(Collectors.toList());
		}
		else
		{
			reachable = me.getStats().stream()
				.filter(s -> s.getType() == type && !s.isHoldsCrown() && s.getDiff() != null && s.getDiff() < 0)
				.sorted(Comparator.comparingInt(s -> -s.getDiff())) // smallest deficit first
				.limit(REACH_LIMIT)
				.collect(Collectors.toList());
		}

		if (reachable.isEmpty())
		{
			return emptyTabPanel("Nothing within reach");
		}

		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		for (CategoryStat stat : reachable)
		{
			list.add(buildReachRow(stat, xpMode));
		}
		return list;
	}

	private JComponent buildReachRow(CategoryStat stat, boolean xpMode)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 4, 3, 4)
		));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		applyIcon(icon, stat);

		JLabel name = new JLabel(stat.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		String text = xpMode
			? String.format("%,d XP to go", -stat.getCrownDiff())
			: (-stat.getDiff()) + " to go";
		JLabel gapLabel = new JLabel(text);
		gapLabel.setFont(FontManager.getRunescapeSmallFont());
		gapLabel.setForeground(BEHIND_COLOR);
		gapLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(gapLabel, BorderLayout.EAST);
		return row;
	}

	// -------------------------------------------------------------------------
	// Shared helpers
	// -------------------------------------------------------------------------

	private static void selectTab(MaterialTabGroup group, String which, MaterialTab skills, MaterialTab bosses, MaterialTab other)
	{
		// Use the GROUP's select() — it populates the display panel.
		switch (which)
		{
			case "Bosses":
				group.select(bosses);
				break;
			case "Other":
				group.select(other);
				break;
			default:
				group.select(skills);
		}
	}

	private JPanel emptyTabPanel(String text)
	{
		JPanel empty = new JPanel(new BorderLayout());
		empty.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		empty.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		JLabel none = new JLabel(text, SwingConstants.CENTER);
		none.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		none.setFont(FontManager.getRunescapeSmallFont());
		empty.add(none, BorderLayout.CENTER);
		return empty;
	}

	private JLabel buildCell(CategoryStat stat)
	{
		JLabel cell = new JLabel(formatValue(stat));
		cell.setFont(FontManager.getRunescapeSmallFont());
		cell.setForeground(colorFor(stat));
		cell.setIconTextGap(2);
		// Centre the icon+value within each (full-width) grid cell so columns are even.
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		cell.setToolTipText(stat.getName() + (stat.isHoldsCrown() ? "  (crown)" : ""));
		applyIcon(cell, stat);
		return cell;
	}

	private void applyIcon(JLabel label, CategoryStat stat)
	{
		if (stat.getSpriteId() > 0)
		{
			loadSpriteIcon(label, stat.getSpriteId());
		}
		else if (stat.getItemId() > 0)
		{
			loadItemIcon(label, stat.getItemId());
		}
		else if (stat.isAggregate())
		{
			label.setIcon(trophyIcon);
		}
	}

	private void loadSpriteIcon(JLabel cell, int spriteId)
	{
		spriteManager.getSpriteAsync(spriteId, 0, img ->
		{
			if (img == null)
			{
				return;
			}
			SwingUtilities.invokeLater(() ->
			{
				cell.setIcon(new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
				cell.revalidate();
				cell.repaint();
			});
		});
	}

	private void loadItemIcon(JLabel cell, int itemId)
	{
		AsyncBufferedImage img = itemManager.getImage(itemId);
		img.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			cell.setIcon(new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
			cell.revalidate();
			cell.repaint();
		}));
	}

	private static String formatValue(CategoryStat stat)
	{
		Integer diff = stat.getDiff();
		if (diff == null)
		{
			return "?";
		}
		// Holder shows their margin as +X (>= 0); others show their deficit (<= 0).
		if (stat.isHoldsCrown() || diff > 0)
		{
			return "+" + diff;
		}
		return String.valueOf(diff);
	}

	private static Color colorFor(CategoryStat stat)
	{
		Integer diff = stat.getDiff();
		if (diff == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		if (stat.isHoldsCrown())
		{
			return AHEAD_COLOR;
		}
		if (diff < 0)
		{
			return BEHIND_COLOR;
		}
		// diff == 0: behind on the tiebreak if a holder exists, otherwise a genuine tie.
		return stat.isHasHolder() ? BEHIND_COLOR : ColorScheme.LIGHT_GRAY_COLOR;
	}
}
