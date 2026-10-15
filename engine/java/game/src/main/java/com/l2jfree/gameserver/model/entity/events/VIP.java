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
 * @author  CubicVirtuoso - William McMahon
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
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.restriction.global.VIPRestriction;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.tools.random.Rnd;

public class VIP
{
	static
	{
		VIPRestriction.getInstance().activate();
	}
	
	public static final class VIPPlayerInfo extends AbstractFunEventPlayerInfo
	{
		/** VIP parameters */
		public boolean _isVIP;
		public boolean _isNotVIP;
		public boolean _isTheVIP;
		public int _nameColourVIP = -1;
		public int _originalKarmaVIP;
		
		private VIPPlayerInfo(L2Player player)
		{
			super(player);
		}
		
		@Override
		public boolean isInFunEvent()
		{
			return VIP._started;
		}
	}
	
	private final static Logger _log = LoggerFactory.getLogger(VIP.class);
	public static String _teamName = "", _joinArea = "", _theVIPName = "";
	
	public static int _time = 0, _winners = 0, _minPlayers = Config.VIP_MIN_PARTICIPANTS, _vipReward = 0,
			_vipRewardAmount = 0, _notVipReward = 0, _notVipRewardAmount = 0, _theVipReward = 0,
			_theVipRewardAmount = 0, _endNPC = 0, _joinNPC = 0, _delay = 0, _endX = 0, _endY = 0, _endZ = 0,
			_startX = 0, _startY = 0, _startZ = 0, _joinX = 0, _joinY = 0, _joinZ = 0, _team = 0; // Human = 1
																									// Elf = 2
																									// Dark = 3
																									// Orc = 4
																									// Dwarf = 5
			
	public static boolean _started = false, _joining = false, _sitForced = false;
	
	public static L2Spawn _endSpawn, _joinSpawn;
	public static CopyOnWriteArrayList<String> _savePlayers = new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<L2Player> _playersVIP = new CopyOnWriteArrayList<L2Player>(),
			_playersNotVIP = new CopyOnWriteArrayList<L2Player>();
	
	public static void setTeam(String team, L2Player activeChar)
	{
		if (team.compareToIgnoreCase("Human") == 0)
		{
			_team = 1;
			_teamName = "Human";
		}
		else if (team.compareToIgnoreCase("Elf") == 0)
		{
			_team = 2;
			_teamName = "Elf";
		}
		else if (team.compareToIgnoreCase("Dark") == 0)
		{
			_team = 3;
			_teamName = "Dark Elf";
		}
		else if (team.compareToIgnoreCase("Orc") == 0)
		{
			_team = 4;
			_teamName = "Orc";
		}
		else if (team.compareToIgnoreCase("Dwarf") == 0)
		{
			_team = 5;
			_teamName = "Dwarf";
		}
		else
		{
			activeChar.sendMessage("Invalid Team Name: //vip_setteam <human/elf/dark/orc/dwarf>");
			return;
		}
		setLoc();
	}
	
	public static void setTeam(String team)
	{
		if (team.compareToIgnoreCase("Human") == 0)
		{
			_team = 1;
			_teamName = "Human";
		}
		else if (team.compareToIgnoreCase("Elf") == 0)
		{
			_team = 2;
			_teamName = "Elf";
		}
		else if (team.compareToIgnoreCase("Dark") == 0)
		{
			_team = 3;
			_teamName = "Dark Elf";
		}
		else if (team.compareToIgnoreCase("Orc") == 0)
		{
			_team = 4;
			_teamName = "Orc";
		}
		else if (team.compareToIgnoreCase("Dwarf") == 0)
		{
			_team = 5;
			_teamName = "Dwarf";
		}
		setLoc();
	}
	
