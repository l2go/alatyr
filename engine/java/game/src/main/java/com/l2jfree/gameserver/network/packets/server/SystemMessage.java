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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.network.SystemMessageId;

public final class SystemMessage extends AbstractSystemMessage<SystemMessage>
{
	private static final String _S__62_SYSTEMMESSAGE = "[S] 62 SystemMessage";
	
	public SystemMessage(SystemMessageId messageId)
	{
		super(messageId);
	}
	
	public SystemMessage(int messageId)
	{
		super(messageId);
	}
	
	public static SystemMessage sendString(String msg)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.S1);
		sm.addString(msg);
		return sm;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x62);
		writeMessageIdAndElements();
	}
	
	@Override
	public String getType()
	{
		return _S__62_SYSTEMMESSAGE;
	}
}
