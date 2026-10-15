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

/**
 * A connection to the login port: framing plus the login cipher.
 */
final class LoginConnection implements AutoCloseable
{
	private final FramedConnection _connection;
	private final LoginCipher _cipher;

	private LoginConnection(FramedConnection connection, LoginCipher cipher)
	{
		_connection = connection;
		_cipher = cipher;
	}

	static LoginConnection open(String host, int port, Deadline deadline) throws SmokeException
	{
		LoginCipher cipher = new LoginCipher();
		return new LoginConnection(FramedConnection.open("login server", host, port, deadline), cipher);
	}

	/** Reads the Init packet and installs the dynamic key it carries. */
	LoginPackets.Init receiveInit() throws SmokeException
	{
		byte[] payload = _cipher.openInit(_connection.receive(Long.MAX_VALUE));
		LoginPackets.Init init = LoginPackets.parseInit(payload);
		_cipher.setKey(init.blowfishKey());
		return init;
	}

	void send(byte[] payload) throws SmokeException
	{
		_connection.send(_cipher.seal(payload));
	}

	PacketReader expect(int opcode, String name) throws SmokeException
	{
		return expect(opcode, name, Long.MAX_VALUE);
	}

	/**
	 * Reads the next packet, which must have the given opcode.
	 *
	 * @return a reader behind the opcode
	 * @throws SmokeException of kind REFUSED for LoginFail and PlayFail, otherwise FAILURE for any other
	 *             packet
	 */
	PacketReader expect(int opcode, String name, long maxWaitMillis) throws SmokeException
	{
		byte[] payload = _cipher.open(_connection.receive(maxWaitMillis));
		PacketReader in = new PacketReader(payload);
		int received = in.readC();
		if (received == opcode)
			return in;

		if (received == LoginPackets.OP_LOGIN_FAIL || received == LoginPackets.OP_PLAY_FAIL)
		{
			int reason = in.readC();
			throw SmokeException.refused("the login server answered " + (received == LoginPackets.OP_LOGIN_FAIL
					? "LoginFail" : "PlayFail") + " with the reason " + reason + " ("
					+ LoginPackets.describeReason(reason) + ") instead of " + name, reason);
		}

		String hint = "";
		if (received == LoginPackets.OP_SERVER_LIST && opcode == LoginPackets.OP_LOGIN_OK)
			hint = "; the server skipped LoginOk, so ShowLicence is False, which the smoke test does not support";

		throw new SmokeException("expected " + name + " (0x" + Integer.toHexString(opcode) + ") from the login server "
				+ "but received the opcode 0x" + Integer.toHexString(received) + hint);
	}

	@Override
	public void close()
	{
		_connection.close();
	}
}
