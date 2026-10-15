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
package com.l2jfree.gameserver.model.mapregion;

import org.apache.commons.lang3.ArrayUtils;
import org.w3c.dom.Node;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.entity.Town;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.tools.random.Rnd;

/**
 * @author Noctarius
 */
public final class L2MapRegionRestart
{
	private final int _restartId;
	private final String _name;
	private final int _bbsId;
	private final int _locName;
	
	private Location[] _restartPoints = new Location[0];
	private Location[] _chaoticPoints = new Location[0];
	
	private Race _bannedRace;
	private int _bannedRaceRestartId = -1;
	
	public L2MapRegionRestart(Node node)
	{
		_restartId = Integer.parseInt(node.getAttributes().getNamedItem("restartId").getNodeValue());
		_name = node.getAttributes().getNamedItem("name").getNodeValue();
		_bbsId = Integer.parseInt(node.getAttributes().getNamedItem("bbs").getNodeValue());
		_locName = Integer.parseInt(node.getAttributes().getNamedItem("locname").getNodeValue());
		
		for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("normal".equalsIgnoreCase(n.getNodeName()))
			{
				_restartPoints = ArrayUtils.add(_restartPoints, getLocation(n));
			}
			else if ("chaotic".equalsIgnoreCase(n.getNodeName()))
			{
				_chaoticPoints = ArrayUtils.add(_chaoticPoints, getLocation(n));
			}
			else if ("bannedrace".equalsIgnoreCase(n.getNodeName()))
			{
				_bannedRace = Race.getRaceByName(n.getAttributes().getNamedItem("race").getNodeValue());
				_bannedRaceRestartId = Integer.parseInt(n.getAttributes().getNamedItem("restartId").getNodeValue());
			}
		}
	}
	
	private static Location getLocation(Node node)
	{
		final int x = Integer.parseInt(node.getAttributes().getNamedItem("X").getNodeValue());
		final int y = Integer.parseInt(node.getAttributes().getNamedItem("Y").getNodeValue());
		final int z = Integer.parseInt(node.getAttributes().getNamedItem("Z").getNodeValue());
		
		return new Location(x, y, z);
	}
	
	public int getRestartId()
	{
		return _restartId;
	}
	
	public int getBbsId()
	{
		return _bbsId;
	}
	
	public int getLocName()
	{
		return _locName;
	}
	
	public String getName()
	{
		return _name;
	}
	
	private int getNextAccessibleRestartId(L2Player activeChar)
	{
		if (activeChar == null)
			return getRestartId();
		
		if (activeChar.getRace() == _bannedRace)
			return _bannedRaceRestartId;
		
		if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DAWN)
		{
			final Town town = TownManager.getInstance().getTownByMaprestart(this);
			if (town != null && town.hasCastleInSiege())
			{
				final int newTownId = TownManager.getInstance().getRedirectTownNumber(town.getTownId());
				
				final L2MapRegion region = TownManager.getInstance().getTown(newTownId).getMapRegion();
				if (region != null)
					return region.getRestartId(activeChar);
			}
		}
		
		return getRestartId();
	}
	
	public Location getRandomRestartPoint(L2Player player)
	{
		final int restartId = getNextAccessibleRestartId(player);
		
		if (restartId != getRestartId())
			return MapRegionManager.getInstance().getRestartPoint(restartId, player);
		
		return _restartPoints[Rnd.get(_restartPoints.length)];
	}
	
	public Location getRandomChaoticRestartPoint(L2Player player)
	{
		final int restartId = getNextAccessibleRestartId(player);
		
		if (restartId != getRestartId())
			return MapRegionManager.getInstance().getChaoticRestartPoint(restartId, player);
		
		return _chaoticPoints[Rnd.get(_chaoticPoints.length)];
	}
	
	public Race getBannedRace()
	{
		return _bannedRace;
	}
	
	public int getRedirectId()
	{
		return _bannedRaceRestartId;
	}
}
