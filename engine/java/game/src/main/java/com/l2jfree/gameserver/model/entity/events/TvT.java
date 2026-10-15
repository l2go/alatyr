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
package com.l2jfree.gameserver.model.entity.events;

/**
 * 
 * @author FBIagent / fixed and moded for l2jfree by SqueezeD
 * 
 */
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.restriction.global.TvTRestriction;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.tools.random.Rnd;

public class TvT
{
	static
	{
		TvTRestriction.getInstance().activate();
	}
	
	public static final class TvTPlayerInfo extends AbstractFunEventPlayerInfo
	{
		/** TvT Engine parameters */
		public String _teamNameTvT;
		public int _nameColorTvT = -1;
		public int _countTvTkills;
		public int _countTvTdies;
		public int _originalKarmaTvT;
		
		private TvTPlayerInfo(L2Player player)
		{
			super(player);
		}
		
		@Override
		public boolean isInFunEvent()
		{
			return TvT._started;
		}
	}
	
	private final static Logger _log = LoggerFactory.getLogger(TvT.class);
	public static String _eventName = "";
	public static String _eventDesc = "";
	public static String _topTeam = "";
	public static String _joiningLocationName = "";
	public static CopyOnWriteArrayList<String> _teams = new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<String> _savePlayers = new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<String> _savePlayerTeams = new CopyOnWriteArrayList<String>();
	
	public static CopyOnWriteArrayList<L2Player> _players = new CopyOnWriteArrayList<L2Player>();
	public static CopyOnWriteArrayList<L2Player> _playersShuffle = new CopyOnWriteArrayList<L2Player>();
	public static CopyOnWriteArrayList<Integer> _teamPlayersCount = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer> _teamKillsCount = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer> _teamColors = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer> _teamsX = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer> _teamsY = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer> _teamsZ = new CopyOnWriteArrayList<Integer>();
	public static boolean _joining = false;
	public static boolean _teleport = false;
	public static boolean _started = false;
	public static boolean _sitForced = false;
	public static L2Spawn _npcSpawn;
	
	public static int _npcId = 0;
	public static int _npcX = 0;
	public static int _npcY = 0;
	public static int _npcZ = 0;
	public static int _npcHeading = 0;
	
	public static int _rewardId = 0;
	public static int _rewardAmount = 0;
	
	public static int _topKills = 0;
	public static int _minlvl = 0;
	public static int _maxlvl = 0;
	
	public static int _joinTime = 0;
	public static int _eventTime = 0;
	
	public static int _minPlayers = 0;
	public static int _maxPlayers = 0;
	
	public static int _playerWon = 0;
	
