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
package com.l2jfree.gameserver.model.world;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.util.ArrayBunch;
import com.l2jfree.util.concurrent.L2EntityMap;
import com.l2jfree.util.concurrent.L2ReadWriteEntityMap;
import com.l2jfree.util.concurrent.SameInstance;

/**
 * This class ...
 * 
 * @version $Revision: 1.21.2.5.2.7 $ $Date: 2005/03/27 15:29:32 $
 */
public final class L2World
{
	private static final Logger _log = LoggerFactory.getLogger(L2World.class);
	
	public static final int SHIFT_BY = 12;
	
	/** Map dimensions */
	public static final int MAP_MIN_X = -327680;
	public static final int MAP_MAX_X = 229376;
	public static final int MAP_MIN_Y = -262144;
	public static final int MAP_MAX_Y = 294912;
	public static final int MAP_MIN_Z = -32768;
	public static final int MAP_MAX_Z = 32767;
	
	public static final int WORLD_SIZE_X = L2World.MAP_MAX_X - L2World.MAP_MIN_X + 1 >> SHIFT_BY;
	public static final int WORLD_SIZE_Y = L2World.MAP_MAX_Y - L2World.MAP_MIN_Y + 1 >> SHIFT_BY;
	
	public static final int SHIFT_BY_FOR_Z = 9;
	
	/** calculated offset used so top left region is 0,0 */
	public static final int OFFSET_X = Math.abs(MAP_MIN_X >> SHIFT_BY);
	public static final int OFFSET_Y = Math.abs(MAP_MIN_Y >> SHIFT_BY);
	public static final int OFFSET_Z = Math.abs(MAP_MIN_Z >> SHIFT_BY_FOR_Z);
	
	/** number of regions */
	public static final int REGIONS_X = (MAP_MAX_X >> SHIFT_BY) + OFFSET_X;
	public static final int REGIONS_Y = (MAP_MAX_Y >> SHIFT_BY) + OFFSET_Y;
	public static final int REGIONS_Z = (MAP_MAX_Z >> SHIFT_BY_FOR_Z) + OFFSET_Z;
	
	public static L2World getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/** all visible objects */
	private final L2EntityMap<L2Object> _objects = new L2ReadWriteEntityMap<L2Object>(50000);
	
	/** all the players in game */
	private final Map<String, L2Player> _players = new ConcurrentHashMap<String, L2Player>(1000);
	
	/** pets and their owner id */
	private final Map<Integer, L2PetInstance> _pets = new ConcurrentHashMap<Integer, L2PetInstance>(100);
	
	private final L2WorldRegion[][] _worldRegions = new L2WorldRegion[REGIONS_X + 1][REGIONS_Y + 1];
	
	private L2World()
	{
		_log.info("L2World: Setting up World Regions");
		
		for (int i = 0; i <= REGIONS_X; i++)
			for (int j = 0; j <= REGIONS_Y; j++)
				_worldRegions[i][j] = new L2WorldRegion(i, j);
		
		for (int x = 0; x <= REGIONS_X; x++)
			for (int y = 0; y <= REGIONS_Y; y++)
				for (int a = -1; a <= 1; a++)
					for (int b = -1; b <= 1; b++)
						if (validRegion(x + a, y + b))
							_worldRegions[x + a][y + b].addSurroundingRegion(_worldRegions[x][y]);
		
		_log.info("L2World: (" + REGIONS_X + " by " + REGIONS_Y + ") World Region Grid set up.");
	}
	
	public void storeObject(L2Object object)
	{
		final Integer objectId = object.getObjectId();
		final L2Object oldObject = findObject(objectId);
		
		if (oldObject != null && oldObject != object)
		{
			_log.warn("[" + oldObject + "] replaced with [" + object + "] - objId: " + objectId + "!",
					new IllegalStateException());
			if (Config.BAN_DUPLICATE_ITEM_OWNER)
			{
				// this does generate a standalone duplicate item!
				if (object instanceof L2ItemInstance)
				{
					L2ItemInstance item = (L2ItemInstance)object;
					L2Player player = findPlayer(item.getOwnerId());
					if (player != null)
						Util.handleIllegalPlayerAction(player, "Duplicate item detected for " + player,
								IllegalPlayerAction.PUNISH_KICKBAN);
				}
			}
		}
		
		_objects.add(object);
	}
	
	public void removeObject(L2Object object)
	{
		_objects.remove(object); // suggestion by whatev
	}
	
	public void removeObjects(List<L2Object> list)
	{
		for (L2Object o : list)
			if (o != null)
				removeObject(o); // suggestion by whatev
	}
	
	public void removeObjects(L2Object[] objects)
	{
		for (L2Object o : objects)
			if (o != null)
				removeObject(o); // suggestion by whatev
	}
	
	public void addOnlinePlayer(L2Player player)
	{
		synchronized (_players)
		{
			_players.put(player.getName().toLowerCase(), player);
		}
	}
	
	public void removeOnlinePlayer(L2Player player)
	{
		SameInstance.remove(_players, player.getName().toLowerCase(), player);
	}
	
