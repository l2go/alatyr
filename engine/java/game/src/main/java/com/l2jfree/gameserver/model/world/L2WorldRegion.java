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

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.mapregion.L2MapArea;
import com.l2jfree.gameserver.model.mapregion.L2MapRegion;
import com.l2jfree.gameserver.model.mapregion.L2SpecialMapRegion;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.util.concurrent.L2EntityMap;
import com.l2jfree.util.concurrent.L2ReadWriteEntityMap;

public final class L2WorldRegion
{
	private static final Logger _log = LoggerFactory.getLogger(L2WorldRegion.class);
	
	public static final int MAP_MIN_X = -131072;
	public static final int MAP_MAX_X = 228608;
	public static final int MAP_MIN_Y = -262144;
	public static final int MAP_MAX_Y = 262144;
	
	private final L2EntityMap<L2Playable> _playables = new L2ReadWriteEntityMap<L2Playable>();
	private final L2EntityMap<L2Object> _objects = new L2ReadWriteEntityMap<L2Object>();
	
	private final int _tileX;
	private final int _tileY;
	
	private L2WorldRegion[] _surroundingRegions = new L2WorldRegion[0];
	private L2Zone[] _zones = new L2Zone[0];
	private L2SpecialMapRegion[] _specialMapRegions = new L2SpecialMapRegion[0];
	private L2MapArea[] _mapAreas = new L2MapArea[0];
	
	private volatile boolean _active = Config.GRIDS_ALWAYS_ON;
	private ScheduledFuture<?> _neighborsTask;
	
	public L2WorldRegion(int pTileX, int pTileY)
	{
		_tileX = pTileX;
		_tileY = pTileY;
	}
	
	public L2Zone[] getZones()
	{
		return _zones;
	}
	
	public void addZone(L2Zone zone)
	{
		_zones = Arrays.copyOf(_zones, _zones.length + 1);
		_zones[_zones.length - 1] = zone;
	}
	
	public void clearZones()
	{
		_zones = new L2Zone[0];
	}
	
	public void revalidateZones(L2Creature character)
	{
		// do NOT update the world region while the character is still in the process of teleporting
		// Once the teleport is COMPLETED, revalidation occurs safely, at that time.
		
		if (character.isTeleporting())
			return;
		
		for (L2Zone z : _zones)
			z.revalidateInZone(character);
	}
	
	public void removeFromZones(L2Creature character)
	{
		for (L2Zone z : _zones)
			z.removeFromZone(character);
	}
	
	public boolean containsZone(int zoneId)
	{
		for (L2Zone z : _zones)
			if (z.getId() == zoneId)
				return true;
		
		return false;
	}
	
	public L2Zone getZone(L2Zone.ZoneType zt, int x, int y)
	{
		for (L2Zone z : _zones)
			if (z.getType() == zt)
				if (z.isInsideZone(x, y))
					return z;
		
		return null;
	}
	
	public void onDie(L2Creature character)
	{
		for (L2Zone z : _zones)
			z.onDie(character);
	}
	
	public void onRevive(L2Creature character)
	{
		for (L2Zone z : _zones)
			z.onRevive(character);
	}
	
	public boolean checkEffectRangeInsidePeaceZone(L2Skill skill, final int x, final int y, final int z)
	{
		final int range = skill.getEffectRange();
		final int up = y + range;
		final int down = y - range;
		final int left = x + range;
		final int right = x - range;
		
		for (L2Zone e : _zones)
		{
			if (e.isPeace())
			{
				if (e.isCloserThan(x, y, range))
					return true;
				
				if (e.isInsideZone(x, up, z))
					return false;
				
				if (e.isInsideZone(x, down, z))
					return false;
				
				if (e.isInsideZone(left, y, z))
					return false;
				
				if (e.isInsideZone(right, y, z))
					return false;
				
				if (e.isInsideZone(x, y, z))
					return false;
			}
		}
		
		return true;
	}
	
	public void addMapRegion(L2MapRegion mapregion)
	{
		if (mapregion instanceof L2SpecialMapRegion)
			_specialMapRegions = (L2SpecialMapRegion[])ArrayUtils.add(_specialMapRegions, mapregion);
		else
			_mapAreas = (L2MapArea[])ArrayUtils.add(_mapAreas, mapregion);
	}
	
	public void clearMapRegions()
	{
		_specialMapRegions = new L2SpecialMapRegion[0];
		_mapAreas = new L2MapArea[0];
	}
	
