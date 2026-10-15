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

import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.NpcWalkerRoutesTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.Manager;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.model.entity.CCHSiege;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.PetInfo;
import com.l2jfree.gameserver.util.Util;

/**
 * This class handles following admin commands:
 * - admin|admin1/admin2/admin3/admin4/admin5 = slots for the 5 starting admin menus
 * - gmliston/gmlistoff = includes/excludes active character from /gmlist results
 * - silence = toggles private messages acceptance mode
 * - diet = toggles weight penalty mode
 * - tradeoff = toggles trade acceptance mode
 * - reload = reloads specified component from multisell|skill|npc|htm|item|instancemanager
 * - set/set_menu/set_mod = alters specified server setting
 * - saveolymp = saves olympiad state manually
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2007/07/28 10:06:06 $
 */
public class AdminAdmin implements IAdminCommandHandler
{
	private final static Logger _log = LoggerFactory.getLogger(AdminAdmin.class);
	
	private static final String[] ADMIN_COMMANDS = { "admin_admin", "admin_admin1", "admin_admin2", "admin_admin3",
			"admin_admin4", "admin_admin5", "admin_gmliston", "admin_gmlistoff", "admin_silence", "admin_diet",
			"admin_tradeoff", "admin_reload", "admin_set", "admin_set_menu", "admin_set_mod", "admin_saveolymp",
			"admin_endolympiad",
			// L2J-FREE
			"admin_reload_config", "admin_config_server",
			//"admin_summon",
			"admin_summon_npc", "admin_unsummon", "admin_memusage", "admin_debug" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_admin"))
		{
			showMainPage(activeChar, command);
		}
		else if (command.equals("admin_config_server"))
		{
			showConfigPage(activeChar);
		}
		else if (command.equals("admin_config_server2"))
		{
			showConfigPage2(activeChar);
		}
		else if (command.startsWith("admin_gmliston"))
		{
			GmListTable.showGm(activeChar);
			activeChar.sendMessage("Showing on gm list");
		}
		else if (command.startsWith("admin_gmlistoff"))
		{
			GmListTable.hideGm(activeChar);
			activeChar.sendMessage("Hiding from gm list");
		}
		else if (command.startsWith("admin_silence"))
		{
			if (activeChar.getMessageRefusal()) // already in message refusal mode
			{
				activeChar.setMessageRefusal(false);
				activeChar.sendPacket(SystemMessageId.MESSAGE_ACCEPTANCE_MODE);
			}
			else
			{
				activeChar.setMessageRefusal(true);
				activeChar.sendPacket(SystemMessageId.MESSAGE_REFUSAL_MODE);
			}
			
		}
		else if (command.startsWith("admin_reload_config"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			
			try
			{
				activeChar.sendMessage(L2AutoInitialization.loadConfig(st.nextToken()));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage:  //reload_config <" + L2AutoInitialization.getLoaderNames() + ">");
			}
		}
		else if (command.startsWith("admin_summon_npc"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			try
			{
				int npcId = Integer.parseInt(st.nextToken());
				if (npcId != 0)
					adminSummon(activeChar, npcId);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //summon <npcid>");
			}
		}
		/*
		else if(command.startsWith("admin_summon"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			try
			{
				int id = Integer.parseInt(st.nextToken());
				if (id > 1000000) // NPC
				{
					L2NpcTemplate tpl = NpcTable.getInstance().getTemplate(id - 1000000);
					if (tpl == null)
					{
						activeChar.sendMessage("NPC not yet implemented.");
						return false;
					}
					L2Spawn spawn = new L2Spawn(tpl);
					spawn.setLocx(activeChar.getX());
					spawn.setLocy(activeChar.getY());
					spawn.setLocz(activeChar.getZ());
					spawn.setAmount(1);
					spawn.setHeading(activeChar.getHeading());
					spawn.setRespawnDelay(Config.STANDARD_RESPAWN_DELAY);
					spawn.setInstanceId(activeChar.getInstanceId());
					spawn.init();
					spawn.stopRespawn();
				}
				else // item
				{
					if (activeChar.addItem("GM", id, 1, activeChar, true, true) == null)
					{
						activeChar.sendMessage("Item not yet implemented.");
					}
				}
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //summon <npcid/itemid>");
			}
		}*/
		else if (command.startsWith("admin_memusage"))
		{
			for (String line : Util.getMemUsage())
			{
				activeChar.sendMessage(line);
			}
		}
		else if (command.startsWith("admin_unsummon"))
		{
			if (activeChar.getPet() != null)
				activeChar.getPet().unSummon(activeChar);
		}
		else if (command.startsWith("admin_saveolymp"))
		{
			Olympiad.getInstance().saveOlympiadStatus();
			activeChar.sendMessage("Olympiad system saved.");
		}
		else if (command.startsWith("admin_endolympiad"))
		{
			try
			{
				Olympiad.getInstance().manualSelectHeroes();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
			activeChar.sendMessage("Heroes formed");
		}
		else if (command.startsWith("admin_diet"))
		{
			try
			{
				StringTokenizer st = new StringTokenizer(command);
				st.nextToken();
				if (st.nextToken().equalsIgnoreCase("on"))
				{
					activeChar.setDietMode(true);
					activeChar.sendMessage("Diet mode on");
				}
				else if (st.nextToken().equalsIgnoreCase("off"))
				{
					activeChar.setDietMode(false);
					activeChar.sendMessage("Diet mode off");
				}
			}
			catch (Exception ex)
			{
				if (activeChar.getDietMode())
				{
					activeChar.setDietMode(false);
					activeChar.sendMessage("Diet mode off");
				}
				else
				{
					activeChar.setDietMode(true);
					activeChar.sendMessage("Diet mode on");
				}
			}
			finally
			{
				activeChar.refreshOverloaded();
			}
		}
		else if (command.startsWith("admin_tradeoff"))
		{
			try
			{
				String mode = command.substring(15);
				if (mode.equalsIgnoreCase("on"))
				{
					activeChar.setTradeRefusal(true);
					activeChar.sendMessage("Trade refusal enabled");
				}
				else if (mode.equalsIgnoreCase("off"))
				{
					activeChar.setTradeRefusal(false);
					activeChar.sendMessage("Trade refusal disabled");
				}
			}
			catch (Exception ex)
			{
				if (activeChar.getTradeRefusal())
				{
					activeChar.setTradeRefusal(false);
					activeChar.sendMessage("Trade refusal disabled");
				}
				else
				{
					activeChar.setTradeRefusal(true);
					activeChar.sendMessage("Trade refusal enabled");
				}
			}
		}
		else if (command.startsWith("admin_reload"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			try
			{
				String type = st.nextToken();
				if (type.startsWith("multisell"))
				{
					MultisellTable.getInstance().reload();
					activeChar.sendMessage("Multisell reloaded");
				}
				else if (type.startsWith("teleport"))
				{
					TeleportLocationTable.getInstance().reloadAll();
					activeChar.sendMessage("Teleport location table reloaded");
				}
				else if (type.startsWith("skill"))
				{
					SkillTable.reload();
					activeChar.sendMessage("Skills reloaded");
				}
				else if (type.startsWith("npcwalker"))
				{
					NpcWalkerRoutesTable.getInstance().load();
					activeChar.sendMessage("All NPC walker routes have been reloaded");
				}
				else if (type.startsWith("npc"))
				{
					NpcTable.getInstance().cleanUp();
					NpcTable.getInstance().reloadAll(false);
					activeChar.sendMessage("Npcs reloaded");
					// so NPCs can be reloaded without the need to wait for scripts getting executed
					activeChar.sendMessage("You should consider using \"//reload quests\".");
				}
				else if (type.startsWith("door"))
				{
					DoorTable.getInstance().reloadAll();
					activeChar.sendMessage("Doors reloaded");
				}
				else if (type.startsWith("htm"))
				{
					HtmCache.getInstance().reload(true);
					activeChar.sendMessage(HtmCache.getInstance().toString());
				}
				else if (type.startsWith("item"))
				{
					ItemTable.reload();
					activeChar.sendMessage("Item templates reloaded");
				}
				else if (type.startsWith("config"))
				{
					Config.load();
					activeChar.sendMessage("All config settings have been reload");
				}
				else if (type.startsWith("instancemanager"))
				{
					Manager.reloadAll();
					activeChar.sendMessage("All instance manager has been reloaded");
				}
				else if (type.startsWith("tradelist"))
				{
					TradeListTable.getInstance().reloadAll();
					activeChar.sendMessage("TradeList Table reloaded.");
				}
				else if (type.startsWith("zone"))
				{
					ZoneManager.getInstance().reload();
					activeChar.sendMessage("Zones reloaded.");
				}
				else if (type.startsWith("mapregion"))
				{
					MapRegionManager.getInstance().reload();
					activeChar.sendMessage("MapRegions reloaded.");
				}
				else if (type.startsWith("siege"))
				{
					SiegeManager.getInstance().reload();
					FortSiegeManager.getInstance().reload();
					activeChar.sendMessage("Castle/Fortress Siege configs reloaded");
				}
				else if (type.startsWith("fortsiege"))
				{
					FortSiegeManager.getInstance().reload();
					activeChar.sendMessage("Castle/Fortress Siege configs reloaded");
				}
				else if (type.startsWith("quests"))
				{
					QuestManager.getInstance().reloadAllQuests();
					activeChar.sendMessage("All Quests have been reloaded");
				}
				else if (type.startsWith("chsiege"))
				{
					for (CCHSiege siege : CCHManager.getInstance().getSieges())
						siege.getGuardManager().load();
					activeChar.sendMessage("All clan hall siege guards have been reloaded");
				}
				else
				{
					activeChar
							.sendMessage("Usage:  //reload <multisell|skill|npc|htm|item|instancemanager|teleport|tradelist|zone|mapregion|npcwalkers|siege|fortsiege|chsiege|door>");
				}
			}
			catch (Exception e)
			{
				activeChar
						.sendMessage("Usage:  //reload <multisell|skill|npc|htm|item|instancemanager|teleport|tradelist|zone|mapregion|siege|fortsiege|chsiege|door>");
			}
		}
		
		else if (command.startsWith("admin_set"))
		{
			StringTokenizer st = new StringTokenizer(command);
			String[] cmd = st.nextToken().split("_");
			try
			{
				String[] parameter = st.nextToken().split("=");
				String pName = parameter[0].trim();
				String pValue = parameter[1].trim();
				if (Config.setParameterValue(pName, pValue))
					activeChar.sendMessage("parameter " + pName + " succesfully set to " + pValue);
				else
					activeChar.sendMessage("Invalid parameter!");
			}
			catch (Exception e)
			{
				if (cmd.length == 2)
					activeChar.sendMessage("Usage: //set parameter=value");
			}
			finally
			{
				if (cmd.length == 3)
				{
					if (cmd[2].equalsIgnoreCase("menu"))
						AdminHelpPage.showHelpPage(activeChar, "settings.htm");
					else if (cmd[2].equalsIgnoreCase("mod"))
						AdminHelpPage.showHelpPage(activeChar, "mods_menu.htm");
				}
			}
		}
		
		else if (command.startsWith("admin_debug"))
		{
			//TODO: implement
			_log.info("A GM requested debug information for player " + command.substring(12));
			activeChar.sendPacket(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	public void showMainPage(L2Player activeChar, String command)
	{
		int mode = 0;
		String filename = null;
		try
		{
			mode = Integer.parseInt(command.substring(11));
		}
		catch (Exception e)
		{
		}
		switch (mode)
		{
			case 1:
				filename = "main";
				break;
			case 2:
				filename = "game";
				break;
			case 3:
				filename = "effects";
				break;
			case 4:
				filename = "server";
				break;
			case 5:
				filename = "mods";
				break;
			default:
				if (Config.GM_ADMIN_MENU_STYLE.equals("modern"))
					filename = "main";
				else
					filename = "classic";
				break;
		}
		AdminHelpPage.showHelpPage(activeChar, filename + "_menu.htm");
	}
	
	public void showConfigPage2(L2Player activeChar)
	{
		NpcHtmlMessage adminReply = new NpcHtmlMessage(activeChar.getObjectId());
		
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		replyMSG.append("<center><table width=270><tr><td width=60><button value=\"Admin\" action=\"bypass -h admin_admin\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td><td width=150><font color=\"LEVEL\">Config Server Panel</font></td><td width=60><button value=\"Panel1\" action=\"bypass -h admin_config_server\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr></table></center><br>");
		replyMSG.append("<center><table width=260>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Show GM Login</font> = " + Config.SHOW_GM_LOGIN
				+ "</td><td></td><td><button value=\"" + !Config.SHOW_GM_LOGIN
				+ "\" action=\"bypass -h admin_set ShowGMLogin " + !Config.SHOW_GM_LOGIN
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Spawn Siege Guard</font> = " + Config.ALT_SPAWN_SIEGE_GUARD
				+ "</td><td></td><td><button value=\"" + !Config.ALT_SPAWN_SIEGE_GUARD
				+ "\" action=\"bypass -h admin_set SpawnSiegeGuard " + !Config.ALT_SPAWN_SIEGE_GUARD
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Auto Loot</font> = " + Config.ALT_AUTO_LOOT
				+ "</td><td></td><td><button value=\"" + !Config.ALT_AUTO_LOOT
				+ "\" action=\"bypass -h admin_set AutoLoot " + !Config.ALT_AUTO_LOOT
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Magic Failures</font> = " + Config.ALT_GAME_MAGICFAILURES
				+ "</td><td></td><td><button value=\"" + !Config.ALT_GAME_MAGICFAILURES
				+ "\" action=\"bypass -h admin_set MagicFailures " + !Config.ALT_GAME_MAGICFAILURES
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Book Needed</font> = " + Config.ALT_SP_BOOK_NEEDED
				+ "</td><td></td><td><button value=\"" + !Config.ALT_SP_BOOK_NEEDED
				+ "\" action=\"bypass -h admin_set SpBookNeeded " + !Config.ALT_SP_BOOK_NEEDED
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Learn Other Skill</font> = " + Config.ALT_GAME_SKILL_LEARN
				+ "</td><td></td><td><button value=\"" + !Config.ALT_GAME_SKILL_LEARN
				+ "\" action=\"bypass -h admin_set AltGameSkillLearn " + !Config.ALT_GAME_SKILL_LEARN
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Shield Block From All</font> = " + Config.ALT_GAME_SHIELD_BLOCKS
				+ "</td><td></td><td><button value=\"" + !Config.ALT_GAME_SHIELD_BLOCKS
				+ "\" action=\"bypass -h admin_set AltShieldBlocks " + !Config.ALT_GAME_SHIELD_BLOCKS
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Free Teleport</font> = " + Config.ALT_GAME_FREE_TELEPORT
				+ "</td><td></td><td><button value=\"" + !Config.ALT_GAME_FREE_TELEPORT
				+ "\" action=\"bypass -h admin_set AltFreeTeleporting " + !Config.ALT_GAME_FREE_TELEPORT
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Can Discard Items</font> = " + Config.ALLOW_DISCARDITEM
				+ "</td><td></td><td><button value=\"" + !Config.ALLOW_DISCARDITEM
				+ "\" action=\"bypass -h admin_set AllowDiscardItem " + !Config.ALLOW_DISCARDITEM
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Global Chat</font> = "
				+ Config.DEFAULT_GLOBAL_CHAT
				+ "</td><td><edit var=\"menu_command1\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set GlobalChat $menu_command1\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Coord Synchronize</font> = "
				+ Config.COORD_SYNCHRONIZE
				+ "</td><td><edit var=\"menu_command2\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set CoordSynchronize $menu_command2\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("</table></body></html>");
		
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	public void showConfigPage(L2Player activeChar)
	{
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		replyMSG.append("<center><table width=270><tr><td width=60><button value=\"Admin\" action=\"bypass -h admin_admin\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td><td width=150><font color=\"LEVEL\">Config Server Panel</font></td><td width=60><button value=\"Panel2\" action=\"bypass -h admin_config_server2\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr></table></center><br>");
		replyMSG.append("<center><table width=260>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Auto loot</font> = " + Config.ALT_AUTO_LOOT
				+ "</td><td></td><td><button value=\"" + !Config.ALT_AUTO_LOOT
				+ "\" action=\"bypass -h admin_set AutoLoot " + !Config.ALT_AUTO_LOOT
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Allow Discard Item</font> = " + Config.ALLOW_DISCARDITEM
				+ "</td><td></td><td><button value=\"" + !Config.ALLOW_DISCARDITEM
				+ "\" action=\"bypass -h admin_set AllowDiscardItem " + !Config.ALLOW_DISCARDITEM
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Deep Blue Mobs Drop Rule</font> = " + Config.DEEPBLUE_DROP_RULES
				+ "</td><td></td><td><button value=\"" + !Config.DEEPBLUE_DROP_RULES
				+ "\" action=\"bypass -h admin_set UseDeepBlueDropRules " + !Config.DEEPBLUE_DROP_RULES
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Auto Destroy Item aft. sec.</font> = "
				+ Config.AUTODESTROY_ITEM_AFTER
				+ "</td><td><edit var=\"menu_command\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set AutoDestroyDroppedItemAfter $menu_command\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate EXP</font> = "
				+ Config.RATE_XP
				+ "</td><td><edit var=\"menu_command1\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateXP $menu_command1\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate SP</font> = "
				+ Config.RATE_SP
				+ "</td><td><edit var=\"menu_command2\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateSP $menu_command2\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Drop Adena</font> = "
				+ Config.RATE_DROP_ADENA
				+ "</td><td><edit var=\"menu_command3\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateDropAdena $menu_command3\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Drop Items</font> = "
				+ Config.RATE_DROP_ITEMS
				+ "</td><td><edit var=\"menu_command4\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateDropItems $menu_command4\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Drop Spoil</font> = "
				+ Config.RATE_DROP_SPOIL
				+ "</td><td><edit var=\"menu_command5\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateDropSpoil $menu_command5\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Quest Reward Exp/Sp</font> = "
				+ Config.RATE_QUESTS_REWARD_EXPSP
				+ "</td><td><edit var=\"menu_command6\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateQuestsReward $menu_command6\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Quest Reward Adena</font> = "
				+ Config.RATE_QUESTS_REWARD_ADENA
				+ "</td><td><edit var=\"menu_command6\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateQuestsReward $menu_command6\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Quest Reward Items</font> = "
				+ Config.RATE_QUESTS_REWARD_ITEMS
				+ "</td><td><edit var=\"menu_command6\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RateQuestsReward $menu_command6\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Raid Regen HP</font> = "
				+ Config.RAID_HP_REGEN_MULTIPLIER
				+ "</td><td><edit var=\"menu_command7\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RaidHpRegenMultiplier $menu_command7\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Raid Regen MP</font> = "
				+ Config.RAID_MP_REGEN_MULTIPLIER
				+ "</td><td><edit var=\"menu_command8\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RaidMpRegenMultiplier $menu_command8\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Raid PDefence</font> = "
				+ Config.RAID_PDEFENCE_MULTIPLIER
				+ "</td><td><edit var=\"menu_command9\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RaidPDefenceMultiplier $menu_command9\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Rate Raid MDefence</font> = "
				+ Config.RAID_MDEFENCE_MULTIPLIER
				+ "</td><td><edit var=\"menu_command10\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set RaidMDefenceMultiplier $menu_command10\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Alt Buff Time</font> = "
				+ Config.ALT_BUFF_TIME
				+ "</td><td><edit var=\"menu_command11\" width=40 height=15></td><td><button value=\"Set\" action=\"bypass -h admin_set AltBuffTime $menu_command11\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("<tr><td><font color=\"LEVEL\">Alt game creation</font> = " + Config.ALT_GAME_CREATION
				+ "</td><td></td><td><button value=\"" + !Config.ALT_GAME_CREATION
				+ "\" action=\"bypass -h admin_set AltGameCreation " + !Config.ALT_GAME_CREATION
				+ "\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
		replyMSG.append("</table></body></html>");
		
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	//[L2J_JP_ADD]
	public void adminSummon(L2Player activeChar, int npcId)
	{
		if (activeChar.getPet() != null)
		{
			activeChar.getPet().unSummon(activeChar);
		}
		
		L2NpcTemplate summonTemplate = NpcTable.getInstance().getTemplate(npcId);
		L2SummonInstance summon =
				new L2SummonInstance(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, null);
		
		summon.setTitle(activeChar.getName());
		summon.setExpPenalty(0);
		if (summon.getLevel() >= Experience.LEVEL.length)
		{
			summon.getStat().setExp(Experience.LEVEL[Experience.LEVEL.length - 1]);
		}
		else
		{
			summon.getStat().setExp(Experience.LEVEL[(summon.getLevel() % Experience.LEVEL.length)]);
		}
		summon.getStat().setExp(0);
		summon.getStatus().setCurrentHp(summon.getMaxHp());
		summon.getStatus().setCurrentMp(summon.getMaxMp());
		summon.setHeading(activeChar.getHeading());
		summon.setRunning();
		activeChar.setPet(summon);
		
		L2World.getInstance().storeObject(summon);
		summon.spawnMe(activeChar.getX() + 50, activeChar.getY() + 100, activeChar.getZ());
		
		summon.setFollowStatus(true);
		summon.setShowSummonAnimation(false); // addVisibleObject created the info packets with summon animation
		// if someone comes into range now, the animation shouldn't show any more
		activeChar.sendPacket(new PetInfo(summon, 0));
	}
}
