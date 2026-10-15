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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager.PlayerStateOnCommunity;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.RecommendationManager;
import com.l2jfree.gameserver.instancemanager.TransformationManager;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SetSummonRemainTime;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.util.Util;

/**
 * This class handles following admin commands:
 * - edit_character
 * - current_player
 * - character_list
 * - show_characters
 * - find_character
 * - find_ip
 * - find_account
 * - rec
 * - nokarma
 * - setkarma
 * - settitle
 * - changename
 * - changename_menu
 * - setsex
 * - setclass
 * - fullfood
 * - save_modifications
 * 
 * @version $Revision: 1.3.2.1.2.10 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminEditChar implements IAdminCommandHandler
{
	private final static Logger _log = LoggerFactory.getLogger(AdminEditChar.class);
	
	private static final String[] ADMIN_COMMANDS = { "admin_edit_character", "admin_current_player", "admin_nokarma", // this is to remove karma from selected char...
			"admin_setkarma", // sets karma of target char to any amount. //setkarma <karma>
			"admin_setfame", // sets fame of target char to any amount. //setfame <fame>
			"admin_character_list", //same as character_info, kept for compatibility purposes
			"admin_character_info", //given a player name, displays an information window
			"admin_show_characters",//list of characters
			"admin_find_character", //find a player by his name or a part of it (case-insensitive)
			"admin_find_ip", // find all the player connections from a given IPv4 number
			"admin_find_account", //list all the characters from an account (useful for GMs w/o DB access)
			"admin_find_dualbox", //list all the IPs with more than 1 char logged in (dualbox)
			"admin_save_modifications", //consider it deprecated...
			"admin_rec", // gives recommendation points
			"admin_settitle", // changes char title
			"admin_changename", // changes char name
			"admin_changename_menu", "admin_setsex", // changes characters' sex
			"admin_setcolor", // change charnames' color display
			"admin_setclass", // changes chars' classId
			"admin_fullfood", // fulfills a pet's food bar
			
			// L2JFREE
			"admin_remclanwait", "admin_sethero", "admin_manualhero" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_current_player"))
		{
			showCharacterInfo(activeChar, null);
		}
		else if ((command.startsWith("admin_character_list")) || (command.startsWith("admin_character_info")))
		{
			try
			{
				String val = command.substring(21);
				L2Player target = L2World.getInstance().getPlayer(val);
				if (target != null)
					showCharacterInfo(activeChar, target);
				else
					activeChar.sendPacket(SystemMessageId.CHARACTER_DOES_NOT_EXIST);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				activeChar.sendMessage("Usage: //character_info <player_name>");
			}
		}
		else if (command.startsWith("admin_show_characters"))
		{
			try
			{
				String val = command.substring(22);
				int page = Integer.parseInt(val);
				listCharacters(activeChar, page);
			}
			catch (Exception e)
			{
				//Case of empty page number
				activeChar.sendMessage("Usage: //show_characters <page_number>");
			}
		}
		else if (command.startsWith("admin_find_character"))
		{
			try
			{
				String val = command.substring(21);
				findCharacter(activeChar, val);
			}
			catch (StringIndexOutOfBoundsException e)
			{ //Case of empty character name
				activeChar.sendMessage("Usage: //find_character <character_name>");
				listCharacters(activeChar, 0);
			}
		}
		else if (command.startsWith("admin_find_ip"))
		{
			try
			{
				String val = command.substring(14);
				findCharactersPerIp(activeChar, val);
			}
			catch (Exception e)
			{ //Case of empty or malformed IP number
				activeChar.sendMessage("Usage: //find_ip <www.xxx.yyy.zzz>");
				listCharacters(activeChar, 0);
			}
		}
		else if (command.startsWith("admin_find_account"))
		{
			try
			{
				String val = command.substring(19);
				findCharactersPerAccount(activeChar, val);
			}
			catch (Exception e)
			{ //Case of empty or malformed player name
				activeChar.sendMessage("Usage: //find_account <player_name>");
				listCharacters(activeChar, 0);
			}
		}
		else if (command.equals("admin_edit_character"))
			editCharacter(activeChar);
		// Karma control commands
		else if (command.equals("admin_nokarma"))
			setTargetKarma(activeChar, 0);
		else if (command.startsWith("admin_setkarma"))
		{
			try
			{
				String val = command.substring(15);
				int karma = Integer.parseInt(val);
				
				setTargetKarma(activeChar, karma);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //setkarma <new_karma_value>");
			}
		}
		else if (command.startsWith("admin_setfame"))
		{
			try
			{
				String val = command.substring(14);
				int fame = Integer.parseInt(val);
				L2Object target = activeChar.getTarget();
				if (target instanceof L2Player)
				{
					L2Player player = (L2Player)target;
					player.setFame(fame);
					player.sendPacket(new UserInfo(player));
					player.sendMessage("A GM changed your Reputation points to " + fame);
				}
				else
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				}
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //setfame <new_fame_value>");
			}
		}
		else if (command.startsWith("admin_save_modifications"))
		{
			try
			{
				String val = command.substring(24);
				adminModifyCharacter(activeChar, val);
			}
			catch (StringIndexOutOfBoundsException e)
			{ //Case of empty character name
				activeChar.sendMessage("Error while modifying character.");
				listCharacters(activeChar, 0);
			}
		}
		else if (command.startsWith("admin_rec"))
		{
			try
			{
				StringTokenizer st = new StringTokenizer(command, " ");
				st.nextToken();
				int recVal = Integer.parseInt(st.nextToken());
				boolean temp = true;
				try
				{
					temp = !st.nextToken().equals("y");
				}
				catch (NoSuchElementException nsee)
				{
				}
				L2Object target = activeChar.getTarget();
				L2Player player = null;
				if (target instanceof L2Player)
					player = (L2Player)target;
				else
					return false;
				if (temp)
					player.setEvalPoints(recVal);
				else
					RecommendationManager.getInstance().onGmEvaluation(player, recVal);
				player.sendMessage("You have been evaluated by a GM!" + (temp ? " (temporarily)" : ""));
				player.broadcastUserInfo();
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //rec number [save? y/n]");
			}
		}
		else if (command.startsWith("admin_setclass"))
		{
			try
			{
				String val = command.substring(15);
				int classidval = Integer.parseInt(val);
				L2Object target = activeChar.getTarget();
				L2Player player = null;
				if (target instanceof L2Player)
					player = (L2Player)target;
				else
					return false;
				boolean valid = false;
				for (ClassId classid : ClassId.values())
					if (classidval == classid.getId())
						valid = true;
				if (valid && (player.getClassId().getId() != classidval))
				{
					player.setClassId(classidval);
					if (!player.isSubClassActive())
						player.setBaseClass(classidval);
					String newclass = player.getTemplate().getClassName();
					player.store();
					if (player != activeChar)
						player.sendMessage("A GM changed your class to " + newclass);
					activeChar.sendMessage(player.getName() + " changed to " + newclass);
					
					// Quickly transform them to force the client to reload the character textures
					TransformationManager.getInstance().transformPlayer(105, player);
					ThreadPoolManager.getInstance().scheduleGeneral(new Untransform(player), 200);
				}
				else
					activeChar.sendMessage("Usage: //setclass <valid_new_classid>");
			}
			catch (Exception e)
			{
				AdminHelpPage.showHelpPage(activeChar, "charclasses.htm");
			}
		}
		else if (command.startsWith("admin_settitle"))
		{
			String val = "";
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			L2Object target = activeChar.getTarget();
			L2Player player = null;
			L2Npc npc = null;
			
			if (target == null)
				player = activeChar;
			else if (target instanceof L2Player)
				player = (L2Player)target;
			else if (target instanceof L2Npc)
				npc = (L2Npc)target;
			else
				return false;
			
			if (st.hasMoreTokens())
				val = st.nextToken();
			while (st.hasMoreTokens())
				val += " " + st.nextToken();
			
			if (player != null)
			{
				player.setTitle(val);
				if (player != activeChar)
					player.sendMessage("Your title has been changed by a GM");
				player.broadcastTitleInfo();
			}
			else if (npc != null)
			{
				npc.setTitle(val);
				npc.updateAbnormalEffect();
			}
		}
		else if (command.startsWith("admin_changename"))
		{
			try
			{
				
				StringTokenizer st = new StringTokenizer(command);
				st.nextToken();
				String val = st.nextToken();
				L2Object target = activeChar.getTarget();
				L2Player player = null;
				
				String oldName = null;
				
				if (target instanceof L2Player)
				{
					player = (L2Player)target;
					oldName = player.getName();
					
					if (CharNameTable.getInstance().getByName(val) != null)
					{
						activeChar.sendMessage("Player with name already exists!");
						return false;
					}
					
					L2World.getInstance().removeOnlinePlayer(player);
					player.setName(val);
					player.store();
					L2World.getInstance().addOnlinePlayer(player);
					
					player.sendMessage("Your name has been changed by a GM.");
					player.broadcastUserInfo();
					
					if (player.isInParty())
					{
						// Delete party window for other party members
						player.getParty().refreshPartyView();
					}
					if (player.getClan() != null)
					{
						player.getClan().broadcastClanStatus();
					}
					
					RegionBBSManager.changeCommunityBoard(player, PlayerStateOnCommunity.NONE);
				}
				else if (target instanceof L2Npc)
				{
					L2Npc npc = (L2Npc)target;
					oldName = npc.getName();
					npc.setName(val);
					npc.updateAbnormalEffect();
				}
				if (oldName == null)
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				else
					activeChar.sendMessage("Name changed from " + oldName + " to " + val);
			}
			catch (Exception e)
			{ //Case of empty character name
				activeChar.sendMessage("Usage: //setname new_name_for_target");
			}
		}
		else if (command.startsWith("admin_setsex"))
		{
			L2Object target = activeChar.getTarget();
			L2Player player = null;
			if (target instanceof L2Player)
			{
				player = (L2Player)target;
			}
			else
			{
				return false;
			}
			player.getAppearance().setSex(player.getAppearance().getSex() ? false : true);
			player.sendMessage("Your gender has been changed by a GM");
			
			// Quickly transform them to force the client to reload the character textures
			TransformationManager.getInstance().transformPlayer(105, player);
			ThreadPoolManager.getInstance().scheduleGeneral(new Untransform(player), 200);
		}
		else if (command.startsWith("admin_setcolor"))
		{
			try
			{
				String val = command.substring(15);
				L2Object target = activeChar.getTarget();
				L2Player player = null;
				if (target instanceof L2Player)
					player = (L2Player)target;
				else
					return false;
				player.getAppearance().setNameColor(Integer.decode("0x" + val));
				player.sendMessage("Your name color has been changed by a GM");
				player.broadcastUserInfo();
			}
			catch (Exception e)
			{ //Case of empty color or invalid hex string
				activeChar.sendMessage("You need to specify a valid new color.");
			}
		}
		else if (command.startsWith("admin_fullfood"))
		{
			L2Object target = activeChar.getTarget();
			if (target instanceof L2PetInstance)
			{
				L2PetInstance targetPet = (L2PetInstance)target;
				targetPet.setCurrentFed(targetPet.getMaxFed());
				targetPet.getOwner().sendPacket(
						new SetSummonRemainTime(targetPet.getMaxFed(), targetPet.getCurrentFed()));
			}
			else
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
		}
		// [L2J_JP ADD START]
		else if (command.startsWith("admin_sethero") || command.startsWith("admin_manualhero"))
		{
			L2Object target = activeChar.getTarget();
			L2Player player = null;
			if (target instanceof L2Player)
				player = (L2Player)target;
			else
				return false;
			player.setHero(!player.isHero());
			if (player.isHero())
				player.broadcastPacket(new SocialAction(player.getObjectId(), 16));
			player.sendMessage("Admin changed your hero status");
			player.broadcastUserInfo();
		}
		// [L2J_JP ADD END]
		else if (command.equals("admin_remclanwait"))
		{
			L2Object target = activeChar.getTarget();
			L2Player player = null;
			if (target instanceof L2Player)
			{
				player = (L2Player)target;
			}
			else
			{
				return false;
			}
			
			if (player.getClan() == null)
			{
				player.setClanJoinExpiryTime(0);
				player.setClanCreateExpiryTime(0);
				player.sendMessage("A GM has reset your clan wait time, You may now join another clan or create one.");
				activeChar.sendMessage("You have reset " + player.getName()
						+ "'s wait time to join/create another clan.");
			}
			else
			{
				activeChar.sendMessage("Sorry, but " + player.getName()
						+ " must not be in a clan. Player must leave clan before the wait limit can be reset.");
			}
		}
		else if (command.startsWith("admin_find_dualbox"))
		{
			int multibox = 2;
			try
			{
				String val = command.substring(19);
				multibox = Integer.parseInt(val);
				if (multibox < 1)
				{
					activeChar.sendMessage("Usage: //find_dualbox [number > 0]");
					return false;
				}
			}
			catch (Exception e)
			{
			}
			findDualbox(activeChar, multibox);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void listCharacters(L2Player activeChar, int page)
	{
		Collection<L2Player> allPlayers = L2World.getInstance().getAllPlayers();
		L2Player[] players = allPlayers.toArray(new L2Player[allPlayers.size()]);
		
		int MaxCharactersPerPage = 20;
		int MaxPages = players.length / MaxCharactersPerPage;
		
		if (players.length > MaxCharactersPerPage * MaxPages)
			MaxPages++;
		
		//Check if number of users changed
		if (page > MaxPages)
			page = MaxPages;
		
		int CharactersStart = MaxCharactersPerPage * page;
		int CharactersEnd = players.length;
		if (CharactersEnd - CharactersStart > MaxCharactersPerPage)
			CharactersEnd = CharactersStart + MaxCharactersPerPage;
		
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/charlist.htm");
		StringBuilder replyMSG = new StringBuilder();
		for (int x = 0; x < MaxPages; x++)
		{
			int pagenr = x + 1;
			replyMSG.append("<center><a action=\"bypass -h admin_show_characters " + x + "\">Page " + pagenr
					+ "</a></center>");
		}
		adminReply.replace("%pages%", replyMSG.toString());
		replyMSG.setLength(0);
		for (int i = CharactersStart; i < CharactersEnd; i++)
		{ //Add player info into new Table row
			replyMSG.append("<tr><td width=80><a action=\"bypass -h admin_character_info " + players[i].getName()
					+ "\">" + players[i].getName() + "</a></td><td width=110>"
					+ players[i].getTemplate().getClassName() + "</td><td width=40>" + players[i].getLevel()
					+ "</td></tr>");
		}
		adminReply.replace("%players%", replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private void showCharacterInfo(L2Player activeChar, L2Player player)
	{
		if (player == null)
		{
			L2Object target = activeChar.getTarget();
			if (target instanceof L2Player)
				player = (L2Player)target;
			else
				return;
		}
		else
			activeChar.setTarget(player);
		gatherCharacterInfo(activeChar, player, "charinfo.htm");
	}
	
	/**
	 * @param activeChar
	 * @param player
	 */
	public static void gatherCharacterInfo(L2Player activeChar, L2Player player, String filename)
	{
		String ip = "N/A";
		String account = "N/A";
		try
		{
			account = player.getAccountName();
			ip = player.getClient().getHostAddress();
		}
		catch (Exception e)
		{
		}
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/" + filename);
		adminReply.replace("%name%", player.getName());
		adminReply.replace("%level%", String.valueOf(player.getLevel()));
		adminReply.replace("%clan%", String.valueOf(ClanTable.getInstance().getClan(player.getClanId())));
		adminReply.replace("%xp%", String.valueOf(player.getExp()));
		adminReply.replace("%sp%", String.valueOf(player.getSp()));
		adminReply.replace("%class%", player.getTemplate().getClassName());
		adminReply.replace("%ordinal%", String.valueOf(player.getClassId().ordinal()));
		adminReply.replace("%classid%", String.valueOf(player.getClassId()));
		adminReply.replace("%x%", String.valueOf(player.getX()));
		adminReply.replace("%y%", String.valueOf(player.getY()));
		adminReply.replace("%z%", String.valueOf(player.getZ()));
		adminReply.replace("%currenthp%", String.valueOf((int)player.getStatus().getCurrentHp()));
		adminReply.replace("%maxhp%", String.valueOf(player.getMaxHp()));
		adminReply.replace("%karma%", String.valueOf(player.getKarma()));
		adminReply.replace("%currentmp%", String.valueOf((int)player.getStatus().getCurrentMp()));
		adminReply.replace("%maxmp%", String.valueOf(player.getMaxMp()));
		adminReply.replace("%pvpflag%", String.valueOf(player.getPvpFlag()));
		adminReply.replace("%currentcp%", String.valueOf((int)player.getStatus().getCurrentCp()));
		adminReply.replace("%maxcp%", String.valueOf(player.getMaxCp()));
		adminReply.replace("%pvpkills%", String.valueOf(player.getPvpKills()));
		adminReply.replace("%pkkills%", String.valueOf(player.getPkKills()));
		adminReply.replace("%currentload%", String.valueOf(player.getCurrentLoad()));
		adminReply.replace("%maxload%", String.valueOf(player.getMaxLoad()));
		adminReply.replace("%percent%",
				String.valueOf(Util.roundTo(((float)player.getCurrentLoad() / (float)player.getMaxLoad()) * 100, 2)));
		adminReply.replace("%patk%", String.valueOf(player.getPAtk(null)));
		adminReply.replace("%matk%", String.valueOf(player.getMAtk(null, null)));
		adminReply.replace("%pdef%", String.valueOf(player.getPDef(null)));
		adminReply.replace("%mdef%", String.valueOf(player.getMDef(null, null)));
		adminReply.replace("%accuracy%", String.valueOf(player.getAccuracy()));
		adminReply.replace("%evasion%", String.valueOf(player.getEvasionRate()));
		adminReply.replace("%critical%", String.valueOf(player.getCriticalHit()));
		adminReply.replace("%runspeed%", String.valueOf(player.getRunSpeed()));
		adminReply.replace("%patkspd%", String.valueOf(player.getPAtkSpd()));
		adminReply.replace("%matkspd%", String.valueOf(player.getMAtkSpd()));
		adminReply.replace("%access%", String.valueOf(player.getAccessLevel()));
		adminReply.replace("%account%", account);
		adminReply.replace("%ip%", ip);
		activeChar.sendPacket(adminReply);
	}
	
	private void setTargetKarma(L2Player activeChar, int newKarma)
	{
		// function to change karma of selected char
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
			return;
		
		if (newKarma >= 0)
		{
			// for display
			int oldKarma = player.getKarma();
			// update karma
			player.setKarma(newKarma);
			//Common character information
			player.sendPacket(new SystemMessage(SystemMessageId.YOUR_KARMA_HAS_BEEN_CHANGED_TO_S1).addString(String
					.valueOf(newKarma)));
			//Admin information
			if (player != activeChar)
				activeChar.sendMessage("Successfully Changed karma for " + player.getName() + " from (" + oldKarma
						+ ") to (" + newKarma + ").");
		}
		else
		{
			// tell admin of mistake
			activeChar.sendMessage("You must enter a value for karma greater than or equal to 0.");
		}
	}
	
	private void adminModifyCharacter(L2Player activeChar, String modifications)
	{
		L2Object target = activeChar.getTarget();
		
		if (!(target instanceof L2Player))
			return;
		
		L2Player player = (L2Player)target;
		StringTokenizer st = new StringTokenizer(modifications);
		
		if (st.countTokens() != 6)
		{
			editCharacter(player);
			return;
		}
		
		String hp = st.nextToken();
		String mp = st.nextToken();
		String cp = st.nextToken();
		String pvpflag = st.nextToken();
		String pvpkills = st.nextToken();
		String pkkills = st.nextToken();
		
		int hpval = Integer.parseInt(hp);
		int mpval = Integer.parseInt(mp);
		int cpval = Integer.parseInt(cp);
		int pvpflagval = Integer.parseInt(pvpflag);
		int pvpkillsval = Integer.parseInt(pvpkills);
		int pkkillsval = Integer.parseInt(pkkills);
		
		//Common character information
		player.sendMessage("Admin has changed your stats." + "  HP: " + hpval + "  MP: " + mpval + "  CP: " + cpval
				+ "  PvP Flag: " + pvpflagval + " PvP/PK " + pvpkillsval + "/" + pkkillsval);
		player.getStatus().setCurrentHp(hpval);
		player.getStatus().setCurrentMp(mpval);
		player.getStatus().setCurrentCp(cpval);
		player.updatePvPFlag(pvpflagval);
		player.setPvpKills(pvpkillsval);
		player.setPkKills(pkkillsval);
		
		// Save the changed parameters to the database.
		player.store();
		
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_HP, hpval);
		su.addAttribute(StatusUpdate.MAX_HP, player.getMaxHp());
		su.addAttribute(StatusUpdate.CUR_MP, mpval);
		su.addAttribute(StatusUpdate.MAX_MP, player.getMaxMp());
		su.addAttribute(StatusUpdate.CUR_CP, cpval);
		su.addAttribute(StatusUpdate.MAX_CP, player.getMaxCp());
		player.sendPacket(su);
		
		//Admin information
		player.sendMessage("Changed stats of " + player.getName() + "." + "  HP: " + hpval + "  MP: " + mpval
				+ "  CP: " + cpval + "  PvP: " + pvpflagval + " / " + pvpkillsval);
		
		if (_log.isDebugEnabled())
			_log.debug("[GM]" + activeChar.getName() + " changed stats of " + player.getName() + ". " + " HP: " + hpval
					+ " MP: " + mpval + " CP: " + cpval + " PvP: " + pvpflagval + " / " + pvpkillsval);
		
		showCharacterInfo(activeChar, null); //Back to start
		
		player.broadcastUserInfo();
		player.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		player.decayMe();
		player.spawnMe(activeChar.getX(), activeChar.getY(), activeChar.getZ());
	}
	
	private void editCharacter(L2Player activeChar)
	{
		L2Object target = activeChar.getTarget();
		if (!(target instanceof L2Player))
			return;
		L2Player player = (L2Player)target;
		gatherCharacterInfo(activeChar, player, "charedit.htm");
	}
	
	/**
	 * @param activeChar
	 * @param CharacterToFind
	 */
	private void findCharacter(L2Player activeChar, String CharacterToFind)
	{
		int CharactersFound = 0;
		String name;
		Collection<L2Player> allPlayers = L2World.getInstance().getAllPlayers();
		L2Player[] players = allPlayers.toArray(new L2Player[allPlayers.size()]);
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/charfind.htm");
		StringBuilder replyMSG = new StringBuilder();
		for (L2Player element : players)
		{ //Add player info into new Table row
			name = element.getName();
			if (name.toLowerCase().contains(CharacterToFind.toLowerCase()))
			{
				CharactersFound = CharactersFound + 1;
				replyMSG.append("<tr><td width=80><a action=\"bypass -h admin_character_list " + name + "\">" + name
						+ "</a></td><td width=110>" + element.getTemplate().getClassName() + "</td><td width=40>"
						+ element.getLevel() + "</td></tr>");
			}
			if (CharactersFound > 20)
				break;
		}
		adminReply.replace("%results%", replyMSG.toString());
		replyMSG.setLength(0);
		if (CharactersFound == 0)
			replyMSG.append("s. Please try again.");
		else if (CharactersFound > 20)
		{
			adminReply.replace("%number%", " more than 20");
			replyMSG.append("s.<br>Please refine your search to see all of the results.");
		}
		else if (CharactersFound == 1)
			replyMSG.append(".");
		else
			replyMSG.append("s.");
		adminReply.replace("%number%", String.valueOf(CharactersFound));
		adminReply.replace("%end%", replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	/**
	 * @param activeChar
	 * @param IpAdress
	 * @throws IllegalArgumentException
	 */
	private void findCharactersPerIp(L2Player activeChar, String IpAdress) throws IllegalArgumentException
	{
		if (!IpAdress
				.matches("^(?:(?:[0-9]|[1-9][0-9]|1[0-9][0-9]|2(?:[0-4][0-9]|5[0-5]))\\.){3}(?:[0-9]|[1-9][0-9]|1[0-9][0-9]|2(?:[0-4][0-9]|5[0-5]))$"))
			throw new IllegalArgumentException("Malformed IPv4 number");
		
		Collection<L2Player> allPlayers = L2World.getInstance().getAllPlayers();
		L2Player[] players = allPlayers.toArray(new L2Player[allPlayers.size()]);
		int CharactersFound = 0;
		L2Client client;
		String name, ip = "0.0.0.0";
		StringBuilder replyMSG = new StringBuilder();
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/ipfind.htm");
		for (L2Player player : players)
		{
			client = player.getClient();
			
			if (client == null)
				continue;
			
			ip = client.getHostAddress();
			if (!ip.equals(IpAdress))
				continue;
			
			name = player.getName();
			CharactersFound = CharactersFound + 1;
			replyMSG.append("<tr><td width=80><a action=\"bypass -h admin_character_list " + name + "\">" + name
					+ "</a></td><td width=110>" + player.getTemplate().getClassName() + "</td><td width=40>"
					+ player.getLevel() + "</td></tr>");
			
			if (CharactersFound > 20)
				break;
		}
		adminReply.replace("%results%", replyMSG.toString());
		replyMSG.setLength(0);
		if (CharactersFound == 0)
			replyMSG.append("s. Maybe they got d/c? :)");
		else if (CharactersFound > 20)
		{
			adminReply.replace("%number%", " more than " + String.valueOf(CharactersFound));
			replyMSG.append("s.<br>In order to avoid you a client crash I won't <br1>display results beyond the 20th character.");
		}
		else if (CharactersFound == 1)
			replyMSG.append(".");
		else
			replyMSG.append("s.");
		adminReply.replace("%ip%", ip);
		adminReply.replace("%number%", String.valueOf(CharactersFound));
		adminReply.replace("%end%", replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	/**
	 * @param activeChar
	 * @param characterName
	 * @throws IllegalArgumentException
	 */
	private void findCharactersPerAccount(L2Player activeChar, String characterName)
			throws IllegalArgumentException
	{
		if (Config.CNAME_PATTERN.matcher(characterName).matches())
		{
			String account = null;
			Map<Integer, String> chars;
			L2Player player = L2World.getInstance().getPlayer(characterName);
			if (player == null)
				throw new IllegalArgumentException("Player doesn't exist");
			chars = player.getAccountChars();
			account = player.getAccountName();
			StringBuilder replyMSG = new StringBuilder();
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
			adminReply.setFile("data/html/admin/accountinfo.htm");
			for (String charname : chars.values())
				replyMSG.append(charname + "<br1>");
			adminReply.replace("%characters%", replyMSG.toString());
			adminReply.replace("%account%", account);
			adminReply.replace("%player%", characterName);
			activeChar.sendPacket(adminReply);
		}
		else
			throw new IllegalArgumentException("Malformed character name");
	}
	
	/**
	 * @param activeChar
	 * @throws IllegalArgumentException
	 */
	private void findDualbox(L2Player activeChar, int multibox) throws IllegalArgumentException
	{
		Collection<L2Player> allPlayers = L2World.getInstance().getAllPlayers();
		L2Player[] players = allPlayers.toArray(new L2Player[allPlayers.size()]);
		
		Map<String, List<L2Player>> ipMap = new HashMap<String, List<L2Player>>();
		
		String ip = "0.0.0.0";
		L2Client client;
		
		final Map<String, Integer> dualboxIPs = new HashMap<String, Integer>();
		
		for (L2Player player : players)
		{
			client = player.getClient();
			if (player.isInOfflineMode())
				continue;
			else
			{
				ip = client.getHostAddress();
				if (ipMap.get(ip) == null)
					ipMap.put(ip, new ArrayList<L2Player>());
				ipMap.get(ip).add(player);
				
				if (ipMap.get(ip).size() >= multibox)
				{
					Integer count = dualboxIPs.get(ip);
					if (count == null)
						dualboxIPs.put(ip, multibox);
					else
						dualboxIPs.put(ip, count + 1);
				}
			}
		}
		
		List<String> keys = new ArrayList<String>(dualboxIPs.keySet());
		Collections.sort(keys, new Comparator<String>() {
			@Override
			public int compare(String left, String right)
			{
				return dualboxIPs.get(left).compareTo(dualboxIPs.get(right));
			}
		});
		Collections.reverse(keys);
		
		final StringBuilder results = new StringBuilder();
		for (String dualboxIP : keys)
		{
			results.append("<a action=\"bypass -h admin_find_ip ").append(dualboxIP).append("\">").append(dualboxIP)
					.append(" (").append(dualboxIPs.get(dualboxIP)).append(")</a><br1>");
		}
		
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/dualbox.htm");
		adminReply.replace("%multibox%", String.valueOf(multibox));
		adminReply.replace("%results%", results.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private final class Untransform implements Runnable
	{
		private final L2Player _player;
		
		private Untransform(L2Player player)
		{
			_player = player;
		}
		
		@Override
		public void run()
		{
			_player.untransform();
		}
	}
}
