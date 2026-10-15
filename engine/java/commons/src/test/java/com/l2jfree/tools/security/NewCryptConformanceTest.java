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
package com.l2jfree.tools.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * The login cipher against independent implementations: Blowfish of the JDK for the block cipher (the login protocol
 * reads and writes the two 32-bit words of each block little-endian), and the checksum and the XOR pass written from
 * their published description.
 */
class NewCryptConformanceTest
{
	/** Blowfish of the JDK with the byte order of the login protocol: every 32-bit word is swapped before and after. */
	private static byte[] referenceBlowfish(boolean encrypt, byte[] key, byte[] data) throws Exception
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
	
	@Test
	void thePublishedBlowfishVectorComesOutWithItsWordsLittleEndian() throws Exception
	{
		// key 0000000000000000, plaintext 0000000000000000, ciphertext 4EF997456198DD78 (big-endian words)
		byte[] block = new byte[8];
		
		new NewCrypt(new byte[8]).crypt(block, 0, 8);
		
		assertThat(block).containsExactly((byte)0x45, (byte)0x97, (byte)0xF9, (byte)0x4E, (byte)0x78, (byte)0xDD,
				(byte)0x98, (byte)0x61);
	}
	
	@Test
	void theBlockCipherAgreesWithBlowfishOfTheJdkInBothDirections() throws Exception
	{
		Random random = new Random(11);
		for (int round = 0; round < 50; round++)
		{
			byte[] key = new byte[16];
			random.nextBytes(key);
			byte[] plain = new byte[8 * (1 + random.nextInt(20))];
			random.nextBytes(plain);
			
			byte[] ours = plain.clone();
			new NewCrypt(key).crypt(ours, 0, ours.length);
			assertThat(ours).as("encrypt, round %d", round).isEqualTo(referenceBlowfish(true, key, plain));
			
			byte[] back = ours.clone();
			new NewCrypt(key).decrypt(back, 0, back.length);
			assertThat(back).as("decrypt, round %d", round).isEqualTo(plain);
			assertThat(referenceBlowfish(false, key, ours)).isEqualTo(plain);
		}
	}
	
	@Test
	void theBlockCipherWorksOnAPartOfABuffer() throws Exception
	{
		byte[] key = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
		byte[] plain = new byte[16];
		new Random(3).nextBytes(plain);
		byte[] buffer = new byte[5 + 16 + 3];
		System.arraycopy(plain, 0, buffer, 5, 16);
		
		new NewCrypt(key).crypt(buffer, 5, 16);
		
		assertThat(Arrays.copyOfRange(buffer, 0, 5)).containsOnly((byte)0);
		assertThat(Arrays.copyOfRange(buffer, 5, 21)).isEqualTo(referenceBlowfish(true, key, plain));
	}
	
	@Test
	void theChecksumIsTheXorOfTheWordsBeforeTheLastOne()
	{
		// the words 1 and 2, then room for the checksum: 1 ^ 2 = 3
		byte[] raw = { 1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0 };
		
		NewCrypt.appendChecksum(raw, 0, raw.length);
		
		assertThat(raw).containsExactly(1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0);
		assertThat(NewCrypt.verifyChecksum(raw, 0, raw.length)).isTrue();
	}
	
	@Test
	void aFlippedBitFailsTheChecksum()
	{
		byte[] raw = { 1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0 };
		NewCrypt.appendChecksum(raw, 0, raw.length);
		raw[5] ^= 0x10;
		
		assertThat(NewCrypt.verifyChecksum(raw, 0, raw.length)).isFalse();
	}
	
	@Test
	void aSizeThatIsNotAMultipleOfFourOrHasNoRoomForTheChecksumFails()
	{
		assertThat(NewCrypt.verifyChecksum(new byte[10], 0, 10)).isFalse();
		assertThat(NewCrypt.verifyChecksum(new byte[4], 0, 4)).isFalse();
	}
	
	@Test
	void theChecksumOfAPartOfABufferIgnoresTheBytesAround()
	{
		byte[] raw = new byte[3 + 12 + 2];
		Arrays.fill(raw, (byte)0x7f);
		putWord(raw, 3, 1);
		putWord(raw, 7, 2);
		
		NewCrypt.appendChecksum(raw, 3, 12);
		
		assertThat(word(raw, 11)).isEqualTo(3);
		assertThat(NewCrypt.verifyChecksum(raw, 3, 12)).isTrue();
		assertThat(Arrays.copyOfRange(raw, 0, 3)).containsOnly((byte)0x7f);
		assertThat(Arrays.copyOfRange(raw, 15, 17)).containsOnly((byte)0x7f);
	}
	
	/** Undoes the XOR pass of the Init packet, as a client does it, from the published description. */
	private static void decodeXorPass(byte[] raw, int offset, int size)
	{
		int stop = offset + size - 8;
		int running = word(raw, stop);
		for (int pos = stop - 4; pos >= offset + 4; pos -= 4)
		{
			int plain = word(raw, pos) ^ running;
			running -= plain;
			putWord(raw, pos, plain);
		}
	}
	
	@Test
	void theXorPassOfTheInitPacketIsUndoneByTheReferenceDecoder()
	{
		Random random = new Random(5);
		for (int words = 4; words <= 12; words++)
		{
			byte[] original = new byte[words * 4];
			random.nextBytes(original);
			byte[] encoded = original.clone();
			
			NewCrypt.encXORPass(encoded, 0, encoded.length, random.nextInt());
			decodeXorPass(encoded, 0, encoded.length);
			
			int size = encoded.length;
			// the first word and the last word are not part of the pass; the word before the last one holds the key
			assertThat(Arrays.copyOfRange(encoded, 0, size - 8)).as("%d words", words)
					.isEqualTo(Arrays.copyOfRange(original, 0, size - 8));
			assertThat(Arrays.copyOfRange(encoded, size - 4, size)).isEqualTo(Arrays.copyOfRange(original, size - 4, size));
		}
	}
	
	@Test
	void theXorPassWorksOnAPartOfABuffer()
	{
		byte[] original = new byte[5 + 32 + 4];
		new Random(9).nextBytes(original);
		byte[] encoded = original.clone();
		
		NewCrypt.encXORPass(encoded, 5, 32, 0x1234abcd);
		decodeXorPass(encoded, 5, 32);
		
		assertThat(Arrays.copyOfRange(encoded, 0, 5 + 32 - 8)).isEqualTo(Arrays.copyOfRange(original, 0, 5 + 32 - 8));
		assertThat(Arrays.copyOfRange(encoded, 5 + 32 - 4, original.length))
				.isEqualTo(Arrays.copyOfRange(original, 5 + 32 - 4, original.length));
	}
}
