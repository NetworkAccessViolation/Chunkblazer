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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompletedTargetsTest
{
	private static final Map<String, Integer> TARGETS =
		CompletedTargets.parseTargets("logs:143:143,shrimp:1:1,ore:0:20,bad:x:y,old:5");

	@Test
	void parse()
	{
		assertEquals(143, TARGETS.get("logs"));
		assertEquals(20, TARGETS.get("ore"));
		assertEquals(1, TARGETS.get("shrimp"));
		assertTrue(!TARGETS.containsKey("bad") && !TARGETS.containsKey("old"));
	}

	@Test
	void sendsOnlyAboveOne()
	{
		CompletedTargets.Batch b = CompletedTargets.next(Arrays.asList("logs", "shrimp", "ore", "none"), 0, TARGETS);
		assertEquals(Map.of("logs", 143, "ore", 20), b.targets);
		assertEquals(4, b.sentThrough);
	}

	@Test
	void resumesAfterIndex()
	{
		CompletedTargets.Batch b = CompletedTargets.next(Arrays.asList("logs", "shrimp", "ore"), 1, TARGETS);
		assertEquals(Map.of("ore", 20), b.targets);
		assertEquals(3, b.sentThrough);
	}

	@Test
	void backfillsInBatches()
	{
		List<String> ids = new ArrayList<>();
		Map<String, Integer> targets = new HashMap<>();
		for (int i = 0; i < 450; i++)
		{
			ids.add("t" + i);
			targets.put("t" + i, 10);
		}
		CompletedTargets.Batch first = CompletedTargets.next(ids, 0, targets);
		assertEquals(CompletedTargets.BATCH, first.targets.size());
		assertEquals(200, first.sentThrough);
		CompletedTargets.Batch last = CompletedTargets.next(ids, 400, targets);
		assertEquals(50, last.targets.size());
		assertEquals(450, last.sentThrough);
	}

	@Test
	void restartsWhenListShrank()
	{
		CompletedTargets.Batch b = CompletedTargets.next(Arrays.asList("logs"), 9, TARGETS);
		assertEquals(Map.of("logs", 143), b.targets);
		assertEquals(1, b.sentThrough);
	}
}
