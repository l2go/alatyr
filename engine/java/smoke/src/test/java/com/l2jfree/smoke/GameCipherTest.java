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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GameCipherTest
{
	private static byte[] key(int first)
	{
		byte[] key = new byte[16];
		key[0] = (byte)first;
		return key;
	}
	
	@Test
	void nothingIsEncryptedBeforeTheCipherIsEnabled()
	{
		GameCipher cipher = new GameCipher();
		cipher.setKey(key(0xAA));
		byte[] data = { 1, 2, 3 };
		
		cipher.encrypt(data, 0, 3);
		
		assertThat(data).containsExactly(1, 2, 3);
	}
	
	@Test
	void theFirstPacketIsEncryptedByTheRollingXorOfTheKey()
	{
		// worked out by hand: each byte is plain ^ key[i & 15] ^ the previous encrypted byte
		// 01 ^ AA ^ 00 = AB, 02 ^ 00 ^ AB = A9, 03 ^ 00 ^ A9 = AA
		GameCipher cipher = new GameCipher();
		cipher.setKey(key(0xAA));
		cipher.enable();
		byte[] data = { 1, 2, 3 };
		
		cipher.encrypt(data, 0, 3);
		
		assertThat(data).containsExactly((byte)0xAB, (byte)0xA9, (byte)0xAA);
	}
	
	@Test
	void theKeyAdvancesByTheSizeOfEveryPacket()
	{
		// after a packet of 3 bytes the word at key offset 8 is 3, so a second packet of 10 bytes uses it at index 8
		GameCipher cipher = new GameCipher();
		cipher.setKey(key(0));
		cipher.enable();
		cipher.encrypt(new byte[3], 0, 3);
		
		byte[] second = new byte[10];
		cipher.encrypt(second, 0, 10);
		
		// zeros with key 0 stay zero until index 8, where the key byte is 3: 0 ^ 3 ^ 0 = 3, then 0 ^ 0 ^ 3 = 3 for index 9
		assertThat(Arrays.copyOfRange(second, 0, 8)).containsOnly((byte)0);
		assertThat(second[8]).isEqualTo((byte)3);
		assertThat(second[9]).isEqualTo((byte)3);
	}
	
	@Test
	void whatOneEndEncryptsTheOtherDecryptsAndTheKeysStayInStep()
	{
		byte[] key = GameCipher.fullKey(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
		GameCipher client = new GameCipher();
		GameCipher server = new GameCipher();
		client.setKey(key);
		server.setKey(key);
		client.enable();
		server.enable();
		
		for (int round = 1; round <= 5; round++)
		{
			byte[] plain = new byte[round * 7];
			for (int i = 0; i < plain.length; i++)
				plain[i] = (byte)(i * 31 + round);
			byte[] wire = plain.clone();
			
			client.encrypt(wire, 0, wire.length);
			assertThat(wire).isNotEqualTo(plain);
			server.decrypt(wire, 0, wire.length);
			
			assertThat(wire).isEqualTo(plain);
		}
	}
	
	@Test
	void aPacketDecryptedOutOfOrderIsGarbage()
	{
		byte[] key = GameCipher.fullKey(new byte[] { 9, 8, 7, 6, 5, 4, 3, 2 });
		GameCipher client = new GameCipher();
		GameCipher server = new GameCipher();
		client.setKey(key);
		server.setKey(key);
		client.enable();
		server.enable();
		byte[] first = new byte[20];
		byte[] second = new byte[20];
		Arrays.fill(second, (byte)0x55);
		byte[] wireFirst = first.clone();
		byte[] wireSecond = second.clone();
		client.encrypt(wireFirst, 0, 20);
		client.encrypt(wireSecond, 0, 20);
		
		server.decrypt(wireSecond, 0, 20); // the second packet read first
		
		assertThat(wireSecond).isNotEqualTo(second);
	}
	
	@Test
	void theKeyOfTheKeyPacketGetsTheStaticTail()
	{
		byte[] key = GameCipher.fullKey(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
		
		assertThat(key).hasSize(16);
		assertThat(Arrays.copyOfRange(key, 0, 8)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
		assertThat(key[8]).isEqualTo((byte)0xc8);
		assertThat(key[15]).isEqualTo((byte)0x97);
	}
}