	public L2Object findObject(int objectId)
	{
		return _objects.get(objectId);
	}
	
	public L2Creature findCharacter(int objectId)
	{
		L2Object obj = _objects.get(objectId);
		
		if (obj instanceof L2Creature)
			return (L2Creature)obj;
		
		return null;
	}
	
	public L2Player findPlayer(int objectId)
	{
		L2Object obj = _objects.get(objectId);
		
		if (obj instanceof L2Player)
			return (L2Player)obj;
		
		return null;
	}
	
	public L2Object[] getAllVisibleObjects()
	{
		return _objects.toArray(L2Object.class);
	}
	
	/**
	 * Get the count of all visible objects in world.<br>
	 * <br>
	 * 
	 * @return count off all L2World objects
	 */
	public final int getAllVisibleObjectsCount()
	{
		return _objects.size();
	}
	
	/**
	 * Return a collection containing all players in game.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Read-only, please! </B></FONT><BR>
	 * <BR>
	 */
	public Collection<L2Player> getAllPlayers()
	{
		return _players.values();
	}
	
	/**
	 * Return how many players are online.<BR>
	 * <BR>
	 * 
	 * @return number of online players.
	 */
	public int getAllPlayersCount()
	{
		return _players.size();
	}
	
	/**
	 * Return the player instance corresponding to the given name.<BR>
	 * <BR>
	 * 
	 * @param name Name of the player to get Instance
	 */
	public L2Player getPlayer(String name)
	{
		return _players.get(name.toLowerCase());
	}
	
	/**
	 * Return the player instance corresponding to the given objectId.<BR>
	 * <BR>
	 * 
	 * @param objectId ID of the player to get Instance
	 */
	public L2Player getPlayer(int objectId)
	{
		L2Object object = _objects.get(objectId);
		return object instanceof L2Player ? (L2Player)object : null;
	}
	
	/**
	 * Return a collection containing all pets in game.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Read-only, please! </B></FONT><BR>
	 * <BR>
	 */
	public Collection<L2PetInstance> getAllPets()
	{
		return _pets.values();
	}
	
	/**
	 * Return the pet instance from the given ownerId.<BR>
	 * <BR>
	 * 
	 * @param ownerId ID of the owner
	 */
	public L2PetInstance getPet(int ownerId)
	{
		return _pets.get(ownerId);
	}
	
	/**
	 * Add the given pet instance from the given ownerId.<BR>
	 * <BR>
	 * 
	 * @param ownerId ID of the owner
	 * @param pet L2PetInstance of the pet
	 */
	public L2PetInstance addPet(int ownerId, L2PetInstance pet)
	{
		return _pets.put(ownerId, pet);
	}
	
	/**
	 * Remove the given pet instance.<BR>
	 * <BR>
	 * 
	 * @param ownerId ID of the owner
	 */
	public void removePet(int ownerId)
	{
		_pets.remove(ownerId);
	}
	
	/**
	 * Remove the given pet instance.<BR>
	 * <BR>
	 * 
	 * @param pet the pet to remove
	 */
	public void removePet(L2PetInstance pet)
	{
		_pets.values().remove(pet);
	}
	
	public void addVisibleObject(L2Object object)
	{
		if (object == null)
			return;
		
		storeObject(object);
		
		object.getPosition().getWorldRegion().addVisibleObject(object, true);
	}
	
	public void removeVisibleObject(L2Object object, L2WorldRegion oldRegion)
	{
		if (object == null)
			return;
		
		if (oldRegion != null)
			oldRegion.removeVisibleObject(object, true);
		
		object.getKnownList().removeAllKnownObjects();
	}
	
	/**
	 * Return all visible objects of the L2WorldRegions in the circular area (radius) centered on the object.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All visible object are identified in <B>_visibleObjects</B> of their current L2WorldRegion <BR>
	 * All surrounding L2WorldRegion are identified in <B>_surroundingRegions</B> of the selected L2WorldRegion in
	 * order to scan a large area around a L2Object<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Define the aggrolist of monster </li>
	 * <li> Define visible objects of a L2Object </li>
	 * <li> Skill : Confusion... </li>
	 * <BR>
	 * 
	 * @param object L2object that determine the center of the circular area
	 * @param radius Radius of the circular area
	 */
	public L2Object[] getVisibleObjects(L2Object object, int radius)
	{
		if (object == null)
			return L2Object.EMPTY_ARRAY;
		
		final L2WorldRegion selfRegion = object.getWorldRegion();
		
		if (selfRegion == null)
			return L2Object.EMPTY_ARRAY;
		
		final int x = object.getX();
		final int y = object.getY();
		final int sqRadius = radius * radius;
		
		ArrayBunch<L2Object> result = new ArrayBunch<L2Object>();
		
		for (L2WorldRegion region : selfRegion.getSurroundingRegions())
		{
			for (L2Object obj : region.getVisibleObjects())
			{
				if (obj == null || obj == object || !obj.isVisible())
					continue;
				
				final int dx = obj.getX() - x;
				final int dy = obj.getY() - y;
				
				if (dx * dx + dy * dy < sqRadius)
					result.add(obj);
			}
		}
		
		return result.moveToArray(new L2Object[result.size()]);
	}
	
