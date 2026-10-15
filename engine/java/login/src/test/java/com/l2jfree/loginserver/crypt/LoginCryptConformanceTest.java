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
package com.l2jfree.loginserver.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * The login cipher against a client written from the published protocol: the first packet (Init) is XOR-encoded and
 * encrypted with a Blowfish key that every client knows, and every later packet carries a checksum and is encrypted with
 * the key the server announced. Blowfish comes from the JDK, with the two 32-bit words of each block little-endian.
 */
class LoginCryptConformanceTest
{
	private static final byte[] STATIC_KEY = { (byte)0x6b, (byte)0x60, (byte)0xcb, (byte)0x5b, (byte)0x82,
			(byte)0xce, (byte)0x90, (byte)0xb1, (byte)0xcc, (byte)0x2b, (byte)0x6c, (byte)0x55, (byte)0x6c, (byte)0x6c,
			(byte)0x6c, (byte)0x6c };
	private static final byte[] SESSION_KEY = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
	
	private static byte[] blowfish(boolean encrypt, byte[] key, byte[] data) throws Exception
	{
		Cipher cipher = Cipher.getInstance("Blowfish/ECB/NoPadding");
		cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "Blowfish"));
		return swapWords(cipher.doFinal(swapWords(data)));
	}
	
	private static byte[] swapWords(byte[] data)
	{
		byte[] swapped = new byte[data.length];
		for (int i = 0; i < data.length; i += 4)
			for (int j = 0; j < 4; j++)
				swapped[i + j] = data[i + 3 - j];
		return swapped;
	}
	
	private static int word(byte[] raw, int offset)
	{
		return ByteBuffer.wrap(raw, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}
	
	private static void putWord(byte[] raw, int offset, int value)
	{
		ByteBuffer.wrap(raw, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
	}
	
	private static boolean checksumHolds(byte[] block)
	{
		int checksum = 0;
		for (int i = 0; i < block.length - 4; i += 4)
			checksum ^= word(block, i);
		return word(block, block.length - 4) == checksum;
	}
	
	private static byte[] payload(int size, long seed)
	{
		byte[] payload = new byte[size];
		new Random(seed).nextBytes(payload);
		return payload;
	}
	
	private static LoginCrypt serverWithKey() 
	{
		LoginCrypt crypt = new LoginCrypt();
		crypt.setKey(SESSION_KEY);
		return crypt;
	}
	
	@Test
	void theFirstPacketIsXorEncodedAndEncryptedWithTheStaticKey() throws Exception
	{
		byte[] payload = payload(20, 1);
		byte[] raw = new byte[64];
		System.arraycopy(payload, 0, raw, 0, payload.length);
		
		int size = serverWithKey().encrypt(raw, 0, payload.length);
		
		// 20 bytes of payload, 4 for the checksum, 4 for the XOR key, then padding to a multiple of 8
		assertThat(size).isEqualTo(32);
		byte[] block = blowfish(false, STATIC_KEY, Arrays.copyOf(raw, size));
		int stop = size - 8;
		int running = word(block, stop);
		for (int pos = stop - 4; pos >= 4; pos -= 4)
		{
			int plain = word(block, pos) ^ running;
			running -= plain;
			putWord(block, pos, plain);
		}
		assertThat(Arrays.copyOf(block, payload.length)).isEqualTo(payload);
	}
	
	@Test
	void everyLaterPacketCarriesAChecksumAndIsEncryptedWithTheSessionKey() throws Exception
	{
		LoginCrypt crypt = serverWithKey();
		crypt.encrypt(new byte[64], 0, 20); // the first packet uses the static key
		
		for (int length : new int[] { 1, 7, 20, 21, 28, 50 })
		{
			byte[] payload = payload(length, length);
			byte[] raw = new byte[length + 16];
			System.arraycopy(payload, 0, raw, 0, length);
			
			int size = crypt.encrypt(raw, 0, length);
			
			assertThat(size % 8).as("length %d", length).isZero();
			assertThat(size).as("length %d", length).isGreaterThanOrEqualTo(length + 4);
			byte[] block = blowfish(false, SESSION_KEY, Arrays.copyOf(raw, size));
			assertThat(checksumHolds(block)).as("checksum, length %d", length).isTrue();
			assertThat(Arrays.copyOf(block, length)).as("payload, length %d", length).isEqualTo(payload);
		}
	}
	
	@Test
	void aPacketOfTheClientWithAValidChecksumIsAccepted() throws Exception
	{
		LoginCrypt crypt = serverWithKey();
		byte[] block = new byte[24];
		System.arraycopy(payload(18, 5), 0, block, 0, 18);
		int checksum = 0;
		for (int i = 0; i < block.length - 4; i += 4)
			checksum ^= word(block, i);
		putWord(block, block.length - 4, checksum);
		byte[] wire = blowfish(true, SESSION_KEY, block);
		
		boolean accepted = crypt.decrypt(wire, 0, wire.length);
		
		assertThat(accepted).isTrue();
		assertThat(Arrays.copyOf(wire, 18)).isEqualTo(Arrays.copyOf(block, 18));
	}
	
	@Test
	void aPacketOfTheClientWithABrokenChecksumIsRefused() throws Exception
	{
		LoginCrypt crypt = serverWithKey();
		byte[] block = new byte[24];
		System.arraycopy(payload(18, 6), 0, block, 0, 18);
		int checksum = 0;
		for (int i = 0; i < block.length - 4; i += 4)
			checksum ^= word(block, i);
		putWord(block, block.length - 4, checksum ^ 1);
		byte[] wire = blowfish(true, SESSION_KEY, block);
		
		assertThat(crypt.decrypt(wire, 0, wire.length)).isFalse();
	}
}
