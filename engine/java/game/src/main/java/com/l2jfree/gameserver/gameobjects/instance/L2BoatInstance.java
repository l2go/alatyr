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
package com.l2jfree.gameserver.gameobjects.instance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.io.IOUtils;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.knownlist.BoatKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.PlaySound;
import com.l2jfree.gameserver.network.packets.server.VehicleCheckLocation;
import com.l2jfree.gameserver.network.packets.server.VehicleDeparture;
import com.l2jfree.gameserver.network.packets.server.VehicleInfo;
import com.l2jfree.gameserver.network.packets.server.VehicleStarted;
import com.l2jfree.gameserver.taskmanager.MovementController;
import com.l2jfree.gameserver.util.Util;

/**
 * @author Maktakien
 */
public class L2BoatInstance extends L2Creature
{
	public float boatSpeed;
	
	private class L2BoatTrajet
	{
		private Map<Integer, L2BoatPoint> _path;
		
		public int idWaypoint1;
		public int idWTicket1;
		public int ntx1;
		public int nty1;
		public int ntz1;
		public int max;
		public String boatName;
		public String npc1;
		public String sysmess10_1;
		public String sysmess5_1;
		public String sysmess1_1;
		public String sysmessb_1;
		public String sysmess0_1;
		
		protected class L2BoatPoint
		{
			public int speed1;
			public int speed2;
			public int x;
			public int y;
			public int z;
			public int time;
		}
		
		/**
		 * @param pIdWaypoint1
		 * @param pIdWTicket1
		 * @param pNtx1
		 * @param pNty1
		 * @param pNtz1
		 * @param pNpc1
		 * @param pSysmess10_1
		 * @param pSysmess5_1
		 * @param pSysmess1_1
		 * @param pSysmess0_1
		 * @param pSysmessb_1
		 * @param pBoatname
		 */
		public L2BoatTrajet(int pIdWaypoint1, int pIdWTicket1, int pNtx1, int pNty1, int pNtz1, String pNpc1,
				String pSysmess10_1, String pSysmess5_1, String pSysmess1_1, String pSysmess0_1, String pSysmessb_1,
				String pBoatname)
		{
			idWaypoint1 = pIdWaypoint1;
			idWTicket1 = pIdWTicket1;
			ntx1 = pNtx1;
			nty1 = pNty1;
			ntz1 = pNtz1;
			npc1 = pNpc1;
			sysmess10_1 = pSysmess10_1;
			sysmess5_1 = pSysmess5_1;
			sysmess1_1 = pSysmess1_1;
			sysmessb_1 = pSysmessb_1;
			sysmess0_1 = pSysmess0_1;
			boatName = pBoatname;
			loadBoatPath();
		}
		
		/**
		 * @param line
		 * @return
		 */
		public void parseLine(String line)
		{
			// L2BoatPath bp = new L2BoatPath();
			_path = new LinkedHashMap<Integer, L2BoatPoint>();
			StringTokenizer st = new StringTokenizer(line, ";");
			Integer.parseInt(st.nextToken());
			max = Integer.parseInt(st.nextToken());
			for (int i = 0; i < max; i++)
			{
				L2BoatPoint bp = new L2BoatPoint();
				bp.speed1 = Integer.parseInt(st.nextToken());
				bp.speed2 = Integer.parseInt(st.nextToken());
				bp.x = Integer.parseInt(st.nextToken());
				bp.y = Integer.parseInt(st.nextToken());
				bp.z = Integer.parseInt(st.nextToken());
				bp.time = Integer.parseInt(st.nextToken());
				_path.put(i, bp);
			}
		}
		
		/**
		 *
		 */
		private void loadBoatPath()
		{
			LineNumberReader lnr = null;
			try
			{
				File doorData = new File(Config.DATAPACK_ROOT, "data/boatpath.csv");
				lnr = new LineNumberReader(new BufferedReader(new FileReader(doorData)));
				
				String line = null;
				while ((line = lnr.readLine()) != null)
				{
					if (line.trim().length() == 0 || !line.startsWith(idWaypoint1 + ";"))
						continue;
					parseLine(line);
					return;
				}
				_log.warn("No path for boat " + boatName + " !!!");
			}
			catch (FileNotFoundException e)
			{
				_log.warn("boatpath.csv is missing in data folder", e);
			}
			catch (Exception e)
			{
				_log.warn("error while creating boat table ", e);
			}
			finally
			{
				IOUtils.closeQuietly(lnr);
			}
		}
		
