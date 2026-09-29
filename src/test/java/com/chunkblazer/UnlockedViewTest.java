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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The cached unlocked-region view the overlays read every frame. */
@ExtendWith(MockitoExtension.class)
class UnlockedViewTest
{
	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ConfigManager configManager;

	private ChunkBlazerPlugin plugin;
	private Map<String, String> writes;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set("config", config);
		set("configManager", configManager);
		writes = RsProfileTestSupport.install(configManager, config);
		writes.put("unlockedChunks", "12850, 12851");
	}

	@Test
	void cachedUntilChanged()
	{
		Set<String> first = plugin.unlockedRegionIdsView();
		assertEquals(Set.of("12850", "12851"), first);
		assertSame(first, plugin.unlockedRegionIdsView());

		writes.put("unlockedChunks", "12850,12851,12852");
		assertTrue(plugin.unlockedRegionIdsView().contains("12852"));
		assertTrue(plugin.isRegionUnlocked(12852));
	}

	@Test
	void viewIsReadOnlyCopyIsNot()
	{
		assertThrows(UnsupportedOperationException.class, () -> plugin.unlockedRegionIdsView().add("1"));
		Set<String> copy = plugin.getUnlockedRegionIds();
		copy.add("1");
		assertFalse(plugin.unlockedRegionIdsView().contains("1"));
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
