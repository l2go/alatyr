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
package com.l2jfree.gameserver.geodata;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2SiegeGuard;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.geodata.pathfinding.Node;
import com.l2jfree.gameserver.geodata.pathfinding.cellnodes.CellPathFinding;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.util.RuntimeData;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.util.L2Arrays;
import com.l2jfree.util.LookupTable;

/**
 * @authors -Nemesiss-, hex1r0
 */
final class GeoEngine extends GeoData
{
	private static final byte EAST = 1;
	private static final byte WEST = 2;
	private static final byte SOUTH = 4;
	private static final byte NORTH = 8;
	
	private static final byte NSWE_ALL = 15;
	private static final byte NSWE_NONE = 0;
	
	private static final byte FLAT = 0;
	private static final byte COMPLEX = 1;
	private static final byte MULTILEVEL = 2;
	
	private static final class SingletonHolder
	{
		private static final GeoEngine INSTANCE = new GeoEngine();
	}
	
	public static GeoEngine getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final LookupTable<MappedByteBuffer> _geodata = new LookupTable<MappedByteBuffer>();
	private final LookupTable<int[]> _geodataIndex = new LookupTable<int[]>();
	private BufferedOutputStream _geoBugsOut;
	
	private final Map<Integer, Map<Long, Byte>> _instanceGeodata = new ConcurrentHashMap<Integer, Map<Long, Byte>>();
	private final Map<Integer, Map<Long, Byte>> _doorGeodata = new LinkedHashMap<Integer, Map<Long, Byte>>();
	
	private GeoEngine()
	{
		nInitGeodata();
	}
	
	@Override
	public void deleteInstanceGeodata(int instanceId)
	{
		_instanceGeodata.remove(instanceId);
	}
	
	@Override
	public void setDoorGeodataOpen(L2DoorInstance door, boolean open)
	{
		final Map<Long, Byte> doorGeodata = _doorGeodata.get(door.getDoorId());
		
		if (doorGeodata == null)
			return;
		
		Map<Long, Byte> instanceGeodata = _instanceGeodata.get(door.getInstanceId());
		
		if (instanceGeodata == null)
			_instanceGeodata.put(door.getInstanceId(), instanceGeodata = new LinkedHashMap<Long, Byte>());
		
		for (Entry<Long, Byte> doorGeo : doorGeodata.entrySet())
			instanceGeodata.put(doorGeo.getKey(), open ? doorGeo.getValue() : NSWE_NONE);
	}
	
	@Override
	public void initDoorGeodata(L2DoorInstance door)
	{
		int minX = (door.getXMin() - L2World.MAP_MIN_X) >> 4;
		int maxX = (door.getXMax() - L2World.MAP_MIN_X) >> 4;
		int minY = (door.getYMin() - L2World.MAP_MIN_Y) >> 4;
		int maxY = (door.getYMax() - L2World.MAP_MIN_Y) >> 4;
		int z = door.getZMin();
		
		Map<Long, Byte> doorGeodata = _doorGeodata.get(door.getDoorId());
		
		for (int geoX = minX; geoX <= maxX; geoX++)
		{
			for (int geoY = minY; geoY <= maxY; geoY++)
			{
				if (geoX >= minX && geoX <= maxX && geoY >= minY && geoY <= maxY)
				{
					short region = getRegionOffset(geoX, geoY);
					int cellX, cellY, index, neededIndex;
					short NSWE = NSWE_NONE;
					
					neededIndex = index = getIndex(geoX, geoY, region);
					ByteBuffer regionGeo = _geodata.get(region);
					if (regionGeo == null)
					{
						if (Config.DEGUG_DOOR_GEODATA)
							_log.info("GeoEngine: Door: " + door.getDoorId() + " has no geodata!");
						continue;
					}
					
					short tempz = Short.MIN_VALUE;
					byte type = regionGeo.get(index++);
					switch (type)
					{
						case COMPLEX:
							cellX = getCell(geoX);
							cellY = getCell(geoY);
							index += ((cellX << 3) + cellY) << 1;
							short height = regionGeo.getShort(index);
							NSWE = (short)(height & 0x0F);
							neededIndex = index;
							tempz = (short)(height & 0x0fff0);
							tempz = (short)(tempz >> 1);
							break;
						
						case MULTILEVEL:
							cellX = getCell(geoX);
							cellY = getCell(geoY);
							int offset = (cellX << 3) + cellY;
							while (offset > 0)
							{
								byte lc = regionGeo.get(index);
								index += (lc << 1) + 1;
								offset--;
							}
							byte layers = regionGeo.get(index++);
							height = -1;
							if (layers <= 0 || layers > 125)
							{
								_log.warn("Broken geofile (case5), region: " + region + " - invalid layer count: "
										+ layers + " at: " + geoX + " " + geoY);
								continue;
							}
							
							while (layers > 0)
							{
								height = regionGeo.getShort(index);
								height = (short)(height & 0x0fff0);
								height = (short)(height >> 1); // height / 2
								
								if ((z - tempz) * (z - tempz) > (z - height) * (z - height))
								{
									tempz = height;
									NSWE = regionGeo.get(index);
									NSWE = (short)(NSWE & 0x0F);
									neededIndex = index;
								}
								layers--;
								index += 2;
							}
							break;
						
						default:
							continue;
					}
					
					// skip blocks that are above door
					if (tempz > (door.getZMax() + door.getZMin()) / 2)
						continue;
					
					if (doorGeodata == null)
						_doorGeodata.put(door.getDoorId(), doorGeodata = new LinkedHashMap<Long, Byte>());
					
					doorGeodata.put(((long)region << 32) | neededIndex, (byte)NSWE);
				}
			}
		}
		
		// to add it to the proper instance
		setDoorGeodataOpen(door, door.isOpen());
	}
	