		/**
		 * @param state
		 * @return
		 */
		public int state(int state, L2BoatInstance _boat)
		{
			if (state < max)
			{
				L2BoatPoint bp = _path.get(state);
				double dx = (_boat.getX() - bp.x);
				double dy = (_boat.getY() - bp.y);
				double distance = Math.sqrt(dx * dx + dy * dy);
				double cos;
				double sin;
				sin = dy / distance;
				cos = dx / distance;
				
				_boat.getPosition().setHeading(Util.calculateHeadingFrom(cos, sin));
				
				_boat._vd = new VehicleDeparture(_boat, bp.speed1, bp.speed2, bp.x, bp.y, bp.z);
				// _boat.getTemplate().baseRunSpd = bp.speed1;
				boatSpeed = bp.speed1;
				_boat.moveToLocation(bp.x, bp.y, bp.z, (float)bp.speed1);
				if (bp.time == 0)
					bp.time = 1;
				
				Collection<L2Player> knownPlayers = _boat.getKnownList().getKnownPlayers().values();
				if (knownPlayers != null && !knownPlayers.isEmpty())
				{
					for (L2Player player : knownPlayers)
					{
						player.sendPacket(_boat._vd);
					}
				}
				return bp.time;
			}
			
			return 0;
		}
	}
	
	private final String _name;
	protected L2BoatTrajet _t1;
	protected L2BoatTrajet _t2;
	protected int _cycle = 0;
	protected VehicleDeparture _vd = null;
	private Map<Integer, L2Player> _inboat;
	private boolean _inCycle = true;
	
	public int getSizeInside()
	{
		return _inboat == null ? 0 : _inboat.size();
	}
	
	public String getBoatName()
	{
		return _name;
	}
	
	public int getCycle()
	{
		return _cycle;
	}
	
	public boolean isInCycle()
	{
		return _inCycle;
	}
	
	public void stopCycle()
	{
		_inCycle = false;
		stopMove(new L2CharPosition(getX(), getY(), getZ(), getPosition().getHeading()));
	}
	
	public void startCycle()
	{
		_inCycle = true;
		_cycle = 1;
		beginCycle();
	}
	
	public void reloadPath()
	{
		_t1.loadBoatPath();
		_t2.loadBoatPath();
		_cycle = 0;
		stopCycle();
		startCycle();
	}
	
	public L2BoatInstance(int objectId, L2CreatureTemplate template, String name)
	{
		super(objectId, template);
		getKnownList();
		getAI();
		_name = name;
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new BoatKnownList(this);
	}
	
