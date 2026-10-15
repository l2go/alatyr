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

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillDone;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class L2StarCollectorInstance extends L2MerchantInstance
{
	/**
	 * @param objectId
	 * @param template
	 */
	public L2StarCollectorInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		
		if (val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;
		
		return "data/html/default/" + pom + ".htm";
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("CollectionSkillList"))
			showCollectionSkillList(player, false);
		else
			super.onBypassFeedback(player, command);
	}
	
	public void showCollectionSkillList(L2Player player, boolean closable)
	{
		if (player.getLevel() < 75)
		{
			SystemMessage sm =
					new SystemMessage(SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_REACHED_S1);
			sm.addNumber(75);
			player.sendPacket(sm);
			return;
		}
		
		L2SkillLearn[] skills = SkillTreeTable.getInstance().getAvailableSpecialSkills(player);
		AcquireSkillList asl = new AcquireSkillList(AcquireSkillList.SkillType.Collection);
		int counts = 0;
		
		for (L2SkillLearn s : skills)
		{
			L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
			if (sk == null)
				continue;
			
			counts++;
			
			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), 0, 0);
		}
		
		if (counts == 0)
		{
			player.sendPacket(SystemMessageId.NO_MORE_SKILLS_TO_LEARN);
			if (closable)
				player.sendPacket(AcquireSkillDone.PACKET);
		}
		else
			player.sendPacket(asl);
	}
}
