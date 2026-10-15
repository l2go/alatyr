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

/**
 * Format (ch) dd
 * c: (id) 0xD0
 * h: (subid) 0x33
 * d: skill id
 * d: skill lvl
 * @author -Wooden-
 *
 */
public final class RequestExEnchantSkillUntrain extends L2ClientPacket
{
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
		
		L2Npc trainer = player.getLastFolkNPC();
		if (!(trainer instanceof L2NpcInstance))
			return;
		
		if (!trainer.canInteract(player) && !player.isGM())
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		if (player.getLevel() < 76)
		{
			requestFailed(SystemMessageId.YOU_DONT_MEET_SKILL_LEVEL_REQUIREMENTS);
			return;
		}
		
		if (player.getClassId().level() < 3)
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
		
		int reqItemId = SkillTreeTable.UNTRAIN_ENCHANT_BOOK;
		
		L2EnchantSkillLearn s = SkillTreeTable.getInstance().getSkillEnchantmentBySkillId(_skillId);
		if (s == null)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		
		int currentLevel = player.getSkillLevel(_skillId);
		if (currentLevel - 1 != _skillLvl && (currentLevel % 100 != 1 || _skillLvl != s.getBaseLevel()))
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		
		EnchantSkillDetail esd = s.getEnchantSkillDetail(currentLevel);
		int requiredSp = esd.getSpCost();
		int requiredExp = esd.getExp();
		//int rate = esd.getRate(player);
		
		L2ItemInstance spb = player.getInventory().getItemByItemId(reqItemId);
		if (Config.ALT_ES_SP_BOOK_NEEDED && spb == null) //if (Config.ES_SP_BOOK_NEEDED && usesBook)  dissabled till official info - will request book for each untrain
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ALL_ITENS_NEEDED_TO_UNTRAIN_SKILL_ENCHANT);
			return;
		}
		
		boolean check = true;
		if (Config.ALT_ES_SP_BOOK_NEEDED) //if (Config.ES_SP_BOOK_NEEDED && usesBook)  dissabled till official info - will request book for each untrain
			check &= player.destroyItem("Consume", spb.getObjectId(), 1, trainer, true);
		check &= player.getStat().addSp((requiredSp * 8) / 10);
		
		if (!check)
		{
			requestFailed(SystemMessageId.YOU_DONT_HAVE_ALL_ITENS_NEEDED_TO_UNTRAIN_SKILL_ENCHANT);
			return;
		}
		
		if (_skillLvl % 100 == 0)
			_skillLvl = s.getBaseLevel();
		
		player.addSkill(skill, true);
		
		if (_log.isDebugEnabled())
			_log.info("Learned skill ID: " + _skillId + " Level: " + _skillLvl + " for " + requiredSp + " SP, "
					+ requiredExp + " EXP.");
		
		sendPacket(new UserInfo(player));
		
		SystemMessage sm;
		if (_skillLvl > 100)
		{
			sm = new SystemMessage(SystemMessageId.UNTRAIN_SUCCESSFUL_SKILL_S1_ENCHANT_LEVEL_DECREASED_BY_ONE);
			sm.addSkillName(_skillId);
			sendPacket(sm);
		}
		else
		{
			sm = new SystemMessage(SystemMessageId.UNTRAIN_SUCCESSFUL_SKILL_S1_ENCHANT_LEVEL_RESETED);
			sm.addSkillName(_skillId);
			sendPacket(sm);
		}
		
		((L2NpcInstance)trainer).showEnchantUntrainSkillList(player, player.getClassId());
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
		return "[C] D0:33 RequestExEnchantSkillUntrain";
	}
}
