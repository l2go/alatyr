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
package com.l2jfree.gameserver.handler.admincommands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MinionInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2RaidBossInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.persistence.AdminBookmarkStore;
import com.l2jfree.gameserver.persistence.AdminBookmarkStore.Bookmark;

/**
 * This class handles following admin commands:
 * - show_moves
 * - show_teleport
 * - teleport_to_character
 * - move_to
 * - teleport_character
 * 
 * @version $Revision: 1.3.2.6.2.4 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminTeleport implements IAdminCommandHandler
{
	private static final Logger _log = LoggerFactory.getLogger(AdminTeleport.class);
	
	private static final String[] ADMIN_COMMANDS = {
			"admin_bookmark", // L2JP_JP ADD
			"admin_show_moves", "admin_show_moves_other", "admin_show_teleport", "admin_teleport_to_character",
			"admin_teleportto", "admin_move_to", "admin_delbookmark", "admin_teleport_character",
			"admin_recall",
			"admin_recall_gm", //[L2J_JP ADD - TSL]
			"admin_recall_all", //[L2J_JP ADD - TSL]
			"admin_recall_offline", "admin_walk", "admin_recall_npc", "admin_go", "admin_tele", "admin_teleto",
			"admin_instant_move" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_bookmark"))// L2J_JP ADD
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			if (st.hasMoreTokens())
				bookmark(activeChar, st.nextToken());
			else
				bookmark(activeChar, null); // Show bookmarks
		}
		else if (command.startsWith("admin_delbookmark"))// L2J_JP ADD
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			if (st.hasMoreTokens())
				delbookmark(st.nextToken());
			bookmark(activeChar, null); // Show bookmarks
		}
		else if (command.equals("admin_teleto") || command.equals("admin_instant_move"))
		{
			activeChar.setTeleMode(1);
		}
		else if (command.startsWith("admin_recall_offline "))
		{
			try
			{
				String[] param = command.split(" ");
				if (param.length < 2)
				{
					activeChar.sendMessage("Wrong usage: //recall_offline <player name>");
					return false;
				}
				changeCharacterPosition(activeChar, param[1]);
			}
			catch (Exception e)
			{
			}
		}
		else if (command.equals("admin_teleto r"))
		{
			activeChar.setTeleMode(2);
		}
		else if (command.equals("admin_teleto end"))
		{
			activeChar.setTeleMode(0);
		}
		else if (command.equals("admin_show_moves"))
		{
			AdminHelpPage.showHelpPage(activeChar, "teleports.htm");
		}
		else if (command.equals("admin_show_moves_other"))
		{
			AdminHelpPage.showHelpPage(activeChar, "tele/other.html");
		}
		else if (command.equals("admin_show_teleport"))
		{
			showTeleportCharWindow(activeChar);
		}
		else if (command.equals("admin_recall_npc"))
		{
			recallNPC(activeChar);
		}
		else if (command.equals("admin_teleport_to_character"))
		{
			teleportToCharacter(activeChar, activeChar.getTarget());
		}
		else if (command.startsWith("admin_walk"))
		{
			try
			{
				String val = command.substring(11);
				StringTokenizer st = new StringTokenizer(val);
				String x1 = st.nextToken();
				int x = Integer.parseInt(x1);
				String y1 = st.nextToken();
				int y = Integer.parseInt(y1);
				String z1 = st.nextToken();
				int z = Integer.parseInt(z1);
				L2CharPosition pos = new L2CharPosition(x, y, z, 0);
				activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, pos);
			}
			catch (Exception e)
			{
				if (_log.isDebugEnabled())
					_log.info("admin_walk: ", e);
			}
		}
		else if (command.startsWith("admin_move_to"))
		{
			try
			{
				String val = command.substring(14);
				teleportTo(activeChar, val);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				//Case of empty or missing coordinates
				AdminHelpPage.showHelpPage(activeChar, "teleports.htm");
			}
			catch (NumberFormatException nfe)
			{
				activeChar.sendMessage("Usage: //move_to <x> <y> <z>");
				AdminHelpPage.showHelpPage(activeChar, "teleports.htm");
			}
		}
		else if (command.startsWith("admin_teleport_character"))
		{
			try
			{
				String val = command.substring(25);
				teleportCharacter(activeChar, val);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				//Case of empty coordinates
				activeChar.sendMessage("Wrong or no Coordinates given.");
				showTeleportCharWindow(activeChar); //back to character teleport
			}
		}
		else if (command.startsWith("admin_teleportto "))
		{
			try
			{
				String targetName = command.substring(17);
				L2Player player = L2World.getInstance().getPlayer(targetName);
				teleportToCharacter(activeChar, player);
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		else if (command.startsWith("admin_recall "))
		{
			try
			{
				String targetName = command.substring(13);
				L2Player player = L2World.getInstance().getPlayer(targetName);
				teleportCharacter(player, activeChar.getX(), activeChar.getY(), activeChar.getZ(),
						activeChar.getInstanceId());
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		else if (command.startsWith("admin_recall_gm "))
		{
			try
			{
				for (L2Player player : L2World.getInstance().getAllPlayers())
					if (player.getAccessLevel() > 0)
						teleportCharacter(player, activeChar.getX(), activeChar.getY(), activeChar.getZ(),
								activeChar.getInstanceId());
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		else if (command.equals("admin_recall_all"))
		{
			for (L2Player character : L2World.getInstance().getAllPlayers())
			{
				if (character == activeChar)
					continue;
				teleportCharacter(character, activeChar.getX(), activeChar.getY(), activeChar.getZ(),
						activeChar.getInstanceId());
			}
		}
		// [L2J_JP ADD END - TSL]
		else if (command.equals("admin_tele"))
		{
			showTeleportWindow(activeChar);
		}
		else if (command.startsWith("admin_go"))
		{
			int intVal = 150;
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			try
			{
				String val = command.substring(8);
				StringTokenizer st = new StringTokenizer(val);
				String dir = st.nextToken();
				if (st.hasMoreTokens())
					intVal = Integer.parseInt(st.nextToken());
				if (dir.equals("east"))
					x += intVal;
				else if (dir.equals("west"))
					x -= intVal;
				else if (dir.equals("north"))
					y -= intVal;
				else if (dir.equals("south"))
					y += intVal;
				else if (dir.equals("up"))
					z += intVal;
				else if (dir.equals("down"))
					z -= intVal;
				activeChar.teleToLocation(x, y, z, false);
				showTeleportWindow(activeChar);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //go<north|south|east|west|up|down> [offset] (default 150)");
			}
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void delbookmark(String Name)
	{
		AdminBookmarkStore.delete(Name);
	}
	
	// L2J_JP ADD
	private void bookmark(L2Player activeChar, String Name)
	{
		if (Name != null && !AdminBookmarkStore.save(new Bookmark(Name, activeChar.getX(), activeChar.getY(), activeChar.getZ())))
			activeChar.sendMessage("The bookmark was not saved.");
		
		StringBuilder table = new StringBuilder();
		for (Bookmark bookmark : AdminBookmarkStore.load())
		{
			table.append("<a action=\"bypass -h admin_move_to ").append(bookmark.x()).append(' ')
					.append(bookmark.y()).append(' ').append(bookmark.z()).append("\">").append(bookmark.name())
					.append("</a>&nbsp;");
			table.append("<a action=\"bypass -h admin_delbookmark ").append(bookmark.name())
					.append("\"><font color=\"FF0000\">[X]</font></a><br>");
		}
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/tele/bookmarks.htm");
		adminReply.replace("%bookmarks%", table.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private void teleportTo(L2Player activeChar, String Cords)
	{
		try
		{
			StringTokenizer st = new StringTokenizer(Cords);
			String x1 = st.nextToken();
			int x = Integer.parseInt(x1);
			String y1 = st.nextToken();
			int y = Integer.parseInt(y1);
			String z1 = st.nextToken();
			int z = Integer.parseInt(z1);
			
			activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			activeChar.teleToLocation(x, y, z, false);
			
			activeChar.sendMessage("You have been teleported to " + Cords);
		}
		catch (NoSuchElementException nsee)
		{
			activeChar.sendMessage("Wrong or no Coordinates given.");
		}
	}
	
	private void showTeleportWindow(L2Player activeChar)
	{
		AdminHelpPage.showHelpPage(activeChar, "move.htm");
	}
	
	private void showTeleportCharWindow(L2Player activeChar)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
		{
			player = (L2Player)target;
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		
		StringBuilder replyMSG = new StringBuilder("<html><title>Teleport Character</title>");
		replyMSG.append("<body>");
		replyMSG.append("The character you will teleport is " + player.getName() + ".");
		replyMSG.append("<br>");
		
		replyMSG.append("Coordinate x");
		replyMSG.append("<edit var=\"char_cord_x\" width=110>");
		replyMSG.append("Coordinate y");
		replyMSG.append("<edit var=\"char_cord_y\" width=110>");
		replyMSG.append("Coordinate z");
		replyMSG.append("<edit var=\"char_cord_z\" width=110>");
		replyMSG.append("<button value=\"Teleport\" action=\"bypass -h admin_teleport_character $char_cord_x $char_cord_y $char_cord_z\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
		replyMSG.append("<button value=\"Teleport near you\" action=\"bypass -h admin_teleport_character "
				+ activeChar.getX() + " " + activeChar.getY() + " " + activeChar.getZ()
				+ "\" width=115 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
		replyMSG.append("<center><button value=\"Back\" action=\"bypass -h admin_current_player\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center>");
		replyMSG.append("</body></html>");
		
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private void teleportCharacter(L2Player activeChar, String Cords)
	{
		L2Player target = activeChar.getTarget(L2Player.class);
		if (target == null)
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		
		if (target == activeChar)
		{
			target.sendPacket(SystemMessageId.CANNOT_USE_ON_YOURSELF);
		}
		else
		{
			try
			{
				StringTokenizer st = new StringTokenizer(Cords);
				String x1 = st.nextToken();
				int x = Integer.parseInt(x1);
				String y1 = st.nextToken();
				int y = Integer.parseInt(y1);
				String z1 = st.nextToken();
				int z = Integer.parseInt(z1);
				int instance;
				if (activeChar.isInMultiverse())
					instance = target.getInstanceId();
				else
					instance = activeChar.getInstanceId();
				teleportCharacter(target, x, y, z, instance);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Failed parsing coordinates!");
			}
		}
	}
	
	private void teleportCharacter(L2Player player, int x, int y, int z, int instance)
	{
		if (player != null)
		{
			player.sendMessage("You have been teleported by a Game Master.");
			player.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			player.teleToLocation(x, y, z);
			if (player.getInstanceId() != instance)
				player.setInstanceId(instance);
		}
	}
	
	private void teleportToCharacter(L2Player activeChar, L2Object target)
	{
		if (target == null)
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		
		if (!activeChar.isSameInstance(target))
		{
			// move to targets instance
			activeChar.setInstanceId(target.getInstanceId());
			activeChar.sendMessage("Switched to instance " + target.getInstanceId() + ".");
		}
		
		L2Player player = (L2Player)target;
		
		if (player.getObjectId() == activeChar.getObjectId())
		{
			player.sendPacket(SystemMessageId.CANNOT_USE_ON_YOURSELF);
		}
		else
		{
			int x = player.getX();
			int y = player.getY();
			int z = player.getZ();
			
			activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			activeChar.teleToLocation(x, y, z);
			
			activeChar.sendMessage("You have teleported to character " + player.getName() + ".");
		}
	}
	
	private void recallNPC(L2Player activeChar)
	{
		L2Object obj = activeChar.getTarget();
		if (obj instanceof L2Npc && !(obj instanceof L2MinionInstance) && !(obj instanceof L2RaidBossInstance)
				&& !(obj instanceof L2GrandBossInstance))
		{
			L2Npc target = (L2Npc)obj;
			
			int monsterTemplate = target.getTemplate().getNpcId();
			L2NpcTemplate template1 = NpcTable.getInstance().getTemplate(monsterTemplate);
			if (template1 == null)
			{
				activeChar.sendMessage("Incorrect monster template.");
				_log.warn("ERROR: NPC " + target.getObjectId() + " has a 'null' template.");
				return;
			}
			
			L2Spawn spawn = target.getSpawn();
			
			if (spawn == null)
			{
				activeChar.sendMessage("Incorrect monster spawn.");
				_log.warn("ERROR: NPC " + target.getObjectId() + " has a 'null' spawn.");
				return;
			}
			
			target.decayMe();
			spawn.setLocx(activeChar.getX());
			spawn.setLocy(activeChar.getY());
			spawn.setLocz(activeChar.getZ());
			spawn.setHeading(activeChar.getHeading());
			spawn.respawnNpc(target);
			spawn.setInstanceId(activeChar.getInstanceId());
			SpawnTable.getInstance().updateSpawn(spawn);
		}
		else if (obj instanceof L2RaidBossInstance)
		{
			L2RaidBossInstance target = (L2RaidBossInstance)obj;
			RaidBossSpawnManager.getInstance().updateSpawn(target.getNpcId(), activeChar.getX(), activeChar.getY(),
					activeChar.getZ(), activeChar.getHeading());
		}
		else if (obj instanceof L2GrandBossInstance)
		{
			L2GrandBossInstance target = (L2GrandBossInstance)obj;
			GrandBossSpawnManager.getInstance().updateSpawn(target.getNpcId(), activeChar.getX(), activeChar.getY(),
					activeChar.getZ(), activeChar.getHeading());
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
		}
	}
	
	private void changeCharacterPosition(L2Player activeChar, String name)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE player SET x = ? WHERE name = ?");
			statement.setInt(1, activeChar.getX());
			statement.setString(2, name);
			statement.execute();
			statement = con.prepareStatement("UPDATE player SET y = ? WHERE name = ?");
			statement.setInt(1, activeChar.getY());
			statement.setString(2, name);
			statement.execute();
			statement = con.prepareStatement("UPDATE player SET z = ? WHERE name = ?");
			statement.setInt(1, activeChar.getZ());
			statement.setString(2, name);
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			if (count == 0)
				activeChar.sendMessage("Character not found or position unaltered.");
			else
			{
				activeChar.sendMessage("Character's position is now set to (" + activeChar.getX() + ","
						+ activeChar.getY() + "," + activeChar.getZ() + ")");
			}
		}
		catch (SQLException se)
		{
			activeChar.sendMessage("SQLException while changing offline character's position");
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
