package com.samwise0101.rivalry;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PlayerSnapshotTest
{
	private static PlayerSnapshot snapshot(Map<HiscoreSkill, Skill> skills)
	{
		return new PlayerSnapshot("Player", new HiscoreResult("Player", skills));
	}

	private static Skill skill(int rank, int level, long xp)
	{
		return new Skill(rank, level, xp);
	}

	@Test
	public void crownValueIsXpForSkillsAndScoreOtherwise()
	{
		Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
		skills.put(HiscoreSkill.ATTACK, skill(1, 92, 6_500_000));
		skills.put(HiscoreSkill.ZULRAH, skill(1, 1234, -1)); // boss KC lives in level
		PlayerSnapshot snap = snapshot(skills);

		assertEquals(6_500_000L, snap.crownValue(HiscoreSkill.ATTACK)); // XP
		assertEquals(92, snap.displayValue(HiscoreSkill.ATTACK));        // level
		assertEquals(1234L, snap.crownValue(HiscoreSkill.ZULRAH));       // KC
		assertEquals(1234, snap.displayValue(HiscoreSkill.ZULRAH));      // KC
	}

	@Test
	public void unrankedReturnsMinusOne()
	{
		PlayerSnapshot snap = snapshot(new EnumMap<>(HiscoreSkill.class));
		assertEquals(-1L, snap.crownValue(HiscoreSkill.ATTACK));
		assertEquals(-1, snap.displayValue(HiscoreSkill.ATTACK));
	}

	@Test
	public void overallAndTotalLevel()
	{
		Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
		skills.put(HiscoreSkill.OVERALL, skill(1, 1500, 200_000_000));
		PlayerSnapshot snap = snapshot(skills);

		assertEquals(200_000_000L, snap.overallXp());
		assertEquals(1500, snap.totalLevel());
	}

	@Test
	public void overallAbsentReturnsMinusOne()
	{
		PlayerSnapshot snap = snapshot(new EnumMap<>(HiscoreSkill.class));
		assertEquals(-1L, snap.overallXp());
		assertEquals(-1, snap.totalLevel());
	}

	@Test
	public void totalBossKcSumsRankedBosses()
	{
		Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
		skills.put(HiscoreSkill.ZULRAH, skill(1, 500, -1));
		skills.put(HiscoreSkill.VORKATH, skill(1, 300, -1));
		skills.put(HiscoreSkill.ATTACK, skill(1, 99, 13_000_000)); // not a boss, ignored
		PlayerSnapshot snap = snapshot(skills);

		assertEquals(800, snap.totalBossKc());
	}

	@Test
	public void totalBossKcMinusOneWhenNoBosses()
	{
		Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
		skills.put(HiscoreSkill.ATTACK, skill(1, 99, 13_000_000));
		assertEquals(-1, snapshot(skills).totalBossKc());
	}

	@Test
	public void nullResultIsAllMinusOne()
	{
		PlayerSnapshot snap = new PlayerSnapshot("Player", null);
		assertEquals(-1L, snap.crownValue(HiscoreSkill.ATTACK));
		assertEquals(-1, snap.displayValue(HiscoreSkill.ATTACK));
		assertEquals(-1L, snap.overallXp());
		assertEquals(-1, snap.totalLevel());
		assertEquals(-1, snap.totalBossKc());
	}
}