	@Override
	public BoatKnownList getKnownList()
	{
		return (BoatKnownList)_knownList;
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 */
	public void moveToLocation(int x, int y, int z, float speed)
	{
		final int curX = getX();
		final int curY = getY();
		
		// Calculate distance (dx,dy) between current position and destination
		final int dx = (x - curX);
		final int dy = (y - curY);
		double distance = Math.sqrt(dx * dx + dy * dy);
		
		if (_log.isDebugEnabled())
			_log.debug("distance to target:" + distance);
		
		// Define movement angles needed
		// ^
		// | X (x,y)
		// | /
		// | /distance
		// | /
		// |/ angle
		// X ---------->
		// (curx,cury)
		
		double cos;
		double sin;
		sin = dy / distance;
		cos = dx / distance;
		// Create and Init a MoveData object
		MoveData m = new MoveData();
		
		// Calculate the Nb of ticks between the current position and the destination
		int ticksToMove = (int)(GameTimeManager.TICKS_PER_SECOND * distance / speed);
		
		// Calculate and set the heading of the L2Creature
		int heading = (int)(Math.atan2(-sin, -cos) * 10430.378350470452724949566316381);
		heading += 32768;
		getPosition().setHeading(heading);
		
		if (_log.isDebugEnabled())
			_log.debug("dist:" + distance + "speed:" + speed + " ttt:" + ticksToMove + " heading:" + heading);
		
		m._xDestination = x;
		m._yDestination = y;
		m._zDestination = z; // this is what was requested from client
		m._heading = 0; // initial value for coordinate sync
		m.onGeodataPathIndex = -1; // Initialize not on geodata path
		m._moveStartTime = GameTimeManager.getGameTicks();
		
		if (_log.isDebugEnabled())
			_log.debug("time to target:" + ticksToMove);
		
		// Set the L2Creature _move object to MoveData object
		_move = m;
		
		MovementController.getInstance().add(this, ticksToMove);
	}
	
	private class BoatCaptain implements Runnable
	{
		private final int _state;
		private final L2BoatInstance _boat;
		
		/**
		 * @param i
		 * @param instance
		 */
		public BoatCaptain(int i, L2BoatInstance instance)
		{
			_state = i;
			_boat = instance;
		}
		
		@Override
		public void run()
		{
			if (!_inCycle)
				return;
			if (_boat.getId() == 5) // Rune <-> Primeval Isle
			{
				switch (_state)
				{
					case 1:
						_boat.say(-1);
						_boat.begin();
						break;
				}
			}
			else
			{
				BoatCaptain bc;
				switch (_state)
				{
					case 1:
						_boat.say(5);
						bc = new BoatCaptain(2, _boat);
						ThreadPoolManager.getInstance().scheduleGeneral(bc, 240000);
						break;
					case 2:
						_boat.say(1);
						bc = new BoatCaptain(3, _boat);
						ThreadPoolManager.getInstance().scheduleGeneral(bc, 40000);
						break;
					case 3:
						_boat.say(0);
						bc = new BoatCaptain(4, _boat);
						ThreadPoolManager.getInstance().scheduleGeneral(bc, 20000);
						break;
					case 4:
						_boat.say(-1);
						_boat.begin();
						break;
				}
			}
		}
	}
	
	private class Boatrun implements Runnable
	{
		private int _state;
		private final L2BoatInstance _boat;
		
		/**
		 * @param i
		 * @param instance
		 */
		public Boatrun(int i, L2BoatInstance instance)
		{
			_state = i;
			_boat = instance;
		}
		
		@Override
		public void run()
		{
			if (!_inCycle)
				return;
			_boat._vd = null;
			_boat.needOnVehicleCheckLocation = false;
			if (_boat._cycle == 1)
			{
				int time = _boat._t1.state(_state, _boat);
				if (time > 0)
				{
					_state++;
					Boatrun bc = new Boatrun(_state, _boat);
					ThreadPoolManager.getInstance().scheduleGeneral(bc, time);
					
				}
				else if (time == 0)
				{
					_boat._cycle = 2;
					_boat.say(10);
					BoatCaptain bc = new BoatCaptain(1, _boat);
					ThreadPoolManager.getInstance().scheduleGeneral(bc, 300000);
				}
				else
				{
					_boat.needOnVehicleCheckLocation = true;
					_state++;
					_boat._runstate = _state;
				}
			}
			else if (_boat._cycle == 2)
			{
				int time = _boat._t2.state(_state, _boat);
				if (time > 0)
				{
					_state++;
					Boatrun bc = new Boatrun(_state, _boat);
					ThreadPoolManager.getInstance().scheduleGeneral(bc, time);
				}
				else if (time == 0)
				{
					_boat._cycle = 1;
					_boat.say(10);
					BoatCaptain bc = new BoatCaptain(1, _boat);
					ThreadPoolManager.getInstance().scheduleGeneral(bc, 300000);
				}
				else
				{
					_boat.needOnVehicleCheckLocation = true;
					_state++;
					_boat._runstate = _state;
				}
			}
		}
	}
	
	public int _runstate = 0;
	
	/**
	 *
	 */
	public void evtArrived()
	{
		
		if (_runstate != 0)
		{
			// _runstate++;
			Boatrun bc = new Boatrun(_runstate, this);
			ThreadPoolManager.getInstance().scheduleGeneral(bc, 10);
			_runstate = 0;
		}
	}
	
	/**
	 * @return
	 */
	public int getId()
	{
		return _id;
	}
	
	/**
	 * @param activeChar
	 */
	public void sendVehicleDeparture(L2Player activeChar)
	{
		if (_vd != null)
		{
			activeChar.sendPacket(_vd);
		}
	}
	
	public VehicleDeparture getVehicleDeparture()
	{
		return _vd;
	}
	
	public void beginCycle()
	{
		_inCycle = true;
		say(10);
		BoatCaptain bc = new BoatCaptain(1, this);
		if (getId() == 5)
			ThreadPoolManager.getInstance().scheduleGeneral(bc, 180000);
		else
			ThreadPoolManager.getInstance().scheduleGeneral(bc, 300000);
	}
	
	/**
	 * @param destination
	 * @param destination2
	 * @param destination3
	 */
	private int lastx = -1;
	private int lasty = -1;
	protected boolean needOnVehicleCheckLocation = false;
	private int _id;
	
	public void updatePeopleInTheBoat(int x, int y, int z)
	{
		if (_inboat != null)
		{
			boolean check = false;
			if ((lastx == -1) || (lasty == -1))
			{
				check = true;
				lastx = x;
				lasty = y;
			}
			else if ((x - lastx) * (x - lastx) + (y - lasty) * (y - lasty) > 2250000) // 1500 sq
			{
				check = true;
				lastx = x;
				lasty = y;
			}
			for (int i = 0; i < _inboat.size(); i++)
			{
				L2Player player = _inboat.get(i);
				if (player != null && player.isInBoat() && player.getBoat() == this)
				{
					// player.getKnownList().addKnownObject(this);
					player.getPosition().setXYZ(x, y, z);
					player.revalidateZone(false);
				}
				if (check)
				{
					if (needOnVehicleCheckLocation && player != null)
					{
						VehicleCheckLocation vcl = new VehicleCheckLocation(this, x, y, z);
						player.sendPacket(vcl);
					}
				}
			}
		}
		
	}
	
	public void begin()
	{
		if (!_inCycle)
			return;
		if (_cycle == 1)
		{
			Collection<L2Player> knownPlayers = getKnownList().getKnownPlayers().values();
			if (knownPlayers != null && !knownPlayers.isEmpty())
			{
				_inboat = new LinkedHashMap<Integer, L2Player>();
				int i = 0;
				for (L2Player player : knownPlayers)
				{
					if (player.isInBoat() && player.getBoat() == this)
					{
						L2ItemInstance it;
						it = player.getInventory().getItemByItemId(_t1.idWTicket1);
						if (it != null)
						{
							player.getInventory().destroyItem("Boat", it.getObjectId(), 1, player, this);
							InventoryUpdate iu = new InventoryUpdate();
							iu.addItem(it);
							player.sendPacket(iu);
							_inboat.put(i, player);
							i++;
						}
						else if (_t1.idWTicket1 == 0)
						{
							_inboat.put(i, player);
							i++;
						}
						else
						{
							player.teleToLocation(_t1.ntx1, _t1.nty1, _t1.ntz1, false);
						}
					}
				}
			}
			Boatrun bc = new Boatrun(0, this);
			ThreadPoolManager.getInstance().scheduleGeneral(bc, 0);
		}
		else if (_cycle == 2)
		{
			Collection<L2Player> knownPlayers = getKnownList().getKnownPlayers().values();
			if (knownPlayers != null && !knownPlayers.isEmpty())
			{
				_inboat = new LinkedHashMap<Integer, L2Player>();
				int i = 0;
				for (L2Player player : knownPlayers)
				{
					if (player.isInBoat())
					{
						L2ItemInstance it;
						it = player.getInventory().getItemByItemId(_t2.idWTicket1);
						if (it != null)
						{
							player.getInventory().destroyItem("Boat", it.getObjectId(), 1, player, this);
							InventoryUpdate iu = new InventoryUpdate();
							iu.addItem(it);
							player.sendPacket(iu);
							_inboat.put(i, player);
							i++;
						}
						else if (_t2.idWTicket1 == 0)
						{
							_inboat.put(i, player);
							i++;
						}
						else
						{
							player.teleToLocation(_t2.ntx1, _t2.nty1, _t2.ntz1, false);
						}
					}
				}
				
			}
			Boatrun bc = new Boatrun(0, this);
			ThreadPoolManager.getInstance().scheduleGeneral(bc, 0);
		}
	}
	
	/**
	 * @param i
	 */
	public void say(int i)
	{
		Collection<L2Player> knownPlayers = getKnownList().getKnownPlayers().values();
		CreatureSay sm;
		CreatureSay sm2 = null;
		PlaySound ps;
		switch (i)
		{
			case 10:
				if (_cycle == 1)
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmess10_1);
					if (getId() == 5)
						sm2 = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmess5_1);
				}
				else
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmess10_1);
					if (getId() == 5)
						sm2 = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmess5_1);
				}
				ps = new PlaySound(0, this, 0, "itemsound.ship_arrival_departure");
				if (knownPlayers == null || knownPlayers.isEmpty())
					return;
				for (L2Player player : knownPlayers)
				{
					player.sendPacket(sm);
					player.sendPacket(ps);
					player.sendPacket(new VehicleStarted(this, 0));
					if (sm2 != null)
						player.sendPacket(sm2);
				}
				break;
			case 5:
				if (_cycle == 1)
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmess5_1);
				}
				else
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmess5_1);
				}
				ps = new PlaySound(0, this, 0, "itemsound.ship_5min");
				if (knownPlayers == null || knownPlayers.isEmpty())
					return;
				for (L2Player player : knownPlayers)
				{
					player.sendPacket(sm);
					player.sendPacket(ps);
				}
				break;
			case 1:
				if (_cycle == 1)
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmess1_1);
				}
				else
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmess1_1);
				}
				ps = new PlaySound(0, this, 0, "itemsound.ship_1min");
				if (knownPlayers == null || knownPlayers.isEmpty())
					return;
				for (L2Player player : knownPlayers)
				{
					player.sendPacket(sm);
					player.sendPacket(ps);
				}
				break;
			case 0:
				if (_cycle == 1)
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmess0_1);
				}
				else
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmess0_1);
				}
				if (knownPlayers == null || knownPlayers.isEmpty())
					return;
				for (L2Player player : knownPlayers)
				{
					player.sendPacket(sm);
					// player.sendPacket(ps);
				}
				break;
			case -1:
				if (_cycle == 1)
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t1.npc1, _t1.sysmessb_1);
				}
				else
				{
					sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, _t2.npc1, _t2.sysmessb_1);
				}
				ps = new PlaySound(0, this, 0, "itemsound.ship_arrival_departure");
				for (L2Player player : knownPlayers)
				{
					player.sendPacket(sm);
					player.sendPacket(ps);
				}
				break;
		}
	}
	
	/**
	 *
	 */
	public void spawn()
	{
		Collection<L2Player> knownPlayers = getKnownList().getKnownPlayers().values();
		_cycle = 1;
		beginCycle();
		if (knownPlayers == null || knownPlayers.isEmpty())
			return;
		VehicleInfo vi = new VehicleInfo(this);
		for (L2Player player : knownPlayers)
			player.sendPacket(vi);
	}
	
	/**
	 * @param idWaypoint1
	 * @param idWTicket1
	 * @param ntx1
	 * @param nty1
	 * @param ntz1
	 * @param idnpc1
	 * @param sysmess10_1
	 * @param sysmess5_1
	 * @param sysmess1_1
	 * @param sysmessb_1
	 */
	public void setTrajet1(int idWaypoint1, int idWTicket1, int ntx1, int nty1, int ntz1, String idnpc1,
			String sysmess10_1, String sysmess5_1, String sysmess1_1, String sysmess0_1, String sysmessb_1)
	{
		_t1 =
				new L2BoatTrajet(idWaypoint1, idWTicket1, ntx1, nty1, ntz1, idnpc1, sysmess10_1, sysmess5_1,
						sysmess1_1, sysmess0_1, sysmessb_1, _name);
	}
	
	public void setTrajet2(int idWaypoint1, int idWTicket1, int ntx1, int nty1, int ntz1, String idnpc1,
			String sysmess10_1, String sysmess5_1, String sysmess1_1, String sysmess0_1, String sysmessb_1)
	{
		_t2 =
				new L2BoatTrajet(idWaypoint1, idWTicket1, ntx1, nty1, ntz1, idnpc1, sysmess10_1, sysmess5_1,
						sysmess1_1, sysmess0_1, sysmessb_1, _name);
	}
	
	/*
	 * (non-Javadoc)
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#getLevel()
	 */
	@Override
	public int getLevel()
	{
		// TODO Auto-generated method stub
		return 0;
	}
	
	/*
	 * Allow setup of the boat AI only once
	 */
	@Override
	protected boolean canReplaceAI()
	{
		return false;
	}
	
	@Override
	protected L2CreatureAI initAI()
	{
		return new L2CreatureAI(new AIAccessor());
	}
	
	/*
	 * boat AI can't be detached
	 */
	public class AIAccessor extends L2Creature.AIAccessor
	{
		/*@Override
		public void detachAI()
		{
		}*/
	}
	
	/**
	 * @param id
	 */
	public void setId(int id)
	{
		_id = id;
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		if (!activeChar.isInBoat())
		{
			if (this != activeChar.getBoat())
			{
				activeChar.sendPacket(new VehicleInfo(this));
				sendVehicleDeparture(activeChar);
			}
		}
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
	}
}
