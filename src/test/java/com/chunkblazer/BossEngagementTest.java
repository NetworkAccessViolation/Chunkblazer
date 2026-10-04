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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When a world boss's death counts as the local player's kill. */
class BossEngagementTest
{
	private final BossEngagement engagement = new BossEngagement();

	@Test
	void ownDamageCounts()
	{
		engagement.onHitsplat("scurrius", true, 7);
		assertTrue(engagement.onBossDeath("scurrius"));
	}

	/** The report: Scurrius healing in front of you isn't your participation. */
	@Test
	void healingSplatDoesNotCount()
	{
		engagement.onHitsplat("scurrius", false, 20);
		assertFalse(engagement.onBossDeath("scurrius"));
	}

	@Test
	void blockDoesNotCount()
	{
		engagement.onHitsplat("scurrius", true, 0);
		assertFalse(engagement.onBossDeath("scurrius"));
	}

	@Test
	void dyingEndsTheFight()
	{
		engagement.onHitsplat("scurrius", true, 7);
		engagement.reset();
		assertFalse(engagement.onBossDeath("scurrius"));
	}

	@Test
	void oneFightOneKill()
	{
		engagement.onHitsplat("scurrius", true, 7);
		assertTrue(engagement.onBossDeath("scurrius"));
		assertFalse(engagement.onBossDeath("scurrius"), "the next kill needs fresh damage");
	}

	@Test
	void otherBossUnaffected()
	{
		engagement.onHitsplat("obor", true, 5);
		assertFalse(engagement.onBossDeath("scurrius"));
		assertTrue(engagement.onBossDeath("obor"));
	}
}
