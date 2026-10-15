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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.client.ConfirmDlgAnswer.AnswerHandler;
import com.l2jfree.tools.random.Rnd;

/**
 * Format: c dd[d s/d/dd/ddd] dd
 * 
 * @author kombat
 */
public final class ConfirmDlg extends AbstractSystemMessage<ConfirmDlg>
{
	private static final String _S__F3_CONFIRMDLG = "[S] f3 ConfirmDlg";
	
	private int _time = 0;
	private final int _requesterId = Rnd.get(Integer.MAX_VALUE);
	private AnswerHandler _answerHandler;
	
	public ConfirmDlg(SystemMessageId messageId)
	{
		super(messageId);
	}
	
	public ConfirmDlg(int messageId)
	{
		super(messageId);
	}
	
	public static ConfirmDlg sendString(String msg)
	{
		ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.S1);
		dlg.addString(msg);
		return dlg;
	}
	
	public ConfirmDlg addTime(int time)
	{
		_time = time;
		return this;
	}
	
	//public ConfirmDlg addRequesterId(int id)
	//{
	//	_requesterId = id;
	//	return this;
	//}
	
	public ConfirmDlg addAnswerHandler(AnswerHandler answerHandler)
	{
		_answerHandler = answerHandler;
		_answerHandler.setRequesterId(_requesterId);
		return this;
	}
	
	@Override
	public boolean canBeSentTo(L2Client client, L2Player activeChar)
	{
		return activeChar != null && _answerHandler != null;
	}
	
	@Override
	public void prepareToSend(L2Client client, L2Player activeChar)
	{
		activeChar.setAnswerHandler(_answerHandler);
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xf3);
		writeMessageIdAndElements();
		// timed dialog (Summon Friend skill request)
		writeD(_time);
		writeD(_requesterId);
	}
	
	@Override
	public String getType()
	{
		return _S__F3_CONFIRMDLG;
	}
}
