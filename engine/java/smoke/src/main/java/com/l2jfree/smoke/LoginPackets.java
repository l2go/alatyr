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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

/**
 * The packets of the login protocol (port 2106), as the CT2.3 client sends and reads them.
 */
final class LoginPackets
{
	// server to client
	static final int OP_INIT = 0x00;
	static final int OP_LOGIN_FAIL = 0x01;
	static final int OP_LOGIN_OK = 0x03;
	static final int OP_SERVER_LIST = 0x04;
	static final int OP_PLAY_FAIL = 0x06;
	static final int OP_PLAY_OK = 0x07;
	static final int OP_GG_AUTH = 0x0b;

	// client to server
	static final int OP_REQUEST_AUTH_LOGIN = 0x00;
	static final int OP_REQUEST_SERVER_LOGIN = 0x02;
	static final int OP_REQUEST_SERVER_LIST = 0x05;
	static final int OP_AUTH_GAME_GUARD = 0x07;

	/** The size of the RSA block of RequestAuthLogin, and of the 1024-bit modulus. */
	static final int RSA_BLOCK = 128;
	/** Offset of the account name in the plain RSA block. */
	static final int USER_OFFSET = 0x5e;
	static final int USER_MAX = 14;
	/** Offset of the password in the plain RSA block. */
	static final int PASSWORD_OFFSET = 0x6c;
	static final int PASSWORD_MAX = 16;

	private static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(65537L);

	private LoginPackets()
	{
	}

	/**
	 * The first packet of the server.
	 *
	 * @param modulus the 128 bytes of the RSA modulus, already unscrambled
	 * @param blowfishKey the 16 bytes of the dynamic Blowfish key
	 */
	record Init(int sessionId, int protocolRevision, byte[] modulus, byte[] blowfishKey)
	{
	}

	/** One entry of the server list. */
	record WorldEntry(int serverId, String host, int port, int ageLimit, boolean pvp, int players, int maxPlayers,
			boolean online, int flags, boolean brackets)
	{
	}

	record ServerList(int lastServerId, List<WorldEntry> worlds)
	{
	}

	/**
	 * Reads the plain Init packet: opcode, session id, protocol revision, the scrambled RSA modulus,
	 * four unused words, the Blowfish key, and a zero byte.
	 */
	static Init parseInit(byte[] payload) throws SmokeException
	{
		PacketReader in = new PacketReader(payload);
		int opcode = in.readC();
		if (opcode != OP_INIT)
		{
			throw new SmokeException("the first packet of the login server has the opcode 0x"
					+ Integer.toHexString(opcode) + " instead of 0x00 (Init)");
		}

		int sessionId = in.readD();
		int protocol = in.readD();
		byte[] scrambled = in.readB(RSA_BLOCK);
		in.skip(16);
		byte[] key = in.readB(16);
		return new Init(sessionId, protocol, LoginCipher.unscrambleModulus(scrambled), key);
	}

