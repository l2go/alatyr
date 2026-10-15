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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.AutoChatManager;
import com.l2jfree.gameserver.instancemanager.AutoSpawnManager;
import com.l2jfree.gameserver.instancemanager.DayNightSpawnManager;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.tools.random.Rnd;

public class AdminSpawn implements IAdminCommandHandler
{
	
	private static final String[][] ADMIN_COMMANDS = {
			{ "admin_spawn_menu", // show spawn menu
					
					"Admin Menu - Spawn NPC.", },
			{
					"admin_spawnsearch_menu", // show page with NPC search results
					"Admin Menu - NPC search results.", "Usage: //spawnsearch_menu <level|name|part> string <page>",
					"Options:", "level - list NPC's by level <string>",
					"name - list NPC's witch name started with <string>",
					"part - list NPC's where <string> is part of name", "string - part of name or monster level",
					"<page> - page number with results search" },
			{
					"admin_spawndelay", // respawn delay for spawn commands
					"Set or show default respawn delay for newly spawned NPC.", "Usage //spawndelay [sec]", "Options:",
					"<sec> - set default respawn time in seconds" },
			{
					"admin_delay", // respawn delay for spawn commands
					"Set respawn delay for targeted NPC and save in DB.", "Usage //delay <sec>", "Options:",
					"<sec> - set respawn time in seconds" },
			{
					"admin_spawnlist", // get list of NPC spawns
					"Show list of regular spawns of NPC.", "Usage: //spawnlist <id|name>", "Options:",
					"id - NPC template ID", "name - NPC name (use underscope to separate words in npc name)" },
			{
					"admin_spawnlist_menu", // show list of NPC spawns
					"Admin Menu - Show spawns of NPC.", "Usage: //spawnlist <id|name>", "Options:",
					"id - NPC template ID", "name - NPC name (use underscope to separate words in npc name)" },
			{
					"admin_spawn", // spawn NPC and save to DB's default
					"Spawn NPC and store in DB.", "Usage: //spawn <id|name> <num> <radius>", "Options:",
					"id - NPC template ID", "name - NPC name (use underscope to separate words in npc name)",
					"<num> - NPC amount to spawn, Default: 1", "<radius> - radius for NPC spawns, Default: 300" },
			{
					"admin_cspawn", // spawn NPC and save to DB's custom table
					
					"Spawn NPC and store in DB in custom table.", "Usage: //cspawn <id|name> <num> <radius>",
					"Options:", "id - NPC template ID",
					"name - NPC name (use underscope to separate words in npc name)",
					"<num> - NPC amount to spawn, Default: 1", "<radius> - radius for NPC spawns, Default: 300" },
			{
					"admin_otspawn", // spawn NPC but do not store spawn in DB
					
					"Spawn NPC and do not store in DB.", "Usage: //otspawn <id|name> <num> <radius>", "Options:",
					"id - NPC template ID", "name - NPC name (use underscope to separate words in npc name)",
					"<num> - NPC amount to spawn, Default: 1", "<radius> - radius for NPC spawns, Default: 300" },
			{
					"admin_spawn_once", // spawn NPC, do not store in db and do not respawn
					
					"Spawn NPC and do not store in DB, do not respawn, too.",
					"Usage: //spawn_once <id|name> <num> <radius>", "Options:", "id - NPC template ID",
					"name - NPC name (use underscope to separate words in npc name)",
					"<num> - NPC amount to spawn, Default: 1", "<radius> - radius for NPC spawns, Default: 300" },
			{ "admin_unspawnall", // delete all spawned NPC's
					
					"Delete all spawned NPC's.", "Usage: //unspawnall", }, { "admin_respawnall", // delete all spawned NPC's then respawn again
					
					"Delete all spawned NPC's and respawn again.", "Usage: //respawnall", }, { "admin_spawnnight", // spawn night creatures
					
					"Spawn night creatures.", "Usage: //spawnnight", }, { "admin_spawnday", // spawn day creatures
					
					"Spawn day creatures.", "Usage: //spawnday", } };
	
