package com.samwise0101.rivalry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class RivalryPanel extends PluginPanel
{
	private static final Color CROWN_COLOR = new Color(255, 215, 0);
	private static final Color HEADER_COLOR = ColorScheme.BRAND_ORANGE;

	private final JPanel standingsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel("Not refreshed yet", SwingConstants.CENTER);
	private Runnable onRefresh;

	RivalryPanel()
	{
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// Header
		JLabel title = new JLabel("Rivalry", SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		title.setForeground(HEADER_COLOR);
		add(title, BorderLayout.NORTH);

		// Standings list
		standingsPanel.setLayout(new GridLayout(0, 1, 0, 4));
		standingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JScrollPane scroll = new JScrollPane(standingsPanel);
		scroll.setBorder(null);
		scroll.setPreferredSize(new Dimension(200, 400));
		add(scroll, BorderLayout.CENTER);

		// Bottom: status + refresh button
		JPanel bottomPanel = new JPanel(new BorderLayout(0, 4));
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		bottomPanel.add(statusLabel, BorderLayout.NORTH);

		JButton refreshBtn = new JButton("Refresh Now");
		refreshBtn.addActionListener(e -> { if (onRefresh != null) onRefresh.run(); });
		bottomPanel.add(refreshBtn, BorderLayout.SOUTH);
		add(bottomPanel, BorderLayout.SOUTH);
	}

	void setRefreshCallback(Runnable callback)
	{
		this.onRefresh = callback;
	}

	/**
	 * Rebuilds the standings display. Called from the plugin on the EDT.
	 *
	 * @param players      ordered list of player names
	 * @param crownCounts  map of username → number of crowns held
	 * @param localPlayer  the logged-in player's username (highlighted differently)
	 * @param lastUpdated  human-readable timestamp string
	 */
	void updateStandings(List<String> players, Map<String, Integer> crownCounts,
		String localPlayer, String lastUpdated)
	{
		SwingUtilities.invokeLater(() ->
		{
			standingsPanel.removeAll();

			if (players.isEmpty())
			{
				JLabel empty = new JLabel("No rivals configured.", SwingConstants.CENTER);
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				standingsPanel.add(empty);
			}
			else
			{
				// Sort by crown count descending
				players.stream()
					.sorted((a, b) -> crownCounts.getOrDefault(b, 0) - crownCounts.getOrDefault(a, 0))
					.forEach(name ->
					{
						int crowns = crownCounts.getOrDefault(name, 0);
						JPanel row = buildPlayerRow(name, crowns, name.equalsIgnoreCase(localPlayer));
						standingsPanel.add(row);
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

	private JPanel buildPlayerRow(String name, int crowns, boolean isLocal)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(isLocal ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)
		));

		JLabel nameLabel = new JLabel(isLocal ? name + " (you)" : name);
		nameLabel.setForeground(isLocal ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel crownLabel = new JLabel(crowns + " 👑");
		crownLabel.setForeground(crowns > 0 ? CROWN_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		crownLabel.setFont(FontManager.getRunescapeSmallFont());
		crownLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(nameLabel, BorderLayout.WEST);
		row.add(crownLabel, BorderLayout.EAST);
		return row;
	}
}
