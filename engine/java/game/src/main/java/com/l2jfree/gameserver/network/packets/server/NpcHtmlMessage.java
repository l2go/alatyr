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
import com.l2jfree.lang.Replaceable;

public final class NpcHtmlMessage extends AbstractNpcHtmlMessage
{
	private static final String _S__1B_NPCHTMLMESSAGE = "[S] 0f NpcHtmlMessage";
	
	// d S
	// d is usually 0, S is the html text starting with <html> and ending with </html>
	private final int _npcObjId;
	private Replaceable _replaceable;
	private int _itemId = 0;
	
	public NpcHtmlMessage(int npcObjId, int itemId)
	{
		_npcObjId = npcObjId;
		_itemId = itemId;
	}
	
	public NpcHtmlMessage(int npcObjId, String text)
	{
		_npcObjId = npcObjId;
		
		setHtml(text);
	}
	
	public NpcHtmlMessage(int npcObjId)
	{
		_npcObjId = npcObjId;
	}
	
	public void disableValidation()
	{
		// TODO
	}
	
	@Override
	public void prepareToSend(L2Client client, L2Player activeChar)
	{
		if (activeChar != null)
			activeChar.buildBypassCache(_replaceable);
	}
	
	@Override
	public void setHtml(CharSequence text)
	{
		_replaceable = Replaceable.valueOf(text);
	}
	
	@Override
	public boolean canBeSentTo(L2Client client, L2Player activeChar)
	{
		return _replaceable != null;
	}
	
	public void replace(String pattern, String value)
	{
		_replaceable.replace(pattern, value);
	}
	
	public void replace(String pattern, long value)
	{
		_replaceable.replace(pattern, value);
	}
	
	public void replace(String pattern, double value)
	{
		_replaceable.replace(pattern, value);
	}
	
	public void replace(String pattern, Object value)
	{
		_replaceable.replace(pattern, value);
	}
	
	@Override
	protected CharSequence getContent()
	{
		return _replaceable;
	}
	
	@Override
	protected int getNpcObjectId()
	{
		return _npcObjId;
	}
	
	@Override
	protected int getItemId()
	{
		return _itemId;
	}
	
	@Override
	public String getType()
	{
		return _S__1B_NPCHTMLMESSAGE;
	}
}
