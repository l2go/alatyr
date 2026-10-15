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

public final class SendL2ParamSetting extends L2ClientPacket
{
	// currently server acts as opcode is ch?!
	private static final String _C__SENDL2PARAMSETTING = "[C] 4A:03 SendL2ParamSetting cc[dd]";
	
	private int _unk1, _unk2;
	
	@Override
	protected void readImpl()
	{
		_unk1 = readD();
		_unk2 = readD();
	}
	
	@Override
	protected void runImpl()
	{
		_log.info("SendL2ParamSetting, unk=" + _unk1 + ", unk=" + _unk2 + ", sent by " + getActiveChar());
		requestFailed(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
	}
	
	@Override
	public String getType()
	{
		return _C__SENDL2PARAMSETTING;
	}
}
