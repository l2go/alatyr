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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.network.FloodManager.ErrorMode;
import com.l2jfree.network.FloodManager.Result;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.concurrent.FastThreadLocalThread;

/**
 * A server of the Lineage II framing protocol on Netty: it accepts connections, cuts frames, decrypts and reads the
 * packets, and sends the packets back.
 * <p>
 * A subclass supplies the client ({@link #createClient(Channel)}) and what happens to a packet that was read
 * ({@link #executePacket(ReceivablePacket)}). The life cycle is {@link #openServerSocket(InetAddress, int)} (binds),
 * {@link #start()} (accepts), {@link #shutdown()}.
 * <p>
 * <b>Threads.</b> The I/O threads are daemon threads named {@code network-io-N}, their number comes from
 * {@link NetworkConfig#getIoThreads()}. A connection stays on one I/O thread for its whole life. The I/O thread
 * decrypts and reads the packets and calls {@link #executePacket(ReceivablePacket)}; running a packet is the job of
 * that method, and it must not block the I/O thread. Server packets are written, and encrypted, on the I/O thread as
 * well.
 * <p>
 * <b>Back-pressure.</b> A connection whose outbound buffer grows above the high-water mark of
 * {@link NetworkConfig#getWriteHighWaterMark()} is closed as a forced disconnection. Reads are not paused for an
 * unwritable channel: an unwritable channel is closed, so there is no state in which input would have to wait for output,
 * and the order of the packets cannot change.
 * 
 * @author KenM<BR>
 *         Parts of design based on networkcore from WoodenGil
 */
public abstract class NetworkServer<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
{
	protected static final NetworkLog _log = new NetworkLog(NetworkServer.class, 1000);
	
	private static final Logger _logger = LoggerFactory.getLogger(NetworkServer.class);
	
	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
	
	/** Room for the largest frame and for what a cipher appends to the payload. */
	private static final int SCRATCH_SIZE = NetworkConfig.MAX_FRAME_SIZE_LIMIT + 1 + 64;
	
	private final PacketHandler<T, RP, SP> _packetHandler;
	private final ByteOrder _byteOrder;
	private final int _maxFrameSize;
	private final int _writeHighWaterMark;
	private final int _ioThreads;
	
	private final Transport _transport;
	private final EventLoopGroup _group;
	private final List<Channel> _serverChannels = new CopyOnWriteArrayList<Channel>();
	
	private volatile boolean _shuttingDown;
	
	/** A packet writes into a heap buffer of the I/O thread, see {@link Connection}. */
	private final ThreadLocal<ByteBuffer> _scratch;
	
	/**
	 * @param config the settings
	 * @param packetHandler creates the packets of the opcodes
	 * @throws IllegalStateException when the transport the system property demands is not available
	 */
	protected NetworkServer(NetworkConfig config, PacketHandler<T, RP, SP> packetHandler)
	{
		_packetHandler = packetHandler;
		_accepts = new FloodManager(1000, config.getAcceptShort(), config.getAcceptLong()); // 1000 msec per tick
		_packets = new FloodManager(1000, new FloodManager.FloodFilter(250, 300, 2));
		_errors = new FloodManager(200, new FloodManager.FloodFilter(10, 10, 1)); // 200 msec per tick
		_byteOrder = config.getByteOrder();
		_maxFrameSize = config.getMaxFrameSize();
		_writeHighWaterMark = config.getWriteHighWaterMark();
		_ioThreads = config.getIoThreads();
		
		final ByteOrder byteOrder = _byteOrder;
		_scratch = new ThreadLocal<ByteBuffer>() {
			@Override
			protected ByteBuffer initialValue()
			{
				return ByteBuffer.allocate(SCRATCH_SIZE).order(byteOrder);
			}
		};
		
		_transport = Transport.select();
		_group = _transport.newEventLoopGroup(_ioThreads, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable)
			{
				final Thread thread = new FastThreadLocalThread(runnable, "network-io-" + THREAD_COUNTER.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		});
	}
	
	public final void openServerSocket(String address, int port) throws IOException
	{
		openServerSocket(InetAddress.getByName(address), port);
	}
	
	/**
	 * Binds the address. The socket does not accept connections before {@link #start()}; they wait in the backlog.
	 * 
	 * @param address the local address, null for all
	 * @param port the port, 0 for any free port
	 * @throws IOException when the address cannot be bound
	 */
	public final void openServerSocket(InetAddress address, int port) throws IOException
	{
		if (_shuttingDown)
			throw new IllegalStateException("The server is shut down");
		
		final ServerBootstrap bootstrap = new ServerBootstrap().group(_group).channel(_transport.serverChannelClass())
				.option(ChannelOption.SO_REUSEADDR, true)
				.option(ChannelOption.AUTO_READ, false) // accept from start() on
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
						new WriteBufferWaterMark(_writeHighWaterMark / 2, _writeHighWaterMark))
				.childHandler(new ClientInitializer());
		
		final ChannelFuture future =
				bootstrap.bind(address == null ? new InetSocketAddress(port) : new InetSocketAddress(address, port))
						.awaitUninterruptibly();
		
		if (!future.isSuccess())
		{
			final Throwable cause = future.cause();
			
			if (cause instanceof IOException)
				throw (IOException)cause;
			
			throw new IOException("Cannot bind " + (address == null ? "*" : address.getHostAddress()) + ":" + port, cause);
		}
		