	/**
	 * Return all visible objects of the L2WorldRegions in the spheric area (radius) centered on the object.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All visible object are identified in <B>_visibleObjects</B> of their current L2WorldRegion <BR>
	 * All surrounding L2WorldRegion are identified in <B>_surroundingRegions</B> of the selected L2WorldRegion in
	 * order to scan a large area around a L2Object<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Define the target list of a skill </li>
	 * <li> Define the target list of a polearme attack </li>
	 * <BR>
	 * <BR>
	 * 
	 * @param object L2object that determine the center of the circular area
	 * @param radius Radius of the spheric area
	 */
	public L2Object[] getVisibleObjects3D(L2Object object, int radius)
	{
		if (object == null)
			return L2Object.EMPTY_ARRAY;
		
		final L2WorldRegion selfRegion = object.getWorldRegion();
		
		if (selfRegion == null)
			return L2Object.EMPTY_ARRAY;
		
		final int x = object.getX();
		final int y = object.getY();
		final int z = object.getZ();
		final int sqRadius = radius * radius;
		
		ArrayBunch<L2Object> result = new ArrayBunch<L2Object>();
		
		for (L2WorldRegion region : selfRegion.getSurroundingRegions())
		{
			for (L2Object obj : region.getVisibleObjects())
			{
				if (obj == null || obj == object || !obj.isVisible())
					continue;
				
				final int dx = obj.getX() - x;
				final int dy = obj.getY() - y;
				final int dz = obj.getZ() - z;
				
				if (dx * dx + dy * dy + dz * dz < sqRadius)
					result.add(obj);
			}
		}
		
		return result.moveToArray(new L2Object[result.size()]);
	}
	
	/**
	 * Return all visible players of the L2WorldRegion object's and of its surrounding L2WorldRegion.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All visible object are identified in <B>_visibleObjects</B> of their current L2WorldRegion <BR>
	 * All surrounding L2WorldRegion are identified in <B>_surroundingRegions</B> of the selected L2WorldRegion in
	 * order to scan a large area around a L2Object<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Find Close Objects for L2Creature </li>
	 * <BR>
	 * 
	 * @param object L2object that determine the current L2WorldRegion
	 */
	public L2Playable[] getVisiblePlayable(L2Object object)
	{
		if (object == null)
			return L2Playable.EMPTY_ARRAY;
		
		final L2WorldRegion selfRegion = object.getWorldRegion();
		
		if (selfRegion == null)
			return L2Playable.EMPTY_ARRAY;
		
		ArrayBunch<L2Playable> result = new ArrayBunch<L2Playable>();
		
		for (L2WorldRegion region : selfRegion.getSurroundingRegions())
		{
			for (L2Playable obj : region.getVisiblePlayables())
			{
				if (obj == null || obj == object || !obj.isVisible())
					continue;
				
				result.add(obj);
			}
		}
		
		return result.moveToArray(new L2Playable[result.size()]);
	}
	
	/**
	 * Calculate the current L2WorldRegions of the object according to its position (x,y).<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Set position of a new L2Object (drop, spawn...) </li>
	 * <li> Update position of a L2Object after a mouvement </li>
	 * <BR>
	 * 
	 * @param point position of the object
	 */
	public L2WorldRegion getRegion(Point3D point)
	{
		return _worldRegions[(point.getX() >> SHIFT_BY) + OFFSET_X][(point.getY() >> SHIFT_BY) + OFFSET_Y];
	}
	
	public L2WorldRegion getRegion(int x, int y)
	{
		return _worldRegions[(x >> SHIFT_BY) + OFFSET_X][(y >> SHIFT_BY) + OFFSET_Y];
	}
	
	/**
	 * Returns the whole 2d array containing the world regions
	 * 
	 * @return
	 */
	public L2WorldRegion[][] getAllWorldRegions()
	{
		return _worldRegions;
	}
	
	/**
	 * Check if the current L2WorldRegions of the object is valid according to its position (x,y).<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Init L2WorldRegions </li>
	 * <BR>
	 * 
	 * @param x X position of the object
	 * @param y Y position of the object
	 * @return True if the L2WorldRegion is valid
	 */
	private boolean validRegion(int x, int y)
	{
		return (0 <= x && x <= REGIONS_X && 0 <= y && y <= REGIONS_Y);
	}
	
	/**
	 * Deleted all spawns in the world.
	 */
	public synchronized void deleteVisibleNpcSpawns()
	{
		_log.info("Deleting all visible NPC's.");
		
		for (int i = 0; i <= REGIONS_X; i++)
			for (int j = 0; j <= REGIONS_Y; j++)
				_worldRegions[i][j].deleteVisibleNpcSpawns();
		
		_log.info("All visible NPC's deleted.");
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final L2World _instance = new L2World();
	}
}
