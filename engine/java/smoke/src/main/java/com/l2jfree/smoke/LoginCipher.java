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
 * The client side of the login protocol cipher.
 * <ul>
 * <li>The Init packet of the server is encrypted with the static Blowfish key and scrambled by an
 * XOR pass. It carries the dynamic Blowfish key.</li>
 * <li>Every other packet, in both directions, is encrypted with the dynamic key. The plain data is
 * padded with zeros, the last four bytes of the padded block are the XOR checksum of all the bytes
 * before them, and the whole block is a multiple of 8 bytes.</li>
 * </ul>
 */
final class LoginCipher
{
	/** The key of the Init packet. It is public: every client has it. */
	static final byte[] STATIC_KEY = { (byte)0x6b, (byte)0x60, (byte)0xcb, (byte)0x5b, (byte)0x82, (byte)0xce,
			(byte)0x90, (byte)0xb1, (byte)0xcc, (byte)0x2b, (byte)0x6c, (byte)0x55, (byte)0x6c, (byte)0x6c,
			(byte)0x6c, (byte)0x6c };

	private final BlowfishLe _static;
	private BlowfishLe _dynamic;

	LoginCipher() throws SmokeException
	{
		_static = new BlowfishLe(STATIC_KEY);
	}

	/**
	 * Decrypts the first packet of the server.
	 *
	 * @param frame the payload of the frame, as it came from the wire
	 * @return the plain payload, starting with the opcode; the frame is not changed
	 */
	byte[] openInit(byte[] frame) throws SmokeException
	{
		if (frame.length < 16 || frame.length % 8 != 0)
			throw new SmokeException("the init packet has the invalid size " + frame.length);

		byte[] payload = frame.clone();
		_static.decrypt(payload, 0, payload.length);
		decXorPass(payload, payload.length);
		return payload;
	}

	/** Installs the dynamic key that the Init packet carried. */
	void setKey(byte[] key) throws SmokeException
	{
		_dynamic = new BlowfishLe(key);
	}

	/**
	 * Pads, adds the checksum, and encrypts a client packet.
	 *
	 * @param payload the plain payload, starting with the opcode
	 * @return the payload of the frame to send
	 */
	byte[] seal(byte[] payload) throws SmokeException
	{
		requireKey();
		int size = (payload.length + 4 + 7) & ~7;
		byte[] buffer = new byte[size];
		System.arraycopy(payload, 0, buffer, 0, payload.length);
		appendChecksum(buffer, size);
		_dynamic.encrypt(buffer, 0, size);
		return buffer;
	}

	/**
	 * Decrypts a server packet and checks its checksum.
	 *
	 * @param frame the payload of the frame, as it came from the wire
	 * @return the plain block: the packet, the padding, and the checksum; the first byte is the opcode
	 */
	byte[] open(byte[] frame) throws SmokeException
	{
		requireKey();
		if (frame.length < 8 || frame.length % 8 != 0)
			throw new SmokeException("a login packet has the invalid size " + frame.length);

		byte[] payload = frame.clone();
		_dynamic.decrypt(payload, 0, payload.length);
		if (!verifyChecksum(payload, payload.length))
			throw new SmokeException("a packet of the login server has a wrong checksum");

		return payload;
	}

	private void requireKey() throws SmokeException
	{
		if (_dynamic == null)
			throw new SmokeException("the dynamic Blowfish key is not known before the init packet");
	}

	/**
	 * Writes the XOR of all 32-bit words before the last one into the last four bytes.
	 *
	 * @param size the size of the block including the four checksum bytes
	 */
	static void appendChecksum(byte[] raw, int size)
	{
		int checksum = 0;
		for (int i = 0; i < size - 4; i += 4)
			checksum ^= readInt(raw, i);

		writeInt(raw, size - 4, checksum);
	}

	/**
	 * @param size the size of the block including the four checksum bytes
	 * @return true when the last word is the XOR of all the words before it
	 */
	static boolean verifyChecksum(byte[] raw, int size)
	{
		if ((size & 3) != 0 || size <= 4)
			return false;

		int checksum = 0;
		for (int i = 0; i < size - 4; i += 4)
			checksum ^= readInt(raw, i);

		return readInt(raw, size - 4) == checksum;
	}

	/**
	 * Undoes the XOR pass of the Init packet. The server XOR-encodes the words from offset 4 up to
	 * {@code size - 8} with a running sum that starts at a random key, and stores the final sum in the
	 * word at {@code size - 8}. Going backwards from that word restores the plain words. The first word
	 * and the last word are not part of the pass.
	 */
	static void decXorPass(byte[] raw, int size)
	{
		int stop = size - 8;
		int ecx = readInt(raw, stop);
		for (int pos = stop - 4; pos >= 4; pos -= 4)
		{
			int edx = readInt(raw, pos);
			edx ^= ecx;
			ecx -= edx;
			writeInt(raw, pos, edx);
		}
	}

	/**
	 * Undoes the scrambling of the RSA modulus in the Init packet: the four steps of the server in the
	 * reverse order, each of which is its own inverse.
	 *
	 * @param scrambled the 128 bytes from the Init packet
	 * @return the 128 bytes of the modulus, most significant byte first
	 */
	static byte[] unscrambleModulus(byte[] scrambled)
	{
		byte[] n = scrambled.clone();

		// undo step 4: the last 0x40 bytes were XORed with the first 0x40
		for (int i = 0; i < 0x40; i++)
			n[0x40 + i] ^= n[i];

		// undo step 3: bytes 0x0d-0x10 were XORed with bytes 0x34-0x37
		for (int i = 0; i < 4; i++)
			n[0x0d + i] ^= n[0x34 + i];

		// undo step 2: the first 0x40 bytes were XORed with the last 0x40
		for (int i = 0; i < 0x40; i++)
			n[i] ^= n[0x40 + i];

		// undo step 1: bytes 0x00-0x03 and 0x4d-0x50 were swapped
		for (int i = 0; i < 4; i++)
		{
			byte temp = n[i];
			n[i] = n[0x4d + i];
			n[0x4d + i] = temp;
		}

		return n;
	}

	private static int readInt(byte[] raw, int offset)
	{
		return (raw[offset] & 0xff) | ((raw[offset + 1] & 0xff) << 8) | ((raw[offset + 2] & 0xff) << 16)
				| ((raw[offset + 3] & 0xff) << 24);
	}

	private static void writeInt(byte[] raw, int offset, int value)
	{
		raw[offset] = (byte)value;
		raw[offset + 1] = (byte)(value >>> 8);
		raw[offset + 2] = (byte)(value >>> 16);
		raw[offset + 3] = (byte)(value >>> 24);
	}
}
