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
package com.l2jfree.gameserver.bridge.endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.WireException;
import com.l2jfree.gameserver.extension.GameServerContext;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalChannel;

/**
 * One selector thread on 127.0.0.1. The 65th socket is refused with a small frame before a read buffer is allocated.
 */
final class BridgeLoopback implements Runnable
{
	static final int TRANSPORT_CAP = 64;
	static final int FRAME_CAP = 65536;
	static final int OUT_QUEUE_BYTES = 262144;
	static final long BYE_CLOSE_NS = 100_000_000L;
	/** How long a bound close waits for the disconnect save before the bye is internal. */
	static final long RESULT_BUDGET_NS = 30_000_000_000L;
	
	private final BridgeSettings settings;
	private final ServerSocketChannel server;
	private final Selector selector;
	private final Thread thread;
	private final GameServerContext context;
	private final DefaultEventLoopGroup virtualChannels;
	private final List<L2Client> clients = new CopyOnWriteArrayList<>();
	private final byte[] fullBye;
	private volatile boolean admitting = true;
	private int transports;
	private volatile boolean stopping;
	
	private BridgeLoopback(BridgeSettings settings, GameServerContext context, ServerSocketChannel server,
			Selector selector)
	{
		this.settings = settings;
		this.context = context;
		this.server = server;
		this.selector = selector;
		this.virtualChannels = new DefaultEventLoopGroup(1);
		this.fullBye = frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_FULL)));
		this.thread = new Thread(this, "bridge-listener");
		this.thread.setDaemon(true);
	}
	
	static BridgeLoopback open(BridgeSettings settings, GameServerContext context)
	{
		try
		{
			Selector selector = Selector.open();
			ServerSocketChannel server = ServerSocketChannel.open();
			server.configureBlocking(false);
			server.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), settings.port()));
			server.register(selector, SelectionKey.OP_ACCEPT);
			BridgeLoopback loopback = new BridgeLoopback(settings, context, server, selector);
			loopback.thread.start();
			return loopback;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Bridge listener could not bind", e);
		}
	}
	
	void watch(L2Client client)
	{
		if (client != null)
			clients.add(client);
	}
	
	int clientCount()
	{
		return clients.size();
	}
	
	private boolean disconnectsFinished()
	{
		for (L2Client client : clients)
		{
			if (!client.isBridgeDisconnectFinished())
				return false;
		}
		return true;
	}
	
	int port()
	{
		try
		{
			return ((InetSocketAddress) server.getLocalAddress()).getPort();
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
	
	@Override
	public void run()
	{
		while (!stopping)
		{
			try
			{
				selector.select(nextWaitMs());
				for (SelectionKey key : selector.keys())
				{
					if (key.attachment() instanceof Transport)
						expire((Transport) key.attachment());
				}
				flushAll();
				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while (keys.hasNext())
				{
					SelectionKey key = keys.next();
					keys.remove();
					serve(key);
				}
			}
			catch (IOException e)
			{
				if (!stopping)
					stopping = true;
			}
		}
	}
	
	/** One key. A failure of this transport does not stop the listener. */
	void serve(SelectionKey key)
	{
		try
		{
			if (key == null || !key.isValid())
				return;
			if (key.isAcceptable())
				accept();
			else if (key.isReadable())
				read(key);
			else
				flushKey(key);
		}
		catch (IOException e)
		{
			drop(key);
		}
	}
	
	void account()
	{
		transports++;
	}
	
	int transports()
	{
		return transports;
	}
	
	boolean alive()
	{
		return thread.isAlive() && !stopping;
	}
	
	private void accept() throws IOException
	{
		SocketChannel socket = server.accept();
		if (socket == null)
			return;
		
		receive(socket);
	}
	
	/**
	 * Configures one accepted socket. A failure here closes that socket only. The listener socket stays open, and a
	 * slot is counted only after the socket is registered.
	 */
	void receive(SocketChannel socket)
	{
		try
		{
			socket.configureBlocking(false);
			if (!admitting)
			{
				socket.close();
				return;
			}
			if (transports >= TRANSPORT_CAP)
			{
				socket.configureBlocking(true);
				writeAll(socket, fullBye);
				socket.close();
				return;
			}
			socket.register(selector, SelectionKey.OP_READ, new Transport(settings.secret()));
			transports++;
		}
		catch (IOException e)
		{
			try
			{
				socket.close();
			}
			catch (IOException ignored)
			{
				// The new socket is already unusable. The listener keeps accepting.
			}
		}
	}
	
	private void read(SelectionKey key) throws IOException
	{
		Transport transport = (Transport) key.attachment();
		SocketChannel socket = (SocketChannel) key.channel();
		int n = socket.read(transport.input);
		if (n < 0)
		{
			disconnectBound(transport);
			drop(key);
			return;
		}
		transport.input.flip();
		byte[] got = new byte[transport.input.remaining()];
		transport.input.get(got);
		transport.input.clear();
		transport.pending.write(got);
		try
		{
			while (true)
			{
				List<Envelope> records = takeFrame(transport);
				if (records == null)
				{
					if (transport.pending.size() > FRAME_CAP + 10)
						throw WireException.frame();
					break;
				}
				acceptFrame(transport, records);
			}
		}
		catch (WireException e)
		{
			long reason = e.kind() == WireException.Kind.UNSUPPORTED ? BridgeFrames.BYE_UNSUPPORTED : BridgeFrames.BYE_FRAME;
			offer(transport, frame(List.of(BridgeFrames.bye(reason))));
			armClose(transport);
		}
		flushKey(key);
	}
	
	void acceptFrame(Transport transport, List<Envelope> records) throws IOException
	{
		BridgeReply reply = transport.dialogue.accept(records);
		if (reply.admit)
		{
			try
			{
				admit(transport, reply);
			}
			catch (RuntimeException e)
			{
				offer(transport, frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL))));
				armClose(transport);
			}
			return;
		}
		if (reply.close && transport.client != null)
		{
			armResultWait(transport);
			disconnectBound(transport);
			selector.wakeup();
			return;
		}
		if (!reply.records.isEmpty())
			offer(transport, frame(reply.records));
		if (reply.close)
		{
			armClose(transport);
			selector.wakeup();
		}
	}
	
	/**
	 * Builds the virtual channel and queues admission. Returns before the client queue reads the account.
	 */
	private void admit(Transport transport, BridgeReply reply) throws IOException
	{
		if (!admitting)
		{
			offer(transport, frame(List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_CLOSED))));
			armClose(transport);
			selector.wakeup();
			return;
		}
		if (transport.client != null)
			throw new IllegalStateException("already admitted");
		
		BridgeSink sink = new BridgeSink(records -> fromSink(transport, records));
		LocalChannel channel = new LocalChannel();
		virtualChannels.register(channel).syncUninterruptibly();
		L2Client client = L2Client.forBridge(context.getNetworkServer(), channel,
				InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		sink.attach(client);
		transport.client = client;
		watch(client);
		BridgeAdmission.schedule(client, LoginLink.getInstance(), reply.account, reply.character,
				records -> enqueue(transport, records));
	}
	
	/** The disconnect save owns the terminal bye. A late bye is dropped once that bye is claimed. */
	void fromSink(Transport transport, List<Envelope> records)
	{
		if (!claimTerminal(transport))
			return;
		
		offer(transport, frame(records));
		armClose(transport);
		selector.wakeup();
	}
	
	/**
	 * A bound close waits for {@link #fromSink}. When the wait exceeds the disconnect budget, the bye is internal.
	 */
	static void expire(Transport transport)
	{
		boolean due;
		synchronized (transport)
		{
			due = transport.awaitingResult && !transport.closing && !transport.terminal && transport.resultDeadline != 0
					&& System.nanoTime() >= transport.resultDeadline;
		}
		if (!due || !claimTerminal(transport))
			return;
		
		offer(transport, frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL))));
		armClose(transport);
	}
	
	private static void armResultWait(Transport transport)
	{
		synchronized (transport)
		{
			if (transport.closing || transport.terminal || transport.awaitingResult)
				return;
			
			transport.awaitingResult = true;
			transport.resultDeadline = System.nanoTime() + RESULT_BUDGET_NS;
		}
	}
	
	private static boolean claimTerminal(Transport transport)
	{
		synchronized (transport)
		{
			if (transport.terminal || transport.closing)
				return false;
			
			transport.terminal = true;
			transport.awaitingResult = false;
			return true;
		}
	}
	
	private void enqueue(Transport transport, List<Envelope> records)
	{
		if (records == null || records.isEmpty())
			return;
		
		offer(transport, frame(records));
		selector.wakeup();
	}
	
	/**
	 * Queues one whole frame. A frame that would pass {@link #OUT_QUEUE_BYTES} is not stored: the transport is closed
	 * for a slow consumer instead, and bytes already queued stay whole.
	 */
	static void offer(Transport transport, byte[] bytes)
	{
		if (bytes == null || bytes.length == 0)
			return;
		
		synchronized (transport)
		{
			if (transport.closing)
				return;
			if (transport.held + bytes.length > OUT_QUEUE_BYTES)
			{
				failSlow(transport);
				return;
			}
			transport.queued.add(bytes);
			transport.held += bytes.length;
		}
	}
	
	private static void failSlow(Transport transport)
	{
		byte[] bye = frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_SLOW)));
		if (transport.held + bye.length <= OUT_QUEUE_BYTES)
		{
			transport.queued.add(bye);
			transport.held += bye.length;
		}
		armClose(transport);
	}
	
	/** After a bye, the socket closes once the queue has been written, and in any case within 100 ms. */
	static void armClose(Transport transport)
	{
		synchronized (transport)
		{
			transport.closing = true;
			if (transport.closeDeadline == 0)
				transport.closeDeadline = System.nanoTime() + BYE_CLOSE_NS;
		}
	}
	
	/** The selector waits no longer than the nearest bye deadline, and never longer than one second. */
	static long selectTimeout(long now, long deadline)
	{
		if (deadline == 0)
			return 1000;
		long left = (deadline - now) / 1_000_000L;
		if (left < 1)
			return 1;
		return Math.min(1000, left);
	}
	
	private long nextWaitMs()
	{
		long now = System.nanoTime();
		long wait = 1000;
		for (SelectionKey key : selector.keys())
		{
			if (!(key.attachment() instanceof Transport))
				continue;
			Transport transport = (Transport) key.attachment();
			long deadline;
			synchronized (transport)
			{
				if (transport.closing)
					deadline = transport.closeDeadline;
				else if (transport.awaitingResult)
					deadline = transport.resultDeadline;
				else
					deadline = 0;
			}
			wait = Math.min(wait, selectTimeout(now, deadline));
		}
		return wait;
	}
	
	private void flushAll() throws IOException
	{
		for (SelectionKey key : selector.keys())
		{
			if (!key.isValid() || !(key.attachment() instanceof Transport))
				continue;
			flushKey(key);
		}
	}
	
	private void flushKey(SelectionKey key)
	{
		if (!key.isValid() || !(key.channel() instanceof SocketChannel))
			return;
		try
		{
			flushKeyThrows(key);
		}
		catch (IOException e)
		{
			drop(key);
		}
	}
	
	private void flushKeyThrows(SelectionKey key) throws IOException
	{
		if (!key.isValid() || !(key.channel() instanceof SocketChannel))
			return;
		
		Transport transport = (Transport) key.attachment();
		flush(transport, (SocketChannel) key.channel());
		boolean close;
		synchronized (transport)
		{
			boolean drained = transport.writing == null && transport.queued.isEmpty();
			close = transport.closing && (drained || System.nanoTime() >= transport.closeDeadline);
			if (key.isValid())
				key.interestOps(drained ? SelectionKey.OP_READ : SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		}
		if (close)
			drop(key);
	}
	
	/**
	 * Writes what the socket will take. A zero write keeps the unread tail queued.
	 *
	 * @return how many bytes were written
	 */
	static int flush(Transport transport, SocketChannel socket) throws IOException
	{
		int written = 0;
		while (true)
		{
			ByteBuffer slice;
			synchronized (transport)
			{
				if (transport.writing == null)
				{
					transport.writing = transport.queued.poll();
					transport.writingAt = 0;
				}
				if (transport.writing == null)
					return written;
				slice = ByteBuffer.wrap(transport.writing, transport.writingAt,
						transport.writing.length - transport.writingAt);
			}
			int n = socket.write(slice);
			synchronized (transport)
			{
				if (n > 0)
				{
					transport.writingAt += n;
					transport.held -= n;
					written += n;
				}
				if (n <= 0 || transport.writingAt < transport.writing.length)
					return written;
				transport.writing = null;
			}
		}
	}
	
	private void disconnectBound(Transport transport)
	{
		if (transport == null || transport.disconnectStarted)
			return;
		transport.disconnectStarted = true;
		L2Client client = transport.client;
		if (client != null)
			client.requestBridgeDisconnect();
	}
	
	private void drop(SelectionKey key)
	{
		if (key.attachment() instanceof Transport)
			disconnectBound((Transport) key.attachment());
		try
		{
			key.cancel();
			if (key.channel() != null)
				key.channel().close();
		}
		catch (IOException e)
		{
			key.cancel();
		}
		if (transports > 0)
			transports--;
	}
	
	void close()
	{
		close(30_000);
	}
	
	/**
	 * Asks each session for its one full disconnect, keeps writing while those saves run, and waits until they finish
	 * or {@code budgetMs} has passed. The selector thread is stopped after that wait.
	 */
	void close(long budgetMs)
	{
		admitting = false;
		selector.wakeup();
		for (L2Client client : clients)
			client.requestBridgeDisconnect();
		long end = System.nanoTime() + budgetMs * 1_000_000L;
		while (System.nanoTime() < end && !disconnectsFinished())
		{
			selector.wakeup();
			try
			{
				Thread.sleep(10);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
		stopping = true;
		selector.wakeup();
		try
		{
			thread.join(1_000);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		virtualChannels.shutdownGracefully(0, 1, TimeUnit.SECONDS);
		try
		{
			selector.close();
			server.close();
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
	
	/** Blocking write used only for the one-frame refusal of a socket that is about to be closed. */
	private static void writeAll(SocketChannel socket, byte[] bytes) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		while (buffer.hasRemaining())
			socket.write(buffer);
	}
	
	private static byte[] frame(List<Envelope> records)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Codec.bridge().write(out, records);
		return out.toByteArray();
	}
	
	/**
	 * @return the records of one complete frame, or null when more bytes are required
	 */
	static List<Envelope> takeFrame(Transport transport)
	{
		byte[] data = transport.pending.toByteArray();
		if (data.length == 0)
			return null;
		
		Codec.FramePrefix prefix = Codec.bridge().framePrefix(data);
		if (prefix == null)
			return null;
		int size = prefix.size();
		long length = prefix.length();
		if (data.length < size + length)
			return null;
		
		byte[] body = new byte[(int) length];
		System.arraycopy(data, size, body, 0, body.length);
		transport.pending.reset();
		if (data.length > size + length)
			transport.pending.write(data, size + (int) length, data.length - size - (int) length);
		return Codec.bridge().decode(body);
	}
	
	static final class Transport
	{
		final BridgeDialogue dialogue;
		final ByteBuffer input = ByteBuffer.allocate(FRAME_CAP + 10);
		final ByteArrayOutputStream pending = new ByteArrayOutputStream();
		final ArrayDeque<byte[]> queued = new ArrayDeque<>();
		byte[] writing;
		int writingAt;
		int held;
		boolean closing;
		long closeDeadline;
		boolean awaitingResult;
		long resultDeadline;
		boolean terminal;
		boolean disconnectStarted;
		L2Client client;
		
		Transport(byte[] secret)
		{
			this.dialogue = new BridgeDialogue(secret);
		}
	}
}
