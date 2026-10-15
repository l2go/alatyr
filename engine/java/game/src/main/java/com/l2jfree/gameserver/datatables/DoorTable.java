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
package com.l2jfree.gameserver.datatables;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.templates.StatsSet;

public final class DoorTable
{
	private static final Logger _log = LoggerFactory.getLogger(DoorTable.class);
	
	public static DoorTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, L2DoorInstance> _doors = new LinkedHashMap<Integer, L2DoorInstance>();
	
	private DoorTable()
	{
		reloadAll();
	}
	
	public void reloadAll()
	{
		_doorArray = null;
		_doors.clear();
		
		File doorFile = new File(Config.DATAPACK_ROOT, "data/door.csv");
		if (WorldFiles.skip(_log, "doors", doorFile))
			return;
		
		BufferedReader br = null;
		try
		{
			br = new BufferedReader(new FileReader(doorFile));
			
			for (String line; (line = br.readLine()) != null;)
			{
				if (line.trim().length() == 0 || line.startsWith("#"))
					continue;
				
				final L2DoorInstance door = parseLine(line);
				if (door == null)
					continue;
				
				putDoor(door);
				
				door.spawnMe(door.getX(), door.getY(), door.getZ());
				
				// Garden of Eva (every 7 minutes)
				if (door.getDoorName().startsWith("goe"))
					door.setAutoActionDelay(420000);
				
				// Tower of Insolence (every 5 minutes)
				else if (door.getDoorName().startsWith("aden_tower"))
					door.setAutoActionDelay(300000);
				
				/* TODO: check which are automatic
				// devils (every 5 minutes)
				else if (door.getDoorName().startsWith("pirate_isle"))
					door.setAutoActionDelay(300000);
				// Cruma Tower (every 20 minutes)
				else if (door.getDoorName().startsWith("cruma"))
					door.setAutoActionDelay(1200000);
				// Coral Garden Gate (every 15 minutes)
				else if (door.getDoorName().startsWith("Coral_garden"))
					door.setAutoActionDelay(900000);
				// Normil's cave (every 5 minutes)
				else if (door.getDoorName().startsWith("Normils_cave"))
					door.setAutoActionDelay(300000);
				// Normil's Garden (every 15 minutes)
				else if (door.getDoorName().startsWith("Normils_garden"))
					door.setAutoActionDelay(900000);
				*/
			}
			
			_log.info("DoorTable: Loaded " + _doors.size() + " Door Templates.");
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			IOUtils.closeQuietly(br);
		}
	}
	
	public void registerToClanHalls()
	{
		for (L2DoorInstance door : getDoors())
		{
			ClanHall clanhall = ClanHallManager.getInstance().getNearbyClanHall(door.getX(), door.getY(), 700);
			if (clanhall != null)
			{
				clanhall.getDoors().add(door);
				door.setClanHall(clanhall);
			}
		}
	}
	
	public void setCommanderDoors()
	{
		for (L2DoorInstance door : getDoors())
		{
			if (door.getFort() != null && door.isOpen())
			{
				door.setOpen(false);
				door.setIsCommanderDoor(true);
			}
		}
	}
	
