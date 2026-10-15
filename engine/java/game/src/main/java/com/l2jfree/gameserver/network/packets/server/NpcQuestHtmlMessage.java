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

import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.lang.Replaceable;

/**
 *
 * the HTML parser in the client knowns these standard and non-standard tags and attributes
 * VOLUMN
 * UNKNOWN
 * UL
 * U
 * TT
 * TR
 * TITLE
 * TEXTCODE
 * TEXTAREA
 * TD
 * TABLE
 * SUP
 * SUB
 * STRIKE
 * SPIN
 * SELECT
 * RIGHT
 * PRE
 * P
 * OPTION
 * OL
 * MULTIEDIT
 * LI
 * LEFT
 * INPUT
 * IMG
 * I
 * HTML
 * H7
 * H6
 * H5
 * H4
 * H3
 * H2
 * H1
 * FONT
 * EXTEND
 * EDIT
 * COMMENT
 * COMBOBOX
 * CENTER
 * BUTTON
 * BR
 * BODY
 * BAR
 * ADDRESS
 * A
 * SEL
 * LIST
 * VAR
 * FORE
 * READONL
 * ROWS
 * VALIGN
 * FIXWIDTH
 * BORDERCOLORLI
 * BORDERCOLORDA
 * BORDERCOLOR
 * BORDER
 * BGCOLOR
 * BACKGROUND
 * ALIGN
 * VALU
 * READONLY
 * MULTIPLE
 * SELECTED
 * TYP
 * TYPE
 * MAXLENGTH
 * CHECKED
 * SRC
 * Y
 * X
 * QUERYDELAY
 * NOSCROLLBAR
 * IMGSRC
 * B
 * FG
 * SIZE
 * FACE
 * COLOR
 * DEFFON
 * DEFFIXEDFONT
 * WIDTH
 * VALUE
 * TOOLTIP
 * NAME
 * MIN
 * MAX
 * HEIGHT
 * DISABLED
 * ALIGN
 * MSG
 * LINK
 * HREF
 * ACTION
 *
 *
 * @version $Revision: 1.3.2.1.2.3 $ $Date: 2005/03/27 15:29:57 $
 */
public final class NpcQuestHtmlMessage extends L2ServerPacket
{
	private final int _npcObjId;
	private Replaceable _replaceable;
	private int _questId = 0;
	
	/**
	 * @param npcObjId
	 * @param questId
	 */
	public NpcQuestHtmlMessage(int npcObjId, int questId)
	{
		_npcObjId = npcObjId;
		_questId = questId;
	}
	
	@Override
	public void prepareToSend(L2Client client, L2Player activeChar)
	{
		if (activeChar != null)
			activeChar.buildBypassCache(_replaceable);
	}
	
	public void setHtml(CharSequence text)
	{
		_replaceable = Replaceable.valueOf(text);
	}
	
	@Override
	public boolean canBeSentTo(L2Client client, L2Player activeChar)
	{
		return _replaceable != null;
	}
	
	public void setFile(String path)
	{
		String content = HtmCache.getInstance().getHtm(path);
		
		if (content == null)
		{
			content = "<html><body>Sorry, my HTML is missing!<br>" + path + "</body></html>";
			
			_log.warn("Missing html page: " + path);
		}
		
		setHtml(content);
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
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x8d);
		writeD(_npcObjId);
		writeS(_replaceable);
		writeD(_questId);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "[S] FE:8D NpcQuestHtmlMessage";
	}
}
