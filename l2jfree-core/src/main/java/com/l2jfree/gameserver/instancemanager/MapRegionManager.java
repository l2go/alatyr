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
package com.l2jfree.gameserver.instancemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.entity.FortSiege;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.entity.Town;
import com.l2jfree.gameserver.model.mapregion.L2MapArea;
import com.l2jfree.gameserver.model.mapregion.L2MapRegion;
import com.l2jfree.gameserver.model.mapregion.L2MapRegionRestart;
import com.l2jfree.gameserver.model.mapregion.L2SpecialMapRegion;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.util.L2Collections;
import com.l2jfree.util.LookupTable;

/**
 * @author Noctarius
 */
public final class MapRegionManager
{
	private static final Logger _log = LoggerFactory.getLogger(MapRegionManager.class);
	
	private final LookupTable<L2MapRegionRestart> _mapRegionRestart = new LookupTable<L2MapRegionRestart>();
	
	public static MapRegionManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private MapRegionManager()
	{
		load();
	}
	
	private void load()
	{
		File[] files = Util.getDatapackFiles("mapregion", ".xml");
		if (files.length == 0)
		{
			_log.info("World has no map regions");
			return;
		}
		
		for (File xml : files)
		{
			Document doc = null;
			
			try
			{
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				
				factory.setValidating(true);
				factory.setIgnoringComments(true);
				
				doc = factory.newDocumentBuilder().parse(xml);
			}
			catch (Exception e)
			{
				_log.warn("MapRegionManager: Error while loading XML definition: " + xml.getName() + e, e);
				return;
			}
			
			try
			{
				parseDocument(doc);
			}
			catch (Exception e)
			{
				_log.warn("MapRegionManager: Error in XML definition: " + xml.getName() + e, e);
				return;
			}
		}
	}
	
	public void reload()
	{
		// Get the world regions
		for (L2WorldRegion[] finalWorldRegion : L2World.getInstance().getAllWorldRegions())
		{
			for (L2WorldRegion finalElement : finalWorldRegion)
			{
				finalElement.clearMapRegions();
			}
		}
		
		load();
	}
	
