/*
 * Copyright (C) 2010, 2013 Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.diff.RawText;

/**
 * An InputStream that normalizes CRLF to LF.
 *
 * Existing single CR are not changed to LF, but retained as is.
 *
 * Optionally, a binary check on the first 8000 bytes is performed and in case
 * of binary files, canonicalization is turned off (for the complete file).
 * <p>
 * This is the former EolCanonicalizingInputStream with a new name in order to
 * have same naming for all LF / CRLF streams
 *
 * @since 4.3
 */
public class AutoLFInputStream extends InputStream {
	private final byte[] single = new byte[1];

	private final byte[] buf = new byte[8096];

	private final InputStream in;

	private int cnt;

	private int ptr;

	private boolean isBinary;

	private boolean detectBinary;

	private boolean abortIfBinary;

	/**
	 * A special exception thrown when {@link AutoLFInputStream} is told to
	 * throw an exception when attempting to read a binary file. The exception
	 * may be thrown at any stage during reading.
	 *
	 * @since 3.3
	 */
	public static class IsBinaryException extends IOException {
		private static final long serialVersionUID = 1L;

		IsBinaryException() {
			super();
		}
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @since 2.0
	 */
	public AutoLFInputStream(InputStream in, boolean detectBinary) {
		this(in, detectBinary, false);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @param abortIfBinary
	 *            throw an IOException if the file is binary
	 * @since 3.3
	 */
	public AutoLFInputStream(InputStream in, boolean detectBinary,
			boolean abortIfBinary) {
		this.in = in;
		this.detectBinary = detectBinary;
		this.abortIfBinary = abortIfBinary;
	}

	@Override
	public int read() throws IOException {
		final int read = read(single, 0, 1);
		return read == 1 ? single[0] & 0xff : -1;
	}

	@Override
	public int read(byte[] bs, final int off, final int len)
			throws IOException {
		if (len == 0)
			return 0;

		if (cnt == -1)
			return -1;

		int i = off;
		final int end = off + len;

		while (i < end) {
			if (ptr == cnt && !fillBuffer()) {
				break;
			}

			byte b = buf[ptr++];
			if (isBinary || b != '\r') {
				// Logic for binary files ends here
				bs[i++] = b;
				continue;
			}

			if (ptr == cnt && !fillBuffer()) {
				bs[i++] = '\r';
				break;
			}

			if (buf[ptr] == '\n') {
				bs[i++] = '\n';
				ptr++;
			} else
				bs[i++] = '\r';
		}

		return i == off ? -1 : i - off;
	}

	/**
	 * @return true if the stream has detected as a binary so far
	 * @since 3.3
	 */
	public boolean isBinary() {
		return isBinary;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	private boolean fillBuffer() throws IOException {
		cnt = in.read(buf, 0, buf.length);
		if (cnt < 1)
			return false;
		if (detectBinary) {
			isBinary = RawText.isBinary(buf, cnt);
			detectBinary = false;
			if (isBinary && abortIfBinary)
				throw new IsBinaryException();
		}
		ptr = 0;
		return true;
	}
}
