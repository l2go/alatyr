/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class FramedConnectionTest
{
	@Test
	void aSplitFrameIsFinishedByTheNextReceive() throws Exception
	{
		byte[] first = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		byte[] second = { 10, 11, 12 };
		byte[] wire = concatenate(frame(first), frame(second));
		try (SplitServer server = SplitServer.open(wire, frame(first).length / 2))
		{
			try (FramedConnection connection = connect(server.port()))
			{
				assertThat(server.awaitPartial()).isTrue();
				Throwable thrown = catchThrowable(() -> connection.receive(50));
				assertThat(thrown).isInstanceOf(SmokeException.class);
				assertThat(((SmokeException)thrown).kind()).isEqualTo(SmokeException.Kind.TIMEOUT);
				assertThat(connection.receive(2_000)).containsExactly(first);
				assertThat(connection.receive(2_000)).containsExactly(second);
			}
		}
	}

	@Test
	void aSplitFrameStillDecryptsWhenTheCipherIsOn() throws Exception
	{
		byte[] first = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		byte[] second = { 10, 11, 12 };
		byte[] key = GameCipher.fullKey(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
		GameCipher writer = new GameCipher();
		writer.setKey(key);
		writer.enable();
		byte[] wireFirst = first.clone();
		byte[] wireSecond = second.clone();
		writer.encrypt(wireFirst, 0, wireFirst.length);
		writer.encrypt(wireSecond, 0, wireSecond.length);
		byte[] wire = concatenate(frame(wireFirst), frame(wireSecond));
		try (SplitServer server = SplitServer.open(wire, frame(wireFirst).length / 2))
		{
			try (GameConnection connection = new GameConnection(connect(server.port())))
			{
				connection.cipher().setKey(key);
				connection.cipher().enable();
				assertThat(server.awaitPartial()).isTrue();
				Throwable thrown = catchThrowable(() -> connection.receive(50));
				assertThat(thrown).isInstanceOf(SmokeException.class);
				assertThat(((SmokeException)thrown).kind()).isEqualTo(SmokeException.Kind.TIMEOUT);
				assertThat(connection.receive(2_000)).containsExactly(first);
				assertThat(connection.receive(2_000)).containsExactly(second);
			}
		}
	}

	private static FramedConnection connect(int port) throws SmokeException
	{
		return FramedConnection.open("world", "127.0.0.1", port, Deadline.after(15), false, 2_000);
	}

	private static byte[] frame(byte[] payload)
	{
		byte[] frame = new byte[payload.length + 2];
		frame[0] = (byte)frame.length;
		frame[1] = (byte)(frame.length >>> 8);
		System.arraycopy(payload, 0, frame, 2, payload.length);
		return frame;
	}

	private static byte[] concatenate(byte[] left, byte[] right)
	{
		byte[] both = new byte[left.length + right.length];
		System.arraycopy(left, 0, both, 0, left.length);
		System.arraycopy(right, 0, both, left.length, right.length);
		return both;
	}

	/**
	 * Writes the first {@code splitAt} bytes, pauses longer than the client's wait, then writes the rest.
	 */
	private static final class SplitServer implements AutoCloseable
	{
		private final ServerSocket _listen;
		private final Thread _thread;
		private final CountDownLatch _partialSent = new CountDownLatch(1);
		private volatile Exception _failure;

		private SplitServer(ServerSocket listen, byte[] wire, int splitAt)
		{
			_listen = listen;
			_thread = new Thread(() -> serve(wire, splitAt), "split-frame");
			_thread.start();
		}

		private static SplitServer open(byte[] wire, int splitAt) throws Exception
		{
			return new SplitServer(new ServerSocket(0), wire, splitAt);
		}

		private int port()
		{
			return _listen.getLocalPort();
		}

		private boolean awaitPartial() throws InterruptedException
		{
			return _partialSent.await(2, TimeUnit.SECONDS);
		}

		private void serve(byte[] wire, int splitAt)
		{
			try (Socket socket = _listen.accept())
			{
				socket.setTcpNoDelay(true);
				OutputStream out = socket.getOutputStream();
				out.write(wire, 0, splitAt);
				out.flush();
				_partialSent.countDown();
				Thread.sleep(400);
				out.write(wire, splitAt, wire.length - splitAt);
				out.flush();
				Thread.sleep(2_000);
			}
			catch (Exception e)
			{
				_failure = e;
				_partialSent.countDown();
			}
		}

		@Override
		public void close() throws Exception
		{
			_listen.close();
			_thread.join(3_000);
			if (_failure != null)
				throw _failure;
		}
	}
}
