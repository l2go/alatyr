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

import java.math.BigInteger;

/**
 * The steps of the smoke test on the login port.
 */
final class LoginFlow
{
	/** The LoginFail reason "account already in use". */
	private static final int REASON_ALREADY_IN_USE = 7;
	private static final int SECOND_LOGIN_ATTEMPTS = 10;
	private static final long SECOND_LOGIN_WAIT_MILLIS = 8000L;
	private static final long SECOND_LOGIN_PAUSE_MILLIS = 1500L;

	private LoginFlow()
	{
	}

	/** What the login server handed out: the keys for the world and the world itself. */
	record Session(int loginOk1, int loginOk2, int playOk1, int playOk2, LoginPackets.WorldEntry world)
	{
	}

	/**
	 * Logs in, reads the server list, and asks for the only world: Init, GGAuth, LoginOk, ServerList, PlayOk.
	 */
	static Session selectWorld(SmokeClient.Config config, Deadline deadline, Reporter reporter, String account,
			String password) throws SmokeException
	{
		try (LoginConnection login = LoginConnection.open(config.host(), config.loginPort(), deadline))
		{
			reporter.begin("login: init packet");
			LoginPackets.Init init = login.receiveInit();
			BigInteger modulus = new BigInteger(1, init.modulus());
			if (modulus.bitLength() != 1024 || !modulus.testBit(0))
			{
				throw new SmokeException("the RSA modulus of the init packet is not a 1024-bit odd number "
						+ "(bits: " + modulus.bitLength() + "), so the unscrambling or the framing is wrong");
			}
			reporter.ok("session 0x" + Integer.toHexString(init.sessionId()) + ", protocol 0x"
					+ Integer.toHexString(init.protocolRevision()) + ", RSA-1024 key");

			reporter.begin("login: game guard");
			login.send(LoginPackets.authGameGuard(init.sessionId()));
			int echoed = login.expect(LoginPackets.OP_GG_AUTH, "GGAuth").readD();
			if (echoed != init.sessionId())
			{
				throw new SmokeException("GGAuth carries the session id 0x" + Integer.toHexString(echoed)
						+ " instead of 0x" + Integer.toHexString(init.sessionId()));
			}
			reporter.ok("session id echoed");

			reporter.begin("login: authentication");
			login.send(LoginPackets.requestAuthLogin(LoginPackets.publicKey(init.modulus()), account, password));
			PacketReader loginOk = login.expect(LoginPackets.OP_LOGIN_OK, "LoginOk");
			int loginOk1 = loginOk.readD();
			int loginOk2 = loginOk.readD();
			reporter.ok("account " + account + " accepted");

			reporter.begin("login: server list");
			login.send(LoginPackets.requestServerList(loginOk1, loginOk2));
			LoginPackets.ServerList list = LoginPackets.parseServerList(login.expect(LoginPackets.OP_SERVER_LIST,
					"ServerList"));
			if (list.worlds().size() != 1)
			{
				throw new SmokeException("the server list has " + list.worlds().size()
						+ " worlds, expected exactly one");
			}
			LoginPackets.WorldEntry world = list.worlds().get(0);
			if (!world.online())
				throw new SmokeException("the only world, id " + world.serverId() + ", is not online");

			reporter.ok("one world online: id " + world.serverId() + ", announced " + world.host() + ":"
					+ world.port() + ", " + world.players() + " of " + world.maxPlayers() + " players");

			reporter.begin("login: world selection");
			login.send(LoginPackets.requestServerLogin(loginOk1, loginOk2, world.serverId()));
			PacketReader playOk = login.expect(LoginPackets.OP_PLAY_OK, "PlayOk");
			int playOk1 = playOk.readD();
			int playOk2 = playOk.readD();
			reporter.ok("play keys received");

			return new Session(loginOk1, loginOk2, playOk1, playOk2, world);
		}
	}

	/**
	 * Logs in again with the same account and expects LoginOk. The world tells the login module that the
	 * player left after the logout, in its own time, so "account already in use" and a silent login server
	 * are retried for a while. Any other refusal fails at once.
	 */
	static void loginAgain(SmokeClient.Config config, Deadline deadline, Reporter reporter, String account,
			String password) throws SmokeException
	{
		reporter.begin("login: second login after the logout");
		SmokeException last = null;
		for (int attempt = 1; attempt <= SECOND_LOGIN_ATTEMPTS && !deadline.expired(); attempt++)
		{
			try (LoginConnection login = LoginConnection.open(config.host(), config.loginPort(), deadline))
			{
				LoginPackets.Init init = login.receiveInit();
				login.send(LoginPackets.authGameGuard(init.sessionId()));
				login.expect(LoginPackets.OP_GG_AUTH, "GGAuth");
				login.send(LoginPackets.requestAuthLogin(LoginPackets.publicKey(init.modulus()), account, password));
				login.expect(LoginPackets.OP_LOGIN_OK, "LoginOk", SECOND_LOGIN_WAIT_MILLIS);
				reporter.ok(attempt == 1 ? "the account was free at once" : "the account was free after " + attempt
						+ " attempts");
				return;
			}
			catch (SmokeException e)
			{
				boolean retry = e.kind() == SmokeException.Kind.TIMEOUT
						|| (e.kind() == SmokeException.Kind.REFUSED && e.reason() == REASON_ALREADY_IN_USE);
				if (!retry || deadline.expired())
					throw e;

				last = e;
				reporter.info("attempt " + attempt + " of " + SECOND_LOGIN_ATTEMPTS + ": " + e.getMessage()
						+ "; trying again");
				deadline.sleep(SECOND_LOGIN_PAUSE_MILLIS);
			}
		}

		if (last == null)
			throw new SmokeException("the time limit of " + deadline.seconds() + " seconds ended before the second login");

		throw new SmokeException("the account was still in use after " + SECOND_LOGIN_ATTEMPTS
				+ " attempts, so the leave of the world did not reach the login module (" + last.getMessage() + ")");
	}
}
