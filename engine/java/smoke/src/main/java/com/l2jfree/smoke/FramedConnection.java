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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * A TCP connection that speaks the Lineage II framing: a 2-byte little-endian length that includes
 * the two bytes themselves, followed by the payload. It knows nothing about encryption.
 */
final class FramedConnection implements AutoCloseable
{
	private static final int CONNECT_TIMEOUT_MILLIS = 5000;
	private static final long RETRY_PAUSE_MILLIS = 1000L;

	private final Socket _socket;
	private final InputStream _in;
	private final OutputStream _out;
	private final Deadline _deadline;
	/** Header bytes already taken from the socket for a frame that is not complete yet. */
	private final byte[] _pendingHeader = new byte[2];
	private int _pendingHeaderGot;
	/** Body bytes already taken for that same frame. Null until the header is complete. */
	private byte[] _pendingBody;
	private int _pendingBodyGot;

	private FramedConnection(Socket socket, Deadline deadline) throws IOException
	{
		_socket = socket;
		_in = socket.getInputStream();
		_out = socket.getOutputStream();
		_deadline = deadline;
	}

	/**
	 * Connects to a server.
	 *
	 * @param label what the server is, for messages
	 * @param retry keep trying every second until the time limit when the connection is refused
	 * @param attemptMillis the longest time of one connection attempt
	 */
	static FramedConnection open(String label, String host, int port, Deadline deadline, boolean retry,
			int attemptMillis) throws SmokeException
	{
		String last = null;
		while (true)
		{
			long left = deadline.remainingMillis();
			if (left <= 0L)
			{
				throw new SmokeException(SmokeException.Kind.TIMEOUT, "could not connect to the " + label + " at "
						+ host + ":" + port + " within " + deadline.seconds() + " seconds"
						+ (last == null ? "" : " (" + last + ")"));
			}

			Socket socket = new Socket();
			try
			{
				socket.connect(new InetSocketAddress(host, port), (int)Math.min(left, attemptMillis));
				socket.setTcpNoDelay(true);
				return new FramedConnection(socket, deadline);
			}
			catch (IOException e)
			{
				closeQuietly(socket);
				last = e.toString();
				if (!retry)
				{
					throw new SmokeException(SmokeException.Kind.CLOSED, "cannot connect to the " + label + " at "
							+ host + ":" + port + ": " + e, e);
				}

				deadline.sleep(RETRY_PAUSE_MILLIS);
			}
		}
	}

	static FramedConnection open(String label, String host, int port, Deadline deadline) throws SmokeException
	{
		return open(label, host, port, deadline, true, CONNECT_TIMEOUT_MILLIS);
	}

	/**
	 * Sends one frame.
	 *
	 * @param payload the payload, already encrypted when the protocol wants it
	 */
	void send(byte[] payload) throws SmokeException
	{
		int length = payload.length + 2;
		if (length > 0xffff)
			throw new SmokeException("a packet of " + payload.length + " bytes does not fit into a frame");

		byte[] frame = new byte[length];
		frame[0] = (byte)length;
		frame[1] = (byte)(length >>> 8);
		System.arraycopy(payload, 0, frame, 2, payload.length);

		try
		{
			_out.write(frame);
			_out.flush();
		}
		catch (IOException e)
		{
			throw new SmokeException(SmokeException.Kind.CLOSED, "cannot send to the server: " + e, e);
		}
	}

	/**
	 * Reads one frame.
	 * <p>
	 * Bytes already taken from the socket stay on this connection when the wait ends first. The next
	 * call continues the same frame. {@link SmokeException.Kind#TIMEOUT} means a complete frame has
	 * not arrived; the bytes already read are not discarded.
	 *
	 * @param maxWaitMillis the longest time to wait for the whole frame; the time limit of the run
	 *            applies as well
	 * @return the payload, as it came from the wire
	 */
	byte[] receive(long maxWaitMillis) throws SmokeException
	{
		long wait = Math.min(maxWaitMillis, _deadline.remainingMillis());
		if (wait <= 0L)
		{
			throw new SmokeException(SmokeException.Kind.TIMEOUT, "the time limit of " + _deadline.seconds()
					+ " seconds is over");
		}

		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(wait);
		while (_pendingHeaderGot < 2)
		{
			int n = readOnce(_pendingHeader, _pendingHeaderGot, 2 - _pendingHeaderGot, until, _pendingHeaderGot == 0);
			_pendingHeaderGot += n;
		}
		if (_pendingBody == null)
		{
			int length = (_pendingHeader[0] & 0xff) | ((_pendingHeader[1] & 0xff) << 8);
			if (length < 2)
				throw new SmokeException("the server sent a frame with the invalid length " + length);

			_pendingBody = new byte[length - 2];
			_pendingBodyGot = 0;
		}
		while (_pendingBodyGot < _pendingBody.length)
		{
			int n = readOnce(_pendingBody, _pendingBodyGot, _pendingBody.length - _pendingBodyGot, until, false);
			_pendingBodyGot += n;
		}
		byte[] payload = _pendingBody;
		_pendingHeaderGot = 0;
		_pendingBody = null;
		_pendingBodyGot = 0;
		return payload;
	}

	/**
	 * @param atFrameStart true only when nothing of this frame has been read yet
	 * @return how many bytes were read; at least one
	 */
	private int readOnce(byte[] data, int offset, int count, long untilNanos, boolean atFrameStart)
			throws SmokeException
	{
		long left = TimeUnit.NANOSECONDS.toMillis(untilNanos - System.nanoTime());
		if (left <= 0L)
			throw timeout();

		try
		{
			_socket.setSoTimeout((int)Math.min(left, Integer.MAX_VALUE));
			int n = _in.read(data, offset, count);
			if (n < 0)
			{
				throw new SmokeException(SmokeException.Kind.CLOSED,
						offset == 0 && atFrameStart ? "the server closed the connection"
								: "the server closed the connection in the middle of a packet");
			}
			if (n == 0)
				throw timeout();

			return n;
		}
		catch (SocketTimeoutException e)
		{
			throw timeout();
		}
		catch (IOException e)
		{
			throw new SmokeException(SmokeException.Kind.CLOSED, "the connection to the server broke: " + e, e);
		}
	}

	private SmokeException timeout()
	{
		if (_deadline.expired())
		{
			return new SmokeException(SmokeException.Kind.TIMEOUT, "the time limit of " + _deadline.seconds()
					+ " seconds is over");
		}
		return new SmokeException(SmokeException.Kind.TIMEOUT, "the server did not send a packet in time");
	}

	@Override
	public void close()
	{
		closeQuietly(_socket);
	}

	private static void closeQuietly(Socket socket)
	{
		try
		{
			socket.close();
		}
		catch (IOException e)
		{
			// nothing to do: the socket is gone either way
		}
	}
}
