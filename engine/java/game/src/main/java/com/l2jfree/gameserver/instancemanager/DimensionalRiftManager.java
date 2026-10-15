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

import java.awt.Polygon;
import java.awt.Shape;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.entity.DimensionalRift;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.tools.random.Rnd;

/**
* Thanks to L2Fortress and balancer.ru - kombat
*/
public class DimensionalRiftManager
{
	protected static Logger _log = LoggerFactory.getLogger(DimensionalRiftManager.class);
	
	private final Map<Byte, Map<Byte, DimensionalRiftRoom>> _rooms =
			new LinkedHashMap<Byte, Map<Byte, DimensionalRiftRoom>>();
	private final static int DIMENSIONAL_FRAGMENT_ITEM_ID = 7079;
	private final static int MAX_PARTY_PER_AREA = 3;
	
	public static DimensionalRiftManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private DimensionalRiftManager()
	{
		new Quest(635, "RiftQuest", "Dummy Quest shown in players' questlist when inside the rift");
		load();
	}
	
	public DimensionalRiftRoom getRoom(byte type, byte room)
	{
		return _rooms.get(type) == null ? null : _rooms.get(type).get(room);
	}
	
	public boolean isAreaAvailable(byte area)
	{
		Map<Byte, DimensionalRiftRoom> tmap = _rooms.get(area);
		if (tmap == null)
			return false;
		int used = 0;
		for (DimensionalRiftRoom room : tmap.values())
		{
			if (room.isUsed())
				used++;
		}
		return used <= MAX_PARTY_PER_AREA;
	}
	
	public boolean isRoomAvailable(byte area, byte room)
	{
		if (_rooms.get(area) == null || _rooms.get(area).get(room) == null)
			return false;
		return !_rooms.get(area).get(room).isUsed();
	}
	
