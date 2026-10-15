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
import java.util.Timer;
import java.util.TimerTask;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager.DimensionalRiftRoom;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.tools.random.Rnd;

/**
* Thanks to L2Fortress and balancer.ru - kombat
*/
public class DimensionalRift
{
	protected byte _roomType;
	protected L2Party _party;
	protected List<Byte> _completedRooms = new ArrayList<Byte>();
	private static final long seconds_5 = 5000L;
	//private static final int MILLISECONDS_IN_MINUTE = 60000;
	protected byte jumps_current = 0;
	
	private Timer teleporterTimer;
	private TimerTask teleporterTimerTask;
	private Timer spawnTimer;
	private TimerTask spawnTimerTask;
	
	protected byte _choosenRoom = -1;
	private boolean _hasJumped = false;
	protected List<L2Player> deadPlayers = new ArrayList<L2Player>();
	protected List<L2Player> revivedInWaitingRoom = new ArrayList<L2Player>();
	private boolean isBossRoom = false;
	
	public DimensionalRift(L2Party party, byte roomType, byte roomId)
	{
		_roomType = roomType;
		_party = party;
		_choosenRoom = roomId;
		int[] coords = getRoomCoord(roomId);
		party.setDimensionalRift(this);
		
		Quest riftQuest = QuestManager.getInstance().getQuest(635);
		for (L2Player p : party.getPartyMembers())
		{
			if (riftQuest != null)
			{
				QuestState qs = p.getQuestState(riftQuest.getName());
				if (qs == null)
					qs = riftQuest.newQuestState(p);
				if (qs.getInt("cond") != 1)
					qs.set("cond", "1");
			}
			p.teleToLocation(coords[0], coords[1], coords[2]);
		}
		createSpawnTimer(_choosenRoom);
		createTeleporterTimer(true);
	}
	
	public byte getType()
	{
		return _roomType;
	}
	
	public byte getCurrentRoom()
	{
		return _choosenRoom;
	}
	
	protected void createTeleporterTimer(final boolean reasonTP)
	{
		if (teleporterTimerTask != null)
		{
			teleporterTimerTask.cancel();
			teleporterTimerTask = null;
		}
		
		if (teleporterTimer != null)
		{
			teleporterTimer.cancel();
			teleporterTimer = null;
		}
		
		teleporterTimer = new Timer();
		teleporterTimerTask = new TimerTask() {
			@Override
			public void run()
			{
				if (_choosenRoom > -1)
					DimensionalRiftManager.getInstance().getRoom(_roomType, _choosenRoom).unspawn();
				
				if (reasonTP && jumps_current < getMaxJumps() && _party.getMemberCount() > deadPlayers.size())
				{
					jumps_current++;
					
					_completedRooms.add(_choosenRoom);
					_choosenRoom = -1;
					
					for (L2Player p : _party.getPartyMembers())
						if (!revivedInWaitingRoom.contains(p))
							teleportToNextRoom(p);
					createTeleporterTimer(true);
					createSpawnTimer(_choosenRoom);
				}
				else
				{
					for (L2Player p : _party.getPartyMembers())
						if (!revivedInWaitingRoom.contains(p))
							teleportToWaitingRoom(p);
					killRift();
					cancel();
				}
			}
		};
		
		if (reasonTP)
			teleporterTimer.schedule(teleporterTimerTask, calcTimeToNextJump()); //Teleporter task, 8-10 minutes
		else
			teleporterTimer.schedule(teleporterTimerTask, seconds_5); //incorrect party member invited.
	}
	
	public void createSpawnTimer(final byte room)
	{
		if (spawnTimerTask != null)
		{
			spawnTimerTask.cancel();
			spawnTimerTask = null;
		}
		
		if (spawnTimer != null)
		{
			spawnTimer.cancel();
			spawnTimer = null;
		}
		
		final DimensionalRiftRoom riftRoom = DimensionalRiftManager.getInstance().getRoom(_roomType, room);
		riftRoom.setUsed();
		
		spawnTimer = new Timer();
		spawnTimerTask = new TimerTask() {
			@Override
			public void run()
			{
				riftRoom.spawn();
			}
		};
		
		spawnTimer.schedule(spawnTimerTask, Config.ALT_RIFT_SPAWN_DELAY);
	}
	
	public void partyMemberInvited()
	{
		createTeleporterTimer(false);
	}
	
	public void partyMemberExited(L2Player player)
	{
		if (deadPlayers.contains(player))
			deadPlayers.remove(player);
		
		if (revivedInWaitingRoom.contains(player))
			revivedInWaitingRoom.remove(player);
		
		if (_party.getMemberCount() < Config.ALT_RIFT_MIN_PARTY_SIZE || _party.getMemberCount() == 1)
		{
			for (L2Player p : _party.getPartyMembers())
				teleportToWaitingRoom(p);
			killRift();
		}
	}
	
