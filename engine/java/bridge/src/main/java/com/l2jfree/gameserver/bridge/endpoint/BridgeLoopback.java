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
import java.util.function.Consumer;

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
	static final long FRAME_READ_NS = 5_000_000_000L;
	static final long FRAME_WRITE_NS = 5_000_000_000L;
	static final long OPENING_NS = 10_000_000_000L;
	/** How long a bound close waits for the disconnect save before the bye is internal. */
	static final long RESULT_BUDGET_NS = 30_000_000_000L;

	private final BridgeSettings settings;
	private final ServerSocketChannel server;
	private final Selector selector;
	private final Thread thread;
	private final GameServerContext context;
	private final DefaultEventLoopGroup virtualChannels;
	private final List<L2Client> clients = new CopyOnWriteArrayList<>();
	private final java.util.concurrent.ConcurrentMap<L2Client, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
	private final byte[] fullBye;
	private volatile boolean admitting = true;
	private volatile Consumer<L2Client> beforeWatch;
	private volatile int transports;
	private volatile boolean stopping;
	private volatile Consumer<Transport> afterResumeGuard;
	private volatile long shutdownDeadline;
	private final BridgeShutdownBudget shutdownBudget = new BridgeShutdownBudget();
	// Only S registers/removes keys. H observes this concurrent registry, never Selector.keys().
	private final java.util.concurrent.ConcurrentMap<Transport, SelectionKey> registered = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> selectorTestTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private volatile java.util.function.LongConsumer beforeShutdownWait;
	private volatile Runnable beforeSelectorDisposal;
	private volatile Consumer<Transport> afterTransportLost;
	private volatile Consumer<Transport> beforeTransportControl;
	private volatile Consumer<Boolean> afterShutdownOutputCheck;
	private volatile Consumer<String> beforeListenerClose;

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

	/** Test gate between building a client and adding it to the shutdown walk. */
	void holdBeforeWatch(Consumer<L2Client> gate)
	{
		beforeWatch = gate;
	}

	boolean admitting()
	{
		return admitting;
	}

	int clientCount()
	{
		return clients.size();
	}

	void reapClients()
	{
		clients.removeIf(L2Client::isBridgeDisconnectFinished);
		BridgeAdmission.reapSessions();
		sessions.keySet().removeIf(L2Client::isBridgeDisconnectFinished);
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
		try
		{
			while (!stopping)
			{
				try
				{
					selector.select(nextWaitMs());
					Runnable task;
					while ((task = selectorTestTasks.poll()) != null) task.run();
					reapClients();
					for (SelectionKey key : registered.values())
					{
						if (key.attachment() instanceof Transport)
						serviceControl((Transport) key.attachment());
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
				catch (java.nio.channels.ClosedSelectorException e)
				{
					stopping = true;
				}
			}
		}
		finally
		{
			Throwable failure = null;
			try
			{
				Runnable gate = beforeSelectorDisposal;
				if (gate != null) gate.run();
			}
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
			// Selector.close cancels keys, but does not close their channels.
			for (SelectionKey key : List.copyOf(registered.values()))
			{
				try { drop(key); }
				catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
			}
			rethrowCloseFailure(closeListenerResources(failure));
		}
	}

	/** Test-only dispatch to the real selector; no second writer or executor. */
	private void onSelector(Runnable task)
	{
		selectorTestTasks.add(task);
		selector.wakeup();
	}

	/** Test-only gates for observing the shared save/output stage and the disposal reserve. */
	void holdShutdown(java.util.function.LongConsumer waitGate, Runnable disposalGate)
	{
		beforeShutdownWait = waitGate;
		beforeSelectorDisposal = disposalGate;
	}

	/** Test-only gates observe real selector transitions and the shutdown drain decision, outside all monitors. */
	void holdTransportLifecycle(Consumer<Transport> controlGate, Consumer<Transport> lostGate,
			Consumer<Boolean> outputCheck)
	{
		beforeTransportControl = controlGate;
		afterTransportLost = lostGate;
		afterShutdownOutputCheck = outputCheck;
	}

	java.util.concurrent.CompletableFuture<Void> executeOnSelectorForTest(Runnable task)
	{
		var result = new java.util.concurrent.CompletableFuture<Void>();
		onSelector(() ->
		{
			try { task.run(); result.complete(null); }
			catch (Throwable failure) { result.completeExceptionally(failure); }
		});
		return result;
	}

	void dropForTest(SelectionKey key) { drop(key); }
	boolean listenerResourcesClosedForTest() { return !server.isOpen() && !selector.isOpen(); }
	void holdListenerClose(Consumer<String> gate) { beforeListenerClose = gate; }

	/** Test-only registration uses the same selector-owned registry and slot accounting. */
	SelectionKey registerForTest(SocketChannel socket, Transport transport) throws Exception
	{
		var result = new java.util.concurrent.CompletableFuture<SelectionKey>();
		onSelector(() ->
		{
			try { result.complete(registerTransport(socket, transport)); }
			catch (Exception failure) { result.completeExceptionally(failure); }
		});
		return result.get(5, TimeUnit.SECONDS);
	}

	private SelectionKey registerTransport(SocketChannel socket, Transport transport) throws IOException
	{
		SelectionKey key = socket.register(selector, SelectionKey.OP_READ, transport);
		registered.put(transport, key);
		transports++;
		return key;
	}

	/** Test-only gate inside the Session transaction, after the replacement guard. */
	void holdAfterResumeGuard(Consumer<Transport> gate)
	{
		afterResumeGuard = gate;
	}

	/** Selector control work is never entered from a sink callback or with a transport monitor held. */
	void serviceControl(Transport source)
	{
		Consumer<Transport> gate = beforeTransportControl;
		if (gate != null) gate.accept(source);
		Session owner = source.resumeOwner != null ? source.resumeOwner : source.session;
		if (!admitting && owner == null)
		{
			loseTransport(source);
			synchronized (source) { source.retire = true; armClose(source); }
		}
		if (owner != null)
			owner.serviceCloseResult();
		expire(source);
		boolean cancel;
		synchronized (source) { cancel = source.cancelRecords != null || source.slowRequested; }
		if (cancel)
			cancelTransport(source);
		if (source.lost || source.terminal)
			disconnectBound(source);
	}

	private static Session ownerOf(Transport source)
	{
		return source.resumeOwner != null ? source.resumeOwner : source.session;
	}

	/** Owner publication and unbound cancellation meet under M; bound cancellation always enters Q first. */
	private static void cancelTransport(Transport source)
	{
		while (true)
		{
			Session owner = ownerOf(source);
			if (owner != null)
			{
				synchronized (owner) { applyCancellation(source); }
				return;
			}
			synchronized (source)
			{
				if (ownerOf(source) == null)
				{
					applyCancellation(source);
					return;
				}
			}
		}
	}

	private static void applyCancellation(Transport source)
	{
		synchronized (source)
		{
			if (source.cancelRecords != null)
			{
				List<Envelope> records = source.cancelRecords;
				source.cancelRecords = null;
				claimTerminal(source, records);
			}
			if (source.slowRequested)
			{
				source.slowRequested = false;
				claimSlow(source);
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
				writeAll(socket, fullBye);
				socket.close();
				return;
			}
			registerTransport(socket, new Transport(settings.secret()));
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
		expire(transport);
		if (transport.closing)
		{
			flushKey(key);
			return;
		}
		int n = socket.read(transport.input);
		if (n < 0)
		{
			loseTransport(transport);
			disconnectBound(transport);
			drop(key);
			return;
		}
		if (n > 0 && transport.pending.size() == 0)
			synchronized (transport) { transport.readDeadline = System.nanoTime() + FRAME_READ_NS; }
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
				if (transport.closing || transport.awaitingResult)
					break;
			}
		}
		catch (WireException e)
		{
			long reason = e.kind() == WireException.Kind.UNSUPPORTED ? BridgeFrames.BYE_UNSUPPORTED : BridgeFrames.BYE_FRAME;
			finishTerminal(transport, List.of(BridgeFrames.bye(reason)));
		}
		flushKey(key);
	}

	void acceptFrame(Transport transport, List<Envelope> records) throws IOException
	{
		if (transport.closing || transport.terminal || transport.lost)
			return;
		if ((transport.session != null || transport.resumeOwner != null) && records.size() == 1
				&& records.get(0).type() == com.l2jfree.gameserver.bridge.wire.Schemas.CLOSE)
		{
			Envelope close = records.get(0);
			long epoch = close.field(2).value().u();
			long expectedSession;
			long expectedEpoch;
			synchronized (transport)
			{
				expectedSession = transport.session == null ? transport.resumeSession : transport.session.id;
				expectedEpoch = transport.client == null ? transport.resumeEpoch : transport.epoch;
			}
			if (close.field(1).value().u() != expectedSession || Long.compareUnsigned(epoch, expectedEpoch) > 0)
			{
				finishTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
				return;
			}
			if (Long.compareUnsigned(epoch, expectedEpoch) < 0)
				return;
		}
		BridgeReply reply = transport.dialogue.accept(records);
		if (reply.resume != null)
		{
			long id = reply.resume.field(1).value().u();
			long epoch = reply.resume.field(2).value().u();
			if (transport.client != null && epoch != transport.epoch)
			{
				finishTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
				disconnectBound(transport);
				return;
			}
			if (transport.session != null && transport.session.id != id
					|| transport.resumeOwner != null && transport.resumeOwner.id != id)
			{
				finishTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
				return;
			}
			L2Client client = BridgeAdmission.session(id);
			Session owner = client == null ? null : sessions.get(client);
			if (owner == null || !admitting)
				finishTerminal(transport, List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_CLOSED)));
			else
			{
				if (owner.notePendingResume(transport, id, epoch))
					client.submit(() -> owner.resume(transport, id, epoch));
			}
			selector.wakeup();
			return;
		}
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
		if (reply.close)
		{
			if (reply.waitForDisconnect && transport.resumeOwner != null)
				transport.resumeOwner.closeResumingTransport(transport, records.get(0));
			else if (reply.waitForDisconnect && transport.client != null)
			{
				if (transport.session != null)
					transport.session.registerClose(transport);
				else
					armResultWait(transport);
				disconnectBound(transport);
			}
			else if (!reply.records.isEmpty())
			{
				finishTerminal(transport, reply.records);
				disconnectBound(transport);
			}
			selector.wakeup();
			return;
		}
		if (!reply.records.isEmpty())
			offer(transport, frame(reply.records));
		else if (!reply.close && records.stream().allMatch(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.INTENT))
		{
			// Resume is accepted in FIFO. Until that task publishes the binding, this socket owns no admission
			// table. In particular, do not produce a temporary not-ready Result that a later Resume could lose.
			if (transport.client == null)
				return;
			for (Envelope intent : records)
			{
				if (transport.session != null && (transport.session.transport != transport || transport.lost))
					return;
				if (transport.attempts == null)
				{
					transport.attempts = new BridgeAttemptSession(work -> transport.client.submit(work),
							result -> offer(transport, streamFrame(result)));
					transport.attempts.bindFence(transport.attemptFence);
				}
				var decision = transport.attempts.accept(transport.client, intent, transport.attemptFence);
				if (decision == null)
					return; // The reader lost its fence after the outer transport check.
				if (decision.kind == BridgeIntents.Kind.FRAME)
				{
					finishTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
					break;
				}
				if (decision.kind == BridgeIntents.Kind.MISMATCH || decision.kind == BridgeIntents.Kind.OVERFLOW)
					offer(transport, frame(List.of(Envelope.of(com.l2jfree.gameserver.bridge.wire.Schemas.INTENT_ERROR,
							List.of(com.l2jfree.gameserver.bridge.wire.Field.u(1, true, intent.field(1).value().u()),
									com.l2jfree.gameserver.bridge.wire.Field.u(2, true, decision.code))))));
			}
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

		transport.holdOpeningFacts = true;
		Session owner = new Session(transport);
		BridgeSink sink = owner.sink;
		LocalChannel channel = new LocalChannel();
		virtualChannels.register(channel).syncUninterruptibly();
		L2Client client = L2Client.forBridge(context.getNetworkServer(), channel,
				InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		sink.attach(client);
		transport.client = client;
		owner.attach(client);
		Consumer<L2Client> gate = beforeWatch;
		if (gate != null)
			gate.accept(client);
		watch(client);
		if (!admitting)
		{
			client.requestBridgeDisconnect();
			return;
		}
		BridgeAdmission.schedule(client, LoginLink.getInstance(), reply.account, reply.character,
				owner::opened);
	}

	/** The disconnect save owns the terminal bye. A late bye is dropped once that bye is claimed. */
	void fromSink(Transport transport, List<Envelope> records)
	{
		if (records != null && !records.isEmpty()
				&& records.stream().noneMatch(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.BYE))
		{
			synchronized (transport)
			{
				if (transport.holdOpeningFacts
						&& records.stream().allMatch(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.FACT))
				{
					if (transport.terminal || transport.closing || transport.cancelRecords != null || transport.slowRequested)
						return;
					byte[] bytes = frame(records);
					if (bytes.length > OUT_QUEUE_BYTES - transport.openingFactBytes)
						finishTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)));
					else
					{
						transport.openingFacts.add(bytes);
						transport.openingFactBytes += bytes.length;
					}
					selector.wakeup();
					return;
				}
			}
			enqueue(transport, records);
			return;
		}
		synchronized (transport)
		{
			if (resultExpired(transport, System.nanoTime()))
				records = List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL));
			finishTerminal(transport, records);
		}
		selector.wakeup();
	}

	/**
	 * A bound close waits for {@link #fromSink}. When the wait exceeds the disconnect budget, the bye is internal.
	 */
	static void expire(Transport transport)
	{
		expire(transport, System.nanoTime());
	}

	static void expire(Transport transport, long now)
	{
		while (true)
		{
			Session owner = ownerOf(transport);
			if (owner != null)
			{
				synchronized (owner) { expireLocked(transport, now, owner); }
				return;
			}
			synchronized (transport)
			{
				if (ownerOf(transport) == null)
				{
					expireLocked(transport, now, null);
					return;
				}
			}
		}
	}

	private static void expireLocked(Transport transport, long now, Session owner)
	{
		boolean resultTimeout = false;
		synchronized (transport)
		{
			if (resultExpired(transport, now) || transport.awaitingResult && transport.writeDeadline != 0
					&& now >= transport.writeDeadline)
				resultTimeout = true;
			else if (transport.awaitingResult && !transport.closing && transport.readDeadline != 0
					&& now >= transport.readDeadline)
				claimTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
			else if (!transport.closing && transport.writeDeadline != 0 && now >= transport.writeDeadline)
			{
				transport.lost = true;
				transport.attemptFence.valid = false;
				transport.retire = true;
				armClose(transport);
			}
			else if (!transport.closing && !transport.terminal && !transport.awaitingResult)
			{
				if (transport.checkpointDeadline != 0 && now - transport.checkpointDeadline >= 0)
					claimSlow(transport);
				else if (transport.readDeadline != 0 && now - transport.readDeadline >= 0)
					claimTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
				else if (!transport.opened && now - transport.openingDeadline >= 0)
					claimTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)));
			}
		}
		if (resultTimeout)
		{
			if (owner != null)
				owner.completeCloseResult(List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)));
			else
				claimTerminal(transport, List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)));
		}
	}

	/** Called under the transport lock so checking the deadline and claiming the bye are one operation. */
	private static boolean resultExpired(Transport transport, long now)
	{
		return transport.awaitingResult && !transport.closing && !transport.terminal && transport.resultDeadline != 0
				&& now - transport.resultDeadline >= 0;
	}

	private static void armResultWait(Transport transport)
	{
		synchronized (transport)
		{
			if (transport.closing || transport.terminal || transport.awaitingResult)
				return;

			transport.awaitingResult = true;
			transport.attemptFence.closing = true;
			transport.attemptFence.valid = false;
			transport.resultDeadline = System.nanoTime() + RESULT_BUDGET_NS;
		}
	}

	private static void finishTerminal(Transport transport, List<Envelope> records)
	{
		// Producers can hold sink -> transport. They publish a request, never enter Session.
		if (Thread.holdsLock(transport))
		{
			if (!transport.terminal && !transport.closing && transport.cancelRecords == null && !transport.slowRequested)
				transport.cancelRecords = List.copyOf(records);
			return;
		}
		while (true)
		{
			Session owner = ownerOf(transport);
			if (owner != null)
			{
				synchronized (owner) { claimTerminal(transport, records); }
				return;
			}
			synchronized (transport)
			{
				if (ownerOf(transport) == null)
				{
					claimTerminal(transport, records);
					return;
				}
			}
		}
	}

	private static void claimTerminal(Transport transport, List<Envelope> records)
	{
		synchronized (transport)
		{
			if (transport.terminal || transport.closing)
				return;

			transport.terminal = true;
			transport.attemptFence.closing = true;
			transport.attemptFence.valid = false;
			transport.awaitingResult = false;
			transport.terminalReply = frame(records);
			armClose(transport);
		}
	}

	private void enqueue(Transport transport, List<Envelope> records)
	{
		if (records == null || records.isEmpty())
			return;

		synchronized (transport)
		{
			if (transport.terminal || transport.closing || transport.cancelRecords != null || transport.slowRequested)
				return;
			for (Envelope record : records)
			{
				if (record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.OPENED)
					transport.opened = true;
			}
			if (transport.holdOpeningFacts && transport.opened)
			{
				transport.holdOpeningFacts = false;
				boolean accepted = records.stream().anyMatch(record ->
						record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.OPENED
								&& record.field(1).value().u() == 1);
				if (accepted && !transport.openingFacts.isEmpty())
				{
					// The existing not-ready opening prefix owns these observations.
					// Full projection completeness and teleport-aware readiness are a separate concern.
					for (Envelope record : records)
					{
						offer(transport, frame(List.of(record)));
						if (record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.CHECKPOINT
								&& record.field(1).value().u() == com.l2jfree.gameserver.bridge.wire.Schemas.BEGIN)
							while (!transport.openingFacts.isEmpty())
								offer(transport, transport.openingFacts.remove());
					}
					transport.openingFactBytes = 0;
					selector.wakeup();
					return;
				}
				transport.openingFacts.clear();
				transport.openingFactBytes = 0;
			}
			offer(transport, frame(records));
		}
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
			if (transport.closing || transport.lost || transport.cancelRecords != null || transport.slowRequested)
				return;
			if (transport.held + bytes.length > OUT_QUEUE_BYTES)
			{
				failSlow(transport);
				return;
			}
			if (transport.releaseAfterCheckpoint)
				transport.afterCheckpoint.add(bytes);
			else
				transport.queued.add(bytes);
			transport.held += bytes.length;
		}
	}

	private static void failSlow(Transport transport)
	{
		transport.slowRequested = true;
	}

	private static void claimSlow(Transport transport)
	{
		if (transport.terminal || transport.closing)
			return;
		transport.lost = transport.session != null;
		if (transport.lost)
			transport.attemptFence.valid = false;
		// A slow consumer loses its transport, not its resumable session or started attempt.
		transport.terminalReply = frame(List.of(BridgeFrames.bye(BridgeFrames.BYE_SLOW)));
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
		for (SelectionKey key : registered.values())
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
				{
					deadline = transport.opened ? 0 : transport.openingDeadline;
					if (transport.readDeadline != 0)
						deadline = deadline == 0 ? transport.readDeadline : Math.min(deadline, transport.readDeadline);
					if (transport.checkpointDeadline != 0)
						deadline = deadline == 0 ? transport.checkpointDeadline : Math.min(deadline, transport.checkpointDeadline);
				}
				if (transport.writeDeadline != 0)
					deadline = deadline == 0 ? transport.writeDeadline : Math.min(deadline, transport.writeDeadline);
				if (!transport.closing && transport.readDeadline != 0)
					deadline = deadline == 0 ? transport.readDeadline : Math.min(deadline, transport.readDeadline);
			}
			if (shutdownDeadline != 0)
				deadline = deadline == 0 ? shutdownDeadline : Math.min(deadline, shutdownDeadline);
			wait = Math.min(wait, selectTimeout(now, deadline));
		}
		return wait;
	}

	private void flushAll() throws IOException
	{
		for (SelectionKey key : registered.values())
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
		serviceControl(transport);
		pumpCheckpoint(transport);
		flush(transport, (SocketChannel) key.channel());
		Session owner = transport.resumeOwner != null ? transport.resumeOwner : transport.session;
		if (owner != null)
			owner.serviceCloseResult();
		boolean close;
		synchronized (transport)
		{
			applyOutputFlags(transport);
			boolean drained = outputDrained(transport);
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
			byte[] writing;
			synchronized (transport)
			{
				applyOutputFlags(transport);
				if (transport.writing == null)
				{
					transport.writing = transport.queued.poll();
					transport.writingAt = 0;
					transport.writeDeadline = transport.writing == null ? 0 : System.nanoTime() + FRAME_WRITE_NS;
				}
				if (transport.writing == null)
					return written;
				writing = transport.writing;
				slice = ByteBuffer.wrap(writing, transport.writingAt, writing.length - transport.writingAt);
			}
			int n = socket.write(slice);
			synchronized (transport)
			{
				if (n > 0)
					written += n;
				// A cancelled writer still owns its borrowed slice, not the replacement's byte budget.
				if (transport.writing != writing)
					return written;
				applyOutputFlags(transport);
				if (n > 0)
				{
					transport.writingAt += n;
					transport.held -= n;
					if (writing == transport.saveReplyFrame)
						transport.saveReplyStarted = true;
				}
				if (n <= 0 || transport.writingAt < transport.writing.length)
					return written;
				transport.writing = null;
				transport.writeDeadline = 0;
			}
		}
	}

	/** Selector-only cleanup of queued output; the active borrowed array remains owned by flush. */
	static void applyOutputFlags(Transport transport)
	{
		synchronized (transport)
		{
			while (transport.discardAfterCheckpointCount > 0 && !transport.afterCheckpoint.isEmpty())
			{
				transport.held -= transport.afterCheckpoint.remove().length;
				transport.discardAfterCheckpointCount--;
			}
			transport.discardAfterCheckpointCount = 0;
			if (transport.releaseAfterCheckpoint)
			{
				while (transport.releaseAfterCheckpointCount > 0 && !transport.afterCheckpoint.isEmpty())
				{
					transport.queued.add(transport.afterCheckpoint.remove());
					transport.releaseAfterCheckpointCount--;
				}
				if (transport.checkpoint == null)
				{
					while (!transport.afterCheckpoint.isEmpty())
						transport.queued.add(transport.afterCheckpoint.remove());
				}
				transport.releaseAfterCheckpoint = false;
				transport.releaseAfterCheckpointCount = 0;
			}
			if (transport.retire)
			{
				if (transport.checkpoint != null) transport.checkpoint.cancel();
				transport.checkpoint = null;
				while (!transport.queued.isEmpty())
					transport.held -= transport.queued.remove().length;
				while (!transport.afterCheckpoint.isEmpty())
					transport.held -= transport.afterCheckpoint.remove().length;
				transport.openingFacts.clear();
				transport.openingFactBytes = 0;
				transport.terminalReply = null;
			}
			else if (transport.terminalReply != null)
			{
				byte[] reply = transport.terminalReply;
				// Closing stops the producer. Its unstarted tail must not pin capacity forever.
				if (transport.checkpoint != null) transport.checkpoint.cancel();
				transport.checkpoint = null;
				transport.token = null;
				transport.endScheduled = false;
				transport.checkpointDeadline = 0;
				while (!transport.afterCheckpoint.isEmpty())
					transport.held -= transport.afterCheckpoint.remove().length;
				transport.openingFacts.clear();
				transport.openingFactBytes = 0;
				if (!transport.lost || reply != transport.saveReplyFrame)
				{
					if (reply.length <= OUT_QUEUE_BYTES - transport.held)
					{
						transport.queued.add(reply);
						transport.held += reply.length;
						transport.terminalReply = null;
					}
				}
			}
		}
	}

	/** Pending terminal output is not drained merely because the ordinary queue is empty. */
	static boolean outputDrained(Transport transport)
	{
		return transport.writing == null && transport.queued.isEmpty() && transport.terminalReply == null;
	}

	/** Test seam for the selector's request drain, without acquiring another monitor. */
	static void applyUnboundControl(Transport transport)
	{
		if (transport.resumeOwner != null || transport.session != null)
			throw new IllegalArgumentException("Bound cancellation requires the Session transaction");
		applyCancellation(transport);
		applyOutputFlags(transport);
	}

	private void disconnectBound(Transport transport)
	{
		if (transport == null)
			return;
		// An authenticated pending Resume has an owner, but no client binding or attempt table yet.
		// Cancel under that owner's transition monitor so adoption and EOF have one order.
		Session owner = transport.resumeOwner != null ? transport.resumeOwner : transport.session;
		if (owner != null)
		{
			synchronized (owner)
			{
				if (!invalidateDisconnectedTransport(transport))
					return;
			}
		}
		else if (!invalidateDisconnectedTransport(transport))
			return;
		if (transport.session != null && transport.session.transport != transport
				&& !transport.awaitingResult && !transport.explicitClose)
			return; // Retired transports cannot disconnect a newer binding, or cancel its grace.
		if (transport.session != null && !transport.terminal && !transport.awaitingResult
				&& !transport.client.isBridgeDisconnectRequested())
		{
			transport.session.detach(transport);
			return;
		}
		L2Client client = transport.client;
		if (client != null)
		{
			transport.attemptFence.closing = true;
			transport.attemptFence.valid = false;
			client.requestBridgeDisconnect();
		}
	}

	private static void loseTransport(Transport source)
	{
		while (true)
		{
			Session owner = ownerOf(source);
			if (owner != null)
			{
				synchronized (owner) { synchronized (source) { source.lost = true; source.attemptFence.valid = false; } }
				return;
			}
			synchronized (source)
			{
				if (ownerOf(source) == null)
				{
					source.lost = true;
					source.attemptFence.valid = false;
					return;
				}
			}
		}
	}

	private static boolean invalidateDisconnectedTransport(Transport transport)
	{
		synchronized (transport)
		{
			if (transport.disconnectStarted)
				return false;
			transport.disconnectStarted = true;
			// A bound explicit Close retains its writable transport for the save-produced Bye.
			// Pending sockets and accidental losses have no such result-wait obligation.
			if (!transport.terminal && (transport.client == null || !transport.awaitingResult))
				transport.lost = true;
			transport.attemptFence.valid = false;
			return true;
		}
	}

	/** Only queue operations occur under this monitor; relocation checks are queued after it is released. */
	private void pumpCheckpoint(Transport transport)
	{
		boolean finish = false;
		boolean ready = false;
		long readyGeneration = 0;
		BridgeRecovery.Checkpoint token = null;
		synchronized (transport)
		{
			applyOutputFlags(transport);
			if (transport.lost || transport.closing || transport.checkpoint == null)
				return;
			byte[] frame;
			while ((frame = transport.checkpoint.next(OUT_QUEUE_BYTES - transport.held)) != null)
			{
				transport.queued.add(frame);
				transport.held += frame.length;
			}
			if (transport.checkpoint.waitingForEnd() && !transport.endScheduled)
			{
				transport.endScheduled = true;
				finish = true;
				token = transport.token;
			}
			if (transport.checkpoint.finished())
			{
				transport.checkpoint = null;
				transport.opened = true;
				transport.checkpointDeadline = 0;
				ready = true;
				readyGeneration = transport.checkpointFenceGeneration;
				while (!transport.afterCheckpoint.isEmpty())
					transport.queued.add(transport.afterCheckpoint.remove());
			}
		}
		if (finish)
		{
			var expected = token;
			transport.client.submit(() -> transport.session.finishCheckpoint(transport, expected));
		}
		if (ready)
			transport.attempts.ready(transport.attemptFence, readyGeneration);
	}

	/** One game session owns the sink and attempts across successive sockets. */
	final class Session
	{
		volatile Transport transport;
		// A pending Close can receive only the save outcome, without becoming the session transport.
		volatile Transport closeReplyTransport;
		final java.util.concurrent.atomic.AtomicReference<List<Envelope>> saveNotification = new java.util.concurrent.atomic.AtomicReference<>();
		List<Envelope> closeOutcome;
		long saveDeadline;
		long outcomeCloseDeadline;
		Transport outcomeRecipient;
		final java.util.Set<Transport> sessionTransports = java.util.concurrent.ConcurrentHashMap.newKeySet();
		final BridgeRecovery recovery;
		final BridgeSink sink;
		BridgeAttemptSession attempts;
		L2Client client;
		volatile long id;

		Session(Transport initial)
		{
			transport = initial;
			sessionTransports.add(initial);
			recovery = new BridgeRecovery(initial);
			sink = new BridgeSink(this::publish);
			sink.afterTeleport(this::captureCheckpoint);
		}

		void attach(L2Client value)
		{
			client = value;
			transport.client = value;
			attempts = new BridgeAttemptSession(client::submit, this::publishResult);
			attempts.bindFence(transport.attemptFence);
			transport.session = this;
			transport.attempts = attempts;
			sessions.put(client, this);
		}

		void opened(List<Envelope> records)
		{
			Envelope accepted = records.stream().filter(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.OPENED
					&& record.field(1).value().u() == 1).findFirst().orElse(null);
			if (accepted == null)
			{
				if (!records.isEmpty())
				{
					finishTerminal(transport, records);
					client.requestBridgeDisconnect();
				}
				return;
			}
			id = accepted.field(2).value().u();
			captureCheckpoint();
		}

		void publish(List<Envelope> records)
		{
			boolean terminal = records.stream().anyMatch(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.BYE);
			Transport reply = closeReplyTransport;
			if (terminal && reply != null)
			{
				publishCloseResult(reply, records);
				return;
			}
			Transport current = transport;
			if (current == null)
				return;
			synchronized (current)
			{
				if (!terminal && (current.lost || current.closing || current.cancelRecords != null || current.slowRequested))
					return;
				boolean relocation = records.stream().anyMatch(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.FACT
						&& record.field(67) != null && record.field(67).value().u() == 1);
				if (relocation)
				{
					current.attemptFence.invalidateReadiness();
					current.checkpointDeadline = System.nanoTime() + OPENING_NS;
				}
				if (relocation && current.checkpoint != null)
				{
					boolean began = current.checkpoint.began();
					current.checkpoint.cancel();
					current.checkpoint = null;
					current.token = null;
					current.endScheduled = false;
					if (!current.opened)
					{
						current.holdOpeningFacts = true;
						// The fresh opening/recovery snapshot subsumes these observations. In particular, facts
						// cannot precede Begin when the cancelled producer had not transferred its first frame.
						current.discardAfterCheckpointCount = current.afterCheckpoint.size();
						current.reuseCheckpointBegin = began;
						return;
					}
					current.releaseAfterCheckpoint = true;
					if (began)
					{
						offer(current, frame(records));
						offer(current, frame(List.of(BridgeFrames.checkpoint(com.l2jfree.gameserver.bridge.wire.Schemas.END,
								current.epoch, false))));
						selector.wakeup();
						return;
					}
				}
				if (!terminal && current.holdOpeningFacts && records.stream().allMatch(record ->
						record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.FACT))
					return;
				if (!terminal && current.holdOpeningFacts && records.stream().allMatch(record ->
						record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.RESULT))
					return;
				if (!terminal && current.checkpoint != null)
				{
					byte[] bytes = frame(records);
					if (bytes.length > OUT_QUEUE_BYTES - current.held)
						failSlow(current);
					else
					{
						current.afterCheckpoint.add(bytes);
						current.held += bytes.length;
					}
					selector.wakeup();
					return;
				}
			}
			fromSink(current, records);
		}

		/** Sink callback publishes immutable control work. It must never acquire Session under sink. */
		private void publishCloseResult(Transport reply, List<Envelope> records)
		{
			saveNotification.compareAndSet(null, List.copyOf(records));
			selector.wakeup();
		}

		void registerClose(Transport source)
		{
			synchronized (this)
			{
				armResultWait(source);
				synchronized (source)
				{
					if (saveDeadline == 0) saveDeadline = source.resultDeadline;
					source.resultDeadline = saveDeadline;
				}
				closeReplyTransport = source;
			}
		}

		/** Selector control work, outside sink. Deadline and save compete for one immutable outcome. */
		void serviceCloseResult()
		{
			synchronized (this)
			{
				// Sample the deadline only after entering the arbitration transaction.
				serviceCloseResult(System.nanoTime());
			}
		}

		/** Controlled-clock entry used by the event-order replay; identical selector transaction. */
		void serviceCloseResult(long now)
		{
			synchronized (this)
			{
				List<Envelope> notification = saveNotification.getAndSet(null);
				if (notification != null)
				{
					boolean expired = closeResultExpired(saveDeadline, now);
					completeCloseResult(expired ? List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)) : notification);
				}
				if (closeOutcome == null)
					return;
				if (outcomeRecipient != null)
				{
					boolean started;
					synchronized (outcomeRecipient)
					{
						started = outcomeRecipient.saveReplyStarted;
						if (!outcomeRecipient.lost && !outcomeRecipient.saveReplyStarted)
							return;
					}
					if (started)
					{
						retireOtherTransports(outcomeRecipient);
						return;
					}
				}
				outcomeRecipient = null;
				selectCloseRecipient();
			}
		}

		void completeCloseResult(List<Envelope> records)
		{
			synchronized (this)
			{
				if (closeOutcome != null)
					return;
				closeOutcome = List.copyOf(records);
				selectCloseRecipient();
			}
		}

		private boolean claimSaveRecipient(Transport candidate, boolean requireCapacity)
		{
			if (candidate == null || !sessionTransports.contains(candidate))
				return false;
			byte[] reply = frame(closeOutcome);
			synchronized (candidate)
			{
				if (!saveRecipientEligible(candidate))
					return false;
				if (requireCapacity && reply.length > OUT_QUEUE_BYTES - candidate.held)
					return false;
				armResultWait(candidate);
				// The session has already arbitrated its deadline. Never reinterpret the outcome here.
				candidate.terminalReply = reply;
				candidate.saveReplyFrame = candidate.terminalReply;
				candidate.terminal = true;
				candidate.awaitingResult = false;
				candidate.attemptFence.closing = true;
				candidate.attemptFence.valid = false;
				if (outcomeCloseDeadline == 0) outcomeCloseDeadline = System.nanoTime() + BYE_CLOSE_NS;
				armClose(candidate);
				candidate.closeDeadline = outcomeCloseDeadline;
				outcomeRecipient = candidate;
				return true;
			}
		}

		/** Caller holds Q and this candidate's M. No reservation or other monitor is taken here. */
		private boolean saveRecipientEligible(Transport candidate)
		{
			return !candidate.physicallyClosed && !candidate.lost && !candidate.retire && !candidate.terminal
					&& !candidate.closing && candidate.cancelRecords == null && !candidate.slowRequested
					&& (candidate.awaitingResult || candidate == transport);
		}

		/** Q-owned delivery obligation survives the loss-to-fallback microstep in selector drop. */
		boolean closeDeliveryPending()
		{
			if (closeOutcome == null) return false;
			if (outcomeRecipient != null)
			{
				synchronized (outcomeRecipient)
				{
					if (outcomeRecipient.saveReplyStarted || !outcomeRecipient.lost) return false;
				}
			}
			for (Transport candidate : sessionTransports)
			{
				synchronized (candidate)
				{
					if (saveRecipientEligible(candidate)) return true;
				}
			}
			return false;
		}

		private void selectCloseRecipient()
		{
			// Prefer a recipient that can accept the outcome now. A full but valid
			// backlog remains retryable if no other live transport has capacity.
			if (claimSaveRecipient(closeReplyTransport, true) || claimSaveRecipient(transport, true))
				return;
			for (Transport candidate : sessionTransports)
				if (claimSaveRecipient(candidate, true))
					return;
			if (claimSaveRecipient(closeReplyTransport, false) || claimSaveRecipient(transport, false))
				return;
			for (Transport candidate : sessionTransports)
				if (claimSaveRecipient(candidate, false))
					return;
			retireOtherTransports(null);
		}

		private void retireOtherTransports(Transport recipient)
		{
			for (Transport other : sessionTransports)
			{
				if (other == recipient)
					continue;
				synchronized (other)
				{
					other.lost = true;
					other.attemptFence.valid = false;
					other.attemptFence.closing = true;
					other.terminal = true;
					other.awaitingResult = false;
					other.retire = true;
					armClose(other);
				}
			}
		}

		void publishResult(byte[] result)
		{
			publish(Codec.bridge().decode(result));
		}

		void detach(Transport source)
		{
			long generation;
			boolean fullDisconnect;
			boolean initialOpening;
			// Serialize the state, binding and admission changes with Resume. No source/model callback runs here.
			synchronized (this)
			{
				synchronized (source)
				{
					initialOpening = source.epoch == 1 && !source.opened;
					source.lost = true;
					source.attemptFence.valid = false;
					// A transport-only Bye may already be draining while the session enters grace.
					if (!source.closing) source.retire = true;
					armClose(source);
					source.checkpointDeadline = 0;
				}
				if (transport == source && initialOpening)
				{
					recovery.close();
					generation = 0;
				}
				else
					generation = recovery.detach(source, source.fence);
				fullDisconnect = generation == 0 && transport == source;
				if (generation != 0)
				{
					transport = null;
					attempts.detach();
				}
			}
			if (generation == 0)
			{
				if (fullDisconnect)
					client.requestBridgeDisconnect();
				return;
			}
			com.l2jfree.gameserver.ThreadPoolManager.getInstance().scheduleGeneral(() -> client.submit(() ->
			{
				boolean expired;
				synchronized (this) { expired = recovery.expireGrace(generation); }
				if (expired)
					client.requestBridgeDisconnect();
			}), settings.graceMs());
		}

		void closeResumingTransport(Transport source, Envelope close)
		{
			L2Client bound;
			synchronized (this)
			{
				long currentEpoch = recovery.epoch();
				synchronized (source)
				{
					long expectedEpoch = source.client == null ? source.resumeEpoch : source.epoch;
					long requestedEpoch = close.field(2).value().u();
					if (close.field(1).value().u() != id || source.resumeSession != id
							|| Long.compareUnsigned(requestedEpoch, expectedEpoch) > 0
							|| source.client == null && (currentEpoch == -1L || expectedEpoch != currentEpoch + 1))
					{
						finishTerminal(source, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
						return;
					}
					if (Long.compareUnsigned(requestedEpoch, expectedEpoch) < 0)
						return;
					// The authenticated Close survives EOF and socket disposal before FIFO adoption.
					// Resume observes this intent without binding the dead socket or consuming its epoch.
					source.explicitClose = true;
					bound = source.client;
					armResultWait(source);
					if (saveDeadline == 0) saveDeadline = source.resultDeadline;
					source.resultDeadline = saveDeadline;
					closeReplyTransport = source;
				}
				if (transport != null && transport != source)
				{
					armResultWait(transport);
					synchronized (transport) { transport.resultDeadline = saveDeadline; }
				}
			}
			// No submit under Session. The pending Resume is already ahead of this full task in FIFO.
			client.requestBridgeDisconnect();
			if (bound != null)
				disconnectBound(source);
		}

		boolean notePendingResume(Transport replacement, long session, long epoch)
		{
			synchronized (this)
			{
				synchronized (replacement)
				{
					if (replacement.lost || replacement.closing || replacement.terminal || replacement.disconnectStarted
							|| replacement.cancelRecords != null || replacement.slowRequested)
						return false;
					if (replacement.resumeOwner != null && replacement.resumeOwner != this)
						throw WireException.frame();
					if (replacement.resumeOwner == null)
					{
						replacement.resumeSession = session;
						replacement.resumeEpoch = epoch;
						replacement.resumeOwner = this;
					}
				}
				sessionTransports.add(replacement);
				return true;
			}
		}

		void resume(Transport replacement, long session, long epoch)
		{
			BridgeRecovery.Resume decision;
			boolean closeRequested;
			synchronized (this)
			{
				synchronized (replacement)
				{
					closeRequested = replacement.explicitClose;
					if (!closeRequested && (replacement.lost || replacement.closing
							|| replacement.terminal || replacement.disconnectStarted
							|| replacement.cancelRecords != null || replacement.slowRequested))
						return; // Do not consume an epoch or invalidate the existing grace for a cancelled socket.
				}
				if (closeRequested)
					decision = BridgeRecovery.Resume.CLOSED;
				else if (session != id || client.isBridgeDisconnectRequested())
					decision = BridgeRecovery.Resume.CLOSED;
				else
				{
					Consumer<Transport> gate = afterResumeGuard;
					if (gate != null) gate.accept(replacement);
					if (transport == replacement && epoch != replacement.epoch)
					{
						claimTerminal(replacement, List.of(BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
						decision = BridgeRecovery.Resume.FRAME;
					}
					else
					{
						if (transport != null && (transport.terminal || transport.awaitingResult))
							recovery.close();
						decision = recovery.resume(replacement, epoch);
						if (decision == BridgeRecovery.Resume.ACCEPTED)
						{
							Transport old = transport;
							if (old != null)
							{
								synchronized (old) { old.lost = true; old.attemptFence.valid = false; armClose(old); }
								attempts.detach();
								}
							replacement.session = this;
							replacement.attempts = attempts;
							replacement.fence = recovery.fence();
							replacement.epoch = epoch;
							replacement.holdOpeningFacts = true;
							replacement.attemptFence.ready = false;
							attempts.resume(epoch, this::publishResult, replacement.attemptFence);
							// Publish the complete binding, not a socket whose attempt table still belongs to no session.
							replacement.client = client;
							transport = replacement;
						}
					}
				}
			}
			if (closeRequested)
			{
				client.requestBridgeDisconnect();
				return;
			}
			if (decision == BridgeRecovery.Resume.REPLAY)
			{
				// No producer can also mean relocation is still pending. Join that continuation;
				// only a transport that already completed its opening may replay Opened.
				if (replacement.opened)
					enqueue(replacement, List.of(BridgeFrames.accepted(id, epoch).get(2)));
				return;
			}
			if (decision != BridgeRecovery.Resume.ACCEPTED)
			{
				finishTerminal(replacement, List.of(decision == BridgeRecovery.Resume.CLOSED
						? BridgeFrames.openedRefused(BridgeFrames.REASON_CLOSED) : BridgeFrames.bye(BridgeFrames.BYE_FRAME)));
				if (replacement.client != null)
					client.requestBridgeDisconnect();
				return;
			}
			captureCheckpoint();
		}

		void captureCheckpoint()
		{
			try
			{
				captureCheckpointChecked();
			}
			catch (RuntimeException failure)
			{
				// Encoding limits and sequence exhaustion are terminal, never a slow or falsely ready scene.
				recovery.close();
				client.requestBridgeDisconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure.internal());
			}
		}

		private void captureCheckpointChecked()
		{
			Transport current = transport;
			if (id == 0 || current == null || current.lost || client.isBridgeDisconnectRequested())
				return;
			var player = client.getActiveChar();
			if (player == null)
				return;
			synchronized (current)
			{
				// Duplicate relocation continuations must not replace an in-flight producer with
				// another Begin, or replace the token whose ready tail is already scheduled.
				if (current.checkpoint != null)
					return;
			}
			var token = recovery.beginCheckpoint();
			attempts.checkpoint();
			synchronized (current)
			{
				current.attemptFence.invalidateReadiness();
				if (current.checkpointDeadline == 0)
					current.checkpointDeadline = System.nanoTime() + OPENING_NS;
			}
			long epoch = recovery.epoch();
			List<byte[]> retained = current.opened ? List.of() : attempts.retainedResults();
			player.captureBridgeScene(() ->
			{
				synchronized (sink)
				{
					var capture = sink.snapshot(epoch, true, !current.opened);
					if (!capture.ready())
						return;
					synchronized (current)
					{
						if (current.lost || current.closing)
							return;
						current.openingFacts.clear();
						current.openingFactBytes = 0;
						current.holdOpeningFacts = false;
						// Freeze the old generation before post-snapshot facts can enter this queue.
						if (current.releaseAfterCheckpoint)
							current.releaseAfterCheckpointCount = current.afterCheckpoint.size();
						current.checkpoint = new BridgeCheckpointOutput(capture.records(), epoch, retained,
								current.opened ? null : BridgeFrames.accepted(id, epoch).get(2), !current.reuseCheckpointBegin);
						current.reuseCheckpointBegin = false;
						current.token = token;
						current.checkpointFenceGeneration = current.attemptFence.generation;
						current.endScheduled = false;
					}
				}
			});
			selector.wakeup();
		}

		void finishCheckpoint(Transport current, BridgeRecovery.Checkpoint expected)
		{
			try
			{
				finishCheckpointChecked(current, expected);
			}
			catch (RuntimeException failure)
			{
				recovery.close();
				client.requestBridgeDisconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure.internal());
			}
		}

		private void finishCheckpointChecked(Transport current, BridgeRecovery.Checkpoint expected)
		{
			var player = client.getActiveChar();
			if (player == null || current != transport)
				return;
			boolean[] granted = { false };
			player.captureBridgeScene(() ->
			{
				synchronized (current)
				{
					if (!current.lost && current.checkpoint != null && expected != null && expected.equals(current.token)
							&& current.checkpoint.waitingForEnd())
					{
						current.checkpoint.allowEnd();
						granted[0] = true;
					}
				}
			});
			if (granted[0])
				recovery.finishCheckpoint(expected);
			selector.wakeup();
		}
	}

	private void drop(SelectionKey key)
	{
		Throwable failure = null;
		if (key.attachment() instanceof Transport source)
		{
			loseTransport(source);
			synchronized (source)
			{
				if (source.physicallyClosed)
					return;
				source.physicallyClosed = true;
				source.lost = true;
			}
			try
			{
				Consumer<Transport> gate = afterTransportLost;
				if (gate != null) gate.accept(source);
			}
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
		}
		if (key.attachment() instanceof Transport)
		{
			try { disconnectBound((Transport) key.attachment()); }
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
		}
		if (key.attachment() instanceof Transport source)
		{
			try
			{
				synchronized (source)
				{
					source.retire = true;
					try { applyOutputFlags(source); }
					finally
					{
						// Drop is S-owned and no slice is borrowed now, even if producer cancellation failed.
						source.checkpoint = null; source.token = null; source.endScheduled = false;
						source.checkpointDeadline = 0;
						source.writing = null; source.writingAt = 0; source.writeDeadline = 0;
						source.queued.clear(); source.afterCheckpoint.clear(); source.openingFacts.clear();
						source.held = 0; source.openingFactBytes = 0; source.terminalReply = null;
						source.releaseAfterCheckpoint = false; source.releaseAfterCheckpointCount = 0;
						source.discardAfterCheckpointCount = 0;
					}
				}
			}
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
		}
		try { key.cancel(); }
		catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
		try { if (key.channel() != null) key.channel().close(); }
		catch (IOException | RuntimeException | Error e) { failure = appendFailure(failure, e); }
		if (key.attachment() instanceof Transport source)
		{
			registered.remove(source, key);
			Session owner = source.resumeOwner != null ? source.resumeOwner : source.session;
			if (owner != null)
			{
				owner.sessionTransports.remove(source);
				try { owner.serviceCloseResult(); }
				catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
			}
		}
		if (transports > 0)
			transports--;
		if (failure != null)
			System.getLogger(BridgeLoopback.class.getName()).log(System.Logger.Level.WARNING,
					"Bridge transport disposal failed after completing cleanup", failure);
	}

	void close()
	{
		close(30_000);
	}

	/**
	 * Refuses new clients, then asks every client already on the list to disconnect. A client published after this
	 * list is copied has to notice {@link #admitting} itself.
	 */
	void stopAdmitting()
	{
		admitting = false;
		selector.wakeup();
		for (Session owner : sessions.values())
		{
			synchronized (owner)
			{
				for (Transport source : owner.sessionTransports)
				{
					synchronized (source)
					{
						if (source.client == null && !source.explicitClose)
							claimTerminal(source, List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_CLOSED)));
					}
				}
			}
		}
		for (L2Client client : clients)
			client.requestBridgeDisconnect();
	}

	/**
	 * Asks each session for its one full disconnect, keeps writing while those saves run, and waits until they finish
	 * or {@code budgetMs} has passed. The selector thread is stopped after that wait.
	 */
	void close(long budgetMs)
	{
		long budgetNs = Math.max(0, Math.min(30_000, budgetMs)) * 1_000_000L;
		if (!shutdownBudget.claim(System.nanoTime(), budgetNs))
			return; // Only the first caller owns the deadline and listener disposal.
		long end = shutdownBudget.deadline();
		long saveEnd = shutdownSaveEnd(end, budgetNs);
		shutdownDeadline = end;
		boolean interrupted = Thread.interrupted();
		Throwable failure = null;
		try
		{
			stopAdmitting();
			java.util.function.LongConsumer waitGate = beforeShutdownWait;
			if (waitGate != null) waitGate.accept(saveEnd);
			while (!BridgeShutdownBudget.canStop(System.nanoTime(), saveEnd, disconnectsFinished() && shutdownOutputDrained()))
			{
				selector.wakeup();
				try { Thread.sleep(10); }
				catch (InterruptedException e) { interrupted = true; }
			}
			if (!disconnectsFinished())
				System.getLogger(BridgeLoopback.class.getName()).log(System.Logger.Level.WARNING,
						"Bridge shutdown budget expired with unfinished saves; no parallel save will be started");
			reapClients();
		}
		catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
		finally
		{
			// Interrupt cannot abandon the only cleanup owner or renew the disposal reserve.
			interrupted |= Thread.interrupted();
			stopping = true;
			try { selector.wakeup(); }
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
			while (thread.isAlive() && System.nanoTime() < end)
			{
				try { thread.join(Math.max(1, (end - System.nanoTime()) / 1_000_000L)); }
				catch (InterruptedException e) { interrupted = true; }
			}
			try { virtualChannels.shutdownGracefully(0, 1, TimeUnit.SECONDS); }
			catch (RuntimeException | Error e) { failure = appendFailure(failure, e); }
			failure = closeListenerResources(failure);
			if (thread.isAlive())
				failure = appendFailure(failure, new IllegalStateException("Bridge selector did not finish its bounded socket disposal"));
			if (interrupted) Thread.currentThread().interrupt();
		}
		rethrowCloseFailure(failure);
	}

	/** Each listener resource is attempted even if the preceding close fails. Also used by S on abnormal exit. */
	private Throwable closeListenerResources(Throwable failure)
	{
		try
		{
			try { if (beforeListenerClose != null) beforeListenerClose.accept("server"); }
			finally { server.close(); }
		}
		catch (IOException | RuntimeException | Error e) { failure = appendFailure(failure, e); }
		try
		{
			try { if (beforeListenerClose != null) beforeListenerClose.accept("selector"); }
			finally { selector.close(); }
		}
		catch (IOException | RuntimeException | Error e) { failure = appendFailure(failure, e); }
		return failure;
	}

	private static Throwable appendFailure(Throwable first, Throwable next)
	{
		if (first == null) return next;
		if (first != next) first.addSuppressed(next);
		return first;
	}

	private static void rethrowCloseFailure(Throwable failure)
	{
		if (failure instanceof RuntimeException e) throw e;
		if (failure instanceof Error e) throw e;
		if (failure instanceof IOException e) throw new UncheckedIOException(e);
	}

	private boolean shutdownOutputDrained()
	{
		boolean drained = shutdownOutputDrainedChecked();
		Consumer<Boolean> gate = afterShutdownOutputCheck;
		if (gate != null) gate.accept(drained);
		return drained;
	}

	private boolean shutdownOutputDrainedChecked()
	{
		for (SelectionKey key : registered.values())
		{
			if (key.attachment() instanceof Transport source)
			{
				// Finished clients may already have been reaped from sessions. The
				// registered transport retains its owner until physical disposal.
				Session owner = ownerOf(source);
				if (owner != null)
				{
					// Do not observe the getAndSet-to-terminalReply gap in selector
					// arbitration, or a recipient move between two per-socket checks.
					synchronized (owner)
					{
						if (owner.saveNotification.get() != null || owner.closeDeliveryPending())
							return false;
						for (Transport member : owner.sessionTransports)
							if (!transportOutputDrained(member))
								return false;
					}
				}
				else if (!transportOutputDrained(source))
					return false;
			}
		}
		return true;
	}

	private static boolean transportOutputDrained(Transport source)
	{
		synchronized (source)
		{
			return source.lost || source.cancelRecords == null && outputDrained(source);
		}
	}

	/** Reserve at most a tenth of the total budget for selector disposal, capped at one second. */
	static long shutdownSaveEnd(long end, long budgetNs)
	{
		return end - Math.min(1_000_000_000L, budgetNs / 10);
	}

	static boolean closeResultExpired(long deadline, long now)
	{
		return deadline != 0 && now - deadline >= 0;
	}

	/** Blocking write used only for the one-frame refusal of a socket that is about to be closed. */
	private static void writeAll(SocketChannel socket, byte[] bytes) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		long end = System.nanoTime() + BYE_CLOSE_NS;
		while (buffer.hasRemaining() && System.nanoTime() < end)
			if (socket.write(buffer) == 0)
				break; // Refusal is best effort; never stall the sole selector on an unregistered socket.
	}

	private static byte[] frame(List<Envelope> records)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Codec.bridge().write(out, records);
		return out.toByteArray();
	}

	private static byte[] streamFrame(byte[] body)
	{
		return frame(Codec.bridge().decode(body));
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
		// A coalesced next prefix arrived in the same read: retain its original deadline.
		if (transport.pending.size() == 0)
			synchronized (transport) { transport.readDeadline = 0; }
		return Codec.bridge().decode(body);
	}

	static final class Transport
	{
		final BridgeDialogue dialogue;
		final ByteBuffer input = ByteBuffer.allocate(FRAME_CAP + 10);
		final ByteArrayOutputStream pending = new ByteArrayOutputStream();
		final ArrayDeque<byte[]> queued = new ArrayDeque<>();
		final ArrayDeque<byte[]> openingFacts = new ArrayDeque<>();
		int openingFactBytes;
		boolean holdOpeningFacts;
		byte[] writing;
		int writingAt;
		int held;
		boolean retire;
		boolean releaseAfterCheckpoint;
		int releaseAfterCheckpointCount;
		byte[] terminalReply;
		List<Envelope> cancelRecords;
		boolean slowRequested;
		byte[] saveReplyFrame;
		boolean saveReplyStarted;
		int discardAfterCheckpointCount;
		volatile boolean closing;
		long closeDeadline;
		volatile boolean awaitingResult;
		long resultDeadline;
		volatile boolean terminal;
		boolean disconnectStarted;
		boolean physicallyClosed;
		volatile Session resumeOwner;
		long resumeSession;
		long resumeEpoch;
		boolean explicitClose;
		long readDeadline;
		long writeDeadline;
		final long openingDeadline = System.nanoTime() + OPENING_NS;
		volatile boolean opened;
		volatile L2Client client;
		volatile BridgeAttemptSession attempts;
		volatile Session session;
		volatile boolean lost;
		final BridgeAttemptSession.Fence attemptFence = new BridgeAttemptSession.Fence();
		volatile long fence = 1;
		volatile long epoch = 1;
		boolean reuseCheckpointBegin;
		long checkpointDeadline;
		BridgeCheckpointOutput checkpoint;
		BridgeRecovery.Checkpoint token;
		long checkpointFenceGeneration;
		boolean endScheduled;
		final ArrayDeque<byte[]> afterCheckpoint = new ArrayDeque<>();

		Transport(byte[] secret)
		{
			this.dialogue = new BridgeDialogue(secret);
		}
	}
}
