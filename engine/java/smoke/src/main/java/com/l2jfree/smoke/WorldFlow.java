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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The steps of the smoke test on the world port.
 */
final class WorldFlow
{
	/** The class id of the human fighter, a valid first class with base level 1. */
	private static final int FIGHTER_CLASS_ID = 0;
	private static final int ANNOUNCED_CONNECT_MILLIS = 3000;
	/** The client reads what the world still sends after UserInfo until it is quiet for this long. */
	private static final long QUIET_MILLIS = 500L;
	private static final long SETTLE_MILLIS = 3000L;
	private static final int LOGOUT_ATTEMPTS = 3;
	private static final long LOGOUT_WAIT_MILLIS = 5000L;
	private static final long CLOSE_WAIT_MILLIS = 5000L;

	private WorldFlow()
	{
	}

	/**
	 * Plays one session: ProtocolVersion, KeyPacket, AuthLogin, CharSelectionInfo, character creation,
	 * CharacterSelected, EnterWorld, UserInfo, Logout, LeaveWorld.
	 *
	 * @param expectedCharacters how many characters the account has on entry, or {@link SmokeClient.Config#UNCHECKED}
	 */
	static void play(SmokeClient.Config config, Deadline deadline, Reporter reporter, LoginFlow.Session session,
			String account, int expectedCharacters, String characterName) throws SmokeException
	{
		reporter.begin("world: connect");
		try (GameConnection game = connect(config, deadline, reporter, session.world()))
		{
			reporter.begin("world: protocol version");
			game.send(GamePackets.protocolVersion(config.protocolRevision()));
			GamePackets.KeyPacket keyPacket = GamePackets.parseKeyPacket(game.expect(GamePackets.OP_KEY_PACKET,
					"KeyPacket"));
			if (!keyPacket.protocolOk())
			{
				throw new SmokeException("the world refused the protocol revision " + config.protocolRevision()
						+ " (see MinProtocolRevision and MaxProtocolRevision, or pass --protocol-revision)");
			}
			game.cipher().setKey(GameCipher.fullKey(keyPacket.key()));
			game.cipher().enable();
			reporter.ok("revision " + config.protocolRevision() + " accepted, game cipher on");

			reporter.begin("world: authentication");
			game.send(GamePackets.authLogin(account, session.playOk1(), session.playOk2(), session.loginOk1(),
					session.loginOk2()));
			GamePackets.CharacterList before = GamePackets.parseCharacterList(game.expect(
					GamePackets.OP_CHAR_SELECTION_INFO, "CharSelectionInfo"));
			if (expectedCharacters != SmokeClient.Config.UNCHECKED && before.count() != expectedCharacters)
			{
				throw new SmokeException("the character list has " + before.count() + " characters instead of "
						+ expectedCharacters);
			}
			reporter.ok(before.count() + " characters in the list");

			reporter.begin("world: character templates");
			game.send(GamePackets.newCharacterInit());
			List<GamePackets.Template> templates = GamePackets.parseTemplates(game.expect(
					GamePackets.OP_NEW_CHARACTER_SUCCESS, "NewCharacterSuccess"));
			GamePackets.Template fighter = null;
			for (GamePackets.Template template : templates)
			{
				if (template.classId() == FIGHTER_CLASS_ID)
				{
					fighter = template;
					break;
				}
			}
			if (fighter == null)
				throw new SmokeException("the world offers no template for the class " + FIGHTER_CLASS_ID);

			reporter.ok(templates.size() + " templates, class " + fighter.classId() + " of race " + fighter.race());

			reporter.begin("world: character creation");
			game.send(GamePackets.newCharacter(characterName, fighter, 0, 0, 0, 0));
			game.expect(GamePackets.OP_CHARACTER_CREATE_SUCCESS, "CharacterCreateSuccess");
			GamePackets.CharacterList after = GamePackets.parseCharacterList(game.expect(
					GamePackets.OP_CHAR_SELECTION_INFO, "CharSelectionInfo"));
			if (after.count() != before.count() + 1)
			{
				throw new SmokeException("the character list has " + after.count() + " characters after the creation, "
						+ "expected " + (before.count() + 1));
			}
			if (after.count() == 1)
			{
				if (!characterName.equals(after.firstName()))
				{
					throw new SmokeException("the only character of the list is named '" + after.firstName()
							+ "' instead of '" + characterName + "'");
				}
				if (!account.equals(after.firstAccount()))
				{
					throw new SmokeException("the list names the account '" + after.firstAccount() + "' instead of '"
							+ account + "'");
				}
				if (after.firstSessionId() != session.playOk1())
				{
					throw new SmokeException("the list carries the session id 0x"
							+ Integer.toHexString(after.firstSessionId()) + " instead of the play key 0x"
							+ Integer.toHexString(session.playOk1()));
				}
			}
			reporter.ok("character " + characterName + " created, " + after.count() + " in the list");

			reporter.begin("world: character selection");
			game.send(GamePackets.characterSelected(0));
			String selected = game.expect(GamePackets.OP_CHAR_SELECTED, "CharSelected").readS();
			if (after.count() == 1 && !characterName.equals(selected))
			{
				throw new SmokeException("the world selected the character '" + selected + "' instead of '"
						+ characterName + "'");
			}
			reporter.ok("character " + selected + " selected");

			reporter.begin("world: enter world");
			game.send(GamePackets.enterWorld());
			PacketReader userInfo = game.expect(GamePackets.OP_USER_INFO, "UserInfo");
			userInfo.skip(16); // x, y, z, vehicle id
			int objectId = userInfo.readD();
			String userName = userInfo.readS();
			if (!userName.equals(selected))
			{
				throw new SmokeException("UserInfo describes '" + userName + "' instead of '" + selected + "'");
			}
			int skippedBefore = game.skipped();
			int trailing = settle(game, deadline);
			reporter.ok("UserInfo of " + userName + ", object id " + objectId + ", after " + skippedBefore
					+ " other packets and " + trailing + " more");

			reporter.begin("world: logout");
			logout(game, deadline);
			reporter.ok("LeaveWorld received, the server closed the connection");
		}
	}