	public static L2DoorInstance parseLine(String line)
	{
		L2DoorInstance door = null;
		try
		{
			StringTokenizer st = new StringTokenizer(line, ";");
			
			String name = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			int x = Integer.parseInt(st.nextToken());
			int y = Integer.parseInt(st.nextToken());
			int z = Integer.parseInt(st.nextToken());
			int rangeXMin = Integer.parseInt(st.nextToken());
			int rangeYMin = Integer.parseInt(st.nextToken());
			int rangeZMin = Integer.parseInt(st.nextToken());
			int rangeXMax = Integer.parseInt(st.nextToken());
			int rangeYMax = Integer.parseInt(st.nextToken());
			int rangeZMax = Integer.parseInt(st.nextToken());
			int hp = Integer.parseInt(st.nextToken());
			int pdef = Integer.parseInt(st.nextToken());
			int mdef = Integer.parseInt(st.nextToken());
			boolean unlockable = false;
			if (st.hasMoreTokens())
				unlockable = Boolean.parseBoolean(st.nextToken());
			boolean startOpen = false;
			if (st.hasMoreTokens())
				startOpen = Boolean.parseBoolean(st.nextToken());
			
			if (rangeXMin > rangeXMax)
				_log.error("Error in door data, XMin > XMax, ID:" + id);
			if (rangeYMin > rangeYMax)
				_log.error("Error in door data, YMin > YMax, ID:" + id);
			if (rangeZMin > rangeZMax)
				_log.error("Error in door data, ZMin > ZMax, ID:" + id);
			
			int collisionRadius; // (max) radius for movement checks
			if (rangeXMax - rangeXMin > rangeYMax - rangeYMin)
				collisionRadius = rangeYMax - rangeYMin;
			else
				collisionRadius = rangeXMax - rangeXMin;
			
			StatsSet npcDat = new StatsSet();
			npcDat.set("npcId", id);
			npcDat.set("level", 0);
			npcDat.set("jClass", "door");
			
			npcDat.set("baseSTR", 0);
			npcDat.set("baseCON", 0);
			npcDat.set("baseDEX", 0);
			npcDat.set("baseINT", 0);
			npcDat.set("baseWIT", 0);
			npcDat.set("baseMEN", 0);
			
			npcDat.set("baseShldDef", 0);
			npcDat.set("baseShldRate", 0);
			npcDat.set("baseAccCombat", 38);
			npcDat.set("baseEvasRate", 38);
			npcDat.set("baseCritRate", 38);
			
			//npcDat.set("name", "");
			npcDat.set("collision_radius", collisionRadius);
			npcDat.set("collision_height", rangeZMax - rangeZMin & 0xfff0);
			npcDat.set("fcollision_radius", collisionRadius);
			npcDat.set("fcollision_height", rangeZMax - rangeZMin & 0xfff0);
			npcDat.set("sex", "male");
			npcDat.set("type", "");
			npcDat.set("baseAtkRange", 0);
			npcDat.set("baseMpMax", 0);
			npcDat.set("baseCpMax", 0);
			npcDat.set("rewardExp", 0);
			npcDat.set("rewardSp", 0);
			npcDat.set("basePAtk", 0);
			npcDat.set("baseMAtk", 0);
			npcDat.set("basePAtkSpd", 0);
			npcDat.set("aggroRange", 0);
			npcDat.set("baseMAtkSpd", 0);
			npcDat.set("rhand", 0);
			npcDat.set("lhand", 0);
			npcDat.set("armor", 0);
			npcDat.set("baseWalkSpd", 0);
			npcDat.set("baseRunSpd", 0);
			npcDat.set("name", name);
			npcDat.set("baseHpMax", hp);
			npcDat.set("baseHpReg", 3.e-3f);
			npcDat.set("baseMpReg", 3.e-3f);
			npcDat.set("basePDef", pdef);
			npcDat.set("baseMDef", mdef);
			
			L2CreatureTemplate template = new L2CreatureTemplate(npcDat);
			door = new L2DoorInstance(IdFactory.getInstance().getNextId(), template, id, name, unlockable);
			door.setRange(rangeXMin, rangeYMin, rangeZMin, rangeXMax, rangeYMax, rangeZMax);
			door.setMapRegion(MapRegionManager.getInstance().getRegion(x, y, z));
			door.getStatus().setCurrentHpMp(door.getMaxHp(), door.getMaxMp());
			door.setOpen(startOpen);
			door.getPosition().setXYZInvisible(x, y, z);
			
			door.setMapRegion(MapRegionManager.getInstance().getRegion(x, y));
		}
		catch (Exception e)
		{
			_log.error("Error in door data at line: " + line, e);
		}
		
		return door;
	}
	
	public L2DoorInstance getDoor(Integer id)
	{
		return _doors.get(id);
	}
	
	public void putDoor(L2DoorInstance door)
	{
		_doorArray = null;
		_doors.put(door.getDoorId(), door);
		GeoData.getInstance().initDoorGeodata(door);
	}
	
	private L2DoorInstance[] _doorArray;
	
	public L2DoorInstance[] getDoors()
	{
		if (_doorArray == null)
			_doorArray = _doors.values().toArray(new L2DoorInstance[_doors.size()]);
		
		return _doorArray;
	}
	
	/**
	 * Open list of doors in the instance
	 */
	public static void openInstanceDoors(final int instanceId, final int[] doorIds)
	{
		Instance instance = InstanceManager.getInstance().getInstance(instanceId);
		if (instance == null || doorIds == null)
			return;
		
		for (int doorId : doorIds)
		{
			L2DoorInstance door = instance.getDoor(doorId);
			if (door != null)
				door.openMe();
		}
	}
	
	/**
	 * Close list of doors in the instance
	 */
	public static void closeInstanceDoors(final int instanceId, final int[] doorIds)
	{
		Instance instance = InstanceManager.getInstance().getInstance(instanceId);
		if (instance == null || doorIds == null)
			return;
		
		for (int doorId : doorIds)
		{
			L2DoorInstance door = instance.getDoor(doorId);
			if (door != null)
				door.closeMe();
		}
	}
	
	/**
	 * Close list of doors
	 */
	public void closeDoors(final int[] doorIds)
	{
		if (doorIds == null)
			return;
		
		for (int doorId : doorIds)
			closeDoor(doorId);
	}
	
	public void closeDoor(int doorId)
	{
		L2DoorInstance door = getDoor(doorId);
		if (door != null)
			door.closeMe();
	}
	
	/**
	 * Open list of doors
	 */
	public void openDoors(final int[] doorIds)
	{
		if (doorIds == null)
			return;
		
		for (int doorId : doorIds)
			openDoor(doorId);
	}
	
	public void openDoor(int doorId)
	{
		L2DoorInstance door = getDoor(doorId);
		if (door != null)
			door.openMe();
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final DoorTable _instance = new DoorTable();
	}
}
