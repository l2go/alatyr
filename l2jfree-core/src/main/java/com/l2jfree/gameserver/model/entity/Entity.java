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
package com.l2jfree.gameserver.model.entity;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

public class Entity
{
	protected static Logger _log = LoggerFactory.getLogger(Entity.class);
	
	protected L2Zone _zone;
	
	public void registerZone(L2Zone zone)
	{
		_zone = zone;
	}
	
	public L2Zone getZone()
	{
		return _zone;
	}
	
	public int getTownId()
	{
		if (_zone != null)
			return _zone.getTownId();
		
		_log.error(getClassName() + " has no zone defined");
		return 0; // Talking Island
	}
	
	public int getCastleId()
	{
		if (_zone != null)
			return _zone.getCastleId();
		
		_log.error(getClassName() + " has no zone defined");
		return 1; // Gludio
	}
	
	public int getFortId()
	{
		if (_zone != null)
			return _zone.getFortId();
		
		_log.error(getClassName() + " has no zone defined");
		return 0;
	}
	
	// Entering the world asks every lair this question. A world without that zone is not an error: the creature is not inside.
	public boolean checkIfInZone(L2Creature cha)
	{
		if (_zone != null)
			return _zone.isInsideZone(cha);
		
		return false;
	}
	
	public boolean checkIfInZone(int x, int y, int z)
	{
		if (_zone != null)
			return _zone.isInsideZone(x, y, z);
		
		return false;
	}
	
	public boolean checkIfInZone(int x, int y)
	{
		if (_zone != null)
			return _zone.isInsideZone(x, y);
		
		return false;
	}
	
	public double getDistanceToZone(int x, int y)
	{
		if (_zone != null)
			return _zone.getDistanceToZone(x, y);
		
		_log.error(getClassName() + " has no zone defined");
		return Double.MAX_VALUE;
	}
	
	// A lair asks who is inside while it is starting, including when this world has no zone for it.
	// An empty list is that answer. checkIfInZone answers the same absence with false and does not log.
	// getTownId and the id lookups still report a missing zone when they run.
	protected List<L2Player> getPlayersInside()
	{
		List<L2Player> lst = new ArrayList<L2Player>();
		L2Zone zone = getZone();
		if (zone == null)
			return lst;
		for (L2Creature cha : zone.getCharactersInside())
		{
			if (cha instanceof L2Player)
				lst.add((L2Player)cha);
		}
		return lst;
	}
	
	protected L2Player getRandomPlayer()
	{
		List<L2Player> lst = getPlayersInside();
		if (!lst.isEmpty())
		{
			return lst.get(Rnd.get(lst.size()));
		}
		return null;
	}
	
	/**
	 * @param cha
	 */
	protected boolean checkBanish(L2Player cha)
	{
		return true;
	}
	
	public void banishForeigners()
	{
		for (L2Player player : getPlayersInside())
		{
			if (checkBanish(player))
				player.teleToLocation(TeleportWhereType.Town);
		}
	}
	
	public void broadcastToPlayers(String message)
	{
		SystemMessage msg = SystemMessage.sendString(message);
		for (L2Player player : getPlayersInside())
		{
			player.sendPacket(msg);
		}
	}
	
	public void broadcastToPlayers(L2ServerPacket gsp)
	{
		for (L2Player player : getPlayersInside())
		{
			player.sendPacket(gsp);
		}
	}
	
	public String getClassName()
	{
		String[] parts = this.getClass().toString().split("\\.");
		return parts[parts.length - 1];
	}
}
