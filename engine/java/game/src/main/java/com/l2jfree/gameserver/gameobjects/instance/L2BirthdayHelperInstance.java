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
package com.l2jfree.gameserver.gameobjects.instance;

import java.util.concurrent.ScheduledFuture;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author savormix
 */
public final class L2BirthdayHelperInstance extends L2Npc
{
	private static final int HELPING_LENGTH = 300000;
	private static final int BIRTHDAY_CAKE_EFFECT = 5950;
	
	private int _ownerId;
	private ScheduledFuture<?> _leaveTask;
	
	public L2BirthdayHelperInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		_ownerId = 0;
		_leaveTask = ThreadPoolManager.getInstance().scheduleGeneral(new Leaving(), HELPING_LENGTH);
	}
	
	public final void setOwner(L2Player player)
	{
		if (player == null)
		{
			_leaveTask.cancel(false);
			doDie(null);
		}
		else
			_ownerId = player.getObjectId();
	}
	
	@Override
	public final void onBypassFeedback(L2Player player, String command)
	{
		if (player.getObjectId() != _ownerId)
			return;
		
		_leaveTask.cancel(false);
		doDie(null);
		
		if (command.equals("accept"))
		{
			// 1st anniversary
			player.addItem("Creation Day", 10250, 1, this, true);
			// any anniversary
			L2Skill buff = SkillTable.getInstance().getSkillInfo(BIRTHDAY_CAKE_EFFECT, 1).getSkill();
			buff.getEffects(player, player);
			player.broadcastPacket(new MagicSkillUse(player, player, buff, 0, 0));
			player.broadcastPacket(new MagicSkillLaunched(player, buff, player));
			player.sendPacket(new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(buff));
			showChatWindow(player, 1);
		}
		else if (command.equals("refuse"))
			showChatWindow(player, 2);
	}
	
	@Override
	public final void showChatWindow(L2Player player, int val)
	{
		if (player.getObjectId() != _ownerId)
			val = 3;
		super.showChatWindow(player, val);
	}
	
	private final class Leaving implements Runnable
	{
		@Override
		public void run()
		{
			doDie(null);
		}
	}
}