	public L2MapRegion getMapRegion(int x, int y, int z)
	{
		for (L2SpecialMapRegion region : _specialMapRegions)
			if (region.checkIfInRegion(x, y, z))
				return region;
		
		for (L2MapArea region : _mapAreas)
			if (region.checkIfInRegion(x, y, z))
				return region;
		
		return null;
	}
	
	private final class NeighborsTask implements Runnable
	{
		private final boolean _isActivating;
		
		public NeighborsTask(boolean isActivating)
		{
			_isActivating = isActivating;
		}
		
		@Override
		public void run()
		{
			if (_isActivating)
			{
				for (L2WorldRegion neighbor : getSurroundingRegions())
					neighbor.setActive(true);
			}
			else
			{
				if (areNeighborsEmpty())
					setActive(false);
				
				for (L2WorldRegion neighbor : getSurroundingRegions())
					if (neighbor.areNeighborsEmpty())
						neighbor.setActive(false);
			}
		}
	}
	
	private void setActive(boolean active)
	{
		if (_active == active)
			return;
		
		_active = active;
		
		if (!active)
		{
			for (L2Object obj : getVisibleObjects())
			{
				if (obj instanceof L2Attackable)
				{
					L2Attackable mob = (L2Attackable)obj;
					
					mob.setTarget(null);
					mob.stopMove(null, false);
					mob.stopAllEffects();
					mob.clearAggroList();
					mob.resetAbsorbList();
					mob.getKnownList().removeAllKnownObjects();
					mob.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					mob.getAI().stopAITask();
				}
				
				if (obj instanceof L2Npc)
					((L2Npc)obj).stopRandomAnimation();
			}
		}
		else
		{
			final L2Object[][] surroundingObjects = getAllSurroundingObjects2DArray();
			
			for (L2Object obj : getVisibleObjects())
			{
				if (obj == null)
					continue;
				
				if (obj instanceof L2Attackable)
					((L2Attackable)obj).getStatus().startHpMpRegeneration();
				
				else if (obj instanceof L2Npc)
					((L2Npc)obj).broadcastRandomAnimation(false);
				
				obj.getKnownList().tryAddObjects(surroundingObjects);
			}
		}
	}
	
	public boolean isActive()
	{
		return _active;
	}

	static boolean shouldStartActivation(final boolean gridsAlwaysOn, final boolean hasPlayables,
			final boolean active)
	{
		return !gridsAlwaysOn && hasPlayables && !active;
	}
	
	// check if all 9 neighbors (including self) are inactive or active but with no players.
	// returns true if the above condition is met.
	public boolean areNeighborsEmpty()
	{
		// if this region is occupied, return false.
		if (isActive() && !_playables.isEmpty())
			return false;
		
		// if any one of the neighbors is occupied, return false
		for (L2WorldRegion neighbor : getSurroundingRegions())
			if (neighbor.isActive() && !neighbor._playables.isEmpty())
				return false;
		
		// in all other cases, return true.
		return true;
	}
	
	/**
	 * Immediately sets self as active and starts a timer to set neighbors as active this timer is to avoid turning on
	 * neighbors in the case when a person just teleported into a region and then teleported out immediately...there is
	 * no reason to activate all the neighbors in that case.
	 */
	private void startActivation()
	{
		// first set self to active and do self-tasks...
		setActive(true);
		
		// if the timer to deactivate neighbors is running, cancel it.
		synchronized (this)
		{
			if (_neighborsTask != null)
			{
				_neighborsTask.cancel(true);
				_neighborsTask = null;
			}
			
			// then, set a timer to activate the neighbors
			_neighborsTask =
					ThreadPoolManager.getInstance().schedule(new NeighborsTask(true),
							1000 * Config.GRID_NEIGHBOR_TURNON_TIME);
		}
	}
	
	/**
	 * starts a timer to set neighbors (including self) as inactive this timer is to avoid turning off neighbors in the
	 * case when a person just moved out of a region that he may very soon return to. There is no reason to turn self &
	 * neighbors off in that case.
	 */
	private void startDeactivation()
	{
		// if the timer to activate neighbors is running, cancel it.
		synchronized (this)
		{
			if (_neighborsTask != null)
			{
				_neighborsTask.cancel(true);
				_neighborsTask = null;
			}
			
			// start a timer to "suggest" a deactivate to self and neighbors.
			// suggest means: first check if a neighbor has L2Players in it.  If not, deactivate.
			_neighborsTask =
					ThreadPoolManager.getInstance().schedule(new NeighborsTask(false),
							1000 * Config.GRID_NEIGHBOR_TURNOFF_TIME);
		}
	}
	
