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

import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public final class RequestWithDrawPremiumItem extends L2ClientPacket
{
	private static final String _C__REQUESTWITHDRAWPREMIUMITEM = "[C] D0:52 RequestWriteHeroWords ch[qq]";
	
	private long _unk1, _unk2;
	
	@Override
	protected void readImpl()
	{
		_unk1 = readCompQ();
		_unk2 = readCompQ();
	}
	
	@Override
	protected void runImpl()
	{
		_log.info("RequestWithDrawPremiumItem, unk=" + _unk1 + ", unk=" + _unk2 + ", sent by " + getActiveChar());
		requestFailed(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTWITHDRAWPREMIUMITEM;
	}
}
