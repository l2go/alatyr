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
package com.l2jfree.gameserver.util;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author luisantonioa
 */
public final class Broadcast
{
	private Broadcast()
	{
	}
	
	public static void toKnownPlayers(L2Creature character, L2ServerPacket mov)
	{
		if (!character.getKnownList().getKnownPlayers().isEmpty())
			for (L2Player player : character.getKnownList().getKnownPlayers().values())
				if (player != null)
					player.sendPacket(mov);
	}
	
	public static void toKnownPlayersInRadius(L2Creature character, L2ServerPacket mov, int radius)
	{
		if (radius < 0)
		{
			toKnownPlayers(character, mov);
			return;
		}
		else if (radius > 10000)
		{
			toKnownPlayersInRadius(character, mov, (long)radius);
			return;
		}
		
		if (!character.getKnownList().getKnownPlayers().isEmpty())
			for (L2Player player : character.getKnownList().getKnownPlayers().values())
				if (character.isInsideRadius(player, radius, false, false))
					if (player != null)
						player.sendPacket(mov);
	}
	
	public static void toKnownPlayersInRadius(L2Creature character, L2ServerPacket mov, long radiusSq)
	{
		if (radiusSq < 0)
		{
			toKnownPlayers(character, mov);
			return;
		}
		else if (radiusSq < 10000)
		{
			toKnownPlayersInRadius(character, mov, (int)radiusSq);
			return;
		}
		
		if (!character.getKnownList().getKnownPlayers().isEmpty())
			for (L2Player player : character.getKnownList().getKnownPlayers().values())
				if (character.getDistanceSq(player) <= radiusSq)
					if (player != null)
						player.sendPacket(mov);
	}
	
	public static void toSelfAndKnownPlayers(L2Creature character, L2ServerPacket mov)
	{
		if (character instanceof L2Player)
			((L2Player)character).sendPacket(mov);
		
		toKnownPlayers(character, mov);
	}
	
	public static void toSelfAndKnownPlayersInRadius(L2Creature character, L2ServerPacket mov, int radius)
	{
		if (character instanceof L2Player)
			((L2Player)character).sendPacket(mov);
		
		toKnownPlayersInRadius(character, mov, radius);
	}
	
	public static void toSelfAndKnownPlayersInRadius(L2Creature character, L2ServerPacket mov, long radiusSq)
	{
		if (character instanceof L2Player)
			((L2Player)character).sendPacket(mov);
		
		toKnownPlayersInRadius(character, mov, radiusSq);
	}
	
	public static void toAllOnlinePlayers(L2ServerPacket mov)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
			if (player != null)
				player.sendPacket(mov);
	}
}
