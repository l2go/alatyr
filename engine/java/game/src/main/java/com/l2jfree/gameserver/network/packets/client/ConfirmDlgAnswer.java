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
import com.l2jfree.network.InvalidPacketException;

public final class ConfirmDlgAnswer extends L2ClientPacket
{
	private static final String _C__CONFIRMDLG = "[C] C6 ConfirmDlg c[ddd]";
	
	private int _messageId;
	private int _answer;
	private int _requesterId;
	
	@Override
	protected void readImpl()
	{
		_messageId = readD();
		_answer = readD();
		_requesterId = readD();
	}
	
	@Override
	protected void runImpl() throws InvalidPacketException
	{
		L2Player cha = getActiveChar();
		if (cha == null)
			return;
		
		final AnswerHandler handler = cha.setAnswerHandler(null);
		
		if (handler != null)
		{
			if (handler._requesterId == _requesterId)
				handler.handle(_answer != 0);
			else
				throw new InvalidPacketException("Wrong requesterId for " + getType() + ": messageId=" + _messageId
						+ ", answer=" + _answer + ", requesterId=" + _requesterId);
		}
		else
			throw new InvalidPacketException("Missing handler for " + getType() + ": messageId=" + _messageId
					+ ", answer=" + _answer + ", requesterId=" + _requesterId);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__CONFIRMDLG;
	}
	
	public static abstract class AnswerHandler
	{
		public abstract void handle(boolean answer);
		
		private int _requesterId;
		
		public final void setRequesterId(int requesterId)
		{
			_requesterId = requesterId;
		}
	}
}
