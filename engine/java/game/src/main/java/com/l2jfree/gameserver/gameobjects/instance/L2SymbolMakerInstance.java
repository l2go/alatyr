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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.packets.server.HennaEquipList;
import com.l2jfree.gameserver.network.packets.server.HennaRemoveList;

public class L2SymbolMakerInstance extends L2Npc
{
	private static final String HTML_PATH = "data/html/symbolmaker/SymbolMaker.htm";
	
	public L2SymbolMakerInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.equals("Draw"))
			player.sendPacket(new HennaEquipList(player));
		else if (command.equals("RemoveList"))
			// l2jserver doesn't send this if player has 0 dyes
			player.sendPacket(new HennaRemoveList(player));
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return HTML_PATH;
	}
}
