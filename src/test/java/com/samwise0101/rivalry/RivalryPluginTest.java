package com.samwise0101.rivalry;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RivalryPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RivalryPlugin.class);
		RuneLite.main(args);
	}
}