	private void parseDocument(Document doc) throws Exception
	{
		final Map<Integer, L2MapRegionRestart> restarts = new LinkedHashMap<Integer, L2MapRegionRestart>();
		
		final List<L2MapRegion> specialMapRegions = new ArrayList<L2MapRegion>();
		final List<L2MapArea> mapAreas = new ArrayList<L2MapArea>();
		
		final Set<Integer> restartAreas = new HashSet<Integer>();
		
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("mapregion".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("regions".equalsIgnoreCase(d.getNodeName()))
					{
						for (Node f = d.getFirstChild(); f != null; f = f.getNextSibling())
						{
							if ("region".equalsIgnoreCase(f.getNodeName()))
							{
								specialMapRegions.add(new L2SpecialMapRegion(f));
							}
						}
					}
					else if ("restartpoints".equalsIgnoreCase(d.getNodeName()))
					{
						for (Node f = d.getFirstChild(); f != null; f = f.getNextSibling())
						{
							if ("restartpoint".equalsIgnoreCase(f.getNodeName()))
							{
								L2MapRegionRestart restart = new L2MapRegionRestart(f);
								
								if (!restarts.containsKey(restart.getRestartId()))
									restarts.put(restart.getRestartId(), restart);
								else
									throw new Exception("Duplicate restartpointId: " + restart.getRestartId() + ".");
							}
						}
					}
					else if ("restartareas".equalsIgnoreCase(d.getNodeName()))
					{
						for (Node f = d.getFirstChild(); f != null; f = f.getNextSibling())
						{
							if ("restartarea".equalsIgnoreCase(f.getNodeName()))
							{
								final int restartId =
										Integer.parseInt(f.getAttributes().getNamedItem("restartId").getNodeValue());
								
								restartAreas.add(restartId);
								
								for (Node r = f.getFirstChild(); r != null; r = r.getNextSibling())
								{
									if ("map".equalsIgnoreCase(r.getNodeName()))
									{
										int X = Integer.parseInt(r.getAttributes().getNamedItem("X").getNodeValue());
										int Y = Integer.parseInt(r.getAttributes().getNamedItem("Y").getNodeValue());
										
										mapAreas.add(new L2MapArea(restartId, X, Y));
									}
								}
							}
						}
					}
				}
			}
		}
		
		final L2WorldRegion[][] worldRegions = L2World.getInstance().getAllWorldRegions();
		
		for (L2MapRegion mapregion : L2Collections.concatenatedIterable(specialMapRegions, mapAreas))
		{
			// Register the mapregions to any intersecting world region
			for (int x = 0; x < worldRegions.length; x++)
			{
				for (int y = 0; y < worldRegions[x].length; y++)
				{
					int ax = (x - L2World.OFFSET_X) << L2World.SHIFT_BY;
					int bx = ((x + 1) - L2World.OFFSET_X) << L2World.SHIFT_BY;
					int ay = (y - L2World.OFFSET_Y) << L2World.SHIFT_BY;
					int by = ((y + 1) - L2World.OFFSET_Y) << L2World.SHIFT_BY;
					
					if (mapregion.intersectsRectangle(ax, bx, ay, by))
						worldRegions[x][y].addMapRegion(mapregion);
				}
			}
		}
		
		_mapRegionRestart.clear(false);
		
		for (Map.Entry<Integer, L2MapRegionRestart> entry : restarts.entrySet())
			_mapRegionRestart.set(entry.getKey(), entry.getValue());
		
		int redirectCount = 0;
		
		for (L2MapRegionRestart restart : _mapRegionRestart)
		{
			if (restart.getBannedRace() != null)
				redirectCount++;
		}
		
		_log.info("MapRegionManager: Loaded " + _mapRegionRestart.size() + " restartpoint(s).");
		_log.info("MapRegionManager: Loaded " + restartAreas.size() + " restartareas with " + mapAreas.size()
				+ " arearegion(s).");
		_log.info("MapRegionManager: Loaded " + specialMapRegions.size() + " zoneregion(s).");
		_log.info("MapRegionManager: Loaded " + redirectCount + " race depending redirects.");
	}
	
	public L2MapRegionRestart getRestartLocation(L2Player activeChar)
	{
		L2MapRegion region = getRegion(activeChar);
		
		// Temporary fix for new hunting grounds
		if (region == null)
			return _mapRegionRestart.get(Config.ALT_DEFAULT_RESTARTTOWN);
		else
			return _mapRegionRestart.get(region.getRestartId(activeChar));
	}
	
	public L2MapRegionRestart getRestartLocation(int restartId)
	{
		return _mapRegionRestart.get(restartId);
	}
	
	public Location getRestartPoint(int restartId, L2Player activeChar)
	{
		L2MapRegionRestart restart = _mapRegionRestart.get(restartId);
		
		if (restart == null)
			restart = _mapRegionRestart.get(Config.ALT_DEFAULT_RESTARTTOWN);
		
		return restart.getRandomRestartPoint(activeChar);
	}
	
	public Location getChaoticRestartPoint(int restartId, L2Player activeChar)
	{
		L2MapRegionRestart restart = _mapRegionRestart.get(restartId);
		
		if (restart == null)
			restart = _mapRegionRestart.get(Config.ALT_DEFAULT_RESTARTTOWN);
		
		return restart.getRandomChaoticRestartPoint(activeChar);
	}
	
	public L2MapRegion getRegion(L2Creature activeChar)
	{
		return getRegion(activeChar.getX(), activeChar.getY(), activeChar.getZ());
	}
	
	public L2MapRegion getRegion(int x, int y, int z)
	{
		return L2World.getInstance().getRegion(x, y).getMapRegion(x, y, z);
	}
	
	public L2MapRegion getRegion(int x, int y)
	{
		return getRegion(x, y, -1);
	}
	
	//TODO: Needs to be clean rewritten
	public Location getTeleToLocation(L2Player player, TeleportWhereType teleportWhere)
	{
		L2Clan clan = player.getClan();
		
		// Checking if in Dimensinal Gap
		if (DimensionalRiftManager.getInstance().checkIfInRiftZone(player.getX(), player.getY(), player.getZ(), true)) // true -> ignore waiting room :)
		{
			if (player.isInParty() && player.getParty().isInDimensionalRift())
			{
				player.getParty().getDimensionalRift().usedTeleport(player);
			}
			
			return DimensionalRiftManager.getInstance().getWaitingRoomTeleport();
		}
		
		// Checking if in an instance
		if (player.isInInstance())
		{
			Instance inst = InstanceManager.getInstance().getInstance(player.getInstanceId());
			if (inst != null)
			{
				Location loc = inst.getSpawnLoc();
				if (loc != null)
					return loc;
			}
		}
		
		// Checking if in arena
		L2Zone arena = ZoneManager.getInstance().isInsideZone(L2Zone.ZoneType.Arena, player.getX(), player.getY());
		if (arena != null && arena.isInsideZone(player))
		{
			Location loc = arena.getRestartPoint(L2Zone.RestartType.OWNER);
			if (loc == null)
				loc = arena.getRandomLocation();
			return loc;
		}
		
		if (clan != null && !player.isFlyingMounted() && !player.isFlying()) // flying players in gracia cant use teleports to aden continent
		{
			// If teleport to clan hall
			if (teleportWhere == TeleportWhereType.ClanHall)
			{
				ClanHall clanhall = ClanHallManager.getInstance().getClanHallByOwner(clan);
				if (clanhall != null)
				{
					L2Zone zone = clanhall.getZone();
					if (zone != null)
					{
						Location loc = zone.getRestartPoint(L2Zone.RestartType.OWNER);
						if (loc == null)
							loc = zone.getRandomLocation();
						return loc;
					}
				}
			}
			else if (teleportWhere == TeleportWhereType.Castle)
			{
				Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
				if (castle != null)
				{
					L2Zone zone = castle.getZone();
					if (zone != null)
					{
						if (castle.getSiege().getIsInProgress() && player.isChaotic())
						{
							// Karma player respawns out of siege zone (only during sieges ? o.O )
							return zone.getRestartPoint(L2Zone.RestartType.CHAOTIC);
						}
						
						return zone.getRestartPoint(L2Zone.RestartType.OWNER);
					}
				}
			}
			else if (teleportWhere == TeleportWhereType.Fortress)
			{
				Fort fort = FortManager.getInstance().getFortByOwner(clan);
				if (fort != null)
				{
					L2Zone zone = fort.getZone();
					if (zone != null)
					{
						// If is on castle with siege and player's clan is defender
						if (fort.getSiege().getIsInProgress() && player.isChaotic())
						{
							// Karma player respawns out of siege zone (only during sieges ? o.O )
							return zone.getRestartPoint(L2Zone.RestartType.CHAOTIC);
						}
						
						return zone.getRestartPoint(L2Zone.RestartType.OWNER);
					}
				}
			}
			else if (teleportWhere == TeleportWhereType.SiegeFlag)
			{
				Siege siege = SiegeManager.getInstance().getSiege(clan);
				FortSiege fsiege = FortSiegeManager.getInstance().getSiege(clan);
				
				// Check if player's clan is attacker
				if (siege != null && fsiege == null && siege.checkIsAttacker(clan) && siege.checkIfInZone(player))
				{
					// Karma player respawns out of siege zone
					if (player.isChaotic())
					{
						L2Zone zone = siege.getCastle().getZone();
						if (zone != null)
						{
							return zone.getRestartPoint(L2Zone.RestartType.CHAOTIC);
						}
					}
					// get nearest flag
					L2Npc flag = siege.getClosestFlag(player);
					// spawn to flag
					if (flag != null)
						return flag.getLoc();
				}
				else if (siege == null && fsiege != null && fsiege.checkIsAttacker(clan)
						&& fsiege.checkIfInZone(player))
				{
					// Karma player respawns out of siege zone
					if (player.isChaotic())
					{
						L2Zone zone = fsiege.getFort().getZone();
						if (zone != null)
						{
							return zone.getRestartPoint(L2Zone.RestartType.CHAOTIC);
						}
					}
					// Get nearest flag
					L2Npc flag = fsiege.getClosestFlag(player);
					// Spawn to flag
					if (flag != null)
						return flag.getLoc();
				}
			}
		}
		
		// TeleportWhereType.Town, and other TeleportWhereTypes where the condition was not met
		L2MapRegionRestart restart = getRestartLocation(player);
		
		Location loc = null;
		
		// Karma player land out of city
		if (player.isChaotic())
			loc = restart.getRandomChaoticRestartPoint(player);
		
		if (loc == null)
			loc = restart.getRandomRestartPoint(player);
		
		if (loc != null)
			return loc;
		
		// teleport to default town if nothing else will work
		return getRestartPoint(Config.ALT_DEFAULT_RESTARTTOWN, player);
	}
	
	public int getAreaCastle(L2Creature activeChar)
	{
		Town town = TownManager.getInstance().getClosestTown(activeChar);
		
		if (town == null)
			return 5;
		
		return town.getCastleId();
	}
	
	/**
	 * < 1, 8, > 15 - Empty<BR>1 - Talking island<BR>2 - Gludio<BR>
	 * 3 - Dark elven<BR>4 - Elven<BR>5 - Dion<BR>6 - Giran<BR>
	 * 7 - Neutral zone<BR>9 - Schuttgart<BR>10 - Oren<BR>
	 * 11 - Hunters Village<BR>12 - Innadril<BR>13 - Aden<BR>
	 * 14 - Rune<BR>15 - Goddard
	 * @param player a player
	 * @return L2 region used in partymatching
	 */
	public int getL2Region(L2Player player)
	{
		L2MapRegion region = getRegion(player);
		int locName = -1;
		if (region != null)
		{
			int restartId = region.getRestartId();
			L2MapRegionRestart restart = getRestartLocation(restartId);
			locName = restart.getLocName();
		}
		return convertLocNameToL2Region(locName);
	}
	
	public int convertLocNameToL2Region(int locName)
	{
		switch (locName)
		{
			case 910: // TI
				return 1;
			case 911: // Gludin
			case 912: // Gludio
			case 2190: // Southern wastelands
			case 2710: // Keucereus
			case 2711: // inside SoI
			case 2712: // outside SoI
			case 2716: // inside Cleft
				return 2;
			case 913: // Neutral zone
				return 7;
			case 914: // Elven village
				return 4;
			case 915: // DE village
				return 3;
			case 916: // Dion
			case 917: // Floran
				return 5;
			case 918: // Giran
			case 919: // Harbor
				return 6;
			case 920: // Orc village
			case 921: // Dwarven village
			case 1714: // Schuttgart
				return 9;
			case 922: // Oren
				return 10;
			case 923: // Hunters village
				return 11;
			case 924: // Aden
			case 925: // Coliseum
			case 2189: // Kamael Village
				return 13;
			case 926: // Heine
				return 12;
			case 1537: // Rune
			case 1924: // Primeval Isle
			case 2259: // Fantasy Isle
				return 14;
			case 1538: // Goddard
				return 15;
			default: // TODO: Epilogue locations
				return 0; // no name
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final MapRegionManager _instance = new MapRegionManager();
	}
}
