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
 * The client side of the game protocol cipher: a rolling XOR with a 16-byte key. Each direction has
 * its own copy of the key. After every packet, the 32-bit little-endian word at key offset 8 grows by
 * the size of the packet, so the two ends stay in step as long as they count the same bytes.
 * <p>
 * The first packet in each direction is not encrypted and does not advance the key: the client sends
 * ProtocolVersion in the clear, and the server answers with KeyPacket in the clear. The client calls
 * {@link #enable()} after it has read KeyPacket; every later packet is encrypted.
 */
final class GameCipher
{
	/** The server appends these eight bytes to the eight bytes it sends in KeyPacket. */
	private static final byte[] STATIC_TAIL = { (byte)0xc8, (byte)0x27, (byte)0x93, (byte)0x01, (byte)0xa1,
			(byte)0x6c, (byte)0x31, (byte)0x97 };

	private final byte[] _inKey = new byte[16];
	private final byte[] _outKey = new byte[16];
	private boolean _enabled;

	/**
	 * @param keyPacketKey the eight key bytes of KeyPacket
	 * @return the full 16-byte key
	 */
	static byte[] fullKey(byte[] keyPacketKey)
	{
		if (keyPacketKey.length != 8)
			throw new IllegalArgumentException("KeyPacket carries eight key bytes, not " + keyPacketKey.length);

		byte[] key = new byte[16];
		System.arraycopy(keyPacketKey, 0, key, 0, 8);
		System.arraycopy(STATIC_TAIL, 0, key, 8, 8);
		return key;
	}

	void setKey(byte[] key)
	{
		if (key.length != 16)
			throw new IllegalArgumentException("the game key has 16 bytes, not " + key.length);

		System.arraycopy(key, 0, _inKey, 0, 16);
		System.arraycopy(key, 0, _outKey, 0, 16);
	}

	void enable()
	{
		_enabled = true;
	}

	boolean isEnabled()
	{
		return _enabled;
	}

	/** Encrypts a packet of the client in place. It does nothing before {@link #enable()}. */
	void encrypt(byte[] raw, int offset, int size)
	{
		if (!_enabled)
			return;

		int previous = 0;
		for (int i = 0; i < size; i++)
		{
			int plain = raw[offset + i] & 0xff;
			previous = (plain ^ (_outKey[i & 15] & 0xff) ^ previous) & 0xff;
			raw[offset + i] = (byte)previous;
		}

		advance(_outKey, size);
	}

	/** Decrypts a packet of the server in place. It does nothing before {@link #enable()}. */
	void decrypt(byte[] raw, int offset, int size)
	{
		if (!_enabled)
			return;

		int previous = 0;
		for (int i = 0; i < size; i++)
		{
			int encrypted = raw[offset + i] & 0xff;
			raw[offset + i] = (byte)(encrypted ^ (_inKey[i & 15] & 0xff) ^ previous);
			previous = encrypted;
		}

		advance(_inKey, size);
	}

	private static void advance(byte[] key, int size)
	{
		int word = (key[8] & 0xff) | ((key[9] & 0xff) << 8) | ((key[10] & 0xff) << 16) | ((key[11] & 0xff) << 24);
		word += size;
		key[8] = (byte)word;
		key[9] = (byte)(word >>> 8);
		key[10] = (byte)(word >>> 16);
		key[11] = (byte)(word >>> 24);
	}
}
