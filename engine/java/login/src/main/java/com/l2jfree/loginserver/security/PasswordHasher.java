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
package com.l2jfree.loginserver.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The stored form of a password. New passwords are PBKDF2-HMAC-SHA256 with a salt of their own, in the form
 * {@code pbkdf2-sha256$iterations$salt$hash} (Base64). The accounts of the 2.x line hold the unsalted SHA-1 digest
 * in Base64; they still log in, and the login stores the new form the next time the password is checked.
 */
public final class PasswordHasher
{
	private static final String PREFIX = "pbkdf2-sha256$";
	private static final Pattern PARTS = Pattern.compile("\\$");
	private static final int SALT_BYTES = 16;
	private static final int HASH_BITS = 256;
	private static final int MAX_ITERATIONS = 10_000_000;
	
	/** The OWASP recommendation for PBKDF2-HMAC-SHA256. */
	static final int ITERATIONS = 600_000;
	
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private PasswordHasher()
	{
	}
	
	public static String hash(String password)
	{
		return hash(password, ITERATIONS);
	}
	
	static String hash(String password, int iterations)
	{
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		return PREFIX + iterations + "$" + Base64.getEncoder().encodeToString(salt) + "$"
				+ Base64.getEncoder().encodeToString(pbkdf2(password, salt, iterations));
	}
	
	/**
	 * @return true if the password is the one the stored form was made from; false for anything else, including a
	 *         stored form that is empty or cannot be read
	 */
	public static boolean verify(String password, String stored)
	{
		if (password == null || stored == null || stored.isEmpty())
			return false;
		
		if (stored.startsWith(PREFIX))
		{
			String[] parts = PARTS.split(stored, -1);
			if (parts.length != 4)
				return false;
			try
			{
				int iterations = Integer.parseInt(parts[1]);
				if (iterations < 1 || iterations > MAX_ITERATIONS)
					return false;
				byte[] salt = Base64.getDecoder().decode(parts[2]);
				byte[] expected = Base64.getDecoder().decode(parts[3]);
				return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
			}
			catch (IllegalArgumentException e)
			{
				return false;
			}
		}
		
		// the 2.x form: Base64 of the unsalted SHA-1 digest, always 20 bytes
		try
		{
			byte[] expected = Base64.getDecoder().decode(stored);
			byte[] actual = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
			return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
		}
		catch (IllegalArgumentException | GeneralSecurityException e)
		{
			return false;
		}
	}
	
	/**
	 * @return true if the stored form is the old one, or has fewer iterations than a new password gets
	 */
	public static boolean needsRehash(String stored)
	{
		if (stored == null || !stored.startsWith(PREFIX))
			return true;
		
		String[] parts = PARTS.split(stored, -1);
		try
		{
			return parts.length != 4 || Integer.parseInt(parts[1]) < ITERATIONS;
		}
		catch (NumberFormatException e)
		{
			return true;
		}
	}
	
	private static byte[] pbkdf2(String password, byte[] salt, int iterations)
	{
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
		try
		{
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("PBKDF2 is not available", e);
		}
		finally
		{
			spec.clearPassword();
		}
	}
}
