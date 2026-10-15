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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Sent when a player confirms Change Name Color dialog.
 * 
 * @author savormix
 */
public class RequestChangeNicknameColor extends L2ClientPacket
{
	private static final String _C__D0_4F_REQUESTCHANGENICKNAMECOLOR = "[C] D0:4F RequestChangeNicknameColor";
	public static final int COLOR_NAME_1 = 13021;
	public static final int COLOR_NAME_2 = 13307;
	private static final int[] COLOR_CHOICES = {
			// colors harvested from client, do not modify
			0x9292fc, // pink
			0x7c49fc, // rose pink
			0x98f8fc, // lemon yellow
			0xfa9aee, // lilac
			0xfc5c92, // cobalt violet
			0x00fca0, // mint green
			0xa2a800, // peacock green
			0x7797ad, // yellow ochre
			0x4a669d, // chocolate
			0x999a9a // silver
			};
	
	// Name Color
	private int _color;
	// Ennoble = fail translated entitle (see a dictionary)
	private String _title;
	
	//private int				_unk;
	
	@Override
	protected void readImpl()
	{
		_color = readD();
		_title = readS();
		/*_unk = */readD(); // 202611200 ???
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		if (!player.destroyItemByItemId("ChangeNickColor", COLOR_NAME_1, 1, player, true)
				&& !player.destroyItemByItemId("ChangeNickColor", COLOR_NAME_2, 1, player, true))
		{
			sendAF();
			return;
		}
		
		if (Config.TITLE_PATTERN.matcher(_title).matches())
			player.setTitle(_title);
		else
			player.setTitle(""); // as in description
		sendPacket(SystemMessageId.TITLE_CHANGED);
		
		if (0 <= _color && _color < COLOR_CHOICES.length)
			player.getAppearance().setNickColor(COLOR_CHOICES[_color]);
		else
			player.getAppearance().setNickColor(-1);
		player.broadcastUserInfo();
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_4F_REQUESTCHANGENICKNAMECOLOR;
	}
}
