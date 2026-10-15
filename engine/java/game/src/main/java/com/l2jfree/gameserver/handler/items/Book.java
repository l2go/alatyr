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
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

public class Book implements IItemHandler
{
	// All the item IDs that this handler knows.
	private static final int[] ITEM_IDS = { 5588, 6317, 7561, 7063, 7064, 7065, 7066, 7082, 7083, 7084, 7085, 7086,
			7087, 7088, 7089, 7090, 7091, 7092, 7093, 7094, 7095, 7096, 7097, 7098, 7099, 7100, 7101, 7102, 7103, 7104,
			7105, 7106, 7107, 7108, 7109, 7110, 7111, 7112, 8059, 13130, 13131, 13132, 13133, 13134, 13135, 13136 };
	
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2Player))
			return;
		
		L2Player activeChar = (L2Player)playable;
		final int itemId = item.getItemId();
		
		String filename = "data/html/help/" + item.getItemId() + ".htm";
		String content = HtmCache.getInstance().getHtm(filename);
		
		if (content == null)
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setHtml("<html><body>My Text is missing:<br>" + filename + "</body></html>");
			activeChar.sendPacket(html);
		}
		else
		{
			NpcHtmlMessage itemReply = new NpcHtmlMessage(5, itemId);
			itemReply.setHtml(content);
			itemReply.disableValidation();
			activeChar.sendPacket(itemReply);
		}
		
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