	public static void AnnounceToPlayers(Boolean toall, String announce)
	{
		if (toall)
			Announcements.getInstance().announceToAll(announce);
		else
		{
			CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Announce, "", announce);
			if (_players != null && !_players.isEmpty())
			{
				for (L2Player player : _players)
				{
					if (player != null && player.isOnline() != 0)
						player.sendPacket(cs);
				}
			}
		}
	}
	
	public static void kickPlayerFromTvt(L2Player playerToKick)
	{
		removePlayer(playerToKick, true);
	}
	
	public static void setNpcPos(L2Player activeChar)
	{
		_npcX = activeChar.getX();
		_npcY = activeChar.getY();
		_npcZ = activeChar.getZ();
		_npcHeading = activeChar.getHeading();
	}
	
	public static void setNpcPos(int x, int y, int z)
	{
		_npcX = x;
		_npcY = y;
		_npcZ = z;
	}
	
	public static void addTeam(String teamName)
	{
		if (!checkTeamOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[addTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}
		
		if (teamName.equals(" "))
			return;
		
		_teams.add(teamName);
		_teamPlayersCount.add(0);
		_teamKillsCount.add(0);
		_teamColors.add(0);
		_teamsX.add(0);
		_teamsY.add(0);
		_teamsZ.add(0);
	}
	
	public static boolean checkMaxLevel(int maxlvl)
	{
		return _minlvl < maxlvl;
	}
	
	public static boolean checkMinLevel(int minlvl)
	{
		return _maxlvl > minlvl;
	}
	
	/** returns true if participated players is higher or equal then minimum needed players */
	public static boolean checkMinPlayers(int players)
	{
		return _minPlayers <= players;
	}
	
	/** returns true if max players is higher or equal then participated players */
	public static boolean checkMaxPlayers(int players)
	{
		return _maxPlayers > players;
	}
	
	public static void removeTeam(String teamName)
	{
		if (!checkTeamOk() || _teams.isEmpty())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[removeTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}
		
		if (teamPlayersCount(teamName) > 0)
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[removeTeam(" + teamName + ")]: teamPlayersCount(teamName) > 0");
			return;
		}
		
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamsZ.remove(index);
		_teamsY.remove(index);
		_teamsX.remove(index);
		_teamColors.remove(index);
		_teamKillsCount.remove(index);
		_teamPlayersCount.remove(index);
		_teams.remove(index);
	}
	
	public static void setTeamPos(String teamName, L2Player activeChar)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamsX.set(index, activeChar.getX());
		_teamsY.set(index, activeChar.getY());
		_teamsZ.set(index, activeChar.getZ());
	}
	
	public static void setTeamPos(String teamName, int x, int y, int z)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamsX.set(index, x);
		_teamsY.set(index, y);
		_teamsZ.set(index, z);
	}
	
	public static void setTeamColor(String teamName, int color)
	{
		if (!checkTeamOk())
			return;
		
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamColors.set(index, color);
	}
	
	public static boolean checkTeamOk()
	{
		return !(_started || _teleport || _joining);
		
	}
	
	public static void startJoin(L2Player activeChar)
	{
		if (!startJoinOk())
		{
			activeChar.sendMessage("Event not setted propertly.");
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}
		
		_joining = true;
		spawnEventNpc(activeChar);
		AnnounceToPlayers(true, _eventName + " (TvT)!");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " "
					+ ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
	}
	
	public static void startJoin()
	{
		if (!startJoinOk())
		{
			_log.warn("Event not setted propertly.");
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[startJoin(startJoinOk() = false");
			return;
		}
		
		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName + " (TvT)!");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " "
					+ ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
	}
	
	public static boolean startAutoJoin()
	{
		if (!startJoinOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[startJoin]: startJoinOk() = false");
			return false;
		}
		
		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName + " (TvT)!");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " "
					+ ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
		return true;
	}
	
	public static boolean startJoinOk()
	{
		return !(_started || _teleport || _joining || _teams.size() < 2 || _eventName.isEmpty()
				|| _joiningLocationName.isEmpty() || _eventDesc.isEmpty() || _npcId == 0 || _npcX == 0 || _npcY == 0
				|| _npcZ == 0 || _rewardId == 0 || _rewardAmount == 0 || _teamsX.contains(0) || _teamsY.contains(0) || _teamsZ
					.contains(0));
	}
	
	private static void spawnEventNpc(L2Player activeChar)
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_npcId);
		
		try
		{
			_npcSpawn = new L2Spawn(tmpl);
			
			_npcSpawn.setLocx(_npcX);
			_npcSpawn.setLocy(_npcY);
			_npcSpawn.setLocz(_npcZ);
			_npcSpawn.setAmount(1);
			_npcSpawn.setHeading(_npcHeading);
			_npcSpawn.setRespawnDelay(1);
			
			SpawnTable.getInstance().addNewSpawn(_npcSpawn, false);
			
			_npcSpawn.init();
			_npcSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn()._isEventMobTvT = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(),
					_npcSpawn.getLastSpawn().getZ());
			
			_npcSpawn.getLastSpawn().broadcastPacket(
					new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.error("TvT Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: ", e);
		}
	}
	
	private static void spawnEventNpc()
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_npcId);
		
		try
		{
			_npcSpawn = new L2Spawn(tmpl);
			
			_npcSpawn.setLocx(_npcX);
			_npcSpawn.setLocy(_npcY);
			_npcSpawn.setLocz(_npcZ);
			_npcSpawn.setAmount(1);
			_npcSpawn.setHeading(_npcHeading);
			_npcSpawn.setRespawnDelay(1);
			
			SpawnTable.getInstance().addNewSpawn(_npcSpawn, false);
			
			_npcSpawn.init();
			_npcSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn()._isEventMobTvT = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(),
					_npcSpawn.getLastSpawn().getZ());
			
			_npcSpawn.getLastSpawn().broadcastPacket(
					new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.error("TvT Engine[spawnEventNpc(exception: ", e);
		}
	}
	
	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;
		
		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers
					+ ", Participating : " + _playersShuffle.size());
			return;
		}
		
		_joining = false;
		AnnounceToPlayers(false, _eventName + "(TvT): Teleport to team spot in 20 seconds!");
		
		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				TvT.sit();
				
				for (L2Player player : _players)
				{
					if (player != null)
					{
						if (Config.TVT_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								summon.stopAllEffects();
								
								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}
						
						if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
						{
							player.stopAllEffects();
						}
						
						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						
						final TvTPlayerInfo info = player.as(TvTPlayerInfo.class);
						
						player.getAppearance().setVisibleTitle("Kills: " + info._countTvTkills);
						player.teleToLocation(_teamsX.get(_teams.indexOf(info._teamNameTvT)),
								_teamsY.get(_teams.indexOf(info._teamNameTvT)),
								_teamsZ.get(_teams.indexOf(info._teamNameTvT)));
					}
				}
			}
		}, 20000);
		_teleport = true;
	}
	
	public static boolean teleportAutoStart()
	{
		if (!_joining || _started || _teleport)
			return false;
		
		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers
					+ ", Participating : " + _playersShuffle.size());
			return false;
		}
		
		_joining = false;
		AnnounceToPlayers(false, _eventName + "(TvT): Teleport to team spot in 20 seconds!");
		
		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				TvT.sit();
				
				for (L2Player player : _players)
				{
					if (player != null)
					{
						if (Config.TVT_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								summon.stopAllEffects();
								
								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}
						
						if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
						{
							player.stopAllEffects();
						}
						
						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						
						final TvTPlayerInfo info = player.as(TvTPlayerInfo.class);
						
						player.getAppearance().setVisibleTitle("Kills: " + info._countTvTkills);
						player.teleToLocation(_teamsX.get(_teams.indexOf(info._teamNameTvT)),
								_teamsY.get(_teams.indexOf(info._teamNameTvT)),
								_teamsZ.get(_teams.indexOf(info._teamNameTvT)));
					}
				}
			}
		}, 20000);
		_teleport = true;
		return true;
	}
	
	public static void startEvent(L2Player activeChar)
	{
		if (!startEventOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[startEvent(" + activeChar.getName() + ")]: startEventOk() = false");
			return;
		}
		
		_teleport = false;
		sit();
		AnnounceToPlayers(false, _eventName + "(TvT): Started. Go to kill your enemies!");
		_started = true;
	}
	
	public static void setJoinTime(int time)
	{
		_joinTime = time;
	}
	
	public static void setEventTime(int time)
	{
		_eventTime = time;
	}
	
	public static boolean startAutoEvent()
	{
		if (!startEventOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[startEvent]: startEventOk() = false");
			return false;
		}
		
		_teleport = false;
		sit();
		AnnounceToPlayers(false, _eventName + "(TvT): Started. Go to kill your enemies!");
		_started = true;
		return true;
	}
	
	public static void autoEvent()
	{
		if (startAutoJoin())
		{
			if (_joinTime > 0)
				waiter(_joinTime * 60 * 1000); // minutes for join event
			else if (_joinTime <= 0)
			{
				abortEvent();
				return;
			}
			if (teleportAutoStart())
			{
				waiter(30 * 1000); // 30 sec wait time untill start fight after teleported
				if (startAutoEvent())
				{
					waiter(_eventTime * 60 * 1000); // minutes for event time
					finishEvent();
				}
			}
			else if (!teleportAutoStart())
			{
				abortEvent();
			}
		}
	}
	
	private static void waiter(long interval)
	{
		long startWaiterTime = System.currentTimeMillis();
		int seconds = (int)(interval / 1000);
		
		while (startWaiterTime + interval > System.currentTimeMillis())
		{
			seconds--; // here because we don't want to see two time announce at the same time
			
			if (_joining || _started || _teleport)
			{
				switch (seconds)
				{
					case 3600: // 1 hour left
						if (_joining)
						{
							AnnounceToPlayers(true, "(TvT): Joinable in " + _joiningLocationName + "!");
							AnnounceToPlayers(true, "TvT Event: " + seconds / 60 / 60
									+ " hour(s) till registration ends!");
							
						}
						else if (_started)
							AnnounceToPlayers(false, "TvT Event: " + seconds / 60 / 60 + " hour(s) till event ends!");
						
						break;
					case 1800: // 30 minutes left
					case 900: // 15 minutes left
					case 600: //  10 minutes left
					case 300: // 5 minutes left
					case 60: // 1 minute left
						if (_joining)
						{
							removeOfflinePlayers();
							AnnounceToPlayers(true, "(TvT): Joinable in " + _joiningLocationName + "!");
							AnnounceToPlayers(true, "TvT Event: " + seconds / 60 + " minute(s) till registration ends!");
						}
						else if (_started)
							AnnounceToPlayers(false, "TvT Event: " + seconds / 60 + " minute(s) till event ends!");
						
						break;
					case 30: // 30 seconds left
					case 10: // 10 seconds left
					case 3: // 3 seconds left
					case 2: // 2 seconds left
					case 1: // 1 seconds left
						if (_joining)
							AnnounceToPlayers(true, "TvT Event: " + seconds + " second(s) till registration ends!");
						else if (_teleport)
							AnnounceToPlayers(false, "TvT Event: " + seconds + " seconds(s) till fight starts!");
						else if (_started)
							AnnounceToPlayers(false, "TvT Event: " + seconds + " second(s) till event ends!");
						
						break;
				}
			}
			
			long startOneSecondWaiterStartTime = System.currentTimeMillis();
			
			// only the try catch with Thread.sleep(1000) give bad countdown on high wait times
			while (startOneSecondWaiterStartTime + 1000 > System.currentTimeMillis())
			{
				try
				{
					Thread.sleep(1);
				}
				catch (InterruptedException ie)
				{
				}
			}
		}
	}
	
	private static boolean startEventOk()
	{
		if (_joining || !_teleport || _started)
			return false;
		
		if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			if (_teamPlayersCount.contains(0))
				return false;
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
		{
			CopyOnWriteArrayList<L2Player> playersShuffleTemp = new CopyOnWriteArrayList<L2Player>();
			int loopCount = 0;
			
			loopCount = _playersShuffle.size();
			
			for (int i = 0; i < loopCount; i++)
			{
				if (_playersShuffle != null)
					playersShuffleTemp.add(_playersShuffle.get(i));
			}
			
			_playersShuffle = playersShuffleTemp;
			playersShuffleTemp.clear();
			
			//  if (_playersShuffle.size() < (_teams.size()*2)){
			//	  return false;
			//  }
		}
		
		return true;
	}
	
	public static void shuffleTeams()
	{
		int teamCount = 0, playersCount = 0;
		
		for (;;)
		{
			if (_playersShuffle.isEmpty())
				break;
			
			int playerToAddIndex = Rnd.nextInt(_playersShuffle.size());
			L2Player player = null;
			player = _playersShuffle.get(playerToAddIndex);
			player.as(TvTPlayerInfo.class)._originalKarmaTvT = player.getKarma();
			
			_players.add(player);
			_players.get(playersCount).as(TvTPlayerInfo.class)._teamNameTvT = _teams.get(teamCount);
			_savePlayers.add(_players.get(playersCount).getName());
			_savePlayerTeams.add(_teams.get(teamCount));
			playersCount++;
			
			if (teamCount == _teams.size() - 1)
				teamCount = 0;
			else
				teamCount++;
			
			_playersShuffle.remove(playerToAddIndex);
		}
	}
	
	public static void setUserData()
	{
		for (L2Player player : _players)
		{
			final TvTPlayerInfo info = player.as(TvTPlayerInfo.class);
			
			info._nameColorTvT = _teamColors.get(_teams.indexOf(info._teamNameTvT));
			player.setKarma(0);
			player.broadcastUserInfo();
		}
	}
	
	public static void finishEvent()
	{
		if (!finishEventOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("TvT Engine[finishEvent]: finishEventOk() = false");
			return;
		}
		
		_started = false;
		unspawnEventNpc();
		processTopTeam();
		
		if (_topKills == 0)
			AnnounceToPlayers(true, _eventName + "(TvT): No team wins the match(nobody killed).");
		else
		{
			AnnounceToPlayers(true, _eventName + "(TvT): " + _topTeam + "'s win the match! " + _topKills + " kills.");
			rewardTeam(_topTeam);
			playKneelAnimation(_topTeam);
		}
		
		if (Config.TVT_ANNOUNCE_TEAM_STATS)
		{
			AnnounceToPlayers(true, _eventName + " Team Statistics:");
			for (String team : _teams)
			{
				int _kills = teamKillsCount(team);
				AnnounceToPlayers(true, "Team: " + team + " - Kills: " + _kills);
			}
		}
		
		teleportFinish();
	}
	
	//show loosers and winners animations
	public static void playKneelAnimation(String teamName)
	{
		for (L2Player player : _players)
		{
			if (player != null)
			{
				if (!player.as(TvTPlayerInfo.class)._teamNameTvT.equals(teamName))
				{
					player.broadcastPacket(new SocialAction(player.getObjectId(), 7));
				}
				else if (player.as(TvTPlayerInfo.class)._teamNameTvT.equals(teamName))
				{
					player.broadcastPacket(new SocialAction(player.getObjectId(), 3));
				}
			}
		}
	}
	
	private static boolean finishEventOk()
	{
		return _started;
	}
	
	public static void processTopTeam()
	{
		for (String team : _teams)
		{
			if (teamKillsCount(team) > _topKills)
			{
				_topTeam = team;
				_topKills = teamKillsCount(team);
			}
		}
	}
	
	public static void rewardTeam(String teamName)
	{
		for (L2Player player : _players)
		{
			if (player != null && player.isOnline() != 0 && player.isInEvent(TvTPlayerInfo.class))
			{
				if (player.as(TvTPlayerInfo.class)._teamNameTvT.equals(teamName)
						&& (player.as(TvTPlayerInfo.class)._countTvTkills > 0 || Config.TVT_PRICE_NO_KILLS))
				{
					player.addItem("TvT Event: " + _eventName, _rewardId, _rewardAmount, player, true, true);
					
					_playerWon = 1;
					
					NpcHtmlMessage nhm = new NpcHtmlMessage(5);
					StringBuilder replyMSG = new StringBuilder("");
					
					replyMSG.append("<html><body>Your team wins the event. Look in your inventory for the reward.</body></html>");
					
					nhm.setHtml(replyMSG.toString());
					player.sendPacket(nhm);
					
					// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
		}
	}
	
	public static void abortEvent()
	{
		if (!_joining && !_teleport && !_started)
			return;
		if (_joining && !_teleport && !_started)
		{
			unspawnEventNpc();
			cleanTvT();
			_joining = false;
			AnnounceToPlayers(true, _eventName + "(TvT): Match aborted!");
			return;
		}
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		AnnounceToPlayers(true, _eventName + "(TvT): Match aborted!");
		teleportFinish();
	}
	
	public static void sit()
	{
		_sitForced = !_sitForced;
		
		for (L2Player player : _players)
		{
			if (player != null)
			{
				if (_sitForced)
				{
					player.stopMove(null, false);
					player.abortAttack();
					player.abortCast();
					
					if (!player.isSitting())
						player.sitDown();
				}
				else
				{
					if (player.isSitting())
						player.standUp();
				}
			}
		}
	}
	
	public static void dumpData()
	{
		_log.info("");
		_log.info("");
		
		if (!_joining && !_teleport && !_started)
		{
			_log.info("<<---------------------------------->>");
			_log.info(">> TvT Engine infos dump (INACTIVE) <<");
			_log.info("<<--^----^^-----^----^^------^^----->>");
		}
		else if (_joining && !_teleport && !_started)
		{
			_log.info("<<--------------------------------->>");
			_log.info(">> TvT Engine infos dump (JOINING) <<");
			_log.info("<<--^----^^-----^----^^------^----->>");
		}
		else if (!_joining && _teleport && !_started)
		{
			_log.info("<<---------------------------------->>");
			_log.info(">> TvT Engine infos dump (TELEPORT) <<");
			_log.info("<<--^----^^-----^----^^------^^----->>");
		}
		else if (!_joining && !_teleport && _started)
		{
			_log.info("<<--------------------------------->>");
			_log.info(">> TvT Engine infos dump (STARTED) <<");
			_log.info("<<--^----^^-----^----^^------^----->>");
		}
		
		_log.info("Name: " + _eventName);
		_log.info("Desc: " + _eventDesc);
		_log.info("Join location: " + _joiningLocationName);
		_log.info("Min lvl: " + _minlvl);
		_log.info("Max lvl: " + _maxlvl);
		_log.info("");
		_log.info("##########################");
		_log.info("# _teams(CopyOnWriteArrayList<String>) #");
		_log.info("##########################");
		
		for (String team : _teams)
			_log.info(team + " Kills Done :" + _teamKillsCount.get(_teams.indexOf(team)));
		
		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
		{
			_log.info("");
			_log.info("#########################################");
			_log.info("# _playersShuffle(CopyOnWriteArrayList<L2Player>) #");
			_log.info("#########################################");
			
			for (L2Player player : _playersShuffle)
			{
				if (player != null)
					_log.info("Name: " + player.getName());
			}
		}
		
		_log.info("");
		_log.info("##################################");
		_log.info("# _players(CopyOnWriteArrayList<L2Player>) #");
		_log.info("##################################");
		
		for (L2Player player : _players)
		{
			if (player != null)
				_log.info("Name: " + player.getName() + "   Team: " + player.as(TvTPlayerInfo.class)._teamNameTvT
						+ "  Kills Done:" + player.as(TvTPlayerInfo.class)._countTvTkills);
		}
		
		_log.info("");
		_log.info("#####################################################################");
		_log.info("# _savePlayers(CopyOnWriteArrayList<String>) and _savePlayerTeams(CopyOnWriteArrayList<String>) #");
		_log.info("#####################################################################");
		
		for (String player : _savePlayers)
			_log.info("Name: " + player + "	Team: " + _savePlayerTeams.get(_savePlayers.indexOf(player)));
		
		_log.info("");
		_log.info("");
	}
	
	public static void loadData()
	{
		_eventName = "";
		_eventDesc = "";
		_topTeam = "";
		_joiningLocationName = "";
		_teams = new CopyOnWriteArrayList<String>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_savePlayerTeams = new CopyOnWriteArrayList<String>();
		_players = new CopyOnWriteArrayList<L2Player>();
		_playersShuffle = new CopyOnWriteArrayList<L2Player>();
		_teamPlayersCount = new CopyOnWriteArrayList<Integer>();
		_teamKillsCount = new CopyOnWriteArrayList<Integer>();
		_teamColors = new CopyOnWriteArrayList<Integer>();
		_teamsX = new CopyOnWriteArrayList<Integer>();
		_teamsY = new CopyOnWriteArrayList<Integer>();
		_teamsZ = new CopyOnWriteArrayList<Integer>();
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_npcId = 0;
		_npcX = 0;
		_npcY = 0;
		_npcZ = 0;
		_npcHeading = 0;
		_rewardId = 0;
		_rewardAmount = 0;
		_topKills = 0;
		_minlvl = 0;
		_maxlvl = 0;
		_joinTime = 0;
		_eventTime = 0;
		_minPlayers = 0;
		_maxPlayers = 0;
		
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT name, description, joining_location_name, min_level, max_level, "
							+ "npc_template_id, npc_x, npc_y, npc_z, npc_heading, reward_item_template_id, reward_count, "
							+ "join_duration_s, event_duration_s, min_players, max_players FROM tvt_event");
			rs = statement.executeQuery();
			
			boolean eventFound = false;
			
			while (rs.next())
			{
				_eventName = rs.getString("name");
				_eventDesc = rs.getString("description");
				_joiningLocationName = rs.getString("joining_location_name");
				_minlvl = rs.getInt("min_level");
				_maxlvl = rs.getInt("max_level");
				_npcId = rs.getInt("npc_template_id");
				_npcX = rs.getInt("npc_x");
				_npcY = rs.getInt("npc_y");
				_npcZ = rs.getInt("npc_z");
				_npcHeading = rs.getInt("npc_heading");
				_rewardId = rs.getInt("reward_item_template_id");
				_rewardAmount = rs.getInt("reward_count");
				// The columns count seconds, the event counts minutes.
				_joinTime = rs.getInt("join_duration_s") / 60;
				_eventTime = rs.getInt("event_duration_s") / 60;
				_minPlayers = rs.getInt("min_players");
				_maxPlayers = rs.getInt("max_players");
				eventFound = true;
			}
			statement.close();
			
			if (eventFound)
			{
				statement =
						con.prepareStatement("SELECT team_number, name, x, y, z, name_color FROM tvt_event_team "
								+ "ORDER BY team_number");
				rs = statement.executeQuery();
				int index = 0;
				while (rs.next())
				{
					_teams.add(rs.getString("name"));
					_teamPlayersCount.add(0);
					_teamKillsCount.add(0);
					_teamColors.add(0);
					_teamsX.add(0);
					_teamsY.add(0);
					_teamsZ.add(0);
					_teamsX.set(index, rs.getInt("x"));
					_teamsY.set(index, rs.getInt("y"));
					_teamsZ.set(index, rs.getInt("z"));
					_teamColors.set(index, rs.getInt("name_color"));
					index++;
				}
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.error("Exception: TvT.loadData(): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void saveData()
	{
		// The event and its teams are replaced together.
		WorldTransaction.run("TvT event save", TvT::writeData);
	}
	
	private static void writeData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("DELETE FROM tvt_event");
			statement.execute();
			statement.close();
			
			statement =
					con.prepareStatement("INSERT INTO tvt_event (name, description, joining_location_name, min_level, max_level, npc_template_id, npc_x, npc_y, npc_z, npc_heading, reward_item_template_id, reward_count, join_duration_s, event_duration_s, min_players, max_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setString(1, _eventName);
			statement.setString(2, _eventDesc);
			statement.setString(3, _joiningLocationName);
			statement.setInt(4, _minlvl);
			statement.setInt(5, _maxlvl);
			statement.setInt(6, _npcId);
			statement.setInt(7, _npcX);
			statement.setInt(8, _npcY);
			statement.setInt(9, _npcZ);
			statement.setInt(10, _npcHeading);
			statement.setInt(11, _rewardId);
			statement.setInt(12, _rewardAmount);
			statement.setInt(13, _joinTime * 60);
			statement.setInt(14, _eventTime * 60);
			statement.setInt(15, _minPlayers);
			statement.setInt(16, _maxPlayers);
			statement.execute();
			statement.close();
			
			statement = con.prepareStatement("DELETE FROM tvt_event_team");
			statement.execute();
			statement.close();
			
			for (String teamName : _teams)
			{
				int index = _teams.indexOf(teamName);
				
				if (index == -1)
					return;
				statement =
						con.prepareStatement("INSERT INTO tvt_event_team (team_number, name, x, y, z, name_color) VALUES (?, ?, ?, ?, ?, ?)");
				statement.setInt(1, index);
				statement.setString(2, teamName);
				statement.setInt(3, _teamsX.get(index));
				statement.setInt(4, _teamsY.get(index));
				statement.setInt(5, _teamsZ.get(index));
				statement.setInt(6, _teamColors.get(index));
				statement.execute();
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.error("Exception: TvT.saveData(): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void showEventHtml(L2Player eventPlayer, String objectId)
	{
		try
		{
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			replyMSG.append("TvT Match<br><br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("    ... name:&nbsp;<font color=\"00FF00\">" + _eventName + "</font><br1>");
			replyMSG.append("    ... description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br>");
			if (Config.TVT_ANNOUNCE_REWARD)
				replyMSG.append("    ... reward: (" + _rewardAmount + ") "
						+ ItemTable.getInstance().getTemplate(_rewardId).getName() + "<br>");
			
			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMaxPlayers(_playersShuffle.size()))
			{
				if (!TvT._started)
				{
					replyMSG.append("<font color=\"FFFF00\">The event has reached its maximum capacity.</font><br>Keep checking, someone may crit and you can steal their spot.");
				}
			}
			else if (eventPlayer.isCursedWeaponEquipped() && !Config.TVT_JOIN_CURSED)
			{
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event with a cursed Weapon.</font><br>");
			}
			else if (!_started && _joining && eventPlayer.getLevel() >= _minlvl && eventPlayer.getLevel() < _maxlvl)
			{
				if (_players.contains(eventPlayer) || checkShufflePlayers(eventPlayer))
				{
					if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
						replyMSG.append("You are already participating in team <font color=\"LEVEL\">"
								+ eventPlayer.as(TvTPlayerInfo.class)._teamNameTvT + "</font><br><br>");
					else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
						replyMSG.append("You are already participating!<br><br>");
					
					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"200\">Wait till event start or</td>");
					replyMSG.append("<td width=\"60\"><center><button value=\"remove\" action=\"bypass -h npc_"
							+ objectId
							+ "_tvt_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"100\">your participation!</td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					replyMSG.append("<td width=\"200\">Your level : <font color=\"00FF00\">" + eventPlayer.getLevel()
							+ "</font></td><br>");
					replyMSG.append("<td width=\"200\">Min level : <font color=\"00FF00\">" + _minlvl
							+ "</font></td><br>");
					replyMSG.append("<td width=\"200\">Max level : <font color=\"00FF00\">" + _maxlvl
							+ "</font></td><br><br>");
					
					if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
					{
						replyMSG.append("<center><table border=\"0\">");
						
						for (String team : _teams)
						{
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font>&nbsp;("
									+ teamPlayersCount(team) + " joined)</td>");
							replyMSG.append("<td width=\"60\"><button value=\"Join\" action=\"bypass -h npc_"
									+ objectId
									+ "_tvt_player_join "
									+ team
									+ "\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
						}
						
						replyMSG.append("</table></center>");
					}
					else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
					{
						replyMSG.append("<center><table border=\"0\">");
						
						for (String team : _teams)
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font></td>");
						
						replyMSG.append("</table></center><br>");
						
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_tvt_player_join eventShuffle\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("Teams will be randomly generated!");
					}
				}
			}
			else if (_started && !_joining)
				replyMSG.append("<center>TvT match is in progress.</center>");
			else if (eventPlayer.getLevel() < _minlvl || eventPlayer.getLevel() > _maxlvl)
			{
				replyMSG.append("Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
				replyMSG.append("Min level : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Max level : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event.</font><br>");
			}
			// Show how many players joined & how many are still needed to join
			replyMSG.append("<br>There are " + _playersShuffle.size() + " player(s) participating in this event.<br>");
			if (_joining)
			{
				if (_playersShuffle.size() < _minPlayers)
				{
					int playersNeeded = _minPlayers - _playersShuffle.size();
					replyMSG.append("The event will not start unless " + playersNeeded + " more player(s) joins!");
				}
			}
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);
			
			// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			_log.warn("TvT Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception", e);
		}
	}
	
	public static void addPlayer(L2Player player, String teamName)
	{
		if (!addPlayerOk(teamName, player))
			return;
		
		final TvTPlayerInfo info = new TvTPlayerInfo(player);
		player.setPlayerInfo(info);
		if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			info._teamNameTvT = teamName;
			_players.add(player);
			setTeamPlayersCount(teamName, teamPlayersCount(teamName) + 1);
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
			_playersShuffle.add(player);
		
		info._countTvTkills = 0;
	}
	
	public static void removeOfflinePlayers()
	{
		try
		{
			if (_playersShuffle == null || _playersShuffle.isEmpty())
				return;
			
			for (L2Player player : _playersShuffle)
			{
				if (player == null)
					_playersShuffle.remove(player);
				else if (player.isOnline() == 0 || player.isInJail())
					removePlayer(player);
				if (_playersShuffle.size() == 0 || _playersShuffle.isEmpty())
					break;
			}
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
	}
	
	public static boolean checkShufflePlayers(L2Player eventPlayer)
	{
		try
		{
			for (L2Player player : _playersShuffle)
			{
				if (player == null || player.isOnline() == 0)
				{
					_playersShuffle.remove(player);
					//eventPlayer._inEventTvT = false;
					continue;
				}
				else if (player == eventPlayer)
				{
					return true;
				}
				else if (player.getObjectId() == eventPlayer.getObjectId())
				{
					eventPlayer.setPlayerInfo(player.as(TvTPlayerInfo.class));
					eventPlayer.as(TvTPlayerInfo.class)._countTvTkills = 0;
					return true;
				}
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		return false;
	}
	
	public static boolean addPlayerOk(String teamName, L2Player eventPlayer)
	{
		if (GlobalRestrictions.isRestricted(eventPlayer, TvTRestriction.class))
		{
			// TODO: msg
			return false;
		}
		
		try
		{
			if (checkShufflePlayers(eventPlayer) || eventPlayer.isInEvent(TvTPlayerInfo.class))
			{
				eventPlayer.sendMessage("You are already participating in the event!");
				return false;
			}
			
			for (L2Player player : _players)
			{
				if (player.getObjectId() == eventPlayer.getObjectId())
				{
					eventPlayer.sendMessage("You are already participating in the event!");
					return false;
				}
				else if (player.getName() == eventPlayer.getName())
				{
					eventPlayer.sendMessage("You are already participating in the event!");
					return false;
				}
			}
			if (_players.contains(eventPlayer))
			{
				eventPlayer.sendMessage("You are already participating in the event!");
				return false;
			}
			if (CTF._savePlayers.contains(eventPlayer.getName()))
			{
				eventPlayer.sendMessage("You are already participating in another event!");
				return false;
			}
		}
		catch (Exception e)
		{
			_log.warn("TvT Engine exception: ", e);
		}
		
		if (Config.TVT_EVEN_TEAMS.equals("NO"))
			return true;
		else if (Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			boolean allTeamsEqual = true;
			int countBefore = -1;
			
			for (int playersCount : _teamPlayersCount)
			{
				if (countBefore == -1)
					countBefore = playersCount;
				
				if (countBefore != playersCount)
				{
					allTeamsEqual = false;
					break;
				}
				
				countBefore = playersCount;
			}
			
			if (allTeamsEqual)
				return true;
			
			countBefore = Integer.MAX_VALUE;
			
			for (int teamPlayerCount : _teamPlayersCount)
			{
				if (teamPlayerCount < countBefore)
					countBefore = teamPlayerCount;
			}
			
			CopyOnWriteArrayList<String> joinableTeams = new CopyOnWriteArrayList<String>();
			
			for (String team : _teams)
			{
				if (teamPlayersCount(team) == countBefore)
					joinableTeams.add(team);
			}
			
			if (joinableTeams.contains(teamName))
				return true;
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
			return true;
		
		eventPlayer.sendMessage("Too many players in team \"" + teamName + "\"");
		return false;
	}
	
	public static synchronized void addDisconnectedPlayer(L2Player player)
	{
		if ((Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && (_teleport || _started))
				|| (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE")
						&& (_teleport || _started)))
		{
			if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
			{
				player.stopAllEffects();
			}
			
			final TvTPlayerInfo info = new TvTPlayerInfo(player);
			player.setPlayerInfo(info);
			info._teamNameTvT = _savePlayerTeams.get(_savePlayers.indexOf(player.getName()));
			for (L2Player p : _players)
			{
				if (p == null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					info._originalKarmaTvT = player.getKarma();
					info._countTvTkills = p.as(TvTPlayerInfo.class)._countTvTkills;
					_players.remove(p); //removing old object id from list
					_players.add(player); //adding new objectId to list
					break;
				}
			}
			
			info._nameColorTvT = _teamColors.get(_teams.indexOf(info._teamNameTvT));
			player.setKarma(0);
			player.broadcastUserInfo();
			player.teleToLocation(_teamsX.get(_teams.indexOf(info._teamNameTvT)),
					_teamsY.get(_teams.indexOf(info._teamNameTvT)), _teamsZ.get(_teams.indexOf(info._teamNameTvT)));
		}
	}
	
	public static void removePlayer(L2Player player)
	{
		removePlayer(player, false);
	}
	
	public static void removePlayer(L2Player player, boolean kick)
	{
		final TvTPlayerInfo info = player.getPlayerInfo(TvTPlayerInfo.class);
		
		if (info != null)
		{
			if (!_joining && player.isOnline() != 0)
			{
				info._nameColorTvT = -1;
				player.getAppearance().setVisibleTitle(null);
				player.setKarma(info._originalKarmaTvT);
				player.broadcastUserInfo();
				if (kick)
				{
					player.sendMessage("You have been kicked from the TvT.");
					player.teleToLocation(_npcX, _npcY, _npcZ, false);
				}
			}
			info._teamNameTvT = "";
			info._countTvTkills = 0;
			player.setPlayerInfo(null);
			
			if ((Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
					&& _players.contains(player))
			{
				setTeamPlayersCount(info._teamNameTvT, teamPlayersCount(info._teamNameTvT) - 1);
			}
			
			_players.remove(player);
			_playersShuffle.remove(player);
			_savePlayers.remove(player.getName());
		}
	}
	
	public static void cleanTvT()
	{
		_log.info("TvT : Cleaning players.");
		for (L2Player player : _players)
		{
			if (player != null)
			{
				removePlayer(player);
			}
		}
		if (_playersShuffle != null && !_playersShuffle.isEmpty())
		{
			for (L2Player player : _playersShuffle)
			{
				if (player != null)
					player.setPlayerInfo(null);
			}
		}
		_log.info("TvT : Cleaning teams.");
		for (String team : _teams)
		{
			int index = _teams.indexOf(team);
			
			_teamPlayersCount.set(index, 0);
			_teamKillsCount.set(index, 0);
		}
		
		_topKills = 0;
		_topTeam = "";
		_players = new CopyOnWriteArrayList<L2Player>();
		_playersShuffle = new CopyOnWriteArrayList<L2Player>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_savePlayerTeams = new CopyOnWriteArrayList<String>();
		_log.info("Cleaning TvT done.");
	}
	
	public static void unspawnEventNpc()
	{
		if (_npcSpawn == null)
			return;
		
		_npcSpawn.getLastSpawn().deleteMe();
		_npcSpawn.stopRespawn();
		SpawnTable.getInstance().deleteSpawn(_npcSpawn, true);
	}
	
	public static void teleportFinish()
	{
		AnnounceToPlayers(false, _eventName + "(TvT): Teleport back to participation NPC in 20 seconds!");
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				for (L2Player player : _players)
				{
					if (player != null)
					{
						if (player.isOnline() != 0)
							player.teleToLocation(_npcX, _npcY, _npcZ, false);
						else
						{
							Connection con = null;
							try
							{
								con = L2DatabaseFactory.getInstance().getConnection(con);
								
								PreparedStatement statement =
										con.prepareStatement("UPDATE player SET x = ?, y = ?, z = ? WHERE name = ?");
								statement.setInt(1, _npcX);
								statement.setInt(2, _npcY);
								statement.setInt(3, _npcZ);
								statement.setString(4, player.getName());
								statement.execute();
								statement.close();
							}
							catch (SQLException se)
							{
								_log.error(se.getMessage(), se);
							}
							finally
							{
								L2DatabaseFactory.close(con);
							}
						}
					}
				}
				_log.info("TvT: Teleport done.");
				cleanTvT();
			}
		}, 20000);
	}
	
	public static int teamKillsCount(String teamName)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return -1;
		
		return _teamKillsCount.get(index);
	}
	
	public static void setTeamKillsCount(String teamName, int teamKillsCount)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamKillsCount.set(index, teamKillsCount);
	}
	
	public static int teamPlayersCount(String teamName)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return -1;
		
		return _teamPlayersCount.get(index);
	}
	
	public static void setTeamPlayersCount(String teamName, int teamPlayersCount)
	{
		int index = _teams.indexOf(teamName);
		
		if (index == -1)
			return;
		
		_teamPlayersCount.set(index, teamPlayersCount);
	}
	
	public static void resSummon(final L2Summon summon)
	{
		summon.getOwner().sendMessage("Your pet will be revived in " + Config.TVT_REVIVE_DELAY / 1000 + " seconds!");
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				summon.doRevive();
				summon.teleToLocation(summon.getOwner().getX(), summon.getOwner().getY(), summon.getOwner().getZ(),
						false);
			}
		}, Config.TVT_REVIVE_DELAY);
	}
}
