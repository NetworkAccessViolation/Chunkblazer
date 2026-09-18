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

package com.chunkblazer.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.jupiter.api.Test;

class HttpBodiesTest
{
	private static final long CAP = 4096;

	/**
	 * A ResponseBody backed by an in-memory buffer whose reported Content-Length is
	 * decoupled from the actual byte count, so a lying/omitted length can be simulated.
	 * A reported length of -1 means "unknown", exactly as OkHttp reports a chunked
	 * or length-less response.
	 */
	private static ResponseBody body(long reportedLength, byte[] data)
	{
		final Buffer buffer = new Buffer();
		buffer.write(data);
		return new ResponseBody()
		{
			@Override
			public MediaType contentType()
			{
				return null;
			}

			@Override
			public long contentLength()
			{
				return reportedLength;
			}

			@Override
			public BufferedSource source()
			{
				return buffer;
			}
		};
	}

	private static byte[] bytes(int n)
	{
		byte[] b = new byte[n];
		for (int i = 0; i < n; i++)
		{
			b[i] = (byte) (i & 0xFF);
		}
		return b;
	}

	@Test
	void underCapReturnsExactBytes() throws IOException
	{
		byte[] data = "hello chunkblazer".getBytes(StandardCharsets.UTF_8);
		byte[] out = HttpBodies.readBounded(body(data.length, data), CAP);
		assertArrayEquals(data, out);
	}

	@Test
	void exactlyAtCapIsAllowed() throws IOException
	{
		byte[] data = bytes((int) CAP);
		byte[] out = HttpBodies.readBounded(body(data.length, data), CAP);
		assertEquals(CAP, out.length);
		assertArrayEquals(data, out);
	}

	@Test
	void honestOversizeRejectedByContentLength()
	{
		byte[] data = bytes((int) CAP + 1);
		// Server honestly advertises an over-cap length; rejected before any read.
		assertThrows(IOException.class, () -> HttpBodies.readBounded(body(data.length, data), CAP));
	}

	@Test
	void lyingContentLengthCaughtDuringRead()
	{
		byte[] data = bytes((int) CAP * 4);
		// Server lies: claims a tiny body but streams far more. The early-out passes,
		// so the byte-by-byte ceiling during the read is what must catch it.
		assertThrows(IOException.class, () -> HttpBodies.readBounded(body(10, data), CAP));
	}

	@Test
	void unknownContentLengthUnderCapIsRead() throws IOException
	{
		byte[] data = bytes(1000);
		// -1 == length unknown (chunked); must still read and succeed under the cap.
		byte[] out = HttpBodies.readBounded(body(-1, data), CAP);
		assertArrayEquals(data, out);
	}

	@Test
	void unknownContentLengthOverCapCaughtDuringRead()
	{
		byte[] data = bytes((int) CAP + 1);
		assertThrows(IOException.class, () -> HttpBodies.readBounded(body(-1, data), CAP));
	}

	@Test
	void nullBodyThrows()
	{
		assertThrows(IOException.class, () -> HttpBodies.readBounded(null, CAP));
	}
}