	/**
	 * Add the L2Object in the _visibleObjects containing L2Object visible in this L2WorldRegion <BR>
	 * If L2Object is a L2Player, Add the L2Player in the L2ObjectHashSet(L2Player) _allPlayable containing
	 * L2Player of all player in game in this L2WorldRegion <BR>
	 * Assert : object.getCurrentWorldRegion() == this
	 */
	public void addVisibleObject(L2Object object, boolean addToKnownLists)
	{
		if (Config.ASSERT)
			assert object.getWorldRegion() == this;
		
		if (object == null)
			return;
		
		if (object instanceof L2Playable)
		{
			_playables.add((L2Playable)object);
			
			// if an inactive region has a playable, activate self & neighbors
			if (shouldStartActivation(Config.GRIDS_ALWAYS_ON, !_playables.isEmpty(), isActive()))
				startActivation();
		}
		
		_objects.add(object);
		
		if (addToKnownLists)
		{
			for (L2WorldRegion reg : getSurroundingRegions())
			{
				for (L2Object element : reg.getVisibleObjects())
				{
					element.getKnownList().addKnownObject(object);
					object.getKnownList().addKnownObject(element);
				}
			}
		}
	}
	
	/**
	 * Remove the L2Object from the L2ObjectHashSet(L2Object) _visibleObjects in this L2WorldRegion <BR>
	 * <BR>
	 * If L2Object is a L2Player, remove it from the L2ObjectHashSet(L2Player) _allPlayable of this
	 * L2WorldRegion <BR>
	 * Assert : object.getCurrentWorldRegion() == this || object.getCurrentWorldRegion() == null
	 */
	public void removeVisibleObject(L2Object object, boolean removeFromKnownlist)
	{
		if (Config.ASSERT)
			assert object.getWorldRegion() == this || object.getWorldRegion() == null;
		
		if (object == null)
			return;
		
		_objects.remove(object);
		
		if (object instanceof L2Playable)
		{
			_playables.remove((L2Playable)object);
			
			if (!Config.GRIDS_ALWAYS_ON && _playables.isEmpty())
				startDeactivation();
		}
		
		if (removeFromKnownlist)
		{
			for (L2WorldRegion reg : getSurroundingRegions())
			{
				for (L2Object element : reg.getVisibleObjects())
				{
					element.getKnownList().removeKnownObject(object);
					object.getKnownList().removeKnownObject(element);
				}
			}
		}
	}
	
	public void addSurroundingRegion(L2WorldRegion region)
	{
		_surroundingRegions = Arrays.copyOf(_surroundingRegions, _surroundingRegions.length + 1);
		_surroundingRegions[_surroundingRegions.length - 1] = region;
	}
	
	public L2WorldRegion[] getSurroundingRegions()
	{
		return _surroundingRegions;
	}
	
	public L2Object[] getVisibleObjects()
	{
		return _objects.toArray(L2Object.class);
	}
	
	public L2Playable[] getVisiblePlayables()
	{
		return _playables.toArray(L2Playable.class);
	}
	
	public String getName()
	{
		return "(" + _tileX + ", " + _tileY + ")";
	}
	
	/**
	 * Deleted all spawns in the world.
	 */
	public synchronized void deleteVisibleNpcSpawns()
	{
		for (L2Object obj : getVisibleObjects())
		{
			if (obj instanceof L2Npc)
			{
				L2Npc npc = (L2Npc)obj;
				npc.deleteMe();
				
				L2Spawn spawn = npc.getSpawn();
				if (spawn != null)
				{
					spawn.stopRespawn();
					SpawnTable.getInstance().deleteSpawn(spawn, false);
				}
			}
		}
		
		_log.info("All visible NPC's deleted in Region: " + getName());
	}
	
	public L2Object[][] getAllSurroundingObjects2DArray()
	{
		final L2Object[][] result = new L2Object[_surroundingRegions.length][];
		
		for (int i = 0; i < _surroundingRegions.length; i++)
			result[i] = _surroundingRegions[i].getVisibleObjects();
		
		return result;
	}
}
