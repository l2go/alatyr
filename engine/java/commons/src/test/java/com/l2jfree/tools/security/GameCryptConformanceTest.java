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

import org.junit.jupiter.api.Test;

/**
 * The cipher of the game protocol against an independent implementation written from the published algorithm: a
 * rolling XOR with a 16-byte key, in which every byte also depends on the byte before it, and the 32-bit little-endian
 * word at key offset 8 grows by the size of the packet after each packet. The reference is the client side of the
 * protocol: a client written from it must understand the server.
 */
class GameCryptConformanceTest
{
	private static final byte[] KEY = { 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte)0x88, (byte)0xc8, 0x27,
			(byte)0x93, 0x01, (byte)0xa1, 0x6c, 0x31, (byte)0x97 };
	
	/** One direction of the cipher, as a client would write it. */
	private static final class Reference
	{
		private final byte[] _sendKey = KEY.clone();
		private final byte[] _receiveKey = KEY.clone();
		
		byte[] send(byte[] plain)
		{
			byte[] wire = plain.clone();
			int previous = 0;
			for (int i = 0; i < wire.length; i++)
			{
				previous = ((wire[i] & 0xff) ^ (_sendKey[i & 15] & 0xff) ^ previous) & 0xff;
				wire[i] = (byte)previous;
			}
			advance(_sendKey, wire.length);
			return wire;
		}
		
		byte[] receive(byte[] wire)
		{
			byte[] plain = wire.clone();
			int previous = 0;
			for (int i = 0; i < plain.length; i++)
			{
				int encrypted = wire[i] & 0xff;
				plain[i] = (byte)(encrypted ^ (_receiveKey[i & 15] & 0xff) ^ previous);
				previous = encrypted;
			}
			advance(_receiveKey, wire.length);
			return plain;
		}
		
		private static void advance(byte[] key, int size)
		{
			ByteBuffer word = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
			word.putInt(8, word.getInt(8) + size);
		}
	}
	
	/** The server after it sent KeyPacket: the first call of encrypt is that packet, which is never encrypted. */
	private static GameCrypt enabledServer()
	{
		GameCrypt server = new GameCrypt();
		server.setKey(KEY);
		byte[] keyPacket = new byte[8];
		server.encrypt(keyPacket, 0, keyPacket.length);
		assertThat(keyPacket).containsOnly((byte)0);
		return server;
	}
	
	@Test
	void nothingIsDecryptedBeforeTheKeyPacketWasSent()
	{
		GameCrypt server = new GameCrypt();
		server.setKey(KEY);
		byte[] data = { 1, 2, 3 };
		
		server.decrypt(data, 0, 3);
		
		assertThat(data).containsExactly(1, 2, 3);
	}
	
	@Test
	void theFirstPacketAfterTheKeyPacketMatchesAVectorWorkedOutByHand()
	{
		// key byte 0 is 0x11: 01 ^ 11 ^ 00 = 10, then 02 ^ 22 ^ 10 = 30, then 03 ^ 33 ^ 30 = 00
		GameCrypt server = enabledServer();
		byte[] data = { 1, 2, 3 };
		
		server.encrypt(data, 0, 3);
		
		assertThat(data).containsExactly(0x10, 0x30, 0x00);
	}
	
	@Test
	void whatTheServerEncryptsTheReferenceClientDecrypts()
	{
		GameCrypt server = enabledServer();
		Reference client = new Reference();
		Random random = new Random(7);
		
		for (int round = 0; round < 200; round++)
		{
			byte[] plain = new byte[1 + random.nextInt(300)];
			random.nextBytes(plain);
			byte[] wire = plain.clone();
			
			server.encrypt(wire, 0, wire.length);
			
			assertThat(client.receive(wire)).as("packet %d of %d bytes", round, plain.length).isEqualTo(plain);
		}
	}
	
	@Test
	void whatTheReferenceClientEncryptsTheServerDecrypts()
	{
		GameCrypt server = enabledServer();
		Reference client = new Reference();
		Random random = new Random(8);
		
		for (int round = 0; round < 200; round++)
		{
			byte[] plain = new byte[1 + random.nextInt(300)];
			random.nextBytes(plain);
			byte[] wire = client.send(plain);
			
			server.decrypt(wire, 0, wire.length);
			
			assertThat(wire).as("packet %d of %d bytes", round, plain.length).isEqualTo(plain);
		}
	}
	
	@Test
	void theTwoDirectionsKeepTheirOwnKeys()
	{
		GameCrypt server = enabledServer();
		Reference client = new Reference();
		
		// the server sends a long packet; the client then sends a short one: each direction advances alone
		byte[] fromServer = new byte[100];
		byte[] fromClient = { 9, 8, 7 };
		byte[] wireOut = fromServer.clone();
		server.encrypt(wireOut, 0, wireOut.length);
		byte[] wireIn = client.send(fromClient);
		
		assertThat(client.receive(wireOut)).isEqualTo(fromServer);
		server.decrypt(wireIn, 0, wireIn.length);
		assertThat(wireIn).isEqualTo(fromClient);
	}
	
	@Test
	void aPacketThatIsDecryptedOutOfOrderIsGarbage()
	{
		GameCrypt server = enabledServer();
		Reference client = new Reference();
		byte[] first = new byte[20];
		byte[] second = new byte[20];
		Arrays.fill(second, (byte)0x55);
		byte[] wireFirst = client.send(first);
		byte[] wireSecond = client.send(second);
		
		server.decrypt(wireSecond, 0, wireSecond.length); // the second packet arrives first
		
		assertThat(wireSecond).isNotEqualTo(second);
		assertThat(wireFirst).isNotEqualTo(first);
	}
	
	@Test
	void theCipherWorksOnAPartOfABuffer()
	{
		GameCrypt server = enabledServer();
		Reference client = new Reference();
		byte[] plain = { 4, 5, 6, 7, 8 };
		byte[] padded = new byte[3 + plain.length + 2];
		System.arraycopy(plain, 0, padded, 3, plain.length);
		
		server.encrypt(padded, 3, plain.length);
		
		assertThat(Arrays.copyOfRange(padded, 0, 3)).containsOnly((byte)0);
		assertThat(Arrays.copyOfRange(padded, 3 + plain.length, padded.length)).containsOnly((byte)0);
		assertThat(client.receive(Arrays.copyOfRange(padded, 3, 3 + plain.length))).isEqualTo(plain);
	}
}