	/**
	 * Connects to the world. The server list names an address; when the client cannot reach it (the
	 * default 127.0.0.1 is the client's own host when the client does not run on the server host), it
	 * uses its own {@code --host}. The port is {@code --world-port}.
	 */
	private static GameConnection connect(SmokeClient.Config config, Deadline deadline, Reporter reporter,
			LoginPackets.WorldEntry world) throws SmokeException
	{
		String announced = world.host();
		boolean usable = !announced.isEmpty() && !"0.0.0.0".equals(announced);
		if (world.port() != config.worldPort())
		{
			reporter.info("the server list announces the world port " + world.port() + ", the client uses --world-port "
					+ config.worldPort());
		}

		if (usable && !announced.equals(config.host()))
		{
			try
			{
				FramedConnection connection = FramedConnection.open("world", announced, config.worldPort(), deadline,
						false, ANNOUNCED_CONNECT_MILLIS);
				reporter.ok("connected to the announced address " + announced + ":" + config.worldPort());
				return new GameConnection(connection);
			}
			catch (SmokeException e)
			{
				if (deadline.expired())
					throw e;

				reporter.info("the announced world address " + announced + " is not usable (" + e.getMessage()
						+ "), the client uses its own host " + config.host());
			}
		}

		FramedConnection connection = FramedConnection.open("world", config.host(), config.worldPort(), deadline);
		reporter.ok("connected to " + config.host() + ":" + config.worldPort() + (usable && announced.equals(
				config.host()) ? " (the announced address)" : " (the host of the client)"));
		return new GameConnection(connection);
	}

	/**
	 * Reads what the world still sends after UserInfo until the line is quiet.
	 *
	 * @return the number of packets read
	 */
	private static int settle(GameConnection game, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SETTLE_MILLIS);
		int packets = 0;
		while (System.nanoTime() < until)
		{
			try
			{
				game.receive(QUIET_MILLIS);
				packets++;
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					return packets;

				throw e;
			}
		}
		return packets;
	}

	/** Sends Logout until LeaveWorld arrives, then waits for the server to close the connection. */
	private static void logout(GameConnection game, Deadline deadline) throws SmokeException
	{
		boolean left = false;
		for (int attempt = 1; attempt <= LOGOUT_ATTEMPTS && !left; attempt++)
		{
			game.send(GamePackets.logout());
			left = awaitLeave(game, deadline);
		}
		if (!left)
			throw new SmokeException("the world did not answer Logout with LeaveWorld");

		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_WAIT_MILLIS);
		while (true)
		{
			long left2 = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (left2 <= 0L)
				throw new SmokeException("the world did not close the connection after LeaveWorld");

			try
			{
				game.receive(left2);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.CLOSED)
					return;

				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					throw new SmokeException("the world did not close the connection after LeaveWorld");

				throw e;
			}
		}
	}

	private static boolean awaitLeave(GameConnection game, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOGOUT_WAIT_MILLIS);
		while (true)
		{
			long left = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (left <= 0L)
				return false;

			byte[] payload;
			try
			{
				payload = game.receive(left);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					return false;

				throw e;
			}

			if ((payload[0] & 0xff) == GamePackets.OP_LEAVE_WORLD)
				return true;
		}
	}
}
