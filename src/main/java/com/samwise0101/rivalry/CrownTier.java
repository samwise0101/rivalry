package com.samwise0101.rivalry;

import java.awt.Color;

enum CrownTier
{
	GOLD(new Color(255, 215, 0)),
	SILVER(new Color(192, 192, 192)),
	BRONZE(new Color(205, 127, 50));

	private final Color color;

	CrownTier(Color color)
	{
		this.color = color;
	}

	Color getColor()
	{
		return color;
	}
}
