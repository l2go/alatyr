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

import java.util.Locale;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;

/**
 * The transport under the network core: {@code io_uring}, {@code epoll}, or {@code nio}.
 * <p>
 * {@link #select()} reads the system property {@value #PROPERTY}:
 * <ul>
 * <li>{@code auto} (the default): the first available of {@code io_uring}, {@code epoll}, {@code nio}. The two native
 * transports exist on Linux only, and {@code io_uring} is also unavailable when the container profile blocks its
 * system calls.</li>
 * <li>{@code io_uring}, {@code epoll}, {@code nio}: exactly that transport. A native transport that is not available
 * makes the selection fail with a message that names it and the reason, so a misconfigured deployment does not run
 * silently on another transport.</li>
 * </ul>
 */
public final class Transport
{
	private static final Logger _log = LoggerFactory.getLogger(Transport.class);
	
	/** The system property that chooses the transport. */
	public static final String PROPERTY = "l2jfree.network.transport";
	
	/** The transports, in the order {@code auto} tries them. */
	public static enum Kind
	{
		IO_URING("io_uring"),
		EPOLL("epoll"),
		NIO("nio");
		
		private final String _name;
		
		private Kind(String name)
		{
			_name = name;
		}
		
		public String getName()
		{
			return _name;
		}
	}
	
	private final Kind _kind;
	
	private Transport(Kind kind)
	{
		_kind = kind;
	}
	
	/**
	 * Chooses the transport by the system property {@value #PROPERTY}.
	 * 
	 * @throws IllegalStateException when a forced native transport is not available
	 * @throws IllegalArgumentException when the property has a value that is not a transport
	 */
	public static Transport select()
	{
		return select(System.getProperty(PROPERTY));
	}
	
	/**
	 * Chooses the transport for a requested mode.
	 * 
	 * @param requested {@code auto}, {@code io_uring}, {@code epoll}, or {@code nio}; null or empty means {@code auto}
	 * @throws IllegalStateException when a forced native transport is not available
	 * @throws IllegalArgumentException when the mode is not a transport
	 */
	public static Transport select(String requested)
	{
		final String mode = requested == null || requested.trim().isEmpty() ? "auto" : requested.trim().toLowerCase(Locale.ROOT);
		
		switch (mode)
		{
			case "auto":
			{
				final Transport transport;
				
				if (IoUring.isAvailable())
					transport = new Transport(Kind.IO_URING);
				else
				{
					_log.info("Transport io_uring is not available: " + describe(IoUring.unavailabilityCause()));
					
					if (Epoll.isAvailable())
						transport = new Transport(Kind.EPOLL);
					else
					{
						_log.info("Transport epoll is not available: " + describe(Epoll.unavailabilityCause()));
						transport = new Transport(Kind.NIO);
					}
				}
				
				_log.info("Network transport: " + transport.getName() + " (" + PROPERTY + "=auto)");
				return transport;
			}
			case "io_uring":
			{
				if (!IoUring.isAvailable())
					throw new IllegalStateException("The network transport io_uring was requested (" + PROPERTY
							+ "=io_uring) but is not available: " + describe(IoUring.unavailabilityCause()));
				
				_log.info("Network transport: io_uring (" + PROPERTY + "=io_uring)");
				return new Transport(Kind.IO_URING);
			}
			case "epoll":
			{
				if (!Epoll.isAvailable())
					throw new IllegalStateException("The network transport epoll was requested (" + PROPERTY
							+ "=epoll) but is not available: " + describe(Epoll.unavailabilityCause()));
				
				_log.info("Network transport: epoll (" + PROPERTY + "=epoll)");
				return new Transport(Kind.EPOLL);
			}
			case "nio":
			{
				_log.info("Network transport: nio (" + PROPERTY + "=nio)");
				return new Transport(Kind.NIO);
			}
			default:
				throw new IllegalArgumentException("Unknown network transport '" + requested + "' in " + PROPERTY
						+ ", expected auto, io_uring, epoll, or nio");
		}
	}
	
	private static String describe(Throwable cause)
	{
		if (cause == null)
			return "no reason reported";
		
		return String.valueOf(cause);
	}
	
	public Kind getKind()
	{
		return _kind;
	}
	
	/**
	 * @return {@code io_uring}, {@code epoll}, or {@code nio}
	 */
	public String getName()
	{
		return _kind.getName();
	}
	
	/**
	 * @return the server socket channel class of this transport
	 */
	public Class<? extends ServerSocketChannel> serverChannelClass()
	{
		switch (_kind)
		{
			case IO_URING:
				return IoUringServerSocketChannel.class;
			case EPOLL:
				return EpollServerSocketChannel.class;
			default:
				return NioServerSocketChannel.class;
		}
	}
	
	/**
	 * Creates the event loop group of this transport. The threads start when the first channel registers.
	 * 
	 * @param threads the number of event loop threads
	 * @param threadFactory creates the threads
	 */
	public EventLoopGroup newEventLoopGroup(int threads, ThreadFactory threadFactory)
	{
		final IoHandlerFactory factory;
		
		switch (_kind)
		{
			case IO_URING:
				factory = IoUringIoHandler.newFactory();
				break;
			case EPOLL:
				factory = EpollIoHandler.newFactory();
				break;
			default:
				factory = NioIoHandler.newFactory();
				break;
		}
		
		return new MultiThreadIoEventLoopGroup(threads, threadFactory, factory);
	}
	
	@Override
	public String toString()
	{
		return getName();
	}
}
