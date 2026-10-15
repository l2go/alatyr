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

import java.util.List;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn.EnchantSkillDetail;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExEnchantSkillInfo;
import com.l2jfree.gameserver.network.packets.server.ExEnchantSkillList.EnchantSkillType;

/**
 * Format (ch) dd
 * c: (id) 0xD0
 * h: (subid) 0x06
 * d: skill id
 * d: skill lvl
 * @author -Wooden-
 *
 */
public final class RequestExEnchantSkillInfo extends L2ClientPacket
{
	private static final String _C__D0_06_REQUESTEXENCHANTSKILLINFO = "[C] D0:06 RequestExEnchantSkillInfo";
	
	private static final int TYPE_NORMAL_ENCHANT = 0;
	private static final int TYPE_SAFE_ENCHANT = 1;
	private static final int TYPE_UNTRAIN_ENCHANT = 2;
	private static final int TYPE_CHANGE_ENCHANT = 3;
	
	private int _type;
	private int _skillId;
	private int _skillLvl;
	
	@Override
	protected void readImpl()
	{
		_type = readD();
		_skillId = readD();
		_skillLvl = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		final L2Npc trainer = activeChar.getLastFolkNPC();
		if (!(trainer instanceof L2NpcInstance))
			return;
		
		if (!trainer.canInteract(activeChar) && !activeChar.isGM())
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		if (activeChar.getLevel() < 76)
		{
			requestFailed(SystemMessageId.YOU_DONT_MEET_SKILL_LEVEL_REQUIREMENTS);
			return;
		}
		if (activeChar.getClassId().level() < 3)
		{
			requestFailed(SystemMessageId.NOT_COMPLETED_QUEST_FOR_SKILL_ACQUISITION);
			return;
		}
		
		L2Skill skill = SkillTable.getInstance().getInfo(_skillId, _skillLvl);
		if (skill == null || skill.getId() != _skillId)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_SKILL_THAT_ENABLES_ENCHANT);
			return;
		}
		
		/*
		if (!skill.canTeachBy(trainer.getNpcId()) || !skill.getCanLearn(activeChar.getClassId()))
		{
			if (!Config.ALT_GAME_SKILL_LEARN)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				Util.handleIllegalPlayerAction(activeChar, "Client " + getClient() + " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
				return;
			}
		}
		*/
		
		switch (_type)
		{
			case TYPE_NORMAL_ENCHANT:
				showEnchantInfo(activeChar, false);
				break;
			case TYPE_SAFE_ENCHANT:
				showEnchantInfo(activeChar, true);
				break;
			case TYPE_UNTRAIN_ENCHANT:
				showUntrainEnchantInfo(activeChar);
				break;
			case TYPE_CHANGE_ENCHANT:
				showChangeEnchantInfo(activeChar);
				break;
			default:
				_log.error("Unknown skill enchant type: " + _type);
				break;
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void showEnchantInfo(L2Player activeChar, boolean isSafeEnchant)
	{
		ExEnchantSkillInfo asi =
				new ExEnchantSkillInfo(isSafeEnchant ? EnchantSkillType.SAFE : EnchantSkillType.NORMAL, _skillId);
		
		L2EnchantSkillLearn enchantLearn = SkillTreeTable.getInstance().getSkillEnchantmentBySkillId(_skillId);
		// do we have this skill?
		if (enchantLearn != null)
		{
			// skill already enchanted?
			if (_skillLvl > 100)
			{
				// get detail for next level
				EnchantSkillDetail esd = enchantLearn.getEnchantSkillDetail(_skillLvl + 1);
				
				// if it exists add it
				if (esd != null)
					asi.addEnchantSkillDetail(activeChar, esd);
			}
			else
			// not already enchanted
			{
				for (List<EnchantSkillDetail> esd : enchantLearn.getEnchantRoutes())
					if (esd != null)
						// add first level (+1) of all routes
						asi.addEnchantSkillDetail(activeChar, esd.get(0));
			}
			activeChar.sendPacket(asi);
		}
	}
	
	public void showChangeEnchantInfo(L2Player activeChar)
	{
		ExEnchantSkillInfo asi = new ExEnchantSkillInfo(EnchantSkillType.CHANGE_ROUTE, _skillId);
		
		L2EnchantSkillLearn enchantLearn = SkillTreeTable.getInstance().getSkillEnchantmentBySkillId(_skillId);
		// do we have this skill?
		if (enchantLearn != null)
		{
			// skill already enchanted?
			if (_skillLvl > 100)
			{
				// get current enchant type
				int currentType = L2EnchantSkillLearn.getEnchantType(_skillLvl);
				
				List<EnchantSkillDetail>[] routes = enchantLearn.getEnchantRoutes();
				List<EnchantSkillDetail> route;
				for (int i = 0; i < routes.length; i++)
				{
					// skip current route
					if (i != currentType)
					{
						route = routes[i];
						if (route != null)
						{
							EnchantSkillDetail esd = route.get(L2EnchantSkillLearn.getEnchantIndex(_skillLvl));
							if (esd != null)
								asi.addEnchantSkillDetail(activeChar, esd);
						}
					}
				}
				
				activeChar.sendPacket(asi);
			}
			else
				_log.warn("Client: " + getClient() + " requested change route information for unenchanted skill");
		}
	}
	
	public void showUntrainEnchantInfo(L2Player activeChar)
	{
		ExEnchantSkillInfo asi = new ExEnchantSkillInfo(EnchantSkillType.UNTRAIN, _skillId);
		
		L2EnchantSkillLearn enchantLearn = SkillTreeTable.getInstance().getSkillEnchantmentBySkillId(_skillId);
		// do we have this skill?
		if (enchantLearn != null)
		{
			// skill already enchanted?
			if (_skillLvl > 100)
			{
				EnchantSkillDetail currentLevelDetail = enchantLearn.getEnchantSkillDetail(_skillLvl);
				if (currentLevelDetail != null)
				{
					// no previous enchant level, return to original
					if (_skillLvl % 100 == 1)
						asi.addEnchantSkillDetail(enchantLearn.getBaseLevel(), 100,
								(currentLevelDetail.getSpCost() * 8) / 10, currentLevelDetail.getExp());
					else
					{
						// get detail for previous level
						EnchantSkillDetail esd = enchantLearn.getEnchantSkillDetail(_skillLvl - 1);
						
						// if it exists add it
						if (esd != null)
							asi.addEnchantSkillDetail(esd.getLevel(), 100, (currentLevelDetail.getSpCost() * 8) / 10,
									currentLevelDetail.getExp());
					}
				}
				else
					_log.warn("Client: "
							+ getClient()
							+ " tried to untrain enchanted skill, but server doesn't have data for his current skill enchantment level");
				
				activeChar.sendPacket(asi);
			}
			else
				_log.warn("Client: " + getClient() + " requested untrain information for unenchanted skill");
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_06_REQUESTEXENCHANTSKILLINFO;
	}
}
