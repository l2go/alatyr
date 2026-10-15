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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * One client connection.
 * <p>
 * <b>Sending.</b> {@link #sendPacket(SendablePacket)} is thread-safe. It only queues the packet. The I/O thread of the
 * connection takes the packets from the queue in the order they were queued, lets each packet write itself, encrypts
 * the bytes, puts them into a frame and writes them; one flush per batch. So the cipher, which has a state, always sees
 * the packets in the order they leave, and the order per connection is the order of the calls.
 * <p>
 * <b>Closing.</b> {@link #closeNow()} and {@link #close(SendablePacket)} drop the packets that were not yet written,
 * send one last packet, stop reading, and close the channel when that packet is flushed or when the deadline (100 ms,
 * respectively 10 s) is over. They are idempotent. After either was called, {@link #sendPacket(SendablePacket)} drops
 * its packet.
 * <p>
 * <b>Hooks.</b> {@link #onDisconnection()} is called exactly once when the connection ends, for whatever reason, on the
 * I/O thread, before the channel closes. {@link #onForcedDisconnection()} is called before it when the connection ended
 * by an I/O error, a frame that breaks the framing rules, or an outbound buffer over the high-water mark, and not when
 * the client closed the connection, when {@code closeNow}/{@code close} ended it, or when the server shuts down (a shutdown
 * calls neither hook, as it did before).
 */
public abstract class Connection<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
{
	/** The deadline of {@link #closeNow()}: for a normal connection 100 ms is enough to write the pending packets. */
	static final long CLOSE_NOW_TIMEOUT_MILLIS = 100;
	
	/** The deadline of {@link #close(SendablePacket)}: even a connection with issues gets 10 s to receive the packet. */
	static final long CLOSE_TIMEOUT_MILLIS = 10000;
	
	private final NetworkServer<T, RP, SP> _networkServer;
	private final Channel _channel;
	private final InetAddress _inetAddress;
	
	private final Object _lock = new Object();
	private final ArrayDeque<SP> _sendQueue = new ArrayDeque<SP>(); // guarded by _lock
	private boolean _drainScheduled; // guarded by _lock
	private volatile boolean _closing; // written under _lock
	
	private final AtomicBoolean _terminated = new AtomicBoolean();
	private volatile ScheduledFuture<?> _closeDeadline;
	
	private final Runnable _drainTask = new Runnable() {
		@Override
		public void run()
		{
			drain();
		}
	};
	
	private final ChannelFutureListener _writeListener = new ChannelFutureListener() {
		@Override
		public void operationComplete(ChannelFuture future)
		{
			if (future.isSuccess())
				return;
			
			// a closed channel is reported by channelInactive; only a real I/O error is a forced disconnection
			if (!(future.cause() instanceof ClosedChannelException))
				terminate(true);
		}
	};
	
	/**
	 * @param networkServer the server this connection belongs to
	 * @param channel the Netty channel of the accepted socket
	 */
	protected Connection(NetworkServer<T, RP, SP> networkServer, Channel channel)
	{
		_networkServer = networkServer;
		_channel = channel;
		
		final SocketAddress remote = channel.remoteAddress();
		if (!(remote instanceof InetSocketAddress))
			throw new IllegalArgumentException("The channel has no inet remote address: " + remote);
		
		_inetAddress = ((InetSocketAddress)remote).getAddress();
	}
	
	/**
	 * Queues a packet for this client. It is dropped when the connection is closing or closed.
	 */
	public void sendPacket(SP sp)
	{
		if (sp == null)
			return;
		
		synchronized (_lock)
		{
			if (_closing)
				return;
			
			_sendQueue.addLast(sp);
			scheduleDrain();
		}
	}
	
	/** Must be called with the lock held. */
	private void scheduleDrain()
	{
		if (_drainScheduled)
			return;
		
		_drainScheduled = true;
		
		try
		{
			_channel.eventLoop().execute(_drainTask);
		}
		catch (RejectedExecutionException e)
		{
			// the event loops are shut down, nothing can be sent anymore
			_drainScheduled = false;
			_sendQueue.clear();
		}
	}
	
	final NetworkServer<T, RP, SP> getNetworkServer()
	{
		return _networkServer;
	}
	
	final Channel getChannel()
	{
		return _channel;
	}
	
	public final InetAddress getInetAddress()
	{
		return _inetAddress;
	}
	
	/**
	 * @return true when {@link #closeNow()} or {@link #close(SendablePacket)} was called, or the connection ended
	 */
	final boolean isClosed()
	{
		return _closing;
	}
	
	final boolean isTerminated()
	{
		return _terminated.get();
	}
	
	/**
	 * Sends {@link #getDefaultClosePacket()} and closes as soon as the pending packet is flushed, at the latest after
	 * 100 ms. The packets that were not yet written are dropped.
	 */
	public void closeNow()
	{
		if (isClosed())
			return;
		
		closeAfter(getDefaultClosePacket(), CLOSE_NOW_TIMEOUT_MILLIS);
	}
	
	/**
	 * Sends the packet and closes as soon as it is flushed, at the latest after 10 s. The packets that were not yet
	 * written are dropped.
	 */
	public void close(SP sp)
	{
		if (isClosed())
			return;
		
		closeAfter(sp, CLOSE_TIMEOUT_MILLIS);
	}
	
	private void closeAfter(SP sp, final long timeoutMillis)
	{
		synchronized (_lock)
		{
			if (_closing)
				return;
			
			_closing = true;
			_sendQueue.clear();
			
			if (sp != null)
				_sendQueue.addLast(sp);
			
			// the drain is queued before the task below, and the event loop runs its tasks in order
			scheduleDrain();
		}
		
		// no more input from now on
		_channel.config().setAutoRead(false);
		
		try
		{
			_channel.eventLoop().execute(new Runnable() {
				@Override
				public void run()
				{
					awaitFlush(timeoutMillis);
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			// the event loops are shut down, the channels are closed
		}
	}
	
	/** Runs on the I/O thread, after the drain that writes the last packet. */
	private void awaitFlush(long timeoutMillis)
	{
		if (isTerminated())
			return;
		
		_closeDeadline = _channel.eventLoop().schedule(new Runnable() {
			@Override
			public void run()
			{
				terminate(false);
			}
		}, timeoutMillis, TimeUnit.MILLISECONDS);
		
		// the empty buffer completes after every earlier write was flushed
		_channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future)
			{
				terminate(false);
			}
		});
	}
	
	/**
	 * Ends the connection once: runs the hooks, closes the channel. Runs on the I/O thread.
	 * 
	 * @param forced true when the end is an error and not a close by either side
	 */
	final void terminate(boolean forced)
	{
		if (!_terminated.compareAndSet(false, true))
			return;
		
		synchronized (_lock)
		{
			_closing = true;
			_sendQueue.clear();
		}
		
		final ScheduledFuture<?> deadline = _closeDeadline;
		if (deadline != null)
			deadline.cancel(false);
		
		try
		{
			// a shutdown closes the sockets without the hooks: the players were saved before
			if (!_networkServer.isShuttingDown())
			{
				if (forced)
				{
					try
					{
						onForcedDisconnection();
					}
					catch (RuntimeException e)
					{
						NetworkServer._log.error("Failed onForcedDisconnection: " + this, e);
					}
				}
				
				try
				{
					onDisconnection();
				}
				catch (RuntimeException e)
				{
					NetworkServer._log.error("Failed onDisconnection: " + this, e);
				}
			}
		}
		finally
		{
			_channel.close();
		}
	}
	
	/**
	 * The channel became unwritable: the client does not read as fast as the server sends. The connection is closed
	 * instead of buffering without limit, unless it is closing anyway.
	 */
	final void checkWritable()
	{
		if (_closing || _channel.isWritable())
			return;
		
		NetworkServer._log.warn("Outbound buffer over the high-water mark of " + _networkServer.getWriteHighWaterMark()
				+ " bytes, closing: " + this);
		terminate(true);
	}
	
	/**
	 * Frames written between two flushes inside one drain. A burst is handed to the socket in slices, so that the bytes
	 * of a healthy client do not pile up in the outbound buffer until the high-water mark and close it.
	 */
	private static final int FLUSH_EVERY = 32;
	
	/** Runs on the I/O thread: writes the queued packets and flushes every few frames and at the end. */
	private void drain()
	{
		boolean wrote = false;
		int unflushed = 0;
		
		try
		{
			for (;;)
			{
				final SP sp;
				
				synchronized (_lock)
				{
					sp = _sendQueue.pollFirst();
					
					if (sp == null)
					{
						_drainScheduled = false;
						break;
					}
				}
				
				if (isTerminated())
					continue;
				
				try
				{
					if (writePacket(sp))
					{
						wrote = true;
						
						if (++unflushed >= FLUSH_EVERY && _channel.isActive())
						{
							_channel.flush();
							unflushed = 0;
						}
					}
				}
				catch (Throwable t)
				{
					NetworkServer._log.error("Failed sending: " + this + " - " + sp.getType(), t);
				}
				
				// stop buffering for a client that does not read; a closing connection gets its last packet anyway
				if (!_closing && !_channel.isWritable())
					checkWritable(); // ends the connection, the rest of the queue is discarded above
			}
		}
		finally
		{
			if (wrote && _channel.isActive())
				_channel.flush();
		}
	}
	
	/**
	 * Lets the packet write itself, encrypts the bytes, frames them, and hands the frame to the channel.
	 * 
	 * @return true when a frame was written
	 */
	@SuppressWarnings("unchecked")
	private boolean writePacket(SP sp)
	{
		final T client = (T)this;
		final ByteBuffer buf = _networkServer.getScratchBuffer();
		final ByteBuffer previous = SendablePacket.attachBuffer(buf);
		final int dataSize;
		
		try
		{
			try
			{
				sp.write(client);
			}
			catch (RuntimeException e)
			{
				// a half-written packet would put the client out of sync, so it is not sent
				NetworkServer._log.error("Failed writing: " + client + " - " + sp.getType() + " - "
						+ _networkServer.getVersionInfo(), e);
				return false;
			}
			
			dataSize = buf.position();
		}
		finally
		{
			SendablePacket.restoreBuffer(previous);
		}
		
		// encrypt in place; the cipher leaves the position behind the (possibly longer) encrypted bytes
		buf.position(0);
		
		final boolean encrypted;
		try
		{
			encrypted = encrypt(buf, dataSize);
		}
		catch (RuntimeException e)
		{
			NetworkServer._log.error("Failed encrypting: " + client + " - " + sp.getType() + " - "
					+ _networkServer.getVersionInfo(), e);
			return false;
		}
		
		if (!encrypted)
		{
			NetworkServer._log.error("Failed encrypting, packet dropped: " + client + " - " + sp.getType() + " - "
					+ _networkServer.getVersionInfo());
			return false;
		}
		
		final ByteBuf frame;
		try
		{
			frame = FrameEncoder.encode(_channel.alloc(), buf.array(), 0, buf.position());
		}
		catch (IllegalArgumentException e)
		{
			NetworkServer._log.error("Failed framing: " + client + " - " + sp.getType() + " - "
					+ _networkServer.getVersionInfo(), e);
			return false;
		}
		
		_channel.write(frame).addListener(_writeListener);
		return true;
	}
	
	/**
	 * Called once when the connection ended, on the I/O thread.
	 */
	protected abstract void onDisconnection();
	
	/**
	 * Called before {@link #onDisconnection()} when the connection ended by an error.
	 */
	protected abstract void onForcedDisconnection();
	
	/**
	 * Decrypts the payload of a frame in place.
	 * 
	 * @param buf a heap buffer, its array starts at index 0, the payload starts at the position
	 * @param size the number of payload bytes
	 * @return false when the payload is not valid, the packet is dropped then
	 */
	protected abstract boolean decrypt(ByteBuffer buf, int size);
	
	/**
	 * Encrypts the payload of a frame in place. The buffer may have to grow to hold padding or a checksum, it has room.
	 * 
	 * @param buf a heap buffer, its array starts at index 0, the payload starts at the position
	 * @param size the number of payload bytes
	 * @return true when the buffer holds the encrypted payload and its position is behind the last encrypted byte;
	 *         false drops the packet
	 */
	protected abstract boolean encrypt(ByteBuffer buf, int size);
	
	/**
	 * @return the packet {@link #closeNow()} sends last, or null for none
	 */
	protected abstract SP getDefaultClosePacket();
	
	/**
	 * @return the identity of the client for the flood tables, for example the account; null or empty to use its address
	 */
	protected abstract String getUID();
	
	final String getValidUID()
	{
		final String UID = getUID();
		
		return UID == null || UID.isEmpty() ? _inetAddress.getHostAddress() : UID;
	}
}
