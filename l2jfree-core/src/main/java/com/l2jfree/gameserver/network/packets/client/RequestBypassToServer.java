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
package com.l2jfree.gameserver.network.packets.client;

import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.communitybbs.CommunityBoard;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantSummonInstance;
import com.l2jfree.gameserver.handler.AdminCommandHandler;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExHeroList;
import com.l2jfree.gameserver.network.packets.server.GMViewPledgeInfo;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.network.InvalidPacketException;

/**
 * This class represents a packet sent when player clicks a link in the chat dialog.
 * In HTML files it is <a action="bypass -h command"/>
 * 
 * @version $Revision: 1.12.4.5 $ $Date: 2005/04/11 10:06:11 $
 */
public class RequestBypassToServer extends L2ClientPacket
{
	private static final String _C__21_REQUESTBYPASSTOSERVER = "[C] 21 RequestBypassToServer";
	
	// S
	private String _command;
	
	@Override
	protected void readImpl()
	{
		_command = readS();
	}
	
	@Override
	protected void runImpl() throws InvalidPacketException
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (_command.startsWith("admin_"))
			AdminCommandHandler.getInstance().useAdminCommand(activeChar, _command);
		else if (_command.equals("come_here") && activeChar.getAccessLevel() >= Config.GM_ACCESSLEVEL)
			comeHere(activeChar);
		else if (_command.startsWith("show_clan_info "))
		{
			// The alliance window builds this bypass. Anyone else, and an unknown name, must not
			// receive the roster. A null clan used to throw inside the packet and drop the session.
			L2Clan clan = ClanTable.getInstance().getClanByName(_command.substring(15));
			if (!mayViewClanRoster(activeChar, clan))
			{
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			activeChar.sendPacket(new GMViewPledgeInfo(clan, activeChar));
		}
		else if (_command.startsWith("player_help "))
			playerHelp(activeChar, _command.substring(12));
		else if (_command.startsWith("npc_"))
		{
			activeChar.validateBypass(_command);
			
			int endOfId = _command.indexOf('_', 5);
			String id;
			if (endOfId > 0)
				id = _command.substring(4, endOfId);
			else
				id = _command.substring(4);
			try
			{
				int objectId = Integer.parseInt(id);
				
				L2Npc npc = activeChar.getTarget(L2Npc.class, objectId);
				
				if (npc != null && endOfId > 0
						&& activeChar.isInsideRadius(npc, L2Npc.INTERACTION_DISTANCE, false, false))
				{
					String realCommand = _command.substring(endOfId + 1);
					
					if (GlobalRestrictions.onBypassFeedback(npc, activeChar, realCommand))
					{
					}
					else
						npc.onBypassFeedback(activeChar, realCommand);
				}
				
				sendPacket(ActionFailed.STATIC_PACKET);
			}
			catch (NumberFormatException nfe)
			{
			}
		}
		else if (_command.startsWith("summon_"))
		{
			activeChar.validateBypass(_command);
			
			int endOfId = _command.indexOf('_', 8);
			String id;
			if (endOfId > 0)
				id = _command.substring(7, endOfId);
			else
				id = _command.substring(7);
			try
			{
				int objectId = Integer.parseInt(id);
				
				L2MerchantSummonInstance summon = activeChar.getTarget(L2MerchantSummonInstance.class, objectId);
				
				if (summon != null && endOfId > 0
						&& activeChar.isInsideRadius(summon, L2Npc.INTERACTION_DISTANCE, false, false))
				{
					summon.onBypassFeedback(activeChar, _command.substring(endOfId + 1));
				}
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			}
			catch (NumberFormatException nfe)
			{
			}
		}
		//  Draw a Symbol
		else if (_command.equals("menu_select?ask=-16&reply=1"))
		{
			activeChar.validateBypass(_command);
			
			L2Object object = activeChar.getTarget();
			if (object instanceof L2Npc)
				((L2Npc)object).onBypassFeedback(activeChar, _command);
		}
		else if (_command.equals("menu_select?ask=-16&reply=2"))
		{
			//activeChar.validateBypass(_command); // FIXME: shouldn't we validate here too?
			
			L2Object object = activeChar.getTarget();
			if (object instanceof L2Npc)
				((L2Npc)object).onBypassFeedback(activeChar, _command);
		}
		// Navigate throught Manor windows
		else if (_command.startsWith("manor_menu_select?"))
		{
			//activeChar.validateBypass(_command); // FIXME: shouldn't we validate here too?
			
			L2Object object = activeChar.getTarget();
			if (object instanceof L2Npc)
				((L2Npc)object).onBypassFeedback(activeChar, _command);
		}
		else if (_command.startsWith("bbs_"))
			CommunityBoard.handleCommands(getClient(), _command);
		else if (_command.startsWith("_bbs"))
			CommunityBoard.handleCommands(getClient(), _command);
		else if (_command.startsWith("_maillist_0_1_0_"))
			CommunityBoard.handleCommands(getClient(), _command);
		else if (_command.startsWith("Quest "))
		{
			activeChar.validateBypass(_command);
			
			String p = _command.substring(6).trim();
			int idx = p.indexOf(' ');
			if (idx < 0)
				activeChar.processQuestEvent(p, "");
			else
				activeChar.processQuestEvent(p.substring(0, idx), p.substring(idx).trim());
		}
		else if (_command.startsWith("OlympiadArenaChange"))
			Olympiad.bypassChangeArena(_command, activeChar);
		else if (_command.startsWith("_herolist"))
		{
			activeChar.sendPacket(new ExHeroList());
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/** A GM, or a member of the same alliance. Two clans with no alliance do not match. */
	private static boolean mayViewClanRoster(L2Player player, L2Clan clan)
	{
		if (clan == null)
			return false;
		if (player.getAccessLevel() >= Config.GM_ACCESSLEVEL)
			return true;
		int allyId = player.getAllyId();
		return allyId > 0 && allyId == clan.getAllyId();
	}
	
	private void comeHere(L2Player activeChar)
	{
		L2Object obj = activeChar.getTarget();
		if (obj instanceof L2Npc)
		{
			L2Npc temp = (L2Npc)obj;
			temp.setTarget(activeChar);
			temp.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO,
					new L2CharPosition(activeChar.getX(), activeChar.getY(), activeChar.getZ(), 0));
		}
	}
	
	private void playerHelp(L2Player activeChar, String path)
	{
		if (path.indexOf("..") != -1)
			return;
		
		StringTokenizer st = new StringTokenizer(path);
		String[] cmd = st.nextToken().split("#");
		
		if (cmd.length > 1)
		{
			int itemId = 0;
			itemId = Integer.parseInt(cmd[1]);
			String filename = "data/html/help/" + cmd[0];
			NpcHtmlMessage html = new NpcHtmlMessage(1, itemId);
			html.setFile(filename);
			html.disableValidation();
			activeChar.sendPacket(html);
		}
		else
		{
			String filename = "data/html/help/" + path;
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile(filename);
			html.disableValidation();
			activeChar.sendPacket(html);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__21_REQUESTBYPASSTOSERVER;
	}
}
