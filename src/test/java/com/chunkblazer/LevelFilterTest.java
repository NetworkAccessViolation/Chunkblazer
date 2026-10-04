/*
 * Copyright (c) 2026, btwinnn
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.chunkblazer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.junit.jupiter.api.Test;

/** The "Only tasks I have the level for" filter. */
class LevelFilterTest
{
	private final ChunkBlazerPlugin plugin = new ChunkBlazerPlugin();

	@Test
	void hidesOnlyWhenLevelTooLow()
	{
		NuzlockeTask coal = task("Mining", 30);
		assertTrue(plugin.meetsLevelRequirement(coal), "levels not loaded yet: don't hide");

		plugin.onStatChanged(new StatChanged(Skill.MINING, 0, 20, 20));
		assertFalse(plugin.meetsLevelRequirement(coal));

		plugin.onStatChanged(new StatChanged(Skill.MINING, 0, 30, 30));
		assertTrue(plugin.meetsLevelRequirement(coal));
	}

	@Test
	void nonSkillTasksAlwaysShown()
	{
		plugin.onStatChanged(new StatChanged(Skill.ATTACK, 0, 1, 1));
		assertTrue(plugin.meetsLevelRequirement(task("Combat", 90)));
		assertTrue(plugin.meetsLevelRequirement(task("Quest", 50)));
	}

	@Test
	void categoryNames()
	{
		assertEquals(Skill.RUNECRAFT, ChunkBlazerPlugin.skillForCategory("Runecrafting"));
		assertEquals(Skill.SLAYER, ChunkBlazerPlugin.skillForCategory("Slayer"));
		assertNull(ChunkBlazerPlugin.skillForCategory("Obtain"));
	}

	private static NuzlockeTask task(String category, int level)
	{
		NuzlockeTask t = new NuzlockeTask();
		t.setCategory(category);
		t.setLevel(level);
		return t;
	}
}
