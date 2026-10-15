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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

public class AdminBuffs implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_getbuffs", "admin_stopbuff", "admin_stopallbuffs",
			"admin_areacancel" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_getbuffs"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			command = st.nextToken();
			L2Player target;
			
			if (st.hasMoreTokens())
			{
				String playername = st.nextToken();
				target = L2World.getInstance().getPlayer(playername);
				
				if (target != null)
				{
					showBuffs(target, activeChar);
					return true;
				}
				
				activeChar.sendMessage("The player " + playername + " is not online");
				return false;
			}
			else if ((target = activeChar.getTarget(L2Player.class)) != null)
			{
				showBuffs(target, activeChar);
				return true;
			}
			else
				return true;
		}
		
		else if (command.startsWith("admin_stopbuff"))
		{
			try
			{
				StringTokenizer st = new StringTokenizer(command, " ");
				st.nextToken();
				String playername = st.nextToken();
				int SkillId = Integer.parseInt(st.nextToken());
				
				removeBuff(activeChar, playername, SkillId);
				return true;
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Failed removing effect: " + e.getMessage());
				activeChar.sendMessage("Usage: //stopbuff <playername> [skillId]");
				return false;
			}
		}
		else if (command.startsWith("admin_stopallbuffs"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			String playername = st.nextToken();
			if (playername != null)
			{
				removeAllBuffs(activeChar, playername);
				return true;
			}
			
			return false;
		}
		else if (command.startsWith("admin_areacancel"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			String val = st.nextToken();
			try
			{
				int radius = Integer.parseInt(val);
				
				for (L2Creature knownChar : activeChar.getKnownList().getKnownCharactersInRadius(radius))
				{
					if ((knownChar instanceof L2Player) && !(knownChar.equals(activeChar)))
						knownChar.stopAllEffectsExceptThoseThatLastThroughDeath();
				}
				
				activeChar.sendMessage("All effects canceled within raidus " + radius);
				return true;
			}
			catch (NumberFormatException e)
			{
				activeChar.sendMessage("Usage: //areacancel <radius>");
				return false;
			}
		}
		else
			return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	public void showBuffs(L2Player player, L2Player activeChar)
	{
		StringBuilder html = new StringBuilder("<html><center><font color=\"LEVEL\">Effects of ");
		html.append(player.getName());
		html.append("</font><center><br>");
		
		L2Effect[] effects = player.getAllEffects();
		
		html.append("<table>");
		html.append("<tr><td width=200>Skill</td><td width=70>Action</td></tr>");
		
		for (L2Effect e : effects)
		{
			if (e != null)
			{
				html.append("<tr><td>");
				html.append(e.getSkill().getName());
				html.append("</td><td><button value=\"Remove\" action=\"bypass -h admin_stopbuff ");
				html.append(player.getName());
				html.append(' ');
				html.append(e.getSkill().getId());
				html.append("\" width=60 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td></tr>");
			}
		}
		
		html.append("</table><br>");
		html.append("<button value=\"Remove All\" action=\"bypass -h admin_stopallbuffs ");
		html.append(player.getName());
		html.append("\" width=60 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\">");
		html.append("</html>");
		
		NpcHtmlMessage ms = new NpcHtmlMessage(activeChar.getObjectId());
		ms.setHtml(html.toString());
		activeChar.sendPacket(ms);
	}
	
	private void removeBuff(L2Player remover, String playername, int skillId)
	{
		L2Player player = L2World.getInstance().getPlayer(playername);
		if (player != null && skillId > 0)
		{
			L2Effect[] effects = player.getAllEffects();
			
			for (L2Effect e : effects)
			{
				if (e == null)
					continue;
				else if (e.getId() == skillId)
				{
					e.exit();
					remover.sendMessage("Removed " + e.getSkill().getName() + " level " + e.getLevel() + " from "
							+ playername);
				}
			}
			showBuffs(player, remover);
		}
	}
	
	private void removeAllBuffs(L2Player remover, String playername)
	{
		L2Player player = L2World.getInstance().getPlayer(playername);
		if (player != null)
		{
			player.stopAllEffectsExceptThoseThatLastThroughDeath();
			remover.sendMessage("Removed all effects from " + playername);
			showBuffs(player, remover);
		}
		else
		{
			remover.sendMessage("The player " + playername + " is not online");
		}
	}
}
