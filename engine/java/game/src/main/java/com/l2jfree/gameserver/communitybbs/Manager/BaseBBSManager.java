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
package com.l2jfree.gameserver.communitybbs.Manager;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.server.ShowBoard;

public abstract class BaseBBSManager
{
	public abstract void parsecmd(String command, L2Player activeChar);
	
	public abstract void
			parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar);
	
	protected void separateAndSend(StringBuilder html, L2Player acha)
	{
		separateAndSend(html.toString(), acha);
	}
	
	protected void separateAndSend(String html, L2Player acha)
	{
		ShowBoard.separateAndSend(acha, html);
	}
	
	protected void notImplementedYet(L2Player activeChar, String command)
	{
		ShowBoard.notImplementedYet(activeChar, command);
	}
	
	/**
	 * @param html
	 */
	protected void send1001(String html, L2Player acha)
	{
		if (html.length() < 8180)
		{
			acha.sendPacket(new ShowBoard(html, "1001"));
		}
	}
	
	/**
	 * @param i
	 */
	protected void send1002(L2Player acha)
	{
		send1002(acha, " ", " ", "0");
	}
	
	/**
	 * @param activeChar
	 * @param string
	 * @param string2
	 */
	protected void send1002(L2Player activeChar, String string, String string2, String string3)
	{
		List<String> _arg = new ArrayList<String>(17);
		_arg.add("0");
		_arg.add("0");
		_arg.add("0");
		_arg.add("0");
		_arg.add("0");
		_arg.add("0");
		_arg.add(activeChar.getName());
		_arg.add(Integer.toString(activeChar.getObjectId()));
		_arg.add(activeChar.getAccountName());
		_arg.add("9");
		_arg.add(string2);
		_arg.add(string2);
		_arg.add(string);
		_arg.add(string3);
		_arg.add(string3);
		_arg.add("0");
		_arg.add("0");
		activeChar.sendPacket(new ShowBoard(_arg));
	}
}