	/**
	 * @param activeChar
	 */
	public static void setRandomTeam(L2Player activeChar)
	{
		int random = Rnd.nextInt(5) + 1; // (0 - 4) + 1
		
		if (_log.isDebugEnabled())
			_log.debug("Random number generated in setRandomTeam(): " + random);
		
		switch (random)
		{
			case 1:
				_team = 1;
				_teamName = "Human";
				setLoc();
				break;
			case 2:
				_team = 2;
				_teamName = "Elf";
				setLoc();
				break;
			case 3:
				_team = 3;
				_teamName = "Dark";
				setLoc();
				break;
			case 4:
				_team = 4;
				_teamName = "Orc";
				setLoc();
				break;
			case 5:
				_team = 5;
				_teamName = "Dwarf";
				setLoc();
				break;
			default:
				break;
		}
	}
	
	public static void setAutoRandomTeam()
	{
		int random = Rnd.nextInt(5) + 1; // (0 - 4) + 1
		
		if (_log.isDebugEnabled())
			_log.debug("Random number generated in setRandomTeam(): " + random);
		
		switch (random)
		{
			case 1:
				_team = 1;
				_teamName = "Human";
				setLoc();
				break;
			case 2:
				_team = 2;
				_teamName = "Elf";
				setLoc();
				break;
			case 3:
				_team = 3;
				_teamName = "Dark";
				setLoc();
				break;
			case 4:
				_team = 4;
				_teamName = "Orc";
				setLoc();
				break;
			case 5:
				_team = 5;
				_teamName = "Dwarf";
				setLoc();
				break;
			default:
				break;
		}
	}
	
