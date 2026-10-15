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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.NpcLinkHtmlMessage;
import com.l2jfree.gameserver.util.Util;

/**
 * Lets drink to code!
 * 
 * @author zabbix
 */
public final class RequestLinkHtml extends L2ClientPacket
{
	private static final String REQUESTLINKHTML__C__20 = "[C] 20 RequestLinkHtml";
	
	private String _link;
	
	@Override
	protected void readImpl()
	{
		_link = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player actor = getClient().getActiveChar();
		if (actor == null)
			return;
		
		if (_link.contains("..") || !_link.contains(".htm"))
		{
			Util.handleIllegalPlayerAction(actor, "[RequestLinkHtml] hack by " + actor.getName()
					+ "? link contains prohibited characters: '" + _link + "', skipped");
			return;
		}
		
		try
		{
			String filename = "data/html/" + _link;
			NpcLinkHtmlMessage msg = new NpcLinkHtmlMessage();
			msg.setFile(filename);
			sendPacket(msg);
		}
		catch (Exception e)
		{
			_log.warn("Bad RequestLinkHtml: ", e);
		}
	}
	
	@Override
	public String getType()
	{
		return REQUESTLINKHTML__C__20;
	}
}