	static RSAPublicKey publicKey(byte[] modulus) throws SmokeException
	{
		try
		{
			RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, modulus), PUBLIC_EXPONENT);
			return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(spec);
		}
		catch (GeneralSecurityException e)
		{
			throw new SmokeException("the RSA key of the init packet is not usable", e);
		}
	}

	/** 0x07: the session id of Init, then 35 bytes the server skips. */
	static byte[] authGameGuard(int sessionId)
	{
		return new PacketWriter().writeC(OP_AUTH_GAME_GUARD).writeD(sessionId).writeZeros(35).toByteArray();
	}

	/**
	 * 0x00: the 128-byte RSA block, then 47 bytes the server skips. The plain block is zero except for
	 * the account name at 0x5e (14 bytes) and the password at 0x6c (16 bytes); the word at 0x7c is the
	 * one-time password, zero. The block is encrypted with {@code RSA/ECB/NoPadding}.
	 */
	static byte[] requestAuthLogin(RSAPublicKey key, String user, String password) throws SmokeException
	{
		return new PacketWriter().writeC(OP_REQUEST_AUTH_LOGIN).writeB(rsaBlock(key, user, password)).writeZeros(47)
				.toByteArray();
	}

	static byte[] rsaBlock(RSAPublicKey key, String user, String password) throws SmokeException
	{
		byte[] userBytes = user.getBytes(StandardCharsets.UTF_8);
		byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
		if (userBytes.length < 2 || userBytes.length > USER_MAX)
			throw new SmokeException("the account name must have 2 to " + USER_MAX + " bytes");
		if (passwordBytes.length < 1 || passwordBytes.length > PASSWORD_MAX)
			throw new SmokeException("the password must have 1 to " + PASSWORD_MAX + " bytes");

		byte[] block = new byte[RSA_BLOCK];
		System.arraycopy(userBytes, 0, block, USER_OFFSET, userBytes.length);
		System.arraycopy(passwordBytes, 0, block, PASSWORD_OFFSET, passwordBytes.length);

		try
		{
			Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
			rsa.init(Cipher.ENCRYPT_MODE, key);
			byte[] encrypted = rsa.doFinal(block);
			if (encrypted.length != RSA_BLOCK)
				throw new SmokeException("the RSA block has " + encrypted.length + " bytes instead of " + RSA_BLOCK);
			return encrypted;
		}
		catch (GeneralSecurityException e)
		{
			throw new SmokeException("the credentials cannot be encrypted with the RSA key of the server", e);
		}
	}

	/** 0x05: the two login-ok words, then 23 bytes the server skips. */
	static byte[] requestServerList(int loginOk1, int loginOk2)
	{
		return new PacketWriter().writeC(OP_REQUEST_SERVER_LIST).writeD(loginOk1).writeD(loginOk2).writeZeros(23)
				.toByteArray();
	}

	/** 0x02: the two login-ok words, the server id, then 22 bytes the server skips. */
	static byte[] requestServerLogin(int loginOk1, int loginOk2, int serverId)
	{
		return new PacketWriter().writeC(OP_REQUEST_SERVER_LOGIN).writeD(loginOk1).writeD(loginOk2).writeC(serverId)
				.writeZeros(22).toByteArray();
	}

	/**
	 * Reads ServerList after its opcode: world count, the id of the last world, then per world the id,
	 * four address bytes, the port, age limit, pvp flag, players, maximum players, online flag, flag
	 * bits, and the brackets flag.
	 */
	static ServerList parseServerList(PacketReader in) throws SmokeException
	{
		int count = in.readC();
		int last = in.readC();
		List<WorldEntry> worlds = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			int id = in.readC();
			String host = in.readC() + "." + in.readC() + "." + in.readC() + "." + in.readC();
			int port = in.readD();
			int ageLimit = in.readC();
			boolean pvp = in.readC() != 0;
			int players = in.readH();
			int maxPlayers = in.readH();
			boolean online = in.readC() != 0;
			int flags = in.readD();
			boolean brackets = in.readC() != 0;
			worlds.add(new WorldEntry(id, host, port, ageLimit, pvp, players, maxPlayers, online, flags, brackets));
		}
		return new ServerList(last, worlds);
	}

	/** @return a text for the reason code of LoginFail and PlayFail */
	static String describeReason(int reason)
	{
		return switch (reason)
		{
			case 1 -> "system error";
			case 2, 3 -> "wrong password";
			case 4, 6, 8, 9, 10, 11, 13, 14 -> "access failed";
			case 5 -> "account information incorrect";
			case 7 -> "account already in use";
			case 12 -> "age limitation";
			case 15 -> "too high traffic";
			case 16 -> "maintenance";
			case 21 -> "access failed";
			case 22 -> "restricted IP";
			case 23 -> "ignore";
			default -> "unknown reason";
		};
	}
}
