package com.samwise0101.rivalry;

import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Resolves and applies category icons to labels: RuneLite sprites for skills/
 * bosses/activities, item images for clue tiers, and a bundled trophy for
 * aggregates. Loading is asynchronous; the icon is set on the EDT when ready.
 */
@Singleton
class IconLoader
{
	private static final int ICON_SIZE = 18;

	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ImageIcon trophyIcon;

	@Inject
	IconLoader(SpriteManager spriteManager, ItemManager itemManager)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;

		BufferedImage trophy = ImageUtil.loadImageResource(getClass(), "/trophy.png");
		this.trophyIcon = new ImageIcon(trophy.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
	}

	/** Sets the appropriate icon on the label for this category. */
	void apply(JLabel label, CategoryStat stat)
	{
		if (stat.getSpriteId() > 0)
		{
			loadSprite(label, stat.getSpriteId());
		}
		else if (stat.getItemId() > 0)
		{
			loadItem(label, stat.getItemId());
		}
		else if (stat.isAggregate())
		{
			label.setIcon(trophyIcon);
		}
	}

	private void loadSprite(JLabel label, int spriteId)
	{
		spriteManager.getSpriteAsync(spriteId, 0, img ->
		{
			if (img != null)
			{
				setScaled(label, img);
			}
		});
	}

	private void loadItem(JLabel label, int itemId)
	{
		AsyncBufferedImage img = itemManager.getImage(itemId);
		img.onLoaded(() -> setScaled(label, img));
	}

	private static void setScaled(JLabel label, BufferedImage img)
	{
		SwingUtilities.invokeLater(() ->
		{
			label.setIcon(new ImageIcon(img.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
			label.revalidate();
			label.repaint();
		});
	}
}
