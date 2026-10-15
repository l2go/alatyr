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

import java.util.StringTokenizer;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillDone;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillList;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class L2FishermanInstance extends L2MerchantInstance
{
	/**
	 * @param objectId
	 * @param template
	 */
	public L2FishermanInstance(int objectId, L2NpcTemplate template)
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
		
		return "data/html/fisherman/" + pom + ".htm";
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("FishSkillList"))
		{
			player.setSkillLearningClassId(player.getClassId());
			showSkillList(player, false);
		}
		
		StringTokenizer st = new StringTokenizer(command, " ");
		String cmd = st.nextToken();
		
		if (cmd.equalsIgnoreCase("Buy"))
		{
			if (st.countTokens() < 1)
				return;
			int val = Integer.parseInt(st.nextToken());
			showBuyWindow(player, val);
		}
		else if (cmd.equalsIgnoreCase("Sell"))
		{
			showSellWindow(player);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	public void showSkillList(L2Player player, boolean closable)
	{
		L2SkillLearn[] skills = SkillTreeTable.getInstance().getAvailableFishingSkills(player);
		AcquireSkillList asl = new AcquireSkillList(AcquireSkillList.SkillType.Fishing);
		
		int counts = 0;
		
		for (L2SkillLearn s : skills)
		{
			L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
			
			if (sk == null)
				continue;
			
			counts++;
			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), s.getSpCost(), 1);
		}
		
		if (counts == 0)
		{
			SystemMessage sm;
			int minlevel = SkillTreeTable.getInstance().getMinLevelForNewFishingSkill(player);
			if (minlevel > 0)
			{
				sm = new SystemMessage(SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_REACHED_S1);
				sm.addNumber(minlevel);
			}
			else
			{
				sm = SystemMessageId.NO_MORE_SKILLS_TO_LEARN.getSystemMessage();
			}
			player.sendPacket(sm);
			if (closable)
				player.sendPacket(AcquireSkillDone.PACKET);
		}
		else
		{
			player.sendPacket(asl);
		}
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
}
