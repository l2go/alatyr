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
 * A connection to the world port: framing plus the game cipher.
 */
final class GameConnection implements AutoCloseable
{
	private final FramedConnection _connection;
	private final GameCipher _cipher = new GameCipher();
	private int _skipped;

	GameConnection(FramedConnection connection)
	{
		_connection = connection;
	}

	GameCipher cipher()
	{
		return _cipher;
	}

	void send(byte[] payload) throws SmokeException
	{
		byte[] frame = payload.clone();
		_cipher.encrypt(frame, 0, frame.length);
		_connection.send(frame);
	}

	/**
	 * Reads one packet and decrypts it.
	 *
	 * @return the plain payload, starting with the opcode; never empty
	 */
	byte[] receive(long maxWaitMillis) throws SmokeException
	{
		byte[] frame = _connection.receive(maxWaitMillis);
		_cipher.decrypt(frame, 0, frame.length);
		if (frame.length == 0)
			throw new SmokeException("the world sent an empty packet");

		return frame;
	}

	/** @return how many other packets {@link #expect} skipped on its last call */
	int skipped()
	{
		return _skipped;
	}

	/**
	 * Reads packets until one has the given opcode. The world sends many packets that the test does not
	 * need (ActionFailed, system messages, item lists); they are read, decrypted, and skipped, because
	 * the cipher has to see every byte. A failure packet or the end of the session is an error.
	 *
	 * @return a reader behind the opcode
	 */
	PacketReader expect(int opcode, String name) throws SmokeException
	{
		_skipped = 0;
		while (true)
		{
			byte[] payload = receive(Long.MAX_VALUE);
			PacketReader in = new PacketReader(payload);
			int received = in.readC();
			if (received == opcode)
				return in;

			switch (received)
			{
				case GamePackets.OP_LOGIN_FAIL ->
				{
					int reason = in.readD();
					throw SmokeException.refused("the world answered LoginFail with the reason " + reason
							+ " instead of " + name + " (the session keys were not accepted)", reason);
				}
				case GamePackets.OP_CHARACTER_CREATE_FAIL ->
				{
					int reason = in.readD();
					throw SmokeException.refused("the world refused the new character with the reason " + reason
							+ " (" + GamePackets.describeCreateFail(reason) + ")", reason);
				}
				case GamePackets.OP_SERVER_CLOSE, GamePackets.OP_LEAVE_WORLD ->
				{
					throw new SmokeException("the world ended the session (opcode 0x" + Integer.toHexString(received)
							+ ") while the client waited for " + name);
				}
				default -> _skipped++;
			}
		}
	}

	@Override
	public void close()
	{
		_connection.close();
	}
}
