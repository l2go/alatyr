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

import java.util.List;
import java.util.function.Consumer;

import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.CharSelectInfoPackage;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
import com.l2jfree.gameserver.network.bridge.CharacterSelection;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;

/**
 * Account admission. The selector only schedules it. The body runs on the client queue, which is where the database
 * read belongs.
 */
public final class BridgeAdmission
{
	private BridgeAdmission()
	{
	}

	/**
	 * Queues admission. The consumer is called on the client queue with the records to write, or an empty list when
	 * the full disconnect owns the bye.
	 */
	public static void schedule(L2Client client, LoginLink login, String account, String character,
			Consumer<List<com.l2jfree.gameserver.bridge.wire.Envelope>> replies)
	{
		if (client == null || login == null || replies == null)
			throw new IllegalArgumentException("admission");

		client.submit(() -> replies.accept(open(client, login, account, character)));
	}

	static List<com.l2jfree.gameserver.bridge.wire.Envelope> open(L2Client client, LoginLink login, String account,
			String character)
	{
		boolean reserved = false;
		boolean published = false;
		try
		{
			if (client.isBridgeDisconnectRequested())
				return List.of();

			if (!login.admitTrusted(account, client))
				return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_ALREADY_ONLINE));

			reserved = true;
			client.setState(GameClientState.AUTHED);
			client.setProtocolOk(true);
			client.setGameGuardOk(true);

			if (client.isBridgeDisconnectRequested())
			{
				login.leaveTrusted(account, client);
				return List.of();
			}

			int slot = slot(client, character);
			if (slot < 0)
			{
				login.leaveTrusted(account, client);
				return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_NO_CHARACTER));
			}

			if (!CharacterSelection.enter(client, slot))
			{
				if (client.getActiveChar() != null)
					return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_NOT_ALLOWED));

				if (client.bridgePlayerForCleanup() != null)
				{
					cleanupUnpublished(client);
					login.leaveTrusted(account, client);
					return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_NOT_ALLOWED));
				}

				login.leaveTrusted(account, client);
				return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_BACKEND));
			}

			published = true;
			if (!enterWorld(client))
				return List.of();
			return BridgeFrames.accepted(newSession(client), 1);
		}
		catch (RuntimeException e)
		{
			if (published || client.getActiveChar() != null)
			{
				client.requestBridgeDisconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure.internal());
				return List.of();
			}
			cleanupUnpublished(client);
			if (reserved)
				login.leaveTrusted(account, client);
			return List.of(BridgeFrames.openedRefused(BridgeFrames.REASON_BACKEND));
		}
	}

	/** The caller owns the client FIFO. A teleport queued by entry completes after this task returns. */
	static boolean enterWorld(L2Client client)
	{
		if (client.isBridgeDisconnectRequested())
			return false;
		try
		{
			var packet = com.l2jfree.gameserver.network.packets.client.EnterWorld.forBridge();
			packet.bindDirect(client);
			packet.run();
			if (!packet.failed())
				return !client.isBridgeDisconnectRequested();
		}
		catch (RuntimeException | Error failure)
		{
			// Entry owns the published character already; the one full cleanup must still run.
		}
		client.requestBridgeDisconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure.internal());
		return false;
	}

	private static int slot(L2Client client, String character)
	{
		CharSelectInfoPackage[] packages = new CharSelectionInfo(client).packages();
		for (int i = 0; i < packages.length; i++)
		{
			if (character.equals(packages[i].getName()))
				return i;
		}
		return -1;
	}

	private static void cleanupUnpublished(L2Client client)
	{
		L2Player loaded = client.bridgePlayerForCleanup();
		if (loaded == null || loaded == client.getActiveChar())
			return;

		client.discardOpeningPlayer(loaded);
		try
		{
			loaded.deleteMe();
		}
		catch (RuntimeException ignored)
		{
			// The opening did not publish the character. A failed cleanup must not start the full disconnect.
		}
	}

	private static final java.util.concurrent.ConcurrentMap<Long, L2Client> SESSIONS =
			new java.util.concurrent.ConcurrentHashMap<>();

	static void reapSessions()
	{
		SESSIONS.entrySet().removeIf(entry -> entry.getValue().isBridgeDisconnectFinished());
	}

	static int sessionCount()
	{
		return SESSIONS.size();
	}

	static L2Client session(long id)
	{
		L2Client client = SESSIONS.get(id);
		return client == null || client.isBridgeDisconnectRequested() ? null : client;
	}

	static long newSession(L2Client client)
	{
		for (int attempt = 0; attempt < 8; attempt++)
		{
			long id = java.util.concurrent.ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE;
			if (id != 0 && SESSIONS.putIfAbsent(id, client) == null)
				return id;
		}
		throw new IllegalStateException("Bridge session id collided");
	}
}
