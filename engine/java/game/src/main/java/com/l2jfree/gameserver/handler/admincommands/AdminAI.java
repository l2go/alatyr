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

import java.util.Map;
import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Attackable.AggroInfo;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

public class AdminAI implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_show_ai" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (command.equals("admin_show_ai"))
		{
			L2Object target = activeChar.getTarget();
			if (!(target instanceof L2Creature) || !((L2Creature)target).hasAI())
			{
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				return false;
			}
			
			L2CreatureAI ai = ((L2Creature)target).getAI();
			CtrlIntention intention = ai.getIntention();
			String param0 = ai.getIntentionArg0() == null ? "--" : ai.getIntentionArg0().toString();
			String param1 = ai.getIntentionArg1() == null ? "--" : ai.getIntentionArg1().toString();
			
			NpcHtmlMessage html = new NpcHtmlMessage(target.getObjectId());
			StringBuilder html1 =
					new StringBuilder("<html><body><center><font color=\"LEVEL\">AI Information</font></center><br><br>");
			
			html1.append("<font color=\"LEVEL\">Intention</font>");
			html1.append("<table border=\"0\" width=\"100%\">");
			html1.append("<tr><td>Intention:</td><td>");
			html1.append(intention.toString());
			html1.append("</td></tr>");
			html1.append("<tr><td>Parameter0:</td><td>");
			html1.append(param0);
			html1.append("</td></tr>");
			html1.append("<tr><td>Parameter1:</td><td>");
			html1.append(param1);
			html1.append("</td></tr>");
			html1.append("</table><br><br>");
			
			if (target instanceof L2Attackable)
			{
				html1.append("<font color=\"LEVEL\">Aggrolist</font>");
				html1.append("<table border=\"0\" width=\"100%\">");
				for (Map.Entry<L2Creature, AggroInfo> entry : ((L2Attackable)target).getAggroListRP().entrySet())
				{
					L2Creature attacker = entry.getKey();
					AggroInfo a = entry.getValue();
					html1.append("<tr><td>");
					html1.append(attacker.getName());
					html1.append("</td><td>");
					html1.append(a.getHate());
					html1.append(" (");
					html1.append(a.getDamage());
					html1.append(")</td></tr>");
				}
				html1.append("</table><br><br>");
			}
			
			html1.append("<button value=\"Refresh\" action=\"bypass -h admin_show_ai\" width=60 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
			
			html1.append("</body></html>");
			html.setHtml(html1.toString());
			activeChar.sendPacket(html);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
