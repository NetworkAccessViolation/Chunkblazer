/*
 * Copyright (c) 2026, Vani-Lab
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class OfflineTrackableTest
{
	private static NuzlockeTask task(String type, String category)
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setCompletionType(type);
		task.setCategory(category);
		return task;
	}

	@Test
	void savedStateTasksWorkOffline()
	{
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("QUEST_CHECK", "Quest")));
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("SKILL_THRESHOLD", "Progression")));
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("COMBAT_ACHIEVEMENT", "Combat")));
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("VARBIT_CHECK", "Unlock")));
		// Counts if it's still worn when you log in on RuneLite.
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("EQUIP", "Combat")));
		// Plain obtain tasks count what you hold (inventory + bank + worn), pets included.
		assertTrue(TaskBrowserOverlay.isOfflineTrackable(task("OBTAIN", "Obtain")));
	}

	@Test
	void liveEventTasksDoNot()
	{
		assertFalse(TaskBrowserOverlay.isOfflineTrackable(task("MINING", "Mining")));
		assertFalse(TaskBrowserOverlay.isOfflineTrackable(task("NPC_Kill", "Combat")));
		// Skilling: an item only counts if it arrives with a matching XP drop, live.
		assertFalse(TaskBrowserOverlay.isOfflineTrackable(task("FISHING", "Fishing")));
		// Activating a prayer is momentary: the varbit has reset by the next login.
		assertFalse(TaskBrowserOverlay.isOfflineTrackable(task("VARBIT_CHECK", "Prayer")));
	}
}
