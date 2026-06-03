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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
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

	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ImageIcon trophyIcon;
	private final JPanel standingsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel("Not refreshed yet", SwingConstants.CENTER);
	private Runnable onRefresh;

	// Usernames whose section is expanded, and which tab they last had open.
	private final Set<String> expanded = new HashSet<>();
	private final java.util.Map<String, String> selectedTab = new HashMap<>();

	RivalryPanel(SpriteManager spriteManager, ItemManager itemManager)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;

		BufferedImage trophy = ImageUtil.loadImageResource(getClass(), "/trophy.png");
		this.trophyIcon = new ImageIcon(trophy.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));

		// Keep PluginPanel's default DynamicGridLayout so the surrounding sidebar
		// JScrollPane handles overflow — content grows into the full sidebar height.
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

		standingsPanel.setLayout(new BoxLayout(standingsPanel, BoxLayout.Y_AXIS));
		standingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(standingsPanel);
	}

	void setRefreshCallback(Runnable callback)
	{
		this.onRefresh = callback;
	}

	/**
	 * Rebuilds the standings display. Called from the plugin (any thread).
	 */
	void updateStandings(List<PlayerStanding> standings, String localPlayer, String lastUpdated)
	{
		SwingUtilities.invokeLater(() ->
		{
			standingsPanel.removeAll();

			if (standings.isEmpty())
			{
				JLabel empty = new JLabel("No rivals configured.", SwingConstants.CENTER);
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				standingsPanel.add(empty);
			}
			else
			{
				standings.stream()
					.sorted((a, b) -> b.getCrownCount() - a.getCrownCount())
					.forEach(s ->
					{
						boolean isLocal = s.getName().equalsIgnoreCase(localPlayer);
						standingsPanel.add(buildPlayerRow(s, isLocal, standings, localPlayer, lastUpdated));
						if (expanded.contains(s.getName().toLowerCase()))
						{
							standingsPanel.add(buildExpandSection(s));
						}
					});
			}

			statusLabel.setText("Updated: " + lastUpdated);
			standingsPanel.revalidate();
			standingsPanel.repaint();
		});
	}

	void setStatus(String message)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(message));
	}

	private JPanel buildPlayerRow(PlayerStanding standing, boolean isLocal,
		List<PlayerStanding> standings, String localPlayer, String lastUpdated)
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
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		String arrow = isExpanded ? "▼ " : "▶ "; // ▼ / ▶
		JLabel nameLabel = new JLabel(arrow + (isLocal ? name + " (you)" : name));
		nameLabel.setForeground(isLocal ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());

		int crowns = standing.getCrownCount();
		JLabel crownLabel = new JLabel(crowns + " 👑"); // 👑
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
				updateStandings(standings, localPlayer, lastUpdated);
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

		// Use the GROUP's select() — it populates the display panel. tab.select()
		// alone only restyles the tab label and shows no content.
		switch (selectedTab.getOrDefault(key, "Skills"))
		{
			case "Bosses":
				tabGroup.select(bossesTab);
				break;
			case "Other":
				tabGroup.select(otherTab);
				break;
			default:
				tabGroup.select(skillsTab);
		}

		wrapper.add(tabGroup, BorderLayout.NORTH);
		wrapper.add(display, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel buildGrid(PlayerStanding standing, HiscoreSkillType type)
	{
		List<CategoryStat> stats = standing.getStats().stream()
			.filter(c -> c.getType() == type)
			// Aggregate categories (Total Level / Total Boss KC) first.
			.sorted((a, b) -> Boolean.compare(b.isAggregate(), a.isAggregate()))
			.collect(Collectors.toList());

		if (stats.isEmpty())
		{
			JPanel empty = new JPanel(new BorderLayout());
			empty.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			empty.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			JLabel none = new JLabel("Nothing ranked", SwingConstants.CENTER);
			none.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			none.setFont(FontManager.getRunescapeSmallFont());
			empty.add(none, BorderLayout.CENTER);
			return empty;
		}

		JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 4, 2));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		for (CategoryStat stat : stats)
		{
			grid.add(buildCell(stat));
		}
		return grid;
	}

	private JLabel buildCell(CategoryStat stat)
	{
		JLabel cell = new JLabel(formatValue(stat));
		cell.setFont(FontManager.getRunescapeSmallFont());
		cell.setForeground(colorFor(stat));
		cell.setIconTextGap(2);
		cell.setToolTipText(stat.getName() + (stat.isHoldsCrown() ? "  (crown)" : ""));

		if (stat.isAggregate())
		{
			cell.setIcon(trophyIcon);
		}
		else if (stat.getItemId() > 0)
		{
			loadItemIcon(cell, stat.getItemId());
		}
		else if (stat.getSpriteId() > 0)
		{
			loadSpriteIcon(cell, stat.getSpriteId());
		}

		return cell;
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
		Runnable apply = () -> SwingUtilities.invokeLater(() ->
		{
			cell.setIcon(new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
			cell.revalidate();
			cell.repaint();
		});
		img.onLoaded(apply);
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
		return diff < 0 ? BEHIND_COLOR : ColorScheme.LIGHT_GRAY_COLOR;
	}
}