	public static void setLoc()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT end_x, end_y, end_z, start_x, start_y, start_z FROM vip_event_route "
							+ "WHERE race_id = ?");
			// VIP team 1 (Human) to 5 (Dwarf) is race id 0 to 4.
			statement.setInt(1, _team - 1);
			ResultSet rset = statement.executeQuery();
			rset.next();
			
			_endX = rset.getInt("end_x");
			_endY = rset.getInt("end_y");
			_endZ = rset.getInt("end_z");
			_startX = rset.getInt("start_x");
			_startY = rset.getInt("start_y");
			_startZ = rset.getInt("start_z");
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Could not check End & Start LOC for team" + _team + " got: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static void endNPC(int npcId, L2Player activeChar)
	{
		if (_team == 0)
		{
			activeChar.sendMessage("Please select a team first");
			return;
		}
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(npcId);
		_endNPC = npcId;
		
		try
		{
			_endSpawn = new L2Spawn(npctmp);
			_endSpawn.setLocx(_endX);
			_endSpawn.setLocy(_endY);
			_endSpawn.setLocz(_endZ);
			_endSpawn.setAmount(1);
			_endSpawn.setHeading(activeChar.getHeading());
			_endSpawn.setRespawnDelay(1);
		}
		catch (Exception e)
		{
			activeChar.sendMessage("VIP Engine[endNPC(" + activeChar.getName() + ")]: exception: " + e.getMessage());
		}
	}
	
	public static void endNPC(int npcId)
	{
		if (_team == 0)
		{
			return;
		}
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(npcId);
		_endNPC = npcId;
		
		try
		{
			_endSpawn = new L2Spawn(npctmp);
			_endSpawn.setLocx(_endX);
			_endSpawn.setLocy(_endY);
			_endSpawn.setLocz(_endZ);
			_endSpawn.setAmount(1);
			_endSpawn.setHeading(0);
			_endSpawn.setRespawnDelay(1);
		}
		catch (Exception e)
		{
			_log.error("VIP Engine[endNPC]: exception: ", e);
		}
	}
	
	public static void joinNPC(int npcId, L2Player activeChar)
	{
		if (_joinX == 0)
		{
			activeChar.sendMessage("Please set a join x,y,z first");
			return;
		}
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(npcId);
		_joinNPC = npcId;
		
		try
		{
			_joinSpawn = new L2Spawn(npctmp);
			_joinSpawn.setLocx(_joinX);
			_joinSpawn.setLocy(_joinY);
			_joinSpawn.setLocz(_joinZ);
			_joinSpawn.setAmount(1);
			_joinSpawn.setHeading(activeChar.getHeading());
			_joinSpawn.setRespawnDelay(1);
		}
		catch (Exception e)
		{
			activeChar.sendMessage("VIP Engine[joinNPC(" + activeChar.getName() + ")]: exception: " + e.getMessage());
		}
	}
	
	public static void joinNPC(int npcId)
	{
		if (_joinX == 0)
		{
			return;
		}
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(npcId);
		_joinNPC = npcId;
		
		try
		{
			_joinSpawn = new L2Spawn(npctmp);
			_joinSpawn.setLocx(_joinX);
			_joinSpawn.setLocy(_joinY);
			_joinSpawn.setLocz(_joinZ);
			_joinSpawn.setAmount(1);
			_joinSpawn.setHeading(0);
			_joinSpawn.setRespawnDelay(1);
		}
		catch (Exception e)
		{
			_log.error("VIP Engine[joinNPC]: exception: ", e);
		}
	}
	
	public static void spawnEndNPC()
	{
		try
		{
			SpawnTable.getInstance().addNewSpawn(_endSpawn, false);
			
			_endSpawn.init();
			_endSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_endSpawn.getLastSpawn().setTitle("VIP Npc");
			_endSpawn.getLastSpawn()._isEventVIPNPCEnd = true;
			_endSpawn.getLastSpawn().isAggressive();
			_endSpawn.getLastSpawn().decayMe();
			_endSpawn.getLastSpawn().spawnMe(_endSpawn.getLastSpawn().getX(), _endSpawn.getLastSpawn().getY(),
					_endSpawn.getLastSpawn().getZ());
			
			_endSpawn.getLastSpawn().broadcastPacket(
					new MagicSkillUse(_endSpawn.getLastSpawn(), _endSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.error("VIP Engine[spawnEndNPC()]: exception: ", e);
		}
	}
	
	public static void spawnJoinNPC()
	{
		try
		{
			SpawnTable.getInstance().addNewSpawn(_joinSpawn, false);
			
			_joinSpawn.init();
			_joinSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_joinSpawn.getLastSpawn().setTitle("VIP Npc");
			_joinSpawn.getLastSpawn()._isEventVIPNPC = true;
			_joinSpawn.getLastSpawn().isAggressive();
			_joinSpawn.getLastSpawn().decayMe();
			_joinSpawn.getLastSpawn().spawnMe(_joinSpawn.getLastSpawn().getX(), _joinSpawn.getLastSpawn().getY(),
					_joinSpawn.getLastSpawn().getZ());
			
			_joinSpawn.getLastSpawn().broadcastPacket(
					new MagicSkillUse(_joinSpawn.getLastSpawn(), _joinSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.error("VIP Engine[spawnJoinNPC()]: exception: ", e);
		}
	}
	
	/**
	 * @param id
	 * @param activeChar
	 */
	public static String getNPCName(int id, L2Player activeChar)
	{
		if (id == 0)
			return "";
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(id);
		if (npctmp == null)
		{
			activeChar.sendMessage("VIP Engine[joinNPC(" + activeChar.getName() + ")]: exception: wrong NPC Id");
			return "";
		}
		
		return npctmp.getName();
	}
	
	public static String getNPCName(int id)
	{
		if (id == 0)
			return "";
		
		L2NpcTemplate npctmp = NpcTable.getInstance().getTemplate(id);
		if (npctmp == null)
			return "";
		
		return npctmp.getName();
	}
	
	/**
	 * @param id
	 * @param activeChar
	 */
	public static String getItemName(int id, L2Player activeChar)
	{
		if (id == 0)
			return "";
		
		L2Item itemtmp = ItemTable.getInstance().getTemplate(id);
		if (itemtmp == null)
		{
			activeChar.sendMessage("VIP Engine[joinNPC(" + activeChar.getName() + ")]: exception: wrong item Id");
			return "";
		}
		
		return itemtmp.getName();
	}
	
	public static String getItemName(int id)
	{
		if (id == 0)
			return "";
		
		L2Item itemtmp = ItemTable.getInstance().getTemplate(id);
		if (itemtmp == null)
			return "";
		
		return itemtmp.getName();
	}
	
	public static void setJoinLOC(String x, String y, String z)
	{
		_joinX = Integer.valueOf(x);
		_joinY = Integer.valueOf(y);
		_joinZ = Integer.valueOf(z);
	}
	
	public static void startJoin(L2Player activeChar)
	{
		if (_teamName.isEmpty() || _joinArea.isEmpty() || _time == 0 || _vipReward == 0 || _vipRewardAmount == 0
				|| _notVipReward == 0 || _notVipRewardAmount == 0 || _theVipReward == 0 || _theVipRewardAmount == 0
				|| _endNPC == 0 || _joinNPC == 0 || _delay == 0 || _endX == 0 || _endY == 0 || _endZ == 0
				|| _startX == 0 || _startY == 0 || _startZ == 0 || _joinX == 0 || _joinY == 0 || _joinZ == 0
				|| _team == 0)
		{
			activeChar.sendMessage("Cannot initiate join status of event, not all the values are filled in");
			return;
		}
		
		if (_joining)
		{
			activeChar.sendMessage("Players are already allowed to join the event");
			return;
		}
		
		if (_started)
		{
			activeChar.sendMessage("Event already started. Please wait for it to finish or finish it manually");
			return;
		}
		
		_joining = true;
		Announcements.getInstance().announceToAll("Attention all players. An event is about to start!");
		Announcements.getInstance().announceToAll(
				"At this time you are able to join a VIP event which will start in " + _delay / 1000 / 60 + " mins.");
		Announcements.getInstance().announceToAll(
				"In this event the " + _teamName
						+ " characters must safely escort a certain player from one location to their starter town");
		Announcements.getInstance().announceToAll("Players will automatically be assigned to their respective teams");
		Announcements.getInstance().announceToAll(
				"Please find " + getNPCName(VIP._joinNPC, activeChar) + " located in " + _joinArea + " to sign up.");
		
		spawnJoinNPC();
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				_joining = false;
				_started = true;
				startEvent();
			}
		}, _delay);
	}
	
	public static void startAutoJoin()
	{
		if (_teamName.isEmpty() || _joinArea.isEmpty() || _time == 0 || _vipReward == 0 || _vipRewardAmount == 0
				|| _notVipReward == 0 || _notVipRewardAmount == 0 || _theVipReward == 0 || _theVipRewardAmount == 0
				|| _endNPC == 0 || _joinNPC == 0 || _delay == 0 || _endX == 0 || _endY == 0 || _endZ == 0
				|| _startX == 0 || _startY == 0 || _startZ == 0 || _joinX == 0 || _joinY == 0 || _joinZ == 0
				|| _team == 0)
		{
			_log.error("VIP Engine : Cannot initiate join status of event, not all the values are filled in");
			return;
		}
		
		if (_joining)
		{
			_log.error("VIP Engine : Players are already allowed to join the event");
			return;
		}
		
		if (_started)
		{
			_log.error("VIP Engine : Event already started. Please wait for it to finish or finish it manually");
			return;
		}
		
		_joining = true;
		Announcements.getInstance().announceToAll("Attention all players. An event is about to start!");
		Announcements.getInstance().announceToAll(
				"At this time you are able to join a VIP event which will start in " + _delay / 1000 / 60 + " mins.");
		Announcements.getInstance().announceToAll(
				"In this event the " + _teamName
						+ " characters must safely escort a certain player from one location to their starter town");
		Announcements.getInstance().announceToAll("Players will automatically be assigned to their respective teams");
		Announcements.getInstance().announceToAll(
				"Please find " + getNPCName(VIP._joinNPC) + " located in " + _joinArea + " to sign up.");
		
		spawnJoinNPC();
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				_joining = false;
				_started = true;
				startEvent();
			}
		}, _delay);
	}
	
	public static void startEvent()
	{
		if ((_playersVIP.size() + _playersNotVIP.size()) < _minPlayers)
		{
			Announcements.getInstance().announceToAll(
					"Registration for the VIP event involving " + _teamName + " has ended.");
			Announcements.getInstance().announceToAll(
					"Event aborted due not enought players : min players requested for event " + _minPlayers);
			_started = false;
			spawnEndNPC();
			unspawnEventNpcs();
			VIP.clean();
		}
		else
		{
			Announcements.getInstance().announceToAll(
					"Registration for the VIP event involving " + _teamName + " has ended.");
			Announcements.getInstance().announceToAll("Players will be teleported to their locations in 20 seconds.");
			
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					teleportPlayers();
					chooseVIP();
					setUserData();
					Announcements.getInstance().announceToAll("Players have been teleported for the VIP event.");
					Announcements.getInstance().announceToAll("VIP event will start in 20 seconds.");
					spawnEndNPC();
					
					ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
						@Override
						public void run()
						{
							Announcements
									.getInstance()
									.announceToAll(
											"VIP event has started. "
													+ _teamName
													+ "'s VIP must get to the starter city and talk with "
													+ getNPCName(_endNPC, null)
													+ ". The opposing team must kill the VIP. All players except the VIP will respawn at their current locations.");
							Announcements.getInstance().announceToAll(
									"VIP event will end if the " + _teamName + " team makes it to their town or when "
											+ _time / 1000 / 60 + " mins have elapsed.");
							VIP.sit();
							
							ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
								@Override
								public void run()
								{
									endEventTime();
								}
							}, _time);
						}
					}, 20000);
					
				}
			}, 20000);
		}
	}
	
	public static void vipDied()
	{
		if (!_started)
		{
			_log.info("Could not finish the event. Event not started or event ended prematurly.");
			return;
		}
		
		_started = false;
		unspawnEventNpcs();
		Announcements.getInstance().announceToAll("The VIP has died. The opposing team has won.");
		rewardNotVIP();
		teleportFinish();
	}
	
	public static void endEventTime()
	{
		if (!_started)
		{
			_log.info("Could not finish the event. Event not started or event ended prematurly (VIP died)");
			return;
		}
		
		_started = false;
		unspawnEventNpcs();
		Announcements.getInstance().announceToAll(
				"The time has run out and the " + _teamName
						+ "'s have not made it to their goal. Everybody on the opposing team wins.");
		rewardNotVIP();
		teleportFinish();
	}
	
	public static void unspawnEventNpcs()
	{
		if (_endSpawn != null)
		{
			_endSpawn.getLastSpawn().deleteMe();
			_endSpawn.stopRespawn();
			SpawnTable.getInstance().deleteSpawn(_endSpawn, true);
		}
		
		if (_joinSpawn != null)
		{
			_joinSpawn.getLastSpawn().deleteMe();
			_joinSpawn.stopRespawn();
			SpawnTable.getInstance().deleteSpawn(_joinSpawn, true);
		}
	}
	
	public static void showEndHTML(L2Player eventPlayer, String objectId)
	{
		try
		{
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			replyMSG.append("VIP (End NPC)<br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("    ... Team:&nbsp;<font color=\"FFFFFF\">" + _teamName + "</font><br><br>");
			
			if (!_started)
				replyMSG.append("<center>Please wait until the admin/gm starts the joining period.</center>");
			else if (eventPlayer.isInEvent(VIPPlayerInfo.class) && eventPlayer.as(VIPPlayerInfo.class)._isTheVIP)
			{
				replyMSG.append("You have made it to the end. All you have to do is hit the finish button to reward yourself and your team. Congrats!<br>");
				replyMSG.append("<center>");
				replyMSG.append("<button value=\"Finish\" action=\"bypass -h npc_"
						+ objectId
						+ "_vip_finishVIP\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
				replyMSG.append("</center>");
			}
			else
			{
				replyMSG.append("I am the character the VIP has to reach in order to win the event.<br>");
			}
			
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);
			
			// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			if (_log.isDebugEnabled())
				_log.debug("VIP(showJoinHTML(" + eventPlayer.getName() + ", " + objectId + ")]: exception", e);
		}
	}
	
	/**
	 * @param activeChar
	 */
	public static void vipWin(L2Player activeChar)
	{
		if (!_started)
		{
			_log.info("Could not finish the event. Event not started or event ended prematurly");
			return;
		}
		
		_started = false;
		unspawnEventNpcs();
		Announcements.getInstance().announceToAll(
				"The VIP has made it to the goal. " + _teamName + " has won. Everybody on that team wins.");
		rewardVIP();
		teleportFinish();
	}
	
	public static void rewardNotVIP()
	{
		for (L2Player player : _playersNotVIP)
		{
			if (player != null)
			{
				player.addItem("VIP Event: ", _notVipReward, _notVipRewardAmount, player, true, true);
				
				NpcHtmlMessage nhm = new NpcHtmlMessage(5);
				StringBuilder replyMSG = new StringBuilder("");
				
				replyMSG.append("<html><body>Your team won the event. Your inventory now contains your reward.</body></html>");
				
				nhm.setHtml(replyMSG.toString());
				player.sendPacket(nhm);
				
				// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	public static void rewardVIP()
	{
		for (L2Player player : _playersVIP)
		{
			if (player == null)
				continue;
			
			if (!player.as(VIPPlayerInfo.class)._isTheVIP)
			{
				player.addItem("VIP Event: ", _vipReward, _vipRewardAmount, player, true, true);
				
				NpcHtmlMessage nhm = new NpcHtmlMessage(5);
				StringBuilder replyMSG = new StringBuilder("");
				
				replyMSG.append("<html><body>Your team has won the event. Your inventory now contains your reward.</body></html>");
				
				nhm.setHtml(replyMSG.toString());
				player.sendPacket(nhm);
			}
			else if (player.as(VIPPlayerInfo.class)._isTheVIP)
			{
				player.addItem("VIP Event: ", _theVipReward, _theVipRewardAmount, player, true, true);
				
				NpcHtmlMessage nhm = new NpcHtmlMessage(5);
				StringBuilder replyMSG = new StringBuilder("");
				
				replyMSG.append("<html><body>Your team has won the event. Your inventory now contains your reward.</body></html>");
				
				nhm.setHtml(replyMSG.toString());
				player.sendPacket(nhm);
				
				// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	public static void teleportFinish()
	{
		Announcements.getInstance().announceToAll(
				"Teleporting VIP players back to the Registration area in 20 seconds.");
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				for (L2Player player : _playersVIP)
				{
					if (player != null)
						player.teleToLocation(_joinX, _joinY, _joinZ);
				}
				
				for (L2Player player : _playersNotVIP)
				{
					if (player != null)
						player.teleToLocation(_joinX, _joinY, _joinZ);
				}
				
				VIP.clean();
			}
		}, 20000);
	}
	
	public static void clean()
	{
		_time =
				_winners =
						_endNPC =
								_joinNPC =
										_delay =
												_endX =
														_endY =
																_endZ =
																		_startX =
																				_startY =
																						_startZ =
																								_joinX =
																										_joinY =
																												_joinZ =
																														_team =
																																0;
		_vipReward = _vipRewardAmount = _notVipReward = _notVipRewardAmount = _theVipReward = _theVipRewardAmount = 0;
		_started = _joining = _sitForced = false;
		_teamName = _joinArea = _theVIPName = "";
		
		for (L2Player player : _playersVIP)
		{
			final VIPPlayerInfo info = player.as(VIPPlayerInfo.class);
			info._nameColourVIP = -1;
			player.setKarma(info._originalKarmaVIP);
			player.broadcastUserInfo();
			player.setPlayerInfo(null);
			info._isTheVIP = false;
			info._isNotVIP = false;
			info._isVIP = false;
		}
		
		for (L2Player player : _playersNotVIP)
		{
			final VIPPlayerInfo info = player.as(VIPPlayerInfo.class);
			info._nameColourVIP = -1;
			player.setKarma(info._originalKarmaVIP);
			player.broadcastUserInfo();
			player.setPlayerInfo(null);
			info._isTheVIP = false;
			info._isNotVIP = false;
			info._isVIP = false;
		}
		_savePlayers = new CopyOnWriteArrayList<String>();
		_playersVIP = new CopyOnWriteArrayList<L2Player>();
		_playersNotVIP = new CopyOnWriteArrayList<L2Player>();
	}
	
	public static void chooseVIP()
	{
		int size = _playersVIP.size();
		
		if (_log.isDebugEnabled())
			_log.debug("Size of players on VIP: " + size);
		
		int random = Rnd.nextInt(size);
		
		if (_log.isDebugEnabled())
			_log.debug("Random number chosen in VIP: " + random);
		
		L2Player VIP = _playersVIP.get(random);
		VIP.as(VIPPlayerInfo.class)._isTheVIP = true;
		_theVIPName = VIP.getName();
	}
	
	public static void teleportPlayers()
	{
		VIP.sit();
		
		for (L2Player player : _playersVIP)
		{
			if (player != null)
				player.teleToLocation(_startX, _startY, _startZ);
		}
		for (L2Player player : _playersNotVIP)
		{
			if (player != null)
				player.teleToLocation(_endX, _endY, _endZ);
		}
	}
	
	public static void sit()
	{
		_sitForced = !_sitForced;
		
		for (L2Player player : _playersVIP)
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
		
		for (L2Player player : _playersNotVIP)
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
	
	public static void setUserData()
	{
		for (L2Player player : _playersVIP)
		{
			final VIPPlayerInfo info = player.as(VIPPlayerInfo.class);
			
			if (info._isTheVIP)
				info._nameColourVIP = 0x00ffff;
			else
				info._nameColourVIP = 0x0000ff;
			
			player.setKarma(0);
			player.broadcastUserInfo();
			if (Config.VIP_ON_START_REMOVE_ALL_EFFECTS)
			{
				player.stopAllEffects();
			}
		}
		for (L2Player player : _playersNotVIP)
		{
			player.as(VIPPlayerInfo.class)._nameColourVIP = 0x00ff00;
			player.setKarma(0);
			player.broadcastUserInfo();
			if (Config.VIP_ON_START_REMOVE_ALL_EFFECTS)
			{
				player.stopAllEffects();
			}
		}
	}
	
	public static void showJoinHTML(L2Player eventPlayer, String objectId)
	{
		try
		{
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			replyMSG.append("VIP (Join NPC)<br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("    ... Team:&nbsp;<font color=\"FFFFFF\">" + _teamName + "</font><br><br>");
			
			if (!_joining && !_started) // PreEvent
				replyMSG.append("<center>Please wait until the admin/gm starts the joining period.</center>");
			else if (_joining && !_started) // Joining period
			{
				if (_playersVIP.contains(eventPlayer) || _playersNotVIP.contains(eventPlayer))
				{
					replyMSG.append("You are already on a team<br><br>");
				}
				else if (eventPlayer.getLevel() < Config.VIP_MIN_LEVEL || eventPlayer.getLevel() > Config.VIP_MAX_LEVEL)
				{
					replyMSG.append("Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
					replyMSG.append("Min level : <font color=\"00FF00\">" + Config.VIP_MIN_LEVEL + "</font><br>");
					replyMSG.append("Max level : <font color=\"00FF00\">" + Config.VIP_MAX_LEVEL + "</font><br><br>");
					replyMSG.append("<font color=\"FFFF00\">You can't participate in this event.</font><br>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					if (eventPlayer.getRace() == Race.Human && _team == 1)
					{
						replyMSG.append("It seems you are on the VIP race! Be prepared to protect the VIP when it is decided<br1>");
						replyMSG.append("The VIP will be decided on when the event starts. It's completely random.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
					else if (eventPlayer.getRace() == Race.Elf && _team == 2)
					{
						replyMSG.append("It seems you are on the VIP race! Be prepared to protect the VIP when it is decided<br1>");
						replyMSG.append("The VIP will be decided on when the event starts. It's completely random.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
					else if (eventPlayer.getRace() == Race.Darkelf && _team == 3)
					{
						replyMSG.append("It seems you are on the VIP race! Be prepared to protect the VIP when it is decided<br1>");
						replyMSG.append("The VIP will be decided on when the event starts. It's completely random.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
					else if (eventPlayer.getRace() == Race.Orc && _team == 4)
					{
						replyMSG.append("It seems you are on the VIP race! Be prepared to protect the VIP when it is decided<br1>");
						replyMSG.append("The VIP will be decided on when the event starts. It's completely random.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
					else if (eventPlayer.getRace() == Race.Dwarf && _team == 5)
					{
						replyMSG.append("It seems you are on the VIP race! Be prepared to protect the VIP when it is decided<br1>");
						replyMSG.append("The VIP will be decided on when the event starts. It's completely random.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
					else
					{
						replyMSG.append("It seems you are not on the part of the VIP race.<br>");
						replyMSG.append("When the event starts you will be teleported to the " + _teamName
								+ " town<br1>");
						replyMSG.append("Be sure to cooperate with your team to destroy the VIP.<br1>");
						replyMSG.append("The VIP will be announced when the event starts.<br>");
						replyMSG.append("<center>");
						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_"
								+ objectId
								+ "_vip_joinNotVIPTeam\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("</center>");
					}
				}
			}
			else if (_started) // Event already Started
				replyMSG.append("<center>The event is already taking place. Please sign up for the next event.</center>");
			
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);
			
			// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			if (_log.isDebugEnabled())
				_log.debug("VIP(showJoinHTML(" + eventPlayer.getName() + ", " + objectId + ")]: exception", e);
		}
	}
	
	public static void addPlayerVIP(L2Player activeChar)
	{
		if (GlobalRestrictions.isRestricted(activeChar, VIPRestriction.class))
		{
			// TODO: msg
			return;
		}
		
		if (activeChar.isInEvent(VIPPlayerInfo.class))
		{
			activeChar.sendMessage("You are already participating in the event!");
			return;
		}
		
		final VIPPlayerInfo info = new VIPPlayerInfo(activeChar);
		activeChar.setPlayerInfo(info);
		info._isVIP = true;
		_playersVIP.add(activeChar);
		info._originalKarmaVIP = activeChar.getKarma();
		_savePlayers.add(activeChar.getName());
	}
	
	public static void addPlayerNotVIP(L2Player activeChar)
	{
		if (GlobalRestrictions.isRestricted(activeChar, VIPRestriction.class))
		{
			// TODO: msg
			return;
		}
		
		if (activeChar.isInEvent(VIPPlayerInfo.class))
		{
			activeChar.sendMessage("You are already participating in the event!");
			return;
		}
		
		final VIPPlayerInfo info = new VIPPlayerInfo(activeChar);
		activeChar.setPlayerInfo(info);
		info._isNotVIP = true;
		_playersNotVIP.add(activeChar);
		info._originalKarmaVIP = activeChar.getKarma();
		_savePlayers.add(activeChar.getName());
	}
	
	public static synchronized void addDisconnectedPlayer(L2Player player)
	{
		if (_started)
		{
			if (_savePlayers.contains(player.getName()))
			{
				if (Config.VIP_ON_START_REMOVE_ALL_EFFECTS)
				{
					player.stopAllEffects();
				}
				
				for (L2Player p : _playersVIP)
				{
					if (p == null)
						continue;
					//check by name incase player got new objectId
					else if (p.getName().equals(player.getName()))
					{
						final VIPPlayerInfo info = new VIPPlayerInfo(player);
						player.setPlayerInfo(info);
						info._isVIP = true;
						info._originalKarmaVIP = player.getKarma();
						_playersVIP.remove(p); //removing old object id from list
						_playersVIP.add(player); //adding new objectId to list
						if (_theVIPName.equals(player.getName()))
						{
							info._nameColourVIP = 0x00ffff;
							info._isTheVIP = true;
						}
						else
							info._nameColourVIP = 0x0000ff;
						player.setKarma(0);
						player.broadcastUserInfo();
						player.teleToLocation(_startX, _startY, _startZ);
						return;
					}
				}
				
				for (L2Player p : _playersNotVIP)
				{
					if (p == null)
						continue;
					//check by name incase player got new objectId
					else if (p.getName().equals(player.getName()))
					{
						final VIPPlayerInfo info = new VIPPlayerInfo(player);
						player.setPlayerInfo(info);
						info._isNotVIP = true;
						info._originalKarmaVIP = player.getKarma();
						_playersNotVIP.remove(p); //removing old object id from list
						_playersNotVIP.add(player); //adding new objectId to list
						info._nameColourVIP = 0x00ff00;
						player.setKarma(0);
						player.broadcastUserInfo();
						player.teleToLocation(_endX, _endY, _endZ);
						return;
					}
				}
			}
		}
	}
}
