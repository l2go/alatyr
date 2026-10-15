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
package com.l2jfree.network;

import java.nio.ByteOrder;

/**
 * The few settings of the network core. Every value has a default, so a server that sets nothing runs with these.
 * <p>
 * The setters validate their argument and throw {@link IllegalArgumentException} for a value that cannot work.
 */
public final class NetworkConfig
{
	/** The size of the length header of a frame, in bytes. The length counts the header itself. */
	public static final int HEADER_SIZE = 2;
	
	/** The largest frame the 2-byte length can express. */
	public static final int MAX_FRAME_SIZE_LIMIT = 0xFFFF;
	
	/** The default of the largest frame, header included: the largest the length field can express. */
	public static final int DEFAULT_MAX_FRAME_SIZE = MAX_FRAME_SIZE_LIMIT;
	
	/** The default of the outbound buffer size above which a connection is closed: 1 MiB. */
	public static final int DEFAULT_WRITE_HIGH_WATER_MARK = 1024 * 1024;
	
	/** The most I/O threads the automatic choice picks. */
	public static final int MAX_AUTOMATIC_IO_THREADS = 4;
	
	/** The default limits on the connections accepted from one address: warn, refuse, and the seconds they count over. */
	public static final int[] DEFAULT_ACCEPT_SHORT = { 10, 20, 10 };
	
	/** The default limits over a longer period. */
	public static final int[] DEFAULT_ACCEPT_LONG = { 30, 60, 60 };
	
	private int _ioThreads = 0; // 0: automatic
	private int _maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
	private int _writeHighWaterMark = DEFAULT_WRITE_HIGH_WATER_MARK;
	private ByteOrder _byteOrder = ByteOrder.LITTLE_ENDIAN;
	private FloodManager.FloodFilter _acceptShort = filter(DEFAULT_ACCEPT_SHORT);
	private FloodManager.FloodFilter _acceptLong = filter(DEFAULT_ACCEPT_LONG);
	
	public NetworkConfig()
	{
	}
	
	/**
	 * @return the automatic number of I/O threads: half of the processors, at least 1, at most
	 *         {@value #MAX_AUTOMATIC_IO_THREADS}
	 */
	public static int defaultIoThreads()
	{
		return Math.min(MAX_AUTOMATIC_IO_THREADS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
	}
	
	/**
	 * @param ioThreads the number of I/O threads, or 0 for {@link #defaultIoThreads()}
	 */
	public NetworkConfig setIoThreads(int ioThreads)
	{
		if (ioThreads < 0 || ioThreads > 256)
			throw new IllegalArgumentException("ioThreads must be 0 (automatic) or in 1..256: " + ioThreads);
		
		_ioThreads = ioThreads;
		return this;
	}
	
	/**
	 * @return the number of I/O threads, never 0
	 */
	public int getIoThreads()
	{
		return _ioThreads == 0 ? defaultIoThreads() : _ioThreads;
	}
	
	/**
	 * @param maxFrameSize the largest accepted frame including its 2-byte header, 3 to 65535
	 */
	public NetworkConfig setMaxFrameSize(int maxFrameSize)
	{
		if (maxFrameSize <= HEADER_SIZE || maxFrameSize > MAX_FRAME_SIZE_LIMIT)
			throw new IllegalArgumentException("maxFrameSize must be in " + (HEADER_SIZE + 1) + ".." + MAX_FRAME_SIZE_LIMIT
					+ ": " + maxFrameSize);
		
		_maxFrameSize = maxFrameSize;
		return this;
	}
	
	public int getMaxFrameSize()
	{
		return _maxFrameSize;
	}
	
	/**
	 * @param writeHighWaterMark the number of outbound bytes waiting for a slow client above which the connection is
	 *            closed as a forced disconnection, at least 1024
	 */
	public NetworkConfig setWriteHighWaterMark(int writeHighWaterMark)
	{
		if (writeHighWaterMark < 1024)
			throw new IllegalArgumentException("writeHighWaterMark must be at least 1024: " + writeHighWaterMark);
		
		_writeHighWaterMark = writeHighWaterMark;
		return this;
	}
	
	public int getWriteHighWaterMark()
	{
		return _writeHighWaterMark;
	}
	
	/**
	 * @param byteOrder the byte order of the buffers the packets read from and write to, little-endian by default
	 */
	public NetworkConfig setByteOrder(ByteOrder byteOrder)
	{
		if (byteOrder == null)
			throw new IllegalArgumentException("byteOrder");
		
		_byteOrder = byteOrder;
		return this;
	}
	
	public ByteOrder getByteOrder()
	{
		return _byteOrder;
	}
	
	/**
	 * Sets how many connections one address may open. The server logs a warning above the first number and refuses
	 * above the second, counted over the seconds named, in a short and in a long period. All the players behind one
	 * address (a gateway of Docker Desktop or Colima, a network address translator) count as one address, so a server
	 * that many of them reach through such an address needs higher numbers.
	 * 
	 * @param shortWarn connections in the short period above which the server warns, at least 1
	 * @param shortReject connections in the short period above which the server refuses, at least {@code shortWarn}
	 * @param shortSeconds the length of the short period, 1 to 3600 seconds
	 * @param longWarn the same for the long period
	 * @param longReject the same for the long period
	 * @param longSeconds the same for the long period
	 */
	public NetworkConfig setAcceptLimits(int shortWarn, int shortReject, int shortSeconds, int longWarn, int longReject,
			int longSeconds)
	{
		validateLimits("short", shortWarn, shortReject, shortSeconds);
		validateLimits("long", longWarn, longReject, longSeconds);
		
		_acceptShort = filter(new int[] { shortWarn, shortReject, shortSeconds });
		_acceptLong = filter(new int[] { longWarn, longReject, longSeconds });
		return this;
	}
	
	FloodManager.FloodFilter getAcceptShort()
	{
		return _acceptShort;
	}
	
	FloodManager.FloodFilter getAcceptLong()
	{
		return _acceptLong;
	}
	
	private static FloodManager.FloodFilter filter(int[] limits)
	{
		return new FloodManager.FloodFilter(limits[0], limits[1], limits[2]);
	}
	
	private static void validateLimits(String period, int warn, int reject, int seconds)
	{
		if (warn < 1 || reject < warn)
			throw new IllegalArgumentException("the " + period + " accept limits must satisfy 1 <= warn <= reject: " + warn
					+ ", " + reject);
		
		if (seconds < 1 || seconds > 3600)
			throw new IllegalArgumentException("the " + period + " accept period must be 1 to 3600 seconds: " + seconds);
	}
}