	public void manualTeleport(L2Player player, L2Npc npc)
	{
		if (!player.isInParty() || !player.getParty().isInDimensionalRift())
			return;
		
		if (player.getObjectId() != player.getParty().getPartyLeaderOID())
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm",
					npc);
			return;
		}
		
		if (_hasJumped)
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player,
					"data/html/seven_signs/rift/AlreadyTeleported.htm", npc);
			return;
		}
		
		_hasJumped = true;
		
		DimensionalRiftManager.getInstance().getRoom(_roomType, _choosenRoom).unspawn();
		_completedRooms.add(_choosenRoom);
		_choosenRoom = -1;
		
		for (L2Player p : _party.getPartyMembers())
			teleportToNextRoom(p);
		
		createSpawnTimer(_choosenRoom);
		createTeleporterTimer(true);
	}
	
	public void manualExitRift(L2Player player, L2Npc npc)
	{
		if (!player.isInParty() || !player.getParty().isInDimensionalRift())
			return;
		
		if (player.getObjectId() != player.getParty().getPartyLeaderOID())
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm",
					npc);
			return;
		}
		
		for (L2Player p : player.getParty().getPartyMembers())
			teleportToWaitingRoom(p);
		killRift();
	}
	
	protected void teleportToNextRoom(L2Player player)
	{
		if (_choosenRoom == -1)
		{ //Do not tp in the same room a second time
			do
				_choosenRoom = (byte)Rnd.get(1, 9);
			while (_completedRooms.contains(_choosenRoom));
		}
		
		checkBossRoom(_choosenRoom);
		int[] coords = getRoomCoord(_choosenRoom);
		player.teleToLocation(coords[0], coords[1], coords[2]);
	}
	
	protected void teleportToWaitingRoom(L2Player player)
	{
		DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
		Quest riftQuest = QuestManager.getInstance().getQuest(635);
		if (riftQuest != null)
		{
			QuestState qs = player.getQuestState(riftQuest.getName());
			if (qs != null && qs.getInt("cond") == 1)
				qs.set("cond", "0");
		}
	}
	
	public void killRift()
	{
		_completedRooms = null;
		
		if (_party != null)
			_party.setDimensionalRift(null);
		
		_party = null;
		revivedInWaitingRoom = null;
		deadPlayers = null;
		DimensionalRiftManager.getInstance().getRoom(_roomType, _choosenRoom).unspawn();
		DimensionalRiftManager.getInstance().killRift(this);
	}
	
	public Timer getTeleportTimer()
	{
		return teleporterTimer;
	}
	
	public TimerTask getTeleportTimerTask()
	{
		return teleporterTimerTask;
	}
	
	public Timer getSpawnTimer()
	{
		return spawnTimer;
	}
	
	public TimerTask getSpawnTimerTask()
	{
		return spawnTimerTask;
	}
	
	public void setTeleportTimer(Timer t)
	{
		teleporterTimer = t;
	}
	
	public void setTeleportTimerTask(TimerTask tt)
	{
		teleporterTimerTask = tt;
	}
	
	public void setSpawnTimer(Timer t)
	{
		spawnTimer = t;
	}
	
	public void setSpawnTimerTask(TimerTask st)
	{
		spawnTimerTask = st;
	}
	
	private long calcTimeToNextJump()
	{
		int time = Rnd.get(Config.ALT_RIFT_AUTO_JUMPS_TIME_MIN, Config.ALT_RIFT_AUTO_JUMPS_TIME_MAX) * 1000;
		
		if (isBossRoom)
			return (long)(time * Config.ALT_RIFT_BOSS_ROOM_TIME_MUTIPLY);
		
		return time;
	}
	
	public void memberDead(L2Player player)
	{
		if (!deadPlayers.contains(player))
			deadPlayers.add(player);
		
		if (_party.getMemberCount() == deadPlayers.size())
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					for (L2Player p : _party.getPartyMembers())
						if (!revivedInWaitingRoom.contains(p))
							teleportToWaitingRoom(p);
					killRift();
				}
			}, 5000);
		}
	}
	
	public void memberRessurected(L2Player player)
	{
		if (deadPlayers.contains(player))
			deadPlayers.remove(player);
	}
	
	public void usedTeleport(L2Player player)
	{
		if (!revivedInWaitingRoom.contains(player))
			revivedInWaitingRoom.add(player);
		
		if (!deadPlayers.contains(player))
			deadPlayers.add(player);
		
		if (_party.getMemberCount() - revivedInWaitingRoom.size() < Config.ALT_RIFT_MIN_PARTY_SIZE)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					for (L2Player p : _party.getPartyMembers())
						if (!revivedInWaitingRoom.contains(p))
							teleportToWaitingRoom(p);
					killRift();
				}
			}, 5000);
		}
	}
	
	public List<L2Player> getDeadMemberList()
	{
		return deadPlayers;
	}
	
	public List<L2Player> getRevivedAtWaitingRoom()
	{
		return revivedInWaitingRoom;
	}
	
	public void checkBossRoom(byte roomId)
	{
		isBossRoom = DimensionalRiftManager.getInstance().getRoom(_roomType, roomId).isBossRoom();
	}
	
	public int[] getRoomCoord(byte roomId)
	{
		return DimensionalRiftManager.getInstance().getRoom(_roomType, roomId).getTeleportCoords();
	}
	
	public byte getMaxJumps()
	{
		if (Config.ALT_RIFT_MAX_JUMPS <= 8 && Config.ALT_RIFT_MAX_JUMPS >= 1)
			return (byte)Config.ALT_RIFT_MAX_JUMPS;
		
		return 4;
	}
}