	private short getInstanceNSWE(int instanceId, short initialNSWE, short region, int index)
	{
		Map<Long, Byte> instanceGeodata = _instanceGeodata.get(instanceId);
		if (instanceGeodata != null)
		{
			Byte NSWE = instanceGeodata.get(((long)region << 32) | index);
			if (NSWE != null)
				return NSWE;
		}
		return initialNSWE;
	}
	
	@Override
	public short getType(int x, int y)
	{
		return nGetType((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4);
	}
	
	@Override
	public short getHeight(int x, int y, int z)
	{
		return nGetHeight((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4, z);
	}
	
	@Override
	public short getSpawnHeight(int x, int y, int zmin, int zmax, int spawnid)
	{
		return nGetSpawnHeight((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4, zmin, zmax, spawnid);
	}
	
	@Override
	public String geoPosition(int x, int y)
	{
		int gx = (x - L2World.MAP_MIN_X) >> 4;
		int gy = (y - L2World.MAP_MIN_Y) >> 4;
		return "bx: " + getBlock(gx) + " by: " + getBlock(gy) + " cx: " + getCell(gx) + " cy: " + getCell(gy)
				+ "  region offset: " + getRegionOffset(gx, gy);
	}
	
	@Override
	public boolean canSeeTarget(L2Object cha, Point3D target)
	{
		if (cha.getZ() >= target.getZ())
			return canSeeTarget(cha.getX(), cha.getY(), cha.getZ(), target.getX(), target.getY(), target.getZ(),
					cha.getInstanceId());
		else
			return canSeeTarget(target.getX(), target.getY(), target.getZ(), cha.getX(), cha.getY(), cha.getZ(),
					cha.getInstanceId());
	}
	
	@Override
	public boolean canSeeTarget(L2Object cha, L2Object target)
	{
		// To be able to see over fences and give the player the viewpoint
		// game client has, all coordinates are lifted 45 from ground.
		// Because of layer selection in LOS algorithm (it selects -45 there
		// and some layers can be very close...) do not change this without
		// changing the LOS code.
		// Basically the +45 is character height. Raid bosses are naturally higher,
		// dwarves shorter, but this should work relatively well.
		// If this is going to be improved, use e.g.
		// ((L2Creature)cha).getTemplate().collisionHeight
		int z = cha.getZ() + 45;
		if (cha instanceof L2SiegeGuard)
			z += 30; // well they don't move closer to balcony fence at the moment :(
		int z2 = target.getZ() + 45;
		if (target instanceof L2DoorInstance)
			return true; // door coordinates are hinge coords..
		if (target instanceof L2SiegeGuard)
			z2 += 30; // well they don't move closer to balcony fence at the moment :(
		if (z >= z2)
			return canSeeTarget(cha.getX(), cha.getY(), z, target.getX(), target.getY(), z2, cha.getInstanceId());
		else
			return canSeeTarget(target.getX(), target.getY(), z2, cha.getX(), cha.getY(), z, cha.getInstanceId());
	}
	
	@Override
	public boolean canSeeTargetDebug(L2Player gm, L2Object target)
	{
		int z = gm.getZ() + 45;
		int z2 = target.getZ() + 45;
		if (target instanceof L2DoorInstance)
		{
			gm.sendMessage("door always true");
			return true; // door coordinates are hinge coords..
		}
		
		if (z >= z2)
			return canSeeDebug(gm, (gm.getX() - L2World.MAP_MIN_X) >> 4, (gm.getY() - L2World.MAP_MIN_Y) >> 4, z,
					(target.getX() - L2World.MAP_MIN_X) >> 4, (target.getY() - L2World.MAP_MIN_Y) >> 4, z2,
					gm.getInstanceId());
		else
			return canSeeDebug(gm, (target.getX() - L2World.MAP_MIN_X) >> 4, (target.getY() - L2World.MAP_MIN_Y) >> 4,
					z2, (gm.getX() - L2World.MAP_MIN_X) >> 4, (gm.getY() - L2World.MAP_MIN_Y) >> 4, z,
					gm.getInstanceId());
	}
	
	@Override
	public short getNSWE(int x, int y, int z, int instanceId)
	{
		return nGetNSWE((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4, z, instanceId);
	}
	
	@Override
	public boolean canMoveFromToTarget(int x, int y, int z, int tx, int ty, int tz, int instanceId)
	{
		Location destination = moveCheck(x, y, z, tx, ty, tz, instanceId);
		return (destination.getX() == tx && destination.getY() == ty && destination.getZ() == tz);
	}
	
	@Override
	public void addGeoDataBug(L2Player gm, String comment)
	{
		int gx = (gm.getX() - L2World.MAP_MIN_X) >> 4;
		int gy = (gm.getY() - L2World.MAP_MIN_Y) >> 4;
		int bx = getBlock(gx);
		int by = getBlock(gy);
		int cx = getCell(gx);
		int cy = getCell(gy);
		int rx = (gx >> 11) + 10;
		int ry = (gy >> 11) + 10;
		String out = rx + ";" + ry + ";" + bx + ";" + by + ";" + cx + ";" + cy + ";" + gm.getZ() + ";" + comment + "\n";
		try
		{
			_geoBugsOut.write(out.getBytes());
			_geoBugsOut.flush();
			gm.sendMessage("GeoData bug saved!");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			gm.sendMessage("GeoData bug save Failed!");
		}
	}
	
	@Override
	public boolean canSeeTarget(int x, int y, int z, int tx, int ty, int tz, int instanceId)
	{
		return canSee((x - L2World.MAP_MIN_X) >> 4, (y - L2World.MAP_MIN_Y) >> 4, z, (tx - L2World.MAP_MIN_X) >> 4,
				(ty - L2World.MAP_MIN_Y) >> 4, tz, instanceId);
	}
	
	@Override
	public boolean hasGeo(int x, int y)
	{
		int gx = (x - L2World.MAP_MIN_X) >> 4;
		int gy = (y - L2World.MAP_MIN_Y) >> 4;
		short region = getRegionOffset(gx, gy);
		
		return _geodata.get(region) != null;
	}
	
	private boolean canSee(int x, int y, double z, int tx, int ty, int tz, int instanceId)
	{
		int dx = (tx - x);
		int dy = (ty - y);
		final double dz = (tz - z);
		final int distance2 = dx * dx + dy * dy;
		
		if (distance2 > 90000) // (300*300) 300*16 = 4800 in world coord
		{
			// Avoid too long check
			return false;
		}
		// very short checks: 9 => 144 world distance
		// this ensures NLOS function has enough points to calculate,
		// it might not work when distance is small and path vertical
		else if (distance2 < 82)
		{
			// 150 should be too deep/high.
			if (dz * dz > 22500)
			{
				short region = getRegionOffset(x, y);
				// geodata is loaded for region and mobs should have correct Z coordinate...
				// so there would likely be a floor in between the two
				if (_geodata.get(region) != null)
					return false;
			}
			return true;
		}
		
		// Increment in Z coordinate when moving along X or Y axis
		// and not straight to the target. This is done because
		// calculation moves either in X or Y direction.
		final int inc_x = sign(dx);
		final int inc_y = sign(dy);
		dx = Math.abs(dx);
		dy = Math.abs(dy);
		final double inc_z_directionx = dz * dx / (distance2);
		final double inc_z_directiony = dz * dy / (distance2);
		
		// next_* are used in NLOS check from x,y
		int next_x = x;
		int next_y = y;
		
		// creates path to the target
		// calculation stops when next_* == target
		if (dx >= dy)// dy/dx <= 1
		{
			int delta_A = 2 * dy;
			int d = delta_A - dx;
			int delta_B = delta_A - 2 * dx;
			
			for (int i = 0; i < dx; i++)
			{
				x = next_x;
				y = next_y;
				if (d > 0)
				{
					d += delta_B;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, y, (int)z, inc_x, 0, inc_z_directionx, tz, false, instanceId))
						return false;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(next_x, y, (int)z, 0, inc_y, inc_z_directiony, tz, false, instanceId))
						return false;
				}
				else
				{
					d += delta_A;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, y, (int)z, inc_x, 0, inc_z_directionx, tz, false, instanceId))
						return false;
				}
			}
		}
		else
		{
			int delta_A = 2 * dx;
			int d = delta_A - dy;
			int delta_B = delta_A - 2 * dy;
			for (int i = 0; i < dy; i++)
			{
				x = next_x;
				y = next_y;
				if (d > 0)
				{
					d += delta_B;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(x, y, (int)z, 0, inc_y, inc_z_directiony, tz, false, instanceId))
						return false;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, next_y, (int)z, inc_x, 0, inc_z_directionx, tz, false, instanceId))
						return false;
				}
				else
				{
					d += delta_A;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(x, y, (int)z, 0, inc_y, inc_z_directiony, tz, false, instanceId))
						return false;
				}
			}
		}
		return true;
	}
	
	/*
	 * Debug function for checking if there's a line of sight between
	 * two coordinates.
	 * 
	 * Creates points for line of sight check (x,y,z towards target) and
	 * in each point, layer and movement checks are made with NLOS function.
	 * 
	 * Coordinates here are geodata x,y but z coordinate is world coordinate
	 */
	private boolean canSeeDebug(L2Player gm, int x, int y, double z, int tx, int ty, int tz, int instanceId)
	{
		int dx = (tx - x);
		int dy = (ty - y);
		final double dz = (tz - z);
		final int distance2 = dx * dx + dy * dy;
		
		if (distance2 > 90000) // (300*300) 300*16 = 4800 in world coord
		{
			// Avoid too long check
			gm.sendMessage("dist > 300");
			return false;
		}
		// very short checks: 9 => 144 world distance
		// this ensures NLOS function has enough points to calculate,
		// it might not work when distance is small and path vertical
		else if (distance2 < 82)
		{
			// 150 should be too deep/high.
			if (dz * dz > 22500)
			{
				short region = getRegionOffset(x, y);
				// geodata is loaded for region and mobs should have correct Z coordinate...
				// so there would likely be a floor in between the two
				if (_geodata.get(region) != null)
					return false;
			}
			return true;
		}
		
		// Increment in Z coordinate when moving along X or Y axis
		// and not straight to the target. This is done because
		// calculation moves either in X or Y direction.
		final int inc_x = sign(dx);
		final int inc_y = sign(dy);
		dx = Math.abs(dx);
		dy = Math.abs(dy);
		final double inc_z_directionx = dz * dx / (distance2);
		final double inc_z_directiony = dz * dy / (distance2);
		
		gm.sendMessage("Los: from X: " + x + "Y: " + y + "--->> X: " + tx + " Y: " + ty);
		
		// next_* are used in NLOS check from x,y
		int next_x = x;
		int next_y = y;
		
		// creates path to the target
		// calculation stops when next_* == target
		if (dx >= dy)// dy/dx <= 1
		{
			int delta_A = 2 * dy;
			int d = delta_A - dx;
			int delta_B = delta_A - 2 * dx;
			
			for (int i = 0; i < dx; i++)
			{
				x = next_x;
				y = next_y;
				if (d > 0)
				{
					d += delta_B;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, y, (int)z, inc_x, 0, inc_z_directionx, tz, true, instanceId))
						return false;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(next_x, y, (int)z, 0, inc_y, inc_z_directiony, tz, true, instanceId))
						return false;
				}
				else
				{
					d += delta_A;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, y, (int)z, inc_x, 0, inc_z_directionx, tz, true, instanceId))
						return false;
				}
			}
		}
		else
		{
			int delta_A = 2 * dx;
			int d = delta_A - dy;
			int delta_B = delta_A - 2 * dy;
			for (int i = 0; i < dy; i++)
			{
				x = next_x;
				y = next_y;
				if (d > 0)
				{
					d += delta_B;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(x, y, (int)z, 0, inc_y, inc_z_directiony, tz, true, instanceId))
						return false;
					next_x += inc_x;
					z += inc_z_directionx;
					if (!nLOS(x, next_y, (int)z, inc_x, 0, inc_z_directionx, tz, true, instanceId))
						return false;
				}
				else
				{
					d += delta_A;
					next_y += inc_y;
					z += inc_z_directiony;
					if (!nLOS(x, y, (int)z, 0, inc_y, inc_z_directiony, tz, true, instanceId))
						return false;
				}
			}
		}
		return true;
	}
	
	@Override
	public Location moveCheck(int x, int y, int z, int targetX, int targetY, int targetZ, int instanceId)
	{
		int geoX = (x - L2World.MAP_MIN_X) >> 4;
		int geoY = (y - L2World.MAP_MIN_Y) >> 4;
		int geoTargetX = (targetX - L2World.MAP_MIN_X) >> 4;
		int geoTargetY = (targetY - L2World.MAP_MIN_Y) >> 4;
		
		int dx = (geoTargetX - geoX);
		int dy = (geoTargetY - geoY);
		final int distance2 = dx * dx + dy * dy;
		
		if (distance2 == 0)
			return new Location(x, y, z);
		
		// TODO review this
		if (distance2 > 36100) // 190*190*16 = 3040 world coord
		{
			// Avoid too long check
			// Currently we calculate a middle point
			// for wyvern users and otherwise for comfort
			double divider = Math.sqrt((double)30000 / distance2);
			geoTargetX = geoX + (int)(divider * dx);
			geoTargetY = geoY + (int)(divider * dy);
			int dz = (targetZ - z);
			targetZ = (z + (int)(divider * dz));
			dx = (geoTargetX - geoX);
			dy = (geoTargetY - geoY);
			// return startpoint;
		}
		
		// Increment in Z coordinate when moving along X or Y axis
		// and not straight to the target. This is done because
		// calculation moves either in X or Y direction.
		final int inc_x = sign(dx);
		final int inc_y = sign(dy);
		dx = Math.abs(dx);
		dy = Math.abs(dy);
		
		int previousX, previousY, previousZ;
		
		int nextX = geoX;
		int nextY = geoY;
		int nextZ = z;
		
		if (dx >= dy) // dy/dx <= 1
		{
			int delta_A = 2 * dy;
			int d = delta_A - dx;
			int delta_B = delta_A - 2 * dx;
			for (int i = 0; i < dx; i++)
			{
				previousX = geoX;
				previousY = geoY;
				previousZ = z;
				geoX = nextX;
				geoY = nextY;
				z = nextZ;
				if (d > 0)
				{
					d += delta_B;
					
					nextX += inc_x;
					nextY += inc_y;
					nextZ = nCanMoveNext(geoX, geoY, z, nextX, nextY, instanceId);
					if (nextZ == Integer.MIN_VALUE)
						return new Location((previousX << 4) + L2World.MAP_MIN_X, (previousY << 4) + L2World.MAP_MIN_Y,
								previousZ);
				}
				else
				{
					d += delta_A;
					
					nextX += inc_x;
					nextZ = nCanMoveNext(geoX, geoY, z, nextX, nextY, instanceId);
					if (nextZ == Integer.MIN_VALUE)
						return new Location((previousX << 4) + L2World.MAP_MIN_X, (previousY << 4) + L2World.MAP_MIN_Y,
								previousZ);
				}
			}
		}
		else
		{
			int delta_A = 2 * dx;
			int d = delta_A - dy;
			int delta_B = delta_A - 2 * dy;
			for (int i = 0; i < dy; i++)
			{
				previousX = geoX;
				previousY = geoY;
				previousZ = z;
				geoX = nextX;
				geoY = nextY;
				z = nextZ;
				if (d > 0)
				{
					d += delta_B;
					
					nextX += inc_x;
					nextY += inc_y;
					nextZ = nCanMoveNext(geoX, geoY, z, nextX, nextY, instanceId);
					if (nextZ == Integer.MIN_VALUE)
						return new Location((previousX << 4) + L2World.MAP_MIN_X, (previousY << 4) + L2World.MAP_MIN_Y,
								previousZ);
				}
				else
				{
					d += delta_A;
					
					nextY += inc_y;
					nextZ = nCanMoveNext(geoX, geoY, z, nextX, nextY, instanceId);
					if (nextZ == Integer.MIN_VALUE)
						return new Location((previousX << 4) + L2World.MAP_MIN_X, (previousY << 4) + L2World.MAP_MIN_Y,
								previousZ);
				}
			}
		}
		return new Location(targetX, targetY, nextZ);
	}
	
	private byte sign(int x)
	{
		if (x >= 0)
			return +1;
		else
			return -1;
	}
	
	private void nInitGeodata()
	{
		BufferedReader reader = null;
		try
		{
			_log.info("Geo Engine: - Loading Geodata...");
			File Data = new File(Config.DATAPACK_ROOT, "data/geodata/geo_index.txt");
			if (!Data.exists())
				return;
			
			reader = new BufferedReader(new FileReader(Data));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error("Failed to Load geo_index File.");
		}
		String line;
		try
		{
			while ((line = reader.readLine()) != null)
			{
				if (line.trim().length() == 0)
					continue;
				StringTokenizer st = new StringTokenizer(line, "_");
				byte rx = Byte.parseByte(st.nextToken());
				byte ry = Byte.parseByte(st.nextToken());
				loadGeodataFile(rx, ry);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error("Failed to Read geo_index File.");
		}
		finally
		{
			try
			{
				reader.close();
			}
			catch (Exception e)
			{
			}
		}
		try
		{
			File geo_bugs = RuntimeData.file("geo_bugs.txt");
			_geoBugsOut = new BufferedOutputStream(new FileOutputStream(geo_bugs, true));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error("Failed to Load geo_bugs.txt File.");
		}
	}
	
	@Override
	public void unloadGeodata(byte rx, byte ry)
	{
		short regionoffset = (short)((rx << 5) + ry);
		_geodataIndex.remove(regionoffset);
		_geodata.remove(regionoffset);
	}
	
	@Override
	public boolean loadGeodataFile(byte rx, byte ry)
	{
		String fname = "data/geodata/" + rx + "_" + ry + ".l2j";
		short regionoffset = (short)((rx << 5) + ry);
		_log.info("Geo Engine: - Loading: " + fname + " -> region offset: " + regionoffset + "X: " + rx + " Y: " + ry);
		File Geo = new File(Config.DATAPACK_ROOT, fname);
		int size, index = 0, block = 0, flor = 0;
		FileChannel roChannel = null;
		try
		{
			// Create a read-only memory-mapped file
			roChannel = new RandomAccessFile(Geo, "r").getChannel();
			size = (int)roChannel.size();
			MappedByteBuffer geo;
			// Force O/S to Loads this buffer's content into physical memory.
			// it is not guarantee, because the underlying operating system may
			// have paged out some of the buffer's data
			if (Config.FORCE_GEODATA)
				geo = roChannel.map(FileChannel.MapMode.READ_ONLY, 0, size).load();
			else
				geo = roChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);
			
			geo.order(ByteOrder.LITTLE_ENDIAN);
			if (size > 196608)
			{
				// Indexing geo files, so we will know where each block starts
				int[] indexs = new int[65536];
				while (block < 65536)
				{
					indexs[block++] = index;
					byte type = geo.get(index++);
					switch (type)
					{
						case FLAT:
							index += 2; // 1 x short
							break;
						case COMPLEX:
							index += 128; // 64 x short
							break;
						case MULTILEVEL:
							for (int b = 0; b < 64; b++)
							{
								byte layers = geo.get(index);
								index += (layers << 1) + 1;
								if (layers > flor)
									flor = layers;
							}
					}
				}
				_geodataIndex.put(regionoffset, indexs);
			}
			_geodata.put(regionoffset, geo);
			
			_log.info("Geo Engine: - Max Layers: " + flor + " Size: " + size + " Loaded: " + index);
		}
		catch (Exception e)
		{
			_log.warn("Failed to Load GeoFile at block: " + block, e);
			return false;
		}
		finally
		{
			try
			{
				if (roChannel != null)
					roChannel.close();
			}
			catch (Exception e)
			{
			}
		}
		return true;
	}
	
	/**
	 * @param x
	 * @param y
	 * @return Region Offset
	 */
	private short getRegionOffset(int x, int y)
	{
		int rx = x >> 11; // =/(256 * 8)
		int ry = y >> 11;
		return (short)(((rx + 10) << 5) + (ry + 10));
	}
	
	/**
	 * @param geo_pos
	 * @return Block Index: 0-255
	 */
	private int getBlock(int geo_pos)
	{
		return (geo_pos >> 3) % 256;
	}
	
	/**
	 * @param geo_pos
	 * @return Cell Index: 0-7
	 */
	private int getCell(int geo_pos)
	{
		return geo_pos % 8;
	}
	
	/**
	 * @param x
	 * @param y
	 * @return Type of geo_block: 0-2
	 */
	private short nGetType(int x, int y)
	{
		short region = getRegionOffset(x, y);
		int index = getIndex(x, y, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return FLAT;
		
		return regionGeo.get(index);
	}
	
	/**
	 * @param geox
	 * @param geoy
	 * @param z
	 * @return Nearest Z
	 */
	private short nGetHeight(int geox, int geoy, int z)
	{
		short region = getRegionOffset(geox, geoy);
		int cellX, cellY, index;
		index = getIndex(geox, geoy, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return (short)z;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				return regionGeo.getShort(index);
				
			case COMPLEX:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				index += ((cellX << 3) + cellY) << 1;
				short height = regionGeo.getShort(index);
				height = (short)(height & 0x0fff0);
				height = (short)(height >> 1); // height / 2
				return height;
				
			case MULTILEVEL:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				int offset = (cellX << 3) + cellY;
				while (offset > 0)
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				height = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case1), region: " + region + " - invalid layer count: " + layers
							+ " at: " + geox + " " + geoy);
					return (short)z;
				}
				short temph = Short.MIN_VALUE;
				while (layers > 0)
				{
					height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					if ((z - temph) * (z - temph) > (z - height) * (z - height))
						temph = height;
					layers--;
					index += 2;
				}
				return temph;
				
			default:
				return (short)z;
		}
	}
	
	/**
	 * @param geox
	 * @param geoy
	 * @param z
	 * @return One layer higher Z than parameter Z
	 */
	private short nGetUpperHeight(int geox, int geoy, int z)
	{
		short region = getRegionOffset(geox, geoy);
		int cellX, cellY, index;
		index = getIndex(geox, geoy, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return (short)z;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				return regionGeo.getShort(index);
				
			case COMPLEX:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				index += ((cellX << 3) + cellY) << 1;
				short height = regionGeo.getShort(index);
				height = (short)(height & 0x0fff0);
				height = (short)(height >> 1); // height / 2
				return height;
				
			case MULTILEVEL:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				int offset = (cellX << 3) + cellY;
				while (offset > 0)
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				height = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case1), region: " + region + " - invalid layer count: " + layers
							+ " at: " + geox + " " + geoy);
					return (short)z;
				}
				short temph = Short.MAX_VALUE;
				while (layers > 0) // from higher to lower
				{
					height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					if (height < z)
						return temph;
					temph = height;
					layers--;
					index += 2;
				}
				return temph;
				
			default:
				return (short)z;
		}
	}
	
	/**
	 * @param geox
	 * @param geoy
	 * @param zmin
	 * @param zmax
	 * @return Z betwen zmin and zmax
	 */
	private short nGetSpawnHeight(int geox, int geoy, int zmin, int zmax, int spawnid)
	{
		short region = getRegionOffset(geox, geoy);
		int cellX, cellY, index;
		short temph = Short.MIN_VALUE;
		index = getIndex(geox, geoy, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return (short)zmin;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				temph = regionGeo.getShort(index);
				break;
			
			case COMPLEX:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				index += ((cellX << 3) + cellY) << 1;
				temph = regionGeo.getShort(index);
				temph = (short)(temph & 0x0fff0);
				temph = (short)(temph >> 1); // height / 2
				break;
			
			case MULTILEVEL:
				cellX = getCell(geox);
				cellY = getCell(geoy);
				int offset = (cellX << 3) + cellY;
				while (offset > 0)
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case2), region: " + region + " - invalid layer count: " + layers
							+ " at: " + geox + " " + geoy);
					return (short)zmin;
				}
				while (layers > 0)
				{
					short height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					if ((zmin - temph) * (zmin - temph) > (zmin - height) * (zmin - height))
						temph = height;
					layers--;
					index += 2;
				}
				if (temph > zmax + 200 || temph < zmin - 200)
				{
					if (_log.isDebugEnabled())
						_log.warn("SpawnHeight Error - Couldnt find correct layer to spawn NPC - GeoData or Spawnlist Bug!: zmin: "
								+ zmin
								+ " zmax: "
								+ zmax
								+ " value: "
								+ temph
								+ " SpawnId: "
								+ spawnid
								+ " at: "
								+ geox + " : " + geoy);
					return (short)zmin;
				}
				break;
		}
		
		if (temph > zmax + 1000 || temph < zmin - 1000)
		{
			if (_log.isDebugEnabled())
				_log.warn("SpawnHeight Error - Spawnlist z value is wrong or GeoData error: zmin: " + zmin + " zmax: "
						+ zmax + " value: " + temph + " SpawnId: " + spawnid + " at: " + geox + " : " + geoy);
			return (short)zmin;
		}
		return temph;
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param tx
	 * @param ty
	 * @param tz
	 * @return targetZ coordinate if char can move to (targetX, targetY) <br>
	 *         Integer.MIN_VALUE if char can NOT move to (targetX, targetY)
	 */
	private int nCanMoveNext(int sourceX, int sourceY, int sourceZ, int targetX, int targetY, int instanceId)
	{
		short region;
		int cellX, cellY, index, nsweIndex;
		short NSWE = NSWE_NONE;
		int targetZ = sourceZ;
		
		if (nGetNSWE(targetX, targetY, sourceZ, instanceId) == NSWE_NONE)
			return Integer.MIN_VALUE;
		
		region = getRegionOffset(sourceX, sourceY);
		nsweIndex = index = getIndex(sourceX, sourceY, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return sourceZ;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				targetZ = regionGeo.getShort(index);
				break;
			
			case COMPLEX:
				cellX = getCell(sourceX);
				cellY = getCell(sourceY);
				index += ((cellX << 3) + cellY) << 1;
				short height = regionGeo.getShort(index);
				NSWE = (short)(height & 0x0F);
				height = (short)(height & 0x0fff0);
				height = (short)(height >> 1); // height / 2
				NSWE = getInstanceNSWE(instanceId, NSWE, region, index);
				targetZ = checkNSWE(NSWE, sourceX, sourceY, targetX, targetY) ? height : Integer.MIN_VALUE;
				break;
			
			case MULTILEVEL:
				cellX = getCell(sourceX);
				cellY = getCell(sourceY);
				int offset = (cellX << 3) + cellY;
				while (offset > 0) // iterates (too many times?) to get to
				// layer count
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				height = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case3), region: " + region + " - invalid layer count: " + layers
							+ " at: " + sourceX + " " + sourceY);
					return sourceZ;
				}
				short tempz = Short.MIN_VALUE;
				while (layers > 0)
				{
					height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					
					// searches the closest layer to current z coordinate
					if ((sourceZ - tempz) * (sourceZ - tempz) > (sourceZ - height) * (sourceZ - height))
					{
						tempz = height;
						NSWE = regionGeo.getShort(index);
						NSWE = (short)(NSWE & 0x0F);
						nsweIndex = index;
					}
					layers--;
					index += 2;
				}
				NSWE = getInstanceNSWE(instanceId, NSWE, region, nsweIndex);
				targetZ = checkNSWE(NSWE, sourceX, sourceY, targetX, targetY) ? tempz : Integer.MIN_VALUE;
		}
		
		return targetZ;
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param inc_x
	 * @param inc_y
	 * @param tz
	 * @return True if Char can see target
	 */
	private boolean
			nLOS(int x, int y, int z, int inc_x, int inc_y, double inc_z, int tz, boolean debug, int instanceId)
	{
		short region = getRegionOffset(x, y);
		int cellX, cellY, index, neededIndex;
		short NSWE = NSWE_NONE;
		
		neededIndex = index = getIndex(x, y, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return true;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				short height = regionGeo.getShort(index);
				return (z > height) ? (inc_z > height) : (inc_z < height);
				
			case COMPLEX:
				cellX = getCell(x);
				cellY = getCell(y);
				index += ((cellX << 3) + cellY) << 1;
				height = regionGeo.getShort(index);
				NSWE = (short)(height & 0x0F);
				height = (short)(height & 0x0fff0);
				height = (short)(height >> 1); // height / 2
				NSWE = getInstanceNSWE(instanceId, NSWE, region, index);
				return !checkNSWE(NSWE, x, y, x + inc_x, y + inc_y)
						? z >= nGetUpperHeight(x + inc_x, y + inc_y, height) : true;
				
			case MULTILEVEL:
				cellX = getCell(x);
				cellY = getCell(y);
				
				int offset = (cellX << 3) + cellY;
				while (offset > 0) // iterates (too many times?) to get to layer count
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				short tempZ = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case4), region: " + region + " - invalid layer count: " + layers
							+ " at: " + x + " " + y);
					return false;
				}
				short upperHeight = Short.MAX_VALUE; // big positive value
				short lowerHeight = Short.MIN_VALUE; // big negative value
				byte temp_layers = layers;
				boolean highestlayer = false;
				while (temp_layers > 0) // from higher to lower
				{
					// reads tempZ for current layer, result in world z coordinate
					tempZ = regionGeo.getShort(index);
					tempZ = (short)(tempZ & 0x0fff0);
					tempZ = (short)(tempZ >> 1); // tempZ / 2
					
					if (z > tempZ)
					{
						lowerHeight = tempZ;
						NSWE = regionGeo.getShort(index);
						NSWE = (short)(NSWE & 0x0F);
						neededIndex = index;
						break;
					}
					else
					{
						highestlayer = false;
						upperHeight = tempZ;
					}
					
					temp_layers--;
					index += 2;
				}
				NSWE = getInstanceNSWE(instanceId, NSWE, region, neededIndex);
				
				// Check if LOS goes under a layer/floor
				// clearly under layer but not too much under
				// lowerheight here only for geodata bug checking, layers very close? maybe could be removed
				if ((z - upperHeight) < -10 && (z - upperHeight) > inc_z - 10 && (z - lowerHeight) > 40)
					return false;
				
				// or there's a fence/wall ahead when we're not on highest layer
				if (!highestlayer)
				{
					// a probable wall, there's movement block and layers above you
					// cannot move
					// check one inc_x inc_y further, for the height there
					return !checkNSWE(NSWE, x, y, x + inc_x, y + inc_y) ? z >= nGetUpperHeight(x + inc_x, y + inc_y,
							lowerHeight) : true;
				}
				// if NSWE fails, check one inc_x inc_y further, for the height
				// there
				return !checkNSWE(NSWE, x, y, x + inc_x, y + inc_y) ? z >= nGetUpperHeight(x + inc_x, y + inc_y,
						lowerHeight) : true;
				
			default:
				return true;
		}
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param instanceId
	 * @return NSWE: 0-15
	 */
	private short nGetNSWE(int x, int y, int z, int instanceId)
	{
		short region = getRegionOffset(x, y);
		int cellX, cellY, index, neededIndex;
		short NSWE = NSWE_NONE;
		
		neededIndex = index = getIndex(x, y, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return NSWE_ALL;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				return NSWE_ALL;
				
			case COMPLEX:
				cellX = getCell(x);
				cellY = getCell(y);
				index += ((cellX << 3) + cellY) << 1;
				NSWE = (short)(regionGeo.getShort(index) & 0x0F);
				neededIndex = index;
				break;
			
			case MULTILEVEL:
				cellX = getCell(x);
				cellY = getCell(y);
				int offset = (cellX << 3) + cellY;
				while (offset > 0)
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				short height = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case5), region: " + region + " - invalid layer count: " + layers
							+ " at: " + x + " " + y);
					return NSWE_ALL;
				}
				short tempz = Short.MIN_VALUE;
				while (layers > 0)
				{
					height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					
					if ((z - tempz) * (z - tempz) > (z - height) * (z - height))
					{
						tempz = height;
						NSWE = regionGeo.get(index);
						NSWE = (short)(NSWE & 0x0F);
						neededIndex = index;
					}
					layers--;
					index += 2;
				}
		}
		
		NSWE = getInstanceNSWE(instanceId, NSWE, region, neededIndex);
		return NSWE;
	}
	
	/**
	 * @param n
	 * @return NSWE: 0-15
	 */
	@Override
	public Node[] getNeighbors(Node n, int instanceId)
	{
		Node newNode;
		int x = n.getNodeX();
		int y = n.getNodeY();
		int parentdirection = 0;
		if (n.getParent() != null) // check for not adding parent again
		{
			if (n.getParent().getNodeX() > x)
				parentdirection = 1;
			if (n.getParent().getNodeX() < x)
				parentdirection = -1;
			if (n.getParent().getNodeY() > y)
				parentdirection = 2;
			if (n.getParent().getNodeY() < y)
				parentdirection = -2;
		}
		short z = n.getZ();
		short region = getRegionOffset(x, y);
		int cellX, cellY, neededIndex, index;
		short NSWE = 0;
		neededIndex = index = getIndex(x, y, region);
		ByteBuffer regionGeo = _geodata.get(region);
		if (regionGeo == null)
			return null;
		
		final Node[] Neighbors = new Node[4];
		int arrayIndex = 0;
		
		byte type = regionGeo.get(index++);
		switch (type)
		{
			case FLAT:
				short height = regionGeo.getShort(index);
				n.setZ(height);
				if (parentdirection != 1)
				{
					newNode = CellPathFinding.getInstance().readNode(x + 1, y, height);
					// newNode.setCost(0);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != 2)
				{
					newNode = CellPathFinding.getInstance().readNode(x, y + 1, height);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -2)
				{
					newNode = CellPathFinding.getInstance().readNode(x, y - 1, height);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -1)
				{
					newNode = CellPathFinding.getInstance().readNode(x - 1, y, height);
					Neighbors[arrayIndex++] = newNode;
				}
				break;
			
			case COMPLEX:
				cellX = getCell(x);
				cellY = getCell(y);
				index += ((cellX << 3) + cellY) << 1;
				height = regionGeo.getShort(index);
				NSWE = (short)(height & 0x0F);
				NSWE = getInstanceNSWE(instanceId, NSWE, region, index);
				height = (short)(height & 0x0fff0);
				height = (short)(height >> 1); // height / 2
				n.setZ(height);
				
				// no node with a block will be used
				if (NSWE != NSWE_ALL && parentdirection != 0)
					return null;
				
				if (parentdirection != 1 && checkNSWE(NSWE, x, y, x + 1, y))
				{
					newNode = CellPathFinding.getInstance().readNode(x + 1, y, height);
					// newNode.setCost(basecost+50);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != 2 && checkNSWE(NSWE, x, y, x, y + 1))
				{
					newNode = CellPathFinding.getInstance().readNode(x, y + 1, height);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -2 && checkNSWE(NSWE, x, y, x, y - 1))
				{
					newNode = CellPathFinding.getInstance().readNode(x, y - 1, height);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -1 && checkNSWE(NSWE, x, y, x - 1, y))
				{
					newNode = CellPathFinding.getInstance().readNode(x - 1, y, height);
					Neighbors[arrayIndex++] = newNode;
				}
				break;
			
			case MULTILEVEL:
				cellX = getCell(x);
				cellY = getCell(y);
				int offset = (cellX << 3) + cellY;
				while (offset > 0)
				{
					byte lc = regionGeo.get(index);
					index += (lc << 1) + 1;
					offset--;
				}
				byte layers = regionGeo.get(index++);
				height = -1;
				if (layers <= 0 || layers > 125)
				{
					_log.warn("Broken geofile (case5), region: " + region + " - invalid layer count: " + layers
							+ " at: " + x + " " + y);
					return null;
				}
				short tempz = Short.MIN_VALUE;
				while (layers > 0)
				{
					height = regionGeo.getShort(index);
					height = (short)(height & 0x0fff0);
					height = (short)(height >> 1); // height / 2
					
					if ((z - tempz) * (z - tempz) > (z - height) * (z - height))
					{
						tempz = height;
						NSWE = regionGeo.get(index);
						NSWE = (short)(NSWE & 0x0F);
						neededIndex = index;
					}
					layers--;
					index += 2;
				}
				n.setZ(tempz);
				NSWE = getInstanceNSWE(instanceId, NSWE, region, neededIndex);
				
				// no node with a block will be used
				if (NSWE != NSWE_ALL && parentdirection != 0)
					return null;
				
				if (parentdirection != 1 && checkNSWE(NSWE, x, y, x + 1, y))
				{
					newNode = CellPathFinding.getInstance().readNode(x + 1, y, tempz);
					// newNode.setCost(basecost+50);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != 2 && checkNSWE(NSWE, x, y, x, y + 1))
				{
					newNode = CellPathFinding.getInstance().readNode(x, y + 1, tempz);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -2 && checkNSWE(NSWE, x, y, x, y - 1))
				{
					newNode = CellPathFinding.getInstance().readNode(x, y - 1, tempz);
					Neighbors[arrayIndex++] = newNode;
				}
				if (parentdirection != -1 && checkNSWE(NSWE, x, y, x - 1, y))
				{
					newNode = CellPathFinding.getInstance().readNode(x - 1, y, tempz);
					Neighbors[arrayIndex++] = newNode;
				}
				break;
		}
		
		return L2Arrays.compact(Neighbors);
	}
	
	/**
	 * @param NSWE
	 * @param x
	 * @param y
	 * @param tx
	 * @param ty
	 * @return True if NSWE dont block given direction
	 */
	private boolean checkNSWE(short NSWE, int x, int y, int tx, int ty)
	{
		if (NSWE == NSWE_ALL)
			return true;
		
		if (tx > x)
		{
			if ((NSWE & EAST) == 0)
				return false;
		}
		else if (tx < x)
		{
			if ((NSWE & WEST) == 0)
				return false;
		}
		if (ty > y)
		{
			if ((NSWE & SOUTH) == 0)
				return false;
		}
		else if (ty < y)
		{
			if ((NSWE & NORTH) == 0)
				return false;
		}
		return true;
	}
	
	private int getIndex(int x, int y, short region)
	{
		int blockX = getBlock(x);
		int blockY = getBlock(y);
		
		final int[] tmp = _geodataIndex.get(region);
		// geodata without index - it is just empty so index can be calculated on the fly
		if (tmp == null)
			return ((blockX << 8) + blockY) * 3;
		// get index for current block of current geodata region
		else
			return tmp[(blockX << 8) + blockY];
	}
}
