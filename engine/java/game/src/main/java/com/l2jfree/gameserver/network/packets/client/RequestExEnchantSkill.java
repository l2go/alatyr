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
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.model.L2ShortCut;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn.EnchantSkillDetail;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ShortCutRegister;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.tools.random.Rnd;

/**
 * Format (ch) dd
 * c: (id) 0xD0
 * h: (subid) 0x06
 * d: skill id
 * d: skill lvl
 * @author -Wooden-
 *
 */
public final class RequestExEnchantSkill extends L2ClientPacket
{
	private static final String _C__D0_07_REQUESTEXENCHANTSKILL = "[C] D0:07 RequestExEnchantSkill";
	
	private int _skillId;
	private int _skillLvl;
	
	@Override
	protected void readImpl()
	{
		_skillId = readD();
		_skillLvl = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		final L2Npc trainer = player.getLastFolkNPC();
		if (!(trainer instanceof L2NpcInstance))
			return;
		
		if (!trainer.canInteract(player) && !player.isGM())
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		else if (player.getLevel() < 76)
		{
			requestFailed(SystemMessageId.YOU_DONT_MEET_SKILL_LEVEL_REQUIREMENTS);
			return;
		}
		else if (player.getClassId().level() < 3)
		{
			requestFailed(SystemMessageId.NOT_COMPLETED_QUEST_FOR_SKILL_ACQUISITION);
			return;
		}
		
		L2Skill skill = SkillTable.getInstance().getInfo(_skillId, _skillLvl);
		if (skill == null)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		
		/*
		int npcid = trainer.getNpcId();
		if (!skill.canTeachBy(npcid) || !skill.getCanLearn(player.getClassId()))
		{
		    if (!Config.ALT_GAME_SKILL_LEARN)
		    {
		        sendPacket(ActionFailed.STATIC_PACKET);
		        Util.handleIllegalPlayerAction(player, "Client "+getClient()+" tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
		        return;
		    }
		}
		*/
		
		int costMultiplier = SkillTreeTable.NORMAL_ENCHANT_COST_MULTIPLIER;
		int reqItemId = SkillTreeTable.NORMAL_ENCHANT_BOOK;
		
		L2EnchantSkillLearn s = SkillTreeTable.getInstance().getSkillEnchantmentBySkillId(_skillId);
		if (s == null)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		EnchantSkillDetail esd = s.getEnchantSkillDetail(_skillLvl);
		if (player.getSkillLevel(_skillId) != esd.getMinSkillLevel())
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		
		int requiredSp = esd.getSpCost() * costMultiplier;
		int requiredExp = esd.getExp() * costMultiplier;
		int rate = esd.getRate(player);
		
		if (player.getSp() < requiredSp)
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ENOUGH_SP_TO_ENCHANT_THAT_SKILL);
			return;
		}
		long expAfter = player.getExp() - requiredExp;
		if (player.getExp() < requiredExp || expAfter < Experience.LEVEL[player.getLevel()])
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ENOUGH_EXP_TO_ENCHANT_THAT_SKILL);
			return;
		}
		
		// only first lvl requires book
		boolean usesBook = _skillLvl % 100 == 1; // 101, 201, 301 ...
		L2ItemInstance spb = player.getInventory().getItemByItemId(reqItemId);
		if (Config.ALT_ES_SP_BOOK_NEEDED && usesBook && spb == null)
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
			return;
		}
		
		boolean check;
		check = player.getStat().removeExpAndSp(requiredExp, requiredSp, false);
		if (Config.ALT_ES_SP_BOOK_NEEDED && usesBook)
			check &= player.destroyItem("Consume", spb.getObjectId(), 1, trainer, true);
		
		if (!check)
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ALL_OF_THE_ITEMS_NEEDED_TO_ENCHANT_THAT_SKILL);
			return;
		}
		
		// ok.  Destroy ONE copy of the book
		if (Rnd.get(100) <= rate)
		{
			player.addSkill(skill, true);
			
			if (_log.isDebugEnabled())
				_log.info("Learned skill ID: " + _skillId + " Level: " + _skillLvl + " for " + requiredSp + " SP, "
						+ requiredExp + " EXP.");
			
			sendPacket(new UserInfo(player));
			sendPacket(new SystemMessage(SystemMessageId.YOU_HAVE_SUCCEEDED_IN_ENCHANTING_THE_SKILL_S1)
					.addSkillName(_skillId));
		}
		else
		{
			player.addSkill(SkillTable.getInstance().getInfo(_skillId, s.getBaseLevel()), true);
			sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_ENCHANT_THE_SKILL);
		}
		((L2NpcInstance)trainer).showEnchantSkillList(player, false);
		updateSkillShortcuts(player);
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void updateSkillShortcuts(L2Player player)
	{
		// update all the shortcuts to this skill
		L2ShortCut[] allShortCuts = player.getAllShortCuts();
		
		for (L2ShortCut sc : allShortCuts)
		{
			if (sc.getId() == _skillId && sc.getType() == L2ShortCut.TYPE_SKILL)
			{
				L2ShortCut newsc =
						new L2ShortCut(sc.getSlot(), sc.getPage(), sc.getType(), sc.getId(),
								player.getSkillLevel(_skillId), 1);
				player.sendPacket(new ShortCutRegister(newsc));
				player.registerShortCut(newsc);
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_07_REQUESTEXENCHANTSKILL;
	}
}
