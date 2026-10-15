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

import com.l2jfree.gameserver.cache.CrestCache;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.AllyCrest;

/**
 * This class represents a packet sent by the client when it needs to display an alliance
 * crest.
 * 
 * @version $Revision: 1.3.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestAllyCrest extends L2ClientPacket
{
	private static final String _C__88_REQUESTALLYCREST = "[C] 88 RequestAllyCrest";
	
	private int _crestId;
	
	/**
	 * packet type id 0x88
	 * format: cd
	 */
	@Override
	protected void readImpl()
	{
		_crestId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		if (_log.isDebugEnabled())
			_log.debug("allycrestid " + _crestId + " requested");
		
		byte[] data = CrestCache.getInstance().getAllyCrest(_crestId);
		
		if (data != null)
			sendPacket(new AllyCrest(_crestId, data));
		else if (_log.isDebugEnabled())
			_log.debug("allycrest is missing:" + _crestId);
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__88_REQUESTALLYCREST;
	}
}
