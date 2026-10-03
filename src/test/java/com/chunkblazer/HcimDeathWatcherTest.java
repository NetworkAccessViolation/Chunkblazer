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

/** The account-type varbit sequences a real client produces, and which of them is a death. */
class HcimDeathWatcherTest
{
	private final HcimDeathWatcher watcher = new HcimDeathWatcher();

	@Test
	void loginIsNotDeath()
	{
		assertFalse(watcher.onAccountType(0));
		assertFalse(watcher.onAccountType(3));
	}

	@Test
	void hardcoreDropIsDeathOnce()
	{
		watcher.onAccountType(3);
		assertTrue(watcher.onAccountType(1));
		assertFalse(watcher.onAccountType(1));
	}

	@Test
	void worldHopIsNotDeath()
	{
		watcher.onAccountType(3);
		assertFalse(watcher.onAccountType(0));
		assertFalse(watcher.onAccountType(3));
	}

	@Test
	void otherAccountAfterLogout()
	{
		watcher.onAccountType(3);
		watcher.reset();
		assertFalse(watcher.onAccountType(1));
	}
}
