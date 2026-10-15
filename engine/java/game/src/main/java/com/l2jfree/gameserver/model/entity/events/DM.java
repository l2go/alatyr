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
 * @author SqueezeD
 * 
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.restriction.global.DMRestriction;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class DM
{
	static
	{
		DMRestriction.getInstance().activate();
	}
	
	public static final class DMPlayerInfo extends AbstractFunEventPlayerInfo
	{
		/** DM Engine parameters */
		public int _nameColorDM = -1;
		public int _countDMkills;
		public int _originalKarmaDM;
		
		private DMPlayerInfo(L2Player player)
		{
			super(player);
		}
		
		@Override
		public boolean isInFunEvent()
		{
			return DM._started;
		}
	}
	
	private final static Logger _log = LoggerFactory.getLogger(DM.class);
	public static String _eventName = "", _eventDesc = "", _joiningLocationName = "";
	public static CopyOnWriteArrayList<String> _savePlayers = new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<L2Player> _players = new CopyOnWriteArrayList<L2Player>();
	public static boolean _joining = false, _teleport = false, _started = false, _sitForced = false;
	public static L2Spawn _npcSpawn;
	public static L2Player _topPlayer;
	public static int _npcId = 0, _npcX = 0, _npcY = 0, _npcZ = 0, _rewardId = 0, _rewardAmount = 0, _topKills = 0,
			_minlvl = 0, _maxlvl = 0, _playerColors = 0, _playerX = 0, _playerY = 0, _playerZ = 0;
	
	public static void setNpcPos(L2Player activeChar)
	{
		_npcX = activeChar.getX();
		_npcY = activeChar.getY();
		_npcZ = activeChar.getZ();
	}
	
	public static boolean checkMaxLevel(int maxlvl)
	{
		return _minlvl < maxlvl;
	}
	
	public static boolean checkMinLevel(int minlvl)
	{
		return _maxlvl > minlvl;
	}
	
	public static void setPlayersPos(L2Player activeChar)
	{
		_playerX = activeChar.getX();
		_playerY = activeChar.getY();
		_playerZ = activeChar.getZ();
	}
	
	public static boolean checkPlayerOk()
	{
		return !(_started || _teleport || _joining);
	}
	
	public static void startJoin(L2Player activeChar)
	{
		if (!startJoinOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("DM Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}
		
		_joining = true;
		spawnEventNpc(activeChar);
		Announcements.getInstance().announceToAll(_eventName + "(DM): Joinable in " + _joiningLocationName + "!");
	}
	
	private static boolean startJoinOk()
	{
		return !(_started || _teleport || _joining || _eventName.isEmpty() || _joiningLocationName.isEmpty()
				|| _eventDesc.isEmpty() || _npcId == 0 || _npcX == 0 || _npcY == 0 || _npcZ == 0 || _rewardId == 0
				|| _rewardAmount == 0 || _playerX == 0 || _playerY == 0 || _playerZ == 0);
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
			_npcSpawn.setHeading(activeChar.getHeading());
			_npcSpawn.setRespawnDelay(1);
			
			SpawnTable.getInstance().addNewSpawn(_npcSpawn, false);
			
			_npcSpawn.init();
			_npcSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn()._isEventMobDM = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(),
					_npcSpawn.getLastSpawn().getZ());
			
			_npcSpawn.getLastSpawn().broadcastPacket(
					new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			if (_log.isDebugEnabled())
				_log.debug("DM Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: ", e);
		}
	}
	
	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;
		
		_joining = false;
		Announcements.getInstance().announceToAll(_eventName + "(DM): Teleport to team spot in 20 seconds!");
		
		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				DM.sit();
				
				for (L2Player player : DM._players)
				{
					if (player != null)
					{
						if (Config.DM_ON_START_UNSUMMON_PET)
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
						
						if (Config.DM_ON_START_REMOVE_ALL_EFFECTS)
						{
							player.stopAllEffects();
						}
						
						// Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						player.teleToLocation(_playerX, _playerY, _playerZ, false);
					}
				}
			}
		}, 20000);
		_teleport = true;
	}
	
	public static void startEvent(L2Player activeChar)
	{
		if (!startEventOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("DM Engine[startEvent(" + activeChar.getName() + ")]: startEventOk() = false");
			return;
		}
		
		_teleport = false;
		sit();
		Announcements.getInstance().announceToAll(_eventName + "(DM): Started. Go to kill your enemies!");
		_started = true;
	}
	
	private static boolean startEventOk()
	{
		return !(_joining || !_teleport || _started);
	}
	
	public static void setUserData()
	{
		for (L2Player player : _players)
		{
			final DMPlayerInfo info = player.as(DMPlayerInfo.class);
			info._originalKarmaDM = player.getKarma();
			info._countDMkills = 0;
			info._nameColorDM = _playerColors;
			player.setKarma(0);
			player.broadcastUserInfo();
		}
	}
	
	public static void removeUserData()
	{
		for (L2Player player : _players)
		{
			final DMPlayerInfo info = player.as(DMPlayerInfo.class);
			info._nameColorDM = -1;
			player.setKarma(info._originalKarmaDM);
			player.setPlayerInfo(null);
			info._countDMkills = 0;
			player.broadcastUserInfo();
		}
	}
	
	public static void finishEvent(L2Player activeChar)
	{
		if (!finishEventOk())
		{
			if (_log.isDebugEnabled())
				_log.debug("DM Engine[finishEvent(" + activeChar.getName() + ")]: finishEventOk() = false");
			return;
		}
		
		_started = false;
		unspawnEventNpc();
		processTopPlayer();
		
		if (_topKills == 0)
			Announcements.getInstance().announceToAll(_eventName + "(DM): No players win the match(nobody killed).");
		else
		{
			Announcements.getInstance().announceToAll(
					_eventName + "(DM): " + _topPlayer.getName() + " wins the match! " + _topKills + " kills.");
			rewardPlayer(activeChar);
		}
		
		teleportFinish();
	}
	
	private static boolean finishEventOk()
	{
		return _started;
	}
	
	public static void processTopPlayer()
	{
		for (L2Player player : _players)
		{
			if (player.as(DMPlayerInfo.class)._countDMkills > _topKills)
			{
				_topPlayer = player;
				_topKills = player.as(DMPlayerInfo.class)._countDMkills;
			}
		}
	}
	
	/**
	 * @param activeChar
	 */
	public static void rewardPlayer(L2Player activeChar)
	{
		if (_topPlayer != null)
		{
			_topPlayer.addItem("DM Event: " + _eventName, _rewardId, _rewardAmount, _topPlayer, true, true);
			
			StatusUpdate su = new StatusUpdate(_topPlayer.getObjectId());
			su.addAttribute(StatusUpdate.CUR_LOAD, _topPlayer.getCurrentLoad());
			_topPlayer.sendPacket(su);
			
			NpcHtmlMessage nhm = new NpcHtmlMessage(5);
			StringBuilder replyMSG = new StringBuilder("");
			
			replyMSG.append("<html><body>You won the event. Look in your inventory for the reward.</body></html>");
			
			nhm.setHtml(replyMSG.toString());
			_topPlayer.sendPacket(nhm);
			
			// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
			_topPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	public static void abortEvent()
	{
		if (!_joining && !_teleport && !_started)
			return;
		
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		Announcements.getInstance().announceToAll(_eventName + "(DM): Match aborted!");
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
			_log.info(">> DM Engine infos dump (INACTIVE) <<");
			_log.info("<<--^----^^-----^----^^------^^----->>");
		}
		else if (_joining && !_teleport && !_started)
		{
			_log.info("<<--------------------------------->>");
			_log.info(">> DM Engine infos dump (JOINING) <<");
			_log.info("<<--^----^^-----^----^^------^----->>");
		}
		else if (!_joining && _teleport && !_started)
		{
			_log.info("<<---------------------------------->>");
			_log.info(">> DM Engine infos dump (TELEPORT) <<");
			_log.info("<<--^----^^-----^----^^------^^----->>");
		}
		else if (!_joining && !_teleport && _started)
		{
			_log.info("<<--------------------------------->>");
			_log.info(">> DM Engine infos dump (STARTED) <<");
			_log.info("<<--^----^^-----^----^^------^----->>");
		}
		
		_log.info("Name: " + _eventName);
		_log.info("Desc: " + _eventDesc);
		_log.info("Join location: " + _joiningLocationName);
		_log.info("Min lvl: " + _minlvl);
		_log.info("Max lvl: " + _maxlvl);
		
		_log.info("");
		_log.info("##################################");
		_log.info("# _players(CopyOnWriteArrayList<L2Player>) #");
		_log.info("##################################");
		
		_log.info("Total Players : " + _players.size());
		
		for (L2Player player : _players)
		{
			if (player != null)
				_log.info("Name: " + player.getName() + " kills :" + player.as(DMPlayerInfo.class)._countDMkills);
		}
		
		_log.info("");
		_log.info("################################");
		_log.info("# _savePlayers(CopyOnWriteArrayList<String>) #");
		_log.info("################################");
		
		for (String player : _savePlayers)
			_log.info("Name: " + player);
		
		_log.info("");
		_log.info("");
	}
	
	public static void loadData()
	{
		_eventName = "";
		_eventDesc = "";
		_joiningLocationName = "";
		_savePlayers = new CopyOnWriteArrayList<String>();
		_players = new CopyOnWriteArrayList<L2Player>();
		_topPlayer = null;
		_npcSpawn = null;
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_npcId = 0;
		_npcX = 0;
		_npcY = 0;
		_npcZ = 0;
		_rewardId = 0;
		_rewardAmount = 0;
		_topKills = 0;
		_minlvl = 0;
		_maxlvl = 0;
		_playerColors = 0;
		_playerX = 0;
		_playerY = 0;
		_playerZ = 0;
		
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT name, description, joining_location_name, min_level, max_level, "
							+ "npc_template_id, npc_x, npc_y, npc_z, reward_item_template_id, reward_count, name_color, "
							+ "player_x, player_y, player_z FROM dm_event");
			rs = statement.executeQuery();
			
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
				_rewardId = rs.getInt("reward_item_template_id");
				_rewardAmount = rs.getInt("reward_count");
				_playerColors = rs.getInt("name_color");
				_playerX = rs.getInt("player_x");
				_playerY = rs.getInt("player_y");
				_playerZ = rs.getInt("player_z");
				
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: DM.loadData(): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void saveData()
	{
		// The old row is replaced by the new one.
		WorldTransaction.run("DM event save", DM::writeData);
	}
	
	private static void writeData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("DELETE FROM dm_event");
			statement.execute();
			statement.close();
			
			statement =
					con.prepareStatement("INSERT INTO dm_event (name, description, joining_location_name, min_level, max_level, npc_template_id, npc_x, npc_y, npc_z, reward_item_template_id, reward_count, name_color, player_x, player_y, player_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setString(1, _eventName);
			statement.setString(2, _eventDesc);
			statement.setString(3, _joiningLocationName);
			statement.setInt(4, _minlvl);
			statement.setInt(5, _maxlvl);
			statement.setInt(6, _npcId);
			statement.setInt(7, _npcX);
			statement.setInt(8, _npcY);
			statement.setInt(9, _npcZ);
			statement.setInt(10, _rewardId);
			statement.setInt(11, _rewardAmount);
			statement.setInt(12, _playerColors);
			statement.setInt(13, _playerX);
			statement.setInt(14, _playerY);
			statement.setInt(15, _playerZ);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: DM.saveData(): ", e);
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
			replyMSG.append("DM Match<br><br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("	... name:&nbsp;<font color=\"00FF00\">" + _eventName + "</font><br1>");
			replyMSG.append("	... description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br><br>");
			
			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (!_started && _joining && eventPlayer.getLevel() >= _minlvl && eventPlayer.getLevel() < _maxlvl)
			{
				if (_players.contains(eventPlayer))
				{
					replyMSG.append("You are already participating!<br><br>");
					
					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"200\">Wait till event start or</td>");
					replyMSG.append("<td width=\"60\"><center><button value=\"remove\" action=\"bypass -h npc_"
							+ objectId
							+ "_dmevent_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"100\">your participation!</td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					replyMSG.append("<td width=\"200\">Admin set min lvl : <font color=\"00FF00\">" + _minlvl
							+ "</font></td><br>");
					replyMSG.append("<td width=\"200\">Admin set max lvl : <font color=\"00FF00\">" + _maxlvl
							+ "</font></td><br><br>");
					
					replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
							+ objectId
							+ "_dmevent_player_join\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
					
				}
			}
			else if (_started && !_joining)
				replyMSG.append("<center>DM match is in progress.</center>");
			else if (eventPlayer.getLevel() < _minlvl || eventPlayer.getLevel() > _maxlvl)
			{
				replyMSG.append("Your lvl : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
				replyMSG.append("Admin set min lvl : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Admin set max lvl : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participate to this event.</font><br>");
			}
			
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);
			
			// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			_log.error("DM Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception", e);
		}
	}
	
	public static void addPlayer(L2Player player)
	{
		if (!addPlayerOk(player))
			return;
		_players.add(player);
		final DMPlayerInfo info = new DMPlayerInfo(player);
		player.setPlayerInfo(info);
		info._originalKarmaDM = player.getKarma();
		info._countDMkills = 0;
		_savePlayers.add(player.getName());
		
	}
	
	public static boolean addPlayerOk(L2Player eventPlayer)
	{
		if (GlobalRestrictions.isRestricted(eventPlayer, DMRestriction.class))
		{
			// TODO: msg
			return false;
		}
		
		if (eventPlayer.isInEvent(DMPlayerInfo.class))
		{
			eventPlayer.sendMessage("You are already participating in the event!");
			return false;
		}
		
		return true;
	}
	
	public static synchronized void addDisconnectedPlayer(L2Player player)
	{
		if ((_teleport || _started) || _savePlayers.contains(player.getName()))
		{
			if (Config.DM_ON_START_REMOVE_ALL_EFFECTS)
			{
				player.stopAllEffects();
			}
			for (L2Player p : _players)
			{
				if (p == null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					final DMPlayerInfo info = new DMPlayerInfo(player);
					player.setPlayerInfo(info);
					info._originalKarmaDM = player.getKarma();
					info._countDMkills = p.as(DMPlayerInfo.class)._countDMkills;
					_players.remove(p); //removing old object id from list
					_players.add(player); //adding new objectId to list
					break;
				}
			}
			
			player.as(DMPlayerInfo.class)._nameColorDM = _playerColors;
			player.setKarma(0);
			player.broadcastUserInfo();
			player.teleToLocation(_playerX, _playerY, _playerZ, false);
		}
	}
	
	public static void removePlayer(L2Player player)
	{
		if (player != null)
		{
			_players.remove(player);
		}
	}
	
	public static void cleanDM()
	{
		for (L2Player player : _players)
		{
			removePlayer(player);
		}
		
		_savePlayers = new CopyOnWriteArrayList<String>();
		_topPlayer = null;
		_npcSpawn = null;
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_topKills = 0;
		_players = new CopyOnWriteArrayList<L2Player>();
		
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
		Announcements.getInstance().announceToAll(
				_eventName + "(DM): Teleport back to participation NPC in 20 seconds!");
		
		removeUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				for (L2Player player : _players)
				{
					if (player != null && player.isOnline() != 0)
						player.teleToLocation(_npcX, _npcY, _npcZ, false);
				}
				cleanDM();
			}
		}, 20000);
	}
}
