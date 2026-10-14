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
package com.l2jfree.gameserver.model.rewards;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Counter-based randomness: an unrelated fight cannot advance this sequence. */
public final class HuntRandom
{
	private final String _worldId;
	private final long _lifeId;
	private long _draw;

	public HuntRandom(String worldId, long lifeId)
	{
		_worldId = worldId;
		_lifeId = lifeId;
	}

	public static long seed(String worldId, long lifeId, long draw)
	{
		byte[] world = worldId.getBytes(StandardCharsets.UTF_8);
		byte[] domain = "hunt".getBytes(StandardCharsets.UTF_8);
		ByteBuffer input = ByteBuffer.allocate(4 + world.length + 4 + domain.length + 16);
		input.putInt(world.length).put(world).putInt(domain.length).put(domain).putLong(lifeId).putLong(draw);
		try
		{
			return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(input.array())).getLong();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	public static long roll(long seed, long draw)
	{
		long z = seed + (draw + 1) * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public long nextLong()
	{
		long draw = _draw++;
		return roll(seed(_worldId, _lifeId, draw), draw);
	}

	public int nextInt(int bound)
	{
		if (bound <= 0)
			throw new IllegalArgumentException("Positive bound required");
		return (int)Math.unsignedMultiplyHigh(nextLong(), bound);
	}
}