	public static Logger _log = LoggerFactory.getLogger(AdminSpawn.class);
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		
		String cmd = st.nextToken(); // get command
		
		if (cmd.equals("admin_spawn_menu"))
		{
			AdminHelpPage.showHelpPage(activeChar, "spawns.htm");
		}
		else if (cmd.equals("admin_spawnsearch_menu"))
		{
			if (st.countTokens() < 2)
			{
				AdminHelpPage.showHelpPage(activeChar, "spawns.htm");
			}
			else
			{
				String mode = null;
				String string = null;
				int page = 0;
				
				try
				{
					mode = st.nextToken();
					string = st.nextToken();
					page = Integer.parseInt(st.nextToken());
					
					showNpcs(activeChar, mode.toLowerCase(), string.toLowerCase(), page);
				}
				catch (Exception e)
				{
					AdminHelpPage.showHelpPage(activeChar, "spawns.htm");
				}
			}
		}
		else if (cmd.equals("admin_spawnlist"))
		{
			int npcId = 0;
			String npcName = "";
			
			try
			{
				npcName = st.nextToken();
				
				try
				{
					npcId = Integer.parseInt(npcName);
				}
				catch (NumberFormatException e)
				{
				}
				
			}
			catch (Exception e)
			{
				showAdminCommandHelp(activeChar, cmd);
			}
			if (npcId > 0)
				showSpawns(activeChar, npcId);
			else if (npcName.length() > 0)
				showSpawns(activeChar, npcName);
			else
				showAdminCommandHelp(activeChar, cmd);
		}
		else if (cmd.equals("admin_spawnlist_menu"))
		{
			int npcId = 0;
			String npcName = "";
			int page = 0;
			
			try
			{
				npcName = st.nextToken();
				
				try
				{
					npcId = Integer.parseInt(npcName);
				}
				catch (NumberFormatException e)
				{
				}
				
				if (st.hasMoreTokens())
					page = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				AdminHelpPage.showHelpPage(activeChar, "spawns.htm");
			}
			
			if (npcId > 0)
				showSpawns(activeChar, npcId, page, true);
			else if (npcName.length() > 0)
				showSpawns(activeChar, npcName, page, true);
			else
				AdminHelpPage.showHelpPage(activeChar, "spawns.htm");
		}
		else if (cmd.equals("admin_spawndelay"))
		{
			int delay = 0;
			
			if (st.hasMoreTokens())
			{
				try
				{
					delay = Integer.parseInt(st.nextToken());
					Config.STANDARD_RESPAWN_DELAY = delay;
				}
				catch (Exception e)
				{
					showAdminCommandHelp(activeChar, cmd);
				}
			}
			activeChar.sendMessage("Current default respawn delay is " + Config.STANDARD_RESPAWN_DELAY + " seconds.");
		}
		else if (cmd.equals("admin_delay"))
		{
			int delay = 0;
			L2Npc target = null;
			
			if (activeChar.getTarget() instanceof L2Npc)
				target = (L2Npc)activeChar.getTarget();
			
			if (st.hasMoreTokens() && target != null)
			{
				try
				{
					delay = Integer.parseInt(st.nextToken());
					
					L2Spawn spawn = target.getSpawn();
					
					if (spawn.isRespawnable())
					{
						SpawnTable.getInstance().deleteSpawn(spawn, true);
						target.deleteMe();
						spawn.setRespawnDelay(delay);
						SpawnTable.getInstance().addNewSpawn(spawn, true);
						target.setSpawn(spawn);
						target.spawnMe();
						activeChar.sendMessage("Respawn delay  for " + target.getName() + " changed to " + delay
								+ " seconds.");
					}
					else
						activeChar.sendMessage("Respawn delay  for " + target.getName() + " cant be changed.");
				}
				catch (Exception e)
				{
					showAdminCommandHelp(activeChar, cmd);
				}
			}
			else
				showAdminCommandHelp(activeChar, cmd);
		}
		else if (cmd.equals("admin_spawn") || cmd.equals("admin_cspawn") || cmd.equals("admin_otspawn")
				|| cmd.equals("admin_spawn_once"))
		{
			boolean custom = cmd.equals("admin_cspawn");
			boolean respawn = !cmd.equals("admin_spawn_once");
			boolean storeInDb = !cmd.equals("admin_otspawn") && respawn;
			
			int npcId = 0;
			String npcName = "";
			int count = 1;
			int radius = 300;
			
			try
			{
				npcName = st.nextToken();
				
				try
				{
					npcId = Integer.parseInt(npcName);
				}
				catch (NumberFormatException e)
				{
				}
				
				if (st.hasMoreTokens())
					count = Integer.parseInt(st.nextToken());
				if (st.hasMoreTokens())
					radius = Integer.parseInt(st.nextToken());
				
				if (npcId > 0)
					spawnNpc(activeChar, npcId, count, radius, storeInDb, respawn, custom);
				else if (npcName.length() > 0)
					spawnNpc(activeChar, npcName, count, radius, storeInDb, respawn, custom);
				else
					showAdminCommandHelp(activeChar, cmd);
			}
			catch (Exception e)
			{
				showAdminCommandHelp(activeChar, cmd);
			}
		}
		else if (cmd.equals("admin_unspawnall"))
		{
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				player.sendPacket(SystemMessageId.NPC_SERVER_NOT_OPERATING);
			}
			