	public void load()
	{
		File riftFile = new File(Config.DATAPACK_ROOT, "data/dimensionalRift.xml");
		if (WorldFiles.skip(_log, "dimensional rifts", riftFile))
			return;
		
		int countGood = 0, countBad = 0;
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(true);
			factory.setIgnoringComments(true);
			
			File file = new File(Config.DATAPACK_ROOT, "data/dimensionalRift.xml");
			if (!file.exists())
				throw new IOException();
			
			Document doc = factory.newDocumentBuilder().parse(file);
			NamedNodeMap attrs;
			byte type, roomId;
			int mobId, x, y, z, delay, count;
			L2Spawn spawnDat;
			L2NpcTemplate template;
			int xMin = 0, xMax = 0, yMin = 0, yMax = 0, zMin = 0, zMax = 0, xT = 0, yT = 0, zT = 0;
			boolean isBossRoom;
			
			for (Node rift = doc.getFirstChild(); rift != null; rift = rift.getNextSibling())
			{
				if ("rift".equalsIgnoreCase(rift.getNodeName()))
				{
					for (Node area = rift.getFirstChild(); area != null; area = area.getNextSibling())
					{
						if ("area".equalsIgnoreCase(area.getNodeName()))
						{
							attrs = area.getAttributes();
							type = Byte.parseByte(attrs.getNamedItem("type").getNodeValue());
							
							for (Node room = area.getFirstChild(); room != null; room = room.getNextSibling())
							{
								if ("room".equalsIgnoreCase(room.getNodeName()))
								{
									attrs = room.getAttributes();
									roomId = Byte.parseByte(attrs.getNamedItem("id").getNodeValue());
									Node boss = attrs.getNamedItem("isBossRoom");
									isBossRoom = boss != null && Boolean.parseBoolean(boss.getNodeValue());
									
									for (Node coord = room.getFirstChild(); coord != null; coord =
											coord.getNextSibling())
									{
										if ("teleport".equalsIgnoreCase(coord.getNodeName()))
										{
											attrs = coord.getAttributes();
											xT = Integer.parseInt(attrs.getNamedItem("x").getNodeValue());
											yT = Integer.parseInt(attrs.getNamedItem("y").getNodeValue());
											zT = Integer.parseInt(attrs.getNamedItem("z").getNodeValue());
										}
										else if ("zone".equalsIgnoreCase(coord.getNodeName()))
										{
											attrs = coord.getAttributes();
											xMin = Integer.parseInt(attrs.getNamedItem("xMin").getNodeValue());
											xMax = Integer.parseInt(attrs.getNamedItem("xMax").getNodeValue());
											yMin = Integer.parseInt(attrs.getNamedItem("yMin").getNodeValue());
											yMax = Integer.parseInt(attrs.getNamedItem("yMax").getNodeValue());
											zMin = Integer.parseInt(attrs.getNamedItem("zMin").getNodeValue());
											zMax = Integer.parseInt(attrs.getNamedItem("zMax").getNodeValue());
										}
									}
									
									if (!_rooms.containsKey(type))
										_rooms.put(type, new LinkedHashMap<Byte, DimensionalRiftRoom>());
									
									_rooms.get(type).put(
											roomId,
											new DimensionalRiftRoom(type, roomId, xMin, xMax, yMin, yMax, zMin, zMax,
													xT, yT, zT, isBossRoom));
									
									for (Node spawn = room.getFirstChild(); spawn != null; spawn =
											spawn.getNextSibling())
									{
										if ("spawn".equalsIgnoreCase(spawn.getNodeName()))
										{
											attrs = spawn.getAttributes();
											mobId = Integer.parseInt(attrs.getNamedItem("mobId").getNodeValue());
											delay = Integer.parseInt(attrs.getNamedItem("delay").getNodeValue());
											count = Integer.parseInt(attrs.getNamedItem("count").getNodeValue());
											
											template = NpcTable.getInstance().getTemplate(mobId);
											if (template == null)
											{
												_log.warn("Template " + mobId + " not found!");
											}
											if (!_rooms.containsKey(type))
											{
												_log.warn("Type " + type + " not found!");
											}
											else if (!_rooms.get(type).containsKey(roomId))
											{
												_log.warn("Room " + roomId + " in Type " + type + " not found!");
											}
											
											for (int i = 0; i < count; i++)
											{
												DimensionalRiftRoom riftRoom = _rooms.get(type).get(roomId);
												x = riftRoom.getRandomX();
												y = riftRoom.getRandomY();
												z = riftRoom.getTeleportCoords()[2];
												
												if (template != null && _rooms.containsKey(type)
														&& _rooms.get(type).containsKey(roomId))
												{
													spawnDat = new L2Spawn(template);
													spawnDat.setAmount(1);
													spawnDat.setLocx(x);
													spawnDat.setLocy(y);
													spawnDat.setLocz(z);
													spawnDat.setHeading(-1);
													spawnDat.setRespawnDelay(delay);
													SpawnTable.getInstance().addNewSpawn(spawnDat, false);
													_rooms.get(type).get(roomId).getSpawns().add(spawnDat);
													countGood++;
												}
												else
												{
													countBad++;
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.warn("Error on loading dimensional rift spawns: ", e);
		}
		int typeSize = _rooms.keySet().size();
		int roomSize = 0;
		
		for (Byte b : _rooms.keySet())
			roomSize += _rooms.get(b).keySet().size();
		
		_log.info("DimensionalRiftManager: Loaded " + typeSize + " room types with " + roomSize + " rooms.");
		_log.info("DimensionalRiftManager: Loaded " + countGood + " dimensional rift spawns, " + countBad + " errors.");
	}
	
	public void reload()
	{
		for (Byte b : _rooms.keySet())
		{
			for (int i : _rooms.get(b).keySet())
			{
				_rooms.get(b).get(i).getSpawns().clear();
			}
			_rooms.get(b).clear();
		}
		_rooms.clear();
		load();
	}
	
	public boolean checkIfInRiftZone(int x, int y, int z, boolean excludePeaceZone)
	{
		DimensionalRiftRoom area = room((byte)0, (byte)1);
		if (area == null)
			return false;
		if (!excludePeaceZone)
			return area.checkIfInZone(x, y, z);
		
		DimensionalRiftRoom peace = room((byte)0, (byte)0);
		if (peace == null)
			return false;
		return area.checkIfInZone(x, y, z) && !peace.checkIfInZone(x, y, z);
	}
	
	public boolean checkIfInPeaceZone(int x, int y, int z)
	{
		DimensionalRiftRoom peace = room((byte)0, (byte)0);
		return peace != null && peace.checkIfInZone(x, y, z);
	}
	
	public void teleportToWaitingRoom(L2Player player)
	{
		DimensionalRiftRoom peace = getRoom((byte)0, (byte)0);
		if (peace == null)
			return;
		int[] coords = peace.getTeleportCoords();
		player.teleToLocation(coords[0], coords[1], coords[2]);
	}
	
	public Location getWaitingRoomTeleport()
	{
		DimensionalRiftRoom peace = getRoom((byte)0, (byte)0);
		if (peace == null)
			return null;
		int[] coords = peace.getTeleportCoords();
		return new Location(coords[0], coords[1], coords[2]);
	}
	
	/** Room of a type, or null when this world has no such rift room. */
	private DimensionalRiftRoom room(byte type, byte id)
	{
		Map<Byte, DimensionalRiftRoom> area = _rooms.get(type);
		return area == null ? null : area.get(id);
	}
	
	public void start(L2Player player, byte type, L2Npc npc)
	{
		boolean canPass = true;
		if (!player.isInParty())
		{
			showHtmlFile(player, "data/html/seven_signs/rift/NoParty.htm", npc);
			return;
		}
		
		if (player.getParty().getPartyLeaderOID() != player.getObjectId())
		{
			showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
			return;
		}
		
		if (player.getParty().isInDimensionalRift())
		{
			handleCheat(player, npc);
			return;
		}
		
		if (player.getParty().getMemberCount() < Config.ALT_RIFT_MIN_PARTY_SIZE)
		{
			NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
			html.setFile("data/html/seven_signs/rift/SmallParty.htm");
			html.replace("%npc_name%", npc.getName());
			html.replace("%count%", Integer.valueOf(Config.ALT_RIFT_MIN_PARTY_SIZE).toString());
			player.sendPacket(html);
			return;
		}
		
		for (L2Player p : player.getParty().getPartyMembers())
			if (!checkIfInPeaceZone(p.getX(), p.getY(), p.getZ()))
				canPass = false;
		
		if (!canPass)
		{
			showHtmlFile(player, "data/html/seven_signs/rift/NotInWaitingRoom.htm", npc);
			return;
		}
		
		L2ItemInstance i;
		for (L2Player p : player.getParty().getPartyMembers())
		{
			i = p.getInventory().getItemByItemId(DIMENSIONAL_FRAGMENT_ITEM_ID);
			
			if (i == null)
			{
				canPass = false;
				break;
			}
			
			if (i.getCount() > 0)
				if (i.getCount() < getNeededItems(type))
					canPass = false;
		}
		
		if (!canPass)
		{
			NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
			html.setFile("data/html/seven_signs/rift/NoFragments.htm");
			html.replace("%npc_name%", npc.getName());
			html.replace("%count%", Integer.toString(getNeededItems(type)));
			player.sendPacket(html);
			return;
		}
		
		for (L2Player p : player.getParty().getPartyMembers())
		{
			i = p.getInventory().getItemByItemId(DIMENSIONAL_FRAGMENT_ITEM_ID);
			p.destroyItem("RiftEntrance", i.getObjectId(), getNeededItems(type), null, false);
		}
		
		new DimensionalRift(player.getParty(), type, (byte)Rnd.get(1, 9));
	}
	
	public void killRift(DimensionalRift d)
	{
		if (d.getTeleportTimerTask() != null)
			d.getTeleportTimerTask().cancel();
		d.setTeleportTimerTask(null);
		
		if (d.getTeleportTimer() != null)
			d.getTeleportTimer().cancel();
		d.setTeleportTimer(null);
		
		if (d.getSpawnTimerTask() != null)
			d.getSpawnTimerTask().cancel();
		d.setSpawnTimerTask(null);
		
		if (d.getSpawnTimer() != null)
			d.getSpawnTimer().cancel();
		d.setSpawnTimer(null);
	}
	
	public class DimensionalRiftRoom
	{
		protected final byte _type;
		protected final byte _room;
		private final int _xMin;
		private final int _xMax;
		private final int _yMin;
		private final int _yMax;
		private final int _zMin;
		private final int _zMax;
		private final int[] _teleportCoords;
		private final Shape _s;
		private final boolean _isBossRoom;
		private final List<L2Spawn> _roomSpawns;
		protected final List<L2Npc> _roomMobs;
		private boolean _isUsed = false;
		
		public DimensionalRiftRoom(byte type, byte room, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax,
				int xT, int yT, int zT, boolean isBossRoom)
		{
			_type = type;
			_room = room;
			_xMin = (xMin + 128);
			_xMax = (xMax - 128);
			_yMin = (yMin + 128);
			_yMax = (yMax - 128);
			_zMin = zMin;
			_zMax = zMax;
			_teleportCoords = new int[] { xT, yT, zT };
			_isBossRoom = isBossRoom;
			_roomSpawns = new ArrayList<L2Spawn>();
			_roomMobs = new ArrayList<L2Npc>();
			_s = new Polygon(new int[] { xMin, xMax, xMax, xMin }, new int[] { yMin, yMin, yMax, yMax }, 4);
		}
		
		public int getRandomX()
		{
			return Rnd.get(_xMin, _xMax);
		}
		
		public int getRandomY()
		{
			return Rnd.get(_yMin, _yMax);
		}
		
		public int[] getTeleportCoords()
		{
			return _teleportCoords;
		}
		
		public boolean checkIfInZone(L2Object obj)
		{
			return checkIfInZone(obj.getX(), obj.getY(), obj.getZ());
		}
		
		public boolean checkIfInZone(int x, int y, int z)
		{
			return _s.contains(x, y) && z >= _zMin && z <= _zMax;
		}
		
		public boolean isBossRoom()
		{
			return _isBossRoom;
		}
		
		public List<L2Spawn> getSpawns()
		{
			return _roomSpawns;
		}
		
		public void spawn()
		{
			for (L2Spawn spawn : _roomSpawns)
			{
				spawn.doSpawn();
				spawn.startRespawn();
			}
		}
		
		public void unspawn()
		{
			for (L2Spawn spawn : _roomSpawns)
			{
				spawn.stopRespawn();
				if (spawn.getLastSpawn() != null)
					spawn.getLastSpawn().deleteMe();
			}
			_isUsed = false;
		}
		
		public void setUsed()
		{
			_isUsed = true;
		}
		
		public boolean isUsed()
		{
			return _isUsed;
		}
	}
	
	private int getNeededItems(byte type)
	{
		switch (type)
		{
			case 1:
				return Config.ALT_RIFT_ENTER_COST_RECRUIT;
			case 2:
				return Config.ALT_RIFT_ENTER_COST_SOLDIER;
			case 3:
				return Config.ALT_RIFT_ENTER_COST_OFFICER;
			case 4:
				return Config.ALT_RIFT_ENTER_COST_CAPTAIN;
			case 5:
				return Config.ALT_RIFT_ENTER_COST_COMMANDER;
			case 6:
				return Config.ALT_RIFT_ENTER_COST_HERO;
			default:
				return 999999;
		}
	}
	
	public void showHtmlFile(L2Player player, String file, L2Npc npc)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(file);
		html.replace("%npc_name%", npc.getName());
		player.sendPacket(html);
	}
	
	public void handleCheat(L2Player player, L2Npc npc)
	{
		showHtmlFile(player, "data/html/seven_signs/rift/Cheater.htm", npc);
		if (!player.isGM())
		{
			_log.warn("Player " + player.getName() + "(" + player.getObjectId()
					+ ") was cheating in dimension rift area!");
			Util.handleIllegalPlayerAction(player, "Warning! Character " + player.getName()
					+ " tried to cheat in dimensional rift.", Config.DEFAULT_PUNISH);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final DimensionalRiftManager _instance = new DimensionalRiftManager();
	}
}