		_serverChannels.add(future.channel());
	}
	
	/**
	 * @return the local address of the first bound socket, or null when none is bound; useful with port 0
	 */
	public final InetSocketAddress getLocalAddress()
	{
		if (_serverChannels.isEmpty())
			return null;
		
		return (InetSocketAddress)_serverChannels.get(0).localAddress();
	}
	
	/**
	 * @return the transport this server runs on
	 */
	public final Transport getTransport()
	{
		return _transport;
	}
	
	/**
	 * Starts accepting connections on the bound sockets.
	 */
	public final void start()
	{
		for (Channel channel : _serverChannels)
		{
			channel.config().setAutoRead(true);
			
			_logger.info("Accepting connections on " + channel.localAddress() + " (transport " + _transport.getName() + ", "
					+ _ioThreads + " I/O threads)");
		}
	}
	
	/**
	 * Closes the server sockets so that no new connection is accepted. The connections that exist stay open. The
	 * shutdown of a server uses this first, so that nobody enters while the players are saved.
	 */
	public final void stopAccepting()
	{
		for (Channel channel : _serverChannels)
			channel.close().syncUninterruptibly();
	}
	
	/**
	 * Closes the server sockets and all connections, and stops the I/O threads. The hooks of the connections are not
	 * called.
	 */
	public final void shutdown() throws InterruptedException
	{
		_shuttingDown = true;
		
		for (Channel channel : _serverChannels)
			channel.close();
		
		_group.shutdownGracefully(0, 2, TimeUnit.SECONDS).await();
	}
	
	final boolean isShuttingDown()
	{
		return _shuttingDown;
	}
	
	final ByteOrder getByteOrder()
	{
		return _byteOrder;
	}
	
	final int getWriteHighWaterMark()
	{
		return _writeHighWaterMark;
	}
	
	/**
	 * @return the heap buffer packets of the current I/O thread are written into, cleared
	 */
	final ByteBuffer getScratchBuffer()
	{
		final ByteBuffer buf = _scratch.get();
		buf.clear();
		return buf;
	}
	
	/**
	 * Runs on the I/O thread of a new channel: applies the accept limits, creates the client, builds the pipeline.
	 */
	private final class ClientInitializer extends ChannelInitializer<Channel>
	{
		@Override
		protected void initChannel(Channel channel)
		{
			if (!(channel.remoteAddress() instanceof InetSocketAddress))
			{
				channel.close();
				return;
			}
			
			final InetAddress address = ((InetSocketAddress)channel.remoteAddress()).getAddress();
			
			if (_shuttingDown || !acceptConnectionFrom(address))
			{
				channel.close();
				return;
			}
			
			channel.pipeline().addLast("frameDecoder", new FrameDecoder(_maxFrameSize));
			
			final T client = createClient(channel);
			
			if (client == null)
			{
				channel.close();
				return;
			}
			
			channel.pipeline().addLast("connection", new ConnectionHandler<T, RP, SP>(NetworkServer.this, _packetHandler, client));
		}
	}
	
	// ==============================================
	
	/**
	 * Creates the client of an accepted connection. It runs on the I/O thread of the connection, before the first byte
	 * is read, and may already send packets to the client.
	 * 
	 * @param channel the accepted channel
	 * @return the client, or null to refuse the connection
	 */
	protected abstract T createClient(Channel channel);
	
	/**
	 * Takes over a packet that was read. It runs on the I/O thread of the connection and must not block it: the usual
	 * implementation hands the packet to a thread pool, which calls {@link ReceivablePacket#run()}.
	 */
	protected abstract void executePacket(RP packet);
	
	// ==============================================
	
	private final FloodManager _accepts;
	private final FloodManager _packets;
	private final FloodManager _errors;
	
	
	protected String getVersionInfo()
	{
		return "";
	}
	
	protected boolean acceptConnectionFrom(InetAddress address)
	{
		final String host = address.getHostAddress();
		
		final Result isFlooding = _accepts.isFlooding(host, true);
		
		switch (isFlooding)
		{
			case REJECTED:
			{
				// TODO: punish, warn, log, etc
				_log.warn("Rejected connection from " + host);
				return false;
			}
			case WARNED:
			{
				// TODO: punish, warn, log, etc
				_log.warn("Connection over warn limit from " + host);
				return true;
			}
			default:
				return true;
		}
	}
	
	public void report(ErrorMode mode, T client, RP packet, Throwable throwable)
	{
		final Result isFlooding = _errors.isFlooding(client.getValidUID(), true);
		
		final StringBuilder sb = new StringBuilder();
		if (isFlooding != Result.ACCEPTED)
		{
			sb.append("Flooding with ");
		}
		sb.append(mode);
		sb.append(": ");
		sb.append(client);
		if (packet != null)
		{
			sb.append(" - ");
			sb.append(packet.getType());
		}
		final String versionInfo = getVersionInfo();
		if (versionInfo != null && !versionInfo.isEmpty())
		{
			sb.append(" - ");
			sb.append(versionInfo);
		}
		
		if (throwable != null)
			_log.info(sb, throwable);
		else
			_log.info(sb);
		
		//if (isFlooding != Result.ACCEPTED)
		//{
		//	// TODO: punish, warn, log, etc
		//}
	}
	
	protected boolean canReceivePacketFrom(T client, int opcode)
	{
		final String key = client.getValidUID();
		
		switch (Result.max(_packets.isFlooding(key, true), _errors.isFlooding(key, false)))
		{
			case REJECTED:
			{
				// TODO: punish, warn, log, etc
				_log.warn("Rejected packet (0x" + Integer.toHexString(opcode) + ") from " + client);
				return false;
			}
			case WARNED:
			{
				// TODO: punish, warn, log, etc
				_log.warn("Packet over warn limit (0x" + Integer.toHexString(opcode) + ") from " + client);
				return true;
			}
			default:
				return true;
		}
	}
}
