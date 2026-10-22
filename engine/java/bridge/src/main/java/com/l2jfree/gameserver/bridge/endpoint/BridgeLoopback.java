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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
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
	
	private final BridgeSettings settings;
	private final ServerSocketChannel server;
	private final Selector selector;
	private final Thread thread;
	private final GameServerContext context;
	private final DefaultEventLoopGroup virtualChannels;
	private final List<L2Client> clients = new CopyOnWriteArrayList<>();
	private final byte[] fullBye;
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
				selector.select(1000);
				drain();
				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while (keys.hasNext())
				{
					SelectionKey key = keys.next();
					keys.remove();
					if (!key.isValid())
						continue;
					if (key.isAcceptable())
						accept();
					else if (key.isReadable())
						read(key);
				}
			}
			catch (IOException e)
			{
				if (!stopping)
					stopping = true;
			}
		}
	}
	
	private void accept() throws IOException
	{
		SocketChannel socket = server.accept();
		if (socket == null)
			return;
		
		socket.configureBlocking(false);
		if (transports >= TRANSPORT_CAP)
		{
			writeAll(socket, fullBye);
			socket.close();
			return;
		}
		
		transports++;
		socket.register(selector, SelectionKey.OP_READ, new Transport(settings.secret()));
	}
	
	private void read(SelectionKey key) throws IOException
	{
		Transport transport = (Transport) key.attachment();
		SocketChannel socket = (SocketChannel) key.channel();
		int n = socket.read(transport.input);
		if (n < 0)
		{
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
			List<Envelope> records = takeFrame(transport);
			if (records == null)
				return;
			
			BridgeReply reply = transport.dialogue.accept(records);
			if (reply.admit)
			{
				try
				{
					admit(transport, reply);
				}
				catch (RuntimeException e)
				{
					writeAll(socket, frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL))));
					drop(key);
				}
				return;
			}
			if (!reply.records.isEmpty())
				writeAll(socket, frame(reply.records));
			if (reply.close)
				drop(key);
		}
		catch (WireException e)
		{
			long reason = e.kind() == WireException.Kind.UNSUPPORTED ? BridgeFrames.BYE_UNSUPPORTED : BridgeFrames.BYE_FRAME;
			writeAll(socket, frame(List.of(BridgeFrames.bye(reason))));
			drop(key);
		}
	}
	
	/**
	 * Builds the virtual channel and queues admission. Returns before the client queue reads the account.
	 */
	private void admit(Transport transport, BridgeReply reply) throws IOException
	{
		if (transport.client != null)
			throw new IllegalStateException("already admitted");
		
		BridgeSink sink = new BridgeSink(records -> enqueue(transport, records));
		LocalChannel channel = new LocalChannel();
		virtualChannels.register(channel).syncUninterruptibly();
		L2Client client = L2Client.forBridge(context.getNetworkServer(), channel,
				InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		sink.attach(client);
		transport.client = client;
		clients.add(client);
		BridgeAdmission.schedule(client, LoginLink.getInstance(), reply.account, reply.character,
				records -> enqueue(transport, records));
	}
	
	private void enqueue(Transport transport, List<Envelope> records)
	{
		if (records == null || records.isEmpty())
			return;
		
		transport.outbound.add(frame(records));
		selector.wakeup();
	}
	
	private void drain() throws IOException
	{
		for (SelectionKey key : selector.keys())
		{
			if (!key.isValid() || !(key.channel() instanceof SocketChannel))
				continue;
			if (!(key.attachment() instanceof Transport))
				continue;
			
			SocketChannel socket = (SocketChannel) key.channel();
			Transport transport = (Transport) key.attachment();
			byte[] bytes;
			while ((bytes = transport.outbound.poll()) != null)
				writeAll(socket, bytes);
		}
	}
	
	private void drop(SelectionKey key) throws IOException
	{
		key.cancel();
		key.channel().close();
		if (transports > 0)
			transports--;
	}
	
	void close()
	{
		stopping = true;
		selector.wakeup();
		for (L2Client client : clients)
			client.requestBridgeDisconnect();
		try
		{
			thread.join(30_000);
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
	
	private static void writeAll(SocketChannel socket, byte[] bytes) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		while (buffer.hasRemaining())
		{
			if (socket.write(buffer) == 0)
				break;
		}
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
		
		int size = 0;
		while (size < data.length && size < 10 && (data[size] & 0xff) >= 128)
			size++;
		if (size == data.length)
			return null;
		if (size == 10)
			throw WireException.frame();
		
		size++;
		long length = 0;
		int shift = 0;
		for (int i = 0; i < size; i++)
		{
			length |= (long) (data[i] & 0x7f) << shift;
			shift += 7;
		}
		if (length == 0 || length > FRAME_CAP)
			throw WireException.frame();
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
		final ConcurrentLinkedQueue<byte[]> outbound = new ConcurrentLinkedQueue<>();
		L2Client client;
		
		Transport(byte[] secret)
		{
			this.dialogue = new BridgeDialogue(secret);
		}
	}
}
