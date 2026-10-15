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

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Blowfish as the Lineage II login protocol uses it: the standard algorithm in ECB mode, but the
 * two 32-bit words of every 8-byte block are read and written little-endian. The JCE cipher is
 * big-endian, so each word is byte-swapped before and after it.
 */
final class BlowfishLe
{
	private static final int BLOCK = 8;

	private final Cipher _encrypt;
	private final Cipher _decrypt;

	BlowfishLe(byte[] key) throws SmokeException
	{
		try
		{
			SecretKeySpec spec = new SecretKeySpec(key, "Blowfish");
			_encrypt = Cipher.getInstance("Blowfish/ECB/NoPadding");
			_encrypt.init(Cipher.ENCRYPT_MODE, spec);
			_decrypt = Cipher.getInstance("Blowfish/ECB/NoPadding");
			_decrypt.init(Cipher.DECRYPT_MODE, spec);
		}
		catch (GeneralSecurityException | IllegalArgumentException e)
		{
			throw new SmokeException("Blowfish is not usable with a key of " + key.length + " bytes", e);
		}
	}

	/** Encrypts {@code size} bytes at {@code offset} in place; {@code size} must be a multiple of 8. */
	void encrypt(byte[] data, int offset, int size) throws SmokeException
	{
		process(_encrypt, data, offset, size);
	}

	/** Decrypts {@code size} bytes at {@code offset} in place; {@code size} must be a multiple of 8. */
	void decrypt(byte[] data, int offset, int size) throws SmokeException
	{
		process(_decrypt, data, offset, size);
	}

	private static void process(Cipher cipher, byte[] data, int offset, int size) throws SmokeException
	{
		if (size < 0 || size % BLOCK != 0 || offset < 0 || offset + size > data.length)
			throw new SmokeException("a Blowfish buffer of " + size + " bytes is not a multiple of 8");

		byte[] block = new byte[size];
		swapWords(data, offset, block, 0, size);

		byte[] result;
		try
		{
			result = cipher.doFinal(block);
		}
		catch (GeneralSecurityException e)
		{
			throw new SmokeException("Blowfish failed", e);
		}

		swapWords(result, 0, data, offset, size);
	}

	/** Reverses the byte order of every 4-byte word. */
	private static void swapWords(byte[] source, int sourceOffset, byte[] target, int targetOffset, int size)
	{
		for (int i = 0; i < size; i += 4)
		{
			byte b0 = source[sourceOffset + i];
			byte b1 = source[sourceOffset + i + 1];
			byte b2 = source[sourceOffset + i + 2];
			byte b3 = source[sourceOffset + i + 3];
			target[targetOffset + i] = b3;
			target[targetOffset + i + 1] = b2;
			target[targetOffset + i + 2] = b1;
			target[targetOffset + i + 3] = b0;
		}
	}
}