			RaidBossSpawnManager.getInstance().cleanUp();
			DayNightSpawnManager.getInstance().cleanUp();
			L2World.getInstance().deleteVisibleNpcSpawns();
			activeChar.sendMessage("All NPCs unspawned.");
		}
		else if (cmd.equals("admin_spawnday"))
		{
			DayNightSpawnManager.getInstance().spawnDayCreatures();
			activeChar.sendMessage("All daylight NPCs spawned.");
		}
		else if (cmd.equals("admin_spawnnight"))
		{
			DayNightSpawnManager.getInstance().spawnNightCreatures();
			activeChar.sendMessage("All nightly NPCs spawned.");
		}
		else if (cmd.equals("admin_respawnall"))
		{
			activeChar.sendMessage("NPCs respawn sequence initiated.");
			RaidBossSpawnManager.getInstance().cleanUp();
			DayNightSpawnManager.getInstance().cleanUp();
			L2World.getInstance().deleteVisibleNpcSpawns();
			NpcTable.getInstance().cleanUp();
			
			NpcTable.getInstance().reloadAll(false); // quest reloading will be done 6 lines under
			SpawnTable.getInstance().reloadAll();
			RaidBossSpawnManager.getInstance().reloadBosses();
			AutoSpawnManager.getInstance().reload();
			AutoChatManager.getInstance().reload();
			SevenSigns.getInstance().spawnSevenSignsNPC();
			QuestManager.getInstance().reloadAllQuests();
			activeChar.sendMessage("NPCs respawn sequence complete.");
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		String[] _adminCommandsOnly = new String[ADMIN_COMMANDS.length];
		for (int i = 0; i < ADMIN_COMMANDS.length; i++)
		{
			_adminCommandsOnly[i] = ADMIN_COMMANDS[i][0];
		}
		
		return _adminCommandsOnly;
	}
	
	/**
	 * Spawn NPC.
	 * @param npcId id of NPC Template
	 * @param count count of NPCs to spawn
	 * @param radius radius of spawn
	 * @param respawn if false spawn only once
	 * @param custom if true then spawn will be custom
	 */
	private void spawnNpc(L2Player activeChar, int npcId, int count, int radius, boolean saveInDb, boolean respawn,
			boolean custom)
	{
		L2Object target = activeChar.getTarget();
		if (target == null)
			target = activeChar;
		
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(npcId);
		
		if (template == null)
		{
			activeChar.sendMessage("NPC template ID " + npcId + " not found.");
			return;
		}
		
		try
		{
			for (int i = 0; i < count; i++)
			{
				int x = target.getX();
				int y = target.getY();
				int z = target.getZ();
				int heading = activeChar.getHeading();
				
				if (radius > 0 && count > 1)
				{
					int signX = (Rnd.nextInt(2) == 0) ? -1 : 1;
					int signY = (Rnd.nextInt(2) == 0) ? -1 : 1;
					int randX = Rnd.nextInt(radius);
					int randY = Rnd.nextInt(radius);
					int randH = Rnd.nextInt(0xFFFF);
					
					x = x + signX * randX;
					y = y + signY * randY;
					heading = randH;
				}
				
				L2Spawn spawn = new L2Spawn(template);
				
				if (custom)
					spawn.setCustom();
				
				spawn.setLocx(x);
				spawn.setLocy(y);
				spawn.setLocz(z);
				spawn.setAmount(1);
				spawn.setHeading(heading);
				spawn.setRespawnDelay(Config.STANDARD_RESPAWN_DELAY);
				spawn.setInstanceId(activeChar.getInstanceId());
				
				if (RaidBossSpawnManager.getInstance().isDefined(spawn.getNpcId()) && respawn
						&& !Config.ALT_DEV_NO_SPAWNS && spawn.getInstanceId() == 0)
				{
					activeChar.sendMessage("You cannot spawn another instance of " + template.getName() + ".");
				}
				else if (GrandBossSpawnManager.getInstance().isDefined(spawn.getNpcId()) && respawn
						&& !Config.ALT_DEV_NO_SPAWNS && spawn.getInstanceId() == 0)
				{
					activeChar.sendMessage("You cannot spawn another instance of " + template.getName() + ".");
				}
				else
				{
					if (saveInDb && !Config.ALT_DEV_NO_SPAWNS && spawn.getInstanceId() == 0)
					{
						if (RaidBossSpawnManager.getInstance().getValidTemplate(spawn.getNpcId()) != null)
						{
							spawn.setRespawnMinDelay(43200);
							spawn.setRespawnMaxDelay(129600);
							RaidBossSpawnManager.getInstance().addNewSpawn(spawn, 0, template.getBaseHpMax(),
									template.getBaseMpMax(), true);
						}
						else if (GrandBossSpawnManager.getInstance().getValidTemplate(spawn.getNpcId()) != null)
						{
							spawn.setRespawnMinDelay(43200);
							spawn.setRespawnMaxDelay(129600);
							GrandBossSpawnManager.getInstance().addNewSpawn(spawn, 0, template.getBaseHpMax(),
									template.getBaseMpMax(), true);
						}
						else
						{
							SpawnTable.getInstance().addNewSpawn(spawn, respawn);
						}
					}
					else
					{
						spawn.spawnOne(false);
					}
					
					spawn.init();
					
					if (!respawn)
						spawn.stopRespawn();
					
					activeChar.sendMessage("Created " + template.getName() + " on " + target.getX() + " "
							+ target.getY() + " " + target.getZ() + ".");
				}
			}
		}
		catch (Exception e)
		{
		}
	}
	
	/**
	 * Spawn NPC.
	 * @param npcName name of NPC
	 * @param count count of NPCs to spawn
	 * @param radius radius of spawn
	 * @param respawn if false spawn only once
	 * @param custom if true then spawn will be custom
	 */
	private void spawnNpc(L2Player activeChar, String npcName, int count, int radius, boolean saveInDb,
			boolean respawn, boolean custom)
	{
		int npcId = getNpcIdByName(npcName);
		
		if (npcId > 0)
			spawnNpc(activeChar, npcId, count, radius, saveInDb, respawn, custom);
		else
			activeChar.sendMessage("NPC template with name " + npcName + " not found.");
	}
	
	/**
	 * Search for NPC.
	 * @param mode search mode, by "level","name" or "namepart"
	 * @param string parameter of search: level or part name
	 * @param page number of html page to show
	 */
	private void showNpcs(L2Player activeChar, String mode, String string, int page)
	{
		int level = 0;
		
		try
		{
			level = Integer.parseInt(string);
		}
		catch (Exception e)
		{
		}
		
		List<L2NpcTemplate> list = new ArrayList<L2NpcTemplate>();
		
		for (L2NpcTemplate t : NpcTable.getInstance().getAllTemplates())
		{
			if (mode.equals("name") && (t.getName().toLowerCase().startsWith(string)))
				list.add(t);
			else if (mode.equals("namepart") && (t.getName().toLowerCase().contains(string)))
				list.add(t);
			else if (mode.equals("level") && level != 0 && t.getLevel() == level)
				list.add(t);
		}
		
		L2NpcTemplate[] result = list.toArray(new L2NpcTemplate[list.size()]);
		
		int maxPerPage = 20;
		
		int maxPages = result.length / maxPerPage;
		
		if (result.length > maxPerPage * maxPages)
			maxPages++;
		
		if (page > maxPages)
			page = maxPages;
		
		int start = maxPerPage * page;
		int end = result.length;
		
		if (end - start > maxPerPage)
			end = start + maxPerPage;
		
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		
		replyMSG.append("<table width=260><tr>");
		if (page == 0)
			replyMSG.append("<td width=40><button value=\"Back\" action=\"bypass -h admin_spawn_menu\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		else
			replyMSG.append("<td width=40><button value=\"Back\" action=\"bypass -h admin_spawnsearch_menu " + mode
					+ " " + string + " " + (page - 1)
					+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("<td width=180><center>NPC Search Result<br>Found:" + result.length + "</center></td>");
		if ((page + 1) < maxPages)
			replyMSG.append("<td width=40><button value=\"Next\" action=\"bypass -h admin_spawnsearch_menu " + mode
					+ " " + string + " " + (page + 1)
					+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		else
			replyMSG.append("<td width=40></td>");
		
		replyMSG.append("</tr></table>");
		replyMSG.append("<br><br>");
		
		if (result.length > 0)
		{
			replyMSG.append("<table width=260>");
			replyMSG.append("<tr><td>ID<font color=\"LEVEL\">*</font></td><td>Name<font color=\"LEVEL\">**</font></td><td>Lv</td></tr>");
			for (int i = start; i < end; i++)
			{
				replyMSG.append("<tr><td><a action=\"bypass -h admin_spawnlist_menu " + result[i].getNpcId() + "\">"
						+ result[i].getNpcId() + "</td><td><a action=\"bypass -h admin_spawn " + result[i].getNpcId()
						+ "\">" + result[i].getName() + "</a></td><td>" + result[i].getLevel() + "</td></tr>");
			}
			replyMSG.append("</table>");
			replyMSG.append("<font color=\"LEVEL\">*</font> Click on ID to search NPC spawns.<br1>");
			replyMSG.append("<font color=\"LEVEL\">**</font> Click on name to spawn NPC.<br1>");
		}
		
		replyMSG.append("</body></html>");
		activeChar.sendPacket(new NpcHtmlMessage(5, replyMSG.toString()));
	}
	
	/**
	 * List all spawns of NPC.
	 * @param activeChar
	 * @param npc name
	 */
	private void showSpawns(L2Player activeChar, String npcName)
	{
		int npcId = getNpcIdByName(npcName);
		
		if (npcId > 0)
			showSpawns(activeChar, npcId, 0, false);
		else
			activeChar.sendMessage("NPC template with name " + npcName + " not found.");
	}
	
	/**
	 * List all spawns of NPC.
	 * @param npcId NPC template ID
	 */
	private void showSpawns(L2Player activeChar, int npcId)
	{
		if (NpcTable.getInstance().getTemplate(npcId) != null)
			showSpawns(activeChar, npcId, 0, false);
		else
			activeChar.sendMessage("NPC template ID " + npcId + " not found.");
	}
	
	/**
	 * Show all spawns of NPC.
	 * @param npc name
	 * @param page html page number
	 * @param html show spawns as html page, if false list spawns in chat
	 */
	private void showSpawns(L2Player activeChar, String npcName, int page, boolean html)
	{
		int npcId = getNpcIdByName(npcName);
		
		if (npcId > 0)
			showSpawns(activeChar, npcId, page, html);
		else
			activeChar.sendMessage("NPC template with name " + npcName + " not found.");
	}
	
	/**
	 * Show all spawns of NPC.
	 * @param npcId NPC template ID
	 * @param page html page number
	 * @param html show spawns as html page, if false list spawns in chat
	 */
	private void showSpawns(L2Player activeChar, int npcId, int page, boolean html)
	{
		List<L2Spawn> list = new ArrayList<L2Spawn>();
		
		for (L2Spawn spawn : SpawnTable.getInstance().getAllTemplates().values())
			if (npcId == spawn.getNpcId())
				list.add(spawn);
		
		L2Spawn[] result = list.toArray(new L2Spawn[list.size()]);
		
		if (html)
		{
			int maxPerPage = 20;
			
			int maxPages = result.length / maxPerPage;
			
			if (result.length > maxPerPage * maxPages)
				maxPages++;
			
			if (page > maxPages)
				page = maxPages;
			
			int start = maxPerPage * page;
			int end = result.length;
			
			if (end - start > maxPerPage)
				end = start + maxPerPage;
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			
			replyMSG.append("<table width=260><tr>");
			if (page == 0)
				replyMSG.append("<td width=40><button value=\"Back\" action=\"bypass -h admin_spawn_menu\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			else
				replyMSG.append("<td width=40><button value=\"Back\" action=\"bypass -h admin_spawnlist_menu " + npcId
						+ " " + (page - 1)
						+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			replyMSG.append("<td width=180><center>Spawns Search Result<br>Found:" + result.length + "</center></td>");
			if ((page + 1) < maxPages)
				replyMSG.append("<td width=40><button value=\"Next\" action=\"bypass -h admin_spawnlist_menu " + npcId
						+ " " + (page + 1)
						+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
			else
				replyMSG.append("<td width=40></td>");
			
			replyMSG.append("</tr></table>");
			replyMSG.append("<br><br>");
			if (result.length > 0)
			{
				replyMSG.append("<table width=260>");
				replyMSG.append("<tr><td>ID</td><td>Location</td><td>Amount</td></tr>");
				for (int i = start; i < end; i++)
				{
					replyMSG.append("<tr><td>" + result[i].getDbId() + "</td><td><a action=\"bypass -h admin_move_to "
							+ result[i].getLocx() + " " + result[i].getLocy() + " " + result[i].getLocz() + "\">"
							+ result[i].getLocx() + " " + result[i].getLocy() + " " + result[i].getLocz()
							+ "</a></td><td>" + result[i].getAmount() + "</td></tr>");
				}
				replyMSG.append("</table>");
			}
			
			replyMSG.append("</body></html>");
			activeChar.sendPacket(new NpcHtmlMessage(5, replyMSG.toString()));
		}
		else
		{
			if (result.length > 0)
			{
				for (L2Spawn element : result)
					activeChar.sendMessage(element.getDbId() + " " + element.getLocx() + " " + element.getLocy() + " "
							+ element.getLocz() + " " + element.getAmount());
				
				activeChar.sendMessage("Total spawns for NPC ID " + npcId + " is " + result.length + ".");
			}
			else
				activeChar.sendMessage("No spawns for NPC ID " + npcId + " found.");
		}
	}
	
	/**
	 * Show tips about command usage and syntax.
	 * @param command admin command name
	 */
	private void showAdminCommandHelp(L2Player activeChar, String command)
	{
		for (String[] element : ADMIN_COMMANDS)
		{
			if (command.equals(element[0]))
			{
				for (int k = 1; k < element.length; k++)
					activeChar.sendMessage(element[k]);
			}
		}
	}
	
	/**
	 * Search for NPC ID by NPC name.
	 * @param npcName (use underscope to separate words in npc name)
	 * @return NPC ID or 0 if no template found
	 */
	private int getNpcIdByName(String npcName)
	{
		int npcId = 0;
		
		for (L2NpcTemplate t : NpcTable.getInstance().getAllTemplates())
		{
			if (t.getName().equalsIgnoreCase(npcName.replace("_", " ")))
			{
				npcId = t.getNpcId();
				break;
			}
		}
		return npcId;
	}
}
