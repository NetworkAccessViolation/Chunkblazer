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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.ResponseBody;

/**
 * Helpers for reading HTTP response bodies from the ChunkBlazer server safely.
 *
 * <p>The plugin fetches its task catalog and audio assets from a remote server.
 * A hostile or compromised server (or anything able to spoof it) must not be able
 * to exhaust the client's memory with an oversized response, so bodies are read
 * through a hard ceiling here. This complements the on-disk protections in the
 * stores (sandboxed {@link net.runelite.client.util.Filepath} writes, sha256
 * content verification, and an LRU disk cap): together they bound where a download
 * can be written, whether it can be trusted, how much disk it can use, and — here —
 * how much memory it can consume.
 */
final class HttpBodies
{
	private HttpBodies()
	{
	}

	/**
	 * Read a response body fully into memory, but never more than {@code maxBytes}.
	 *
	 * <p>The advertised {@code Content-Length} is used only as a cheap early-out and
	 * is never trusted: a hostile server can lie about it or omit it entirely, so the
	 * ceiling is also enforced byte-by-byte during the read. Exceeding it throws an
	 * {@link IOException}, which every caller already treats like any other failed
	 * fetch (keep the last-good cache), so an oversized payload is dropped rather than
	 * parsed or persisted.
	 *
	 * @param body     the response body, or null
	 * @param maxBytes the hard ceiling on bytes read
	 * @return the full body, guaranteed no larger than {@code maxBytes}
	 * @throws IOException if the body is null, advertises a length over the ceiling,
	 *                     or streams more than the ceiling
	 */
	static byte[] readBounded(ResponseBody body, long maxBytes) throws IOException
	{
		if (body == null)
		{
			throw new IOException("no response body");
		}
		long advertised = body.contentLength();
		if (advertised > maxBytes)
		{
			throw new IOException("response too large: " + advertised + " > " + maxBytes);
		}
		try (InputStream in = body.byteStream())
		{
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			long total = 0;
			int n;
			while ((n = in.read(chunk)) != -1)
			{
				total += n;
				if (total > maxBytes)
				{
					throw new IOException("response exceeded " + maxBytes + " bytes");
				}
				buf.write(chunk, 0, n);
			}
			return buf.toByteArray();
		}
	}
}
