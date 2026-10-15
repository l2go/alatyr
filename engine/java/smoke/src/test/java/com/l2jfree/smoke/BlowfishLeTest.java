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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlowfishLeTest
{
	@Test
	void theStandardBlowfishVectorComesOutWithItsWordsLittleEndian() throws Exception
	{
		// the published vector: key 0000000000000000, plaintext 0000000000000000, ciphertext 4EF997456198DD78;
		// the login protocol reads and writes each 32-bit word little-endian, so the words come out reversed
		byte[] block = new byte[8];
		
		new BlowfishLe(new byte[8]).encrypt(block, 0, 8);
		
		assertThat(block).containsExactly((byte)0x45, (byte)0x97, (byte)0xF9, (byte)0x4E, (byte)0x78, (byte)0xDD,
				(byte)0x98, (byte)0x61);
	}
	
	@Test
	void decryptionIsTheInverseOfEncryption() throws Exception
	{
		BlowfishLe cipher = new BlowfishLe(LoginCipher.STATIC_KEY);
		byte[] plain = new byte[24];
		for (int i = 0; i < plain.length; i++)
			plain[i] = (byte)(i * 7 + 3);
		byte[] data = plain.clone();
		
		cipher.encrypt(data, 0, data.length);
		assertThat(data).isNotEqualTo(plain);
		cipher.decrypt(data, 0, data.length);
		
		assertThat(data).isEqualTo(plain);
	}
	
	@Test
	void aBufferThatIsNotAMultipleOfEightIsRefused() throws Exception
	{
		BlowfishLe cipher = new BlowfishLe(new byte[8]);
		
		assertThatThrownBy(() -> cipher.encrypt(new byte[12], 0, 12)).isInstanceOf(SmokeException.class);
	}
}
