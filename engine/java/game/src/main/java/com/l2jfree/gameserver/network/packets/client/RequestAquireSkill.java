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
import com.l2jfree.gameserver.datatables.SkillSpellbookTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2FishermanInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2StarCollectorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2TransformManagerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2VillageMasterInstance;
import com.l2jfree.gameserver.model.L2ShortCut;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2CertificationSkillsLearn;
import com.l2jfree.gameserver.model.skills.learn.L2PledgeSkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2TransformSkillLearn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExStorageMaxCount;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillList;
import com.l2jfree.gameserver.network.packets.server.ShortCutRegister;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
* This class represents a packet sent by client when the players confirms the skill
* to be learnt.
* 
* @version $Revision: 1.7.2.1.2.4 $ $Date: 2005/03/27 15:29:30 $
*/
public class RequestAquireSkill extends L2ClientPacket
{
	private static final String _C__6C_REQUESTAQUIRESKILL = "[C] 6C RequestAquireSkill";
	
	private int _id;
	private int _level;
	private int _skillType;
	
	/**
	 * packet type id 0x6c
	 * format rev650:	   cddd
	 */
	@Override
	protected void readImpl()
	{
		_id = readD();
		_level = readD();
		_skillType = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		if (_level < 1 || _level > 1000 || _id < 1 || _id > 32000)
		{
			_log.warn("Recived Wrong Packet Data in Aquired Skill - id:" + _id + " level:" + _level);
			return;
		}
		
		final L2Npc trainer = player.getLastFolkNPC();
		if (!(trainer instanceof L2NpcInstance))
		{
			if (player.isGM())
				player.sendMessage("Request for skill terminated, wrong Npc");
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		//int npcid = trainer.getNpcId();
		
		if (!trainer.canInteract(player) && !player.isGM())
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		if (!Config.ALT_GAME_SKILL_LEARN)
			player.setSkillLearningClassId(player.getClassId());
		
		// already knows the skill with this level
		if (player.getSkillLevel(_id) >= _level)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final L2Skill skill = SkillTable.getInstance().getInfo(_id, _level);
		
		int counts = 0;
		int _requiredSp = 10000000;
		
		switch (_skillType)
		{
			case 0:
			{
				if (trainer instanceof L2TransformManagerInstance && L2TransformSkillLearn.isTransformSkill(_id)) // Transformation skills
				{
					int costid = 0;
					// Skill Learn bug Fix
					L2TransformSkillLearn[] skillst = SkillTreeTable.getInstance().getAvailableTransformSkills(player);
					
					for (L2TransformSkillLearn s : skillst)
					{
						L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
						
						if (sk == null || sk != skill)
							continue;
						
						counts++;
						costid = s.getItemId();
						_requiredSp = s.getSpCost();
					}
					
					if (counts == 0)
					{
						sendPacket(ActionFailed.STATIC_PACKET);
						Util.handleIllegalPlayerAction(player, "Player " + player.getName()
								+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
						return;
					}
					
					if (player.getSp() >= _requiredSp)
					{
						if (!player.destroyItemByItemId("Consume", costid, 1, trainer, false))
						{
							// Haven't spellbook
							requestFailed(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
							return;
						}
						
						SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemNumber(1);
						sm.addItemName(costid);
						sendPacket(sm);
					}
					else
					{
						requestFailed(SystemMessageId.NOT_ENOUGH_SP_TO_LEARN_SKILL);
						return;
					}
					break;
				}
				else if (trainer instanceof L2TransformManagerInstance
						&& L2CertificationSkillsLearn.isCertificationSkill(_id)) // Certification skills
				{
					int costid = 0;
					L2CertificationSkillsLearn[] skillss =
							SkillTreeTable.getInstance().getAvailableCertificationSkills(player);
					
					for (L2CertificationSkillsLearn s : skillss)
					{
						L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
						
						if (sk == null || sk != skill)
							continue;
						
						counts++;
						costid = s.getItemId();
						_requiredSp = 0;
					}
					
					if (counts == 0)
					{
						sendPacket(ActionFailed.STATIC_PACKET);
						Util.handleIllegalPlayerAction(player, "Player " + player.getName()
								+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
						return;
					}
					
					if (!player.destroyItemByItemId("Consume", costid, 1, trainer, false))
					{
						// Does not have either Emergent Ability or Master Ability or Class specific Ability certificate
						requestFailed(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
						return;
					}
					
					SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
					sm.addItemNumber(1);
					sm.addItemName(costid);
					sendPacket(sm);
					
					break;
				}
				// normal skills
				L2SkillLearn[] skills =
						SkillTreeTable.getInstance().getAvailableSkills(player, player.getSkillLearningClassId());
				
				for (L2SkillLearn s : skills)
				{
					L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
					if (sk == null || sk != skill/* || !sk.getCanLearn(player.getSkillLearningClassId())
													|| !sk.canTeachBy(npcid)*/)
						continue;
					counts++;
					_requiredSp = SkillTreeTable.getInstance().getSkillCost(player, skill);
				}
				
				if (counts == 0 && !Config.ALT_GAME_SKILL_LEARN)
				{
					sendPacket(ActionFailed.STATIC_PACKET);
					Util.handleIllegalPlayerAction(player, "Player " + player.getName()
							+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
					return;
				}
				
				if (player.getSp() >= _requiredSp)
				{
					int spbId = -1;
					// divine inspiration require book for each level
					if (skill.getId() == L2Skill.SKILL_DIVINE_INSPIRATION && Config.DIVINE_SP_BOOK_NEEDED)
						spbId = SkillSpellbookTable.getInstance().getBookForSkill(skill, _level);
					else if (Config.ALT_SP_BOOK_NEEDED)
						spbId = SkillSpellbookTable.getInstance().getBookForSkill(skill);
					
					if (skill.getId() == L2Skill.SKILL_DIVINE_INSPIRATION || skill.getLevel() == 1 && spbId > -1)
					{
						L2ItemInstance spb = player.getInventory().getItemByItemId(spbId);
						
						if (spb == null)
						{
							// Haven't spellbook
							requestFailed(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
							return;
						}
						
						// ok
						player.destroyItem("Consume", spb.getObjectId(), 1, trainer, true);
					}
				}
				else
				{
					requestFailed(SystemMessageId.NOT_ENOUGH_SP_TO_LEARN_SKILL);
					return;
				}
				break;
			}
			case 1:
			{
				int costid = 0;
				int costcount = 0;
				// Skill Learn bug Fix
				L2SkillLearn[] skillsc = SkillTreeTable.getInstance().getAvailableFishingSkills(player);
				
				for (L2SkillLearn s : skillsc)
				{
					L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
					
					if (sk == null || sk != skill)
						continue;
					
					counts++;
					costid = s.getIdCost();
					costcount = s.getCostCount();
					_requiredSp = s.getSpCost();
				}
				
				if (counts == 0)
				{
					sendPacket(ActionFailed.STATIC_PACKET);
					Util.handleIllegalPlayerAction(player, "Player " + player.getName()
							+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
					return;
				}
				
				if (player.getSp() >= _requiredSp)
				{
					if (!player.destroyItemByItemId("Consume", costid, costcount, trainer, false))
					{
						// Haven't spellbook
						requestFailed(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
						return;
					}
					
					SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
					sm.addItemNumber(costcount);
					sm.addItemName(costid);
					sendPacket(sm);
				}
				else
				{
					requestFailed(SystemMessageId.NOT_ENOUGH_SP_TO_LEARN_SKILL);
					return;
				}
				break;
			}
			case 2:
			{
				if (!player.isClanLeader())
				{
					requestFailed(SystemMessageId.ONLY_THE_CLAN_LEADER_IS_ENABLED);
					return;
				}
				
				int itemId = 0;
				long itemCount = 0;
				int repCost = 100000000;
				// Skill Learn bug Fix
				L2PledgeSkillLearn[] skills = SkillTreeTable.getInstance().getAvailablePledgeSkills(player);
				
				for (L2PledgeSkillLearn s : skills)
				{
					L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
					
					if (sk == null || sk != skill)
						continue;
					
					counts++;
					itemId = s.getItemId();
					itemCount = s.getItemCount();
					repCost = s.getRepCost();
				}
				
				if (counts == 0)
				{
					sendPacket(ActionFailed.STATIC_PACKET);
					Util.handleIllegalPlayerAction(player, "Player " + player.getName()
							+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
					return;
				}
				
				if (player.getClan().getReputationScore() >= repCost)
				{
					if (Config.ALT_LIFE_CRYSTAL_NEEDED)
					{
						if (!player.destroyItemByItemId("Consume", itemId, itemCount, trainer, false))
						{
							// Haven't spellbook
							requestFailed(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
							return;
						}
						
						SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
						sm.addItemName(itemId);
						sm.addItemNumber(itemCount);
						sendPacket(sm);
					}
				}
				else
				{
					requestFailed(SystemMessageId.ACQUIRE_SKILL_FAILED_BAD_CLAN_REP_SCORE);
					return;
				}
				player.getClan().setReputationScore(player.getClan().getReputationScore() - repCost, true);
				player.getClan().addNewSkill(skill);
				
				if (_log.isDebugEnabled())
					_log.info("Learned pledge skill " + _id + " for " + _requiredSp + " SP.");
				
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_DEDUCTED_FROM_CLAN_REP);
				sm.addNumber(repCost);
				sendPacket(sm);
				sm = new SystemMessage(SystemMessageId.CLAN_SKILL_S1_ADDED);
				sm.addSkillName(_id);
				sendPacket(sm);
				
				player.getClan().broadcastToOnlineMembers(new PledgeSkillList(player.getClan()));
				
				L2VillageMasterInstance.showPledgeSkillList(player, true); //Maybe we should add a check here...
				sendAF();
				return;
			}
			case 4:
			{
				_requiredSp = 0;
				Quest[] qlst = trainer.getTemplate().getEventQuests(Quest.QuestEventType.ON_SKILL_LEARN);
				if ((qlst != null) && qlst.length == 1)
				{
					if (!qlst[0].notifyAcquireSkill(trainer, player, skill))
					{
						qlst[0].notifyAcquireSkillList(trainer, player);
						return;
					}
				}
				else
				{
					return;
				}
				break;
			}
			case 6:
			{
				int costid = 0;
				int costcount = 0;
				// Skill Learn bug Fix
				
				L2SkillLearn[] skillsc = SkillTreeTable.getInstance().getAvailableSpecialSkills(player);
				
				for (L2SkillLearn s : skillsc)
				{
					L2Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
					
					if (sk == null || sk != skill)
						continue;
					
					counts++;
					costid = s.getIdCost();
					costcount = s.getCostCount();
					_requiredSp = s.getSpCost();
				}
				
				if (counts == 0)
				{
					player.sendMessage("You are trying to learn skill that u can't..");
					Util.handleIllegalPlayerAction(player, "Player " + player.getName()
							+ " tried to learn skill that he can't!!!", IllegalPlayerAction.PUNISH_KICK);
					return;
				}
				
				if (player.getSp() >= _requiredSp)
				{
					if (!player.destroyItemByItemId("Consume", costid, costcount, trainer, false))
					{
						// Haven't spellbook
						player.sendPacket(SystemMessageId.ITEM_MISSING_TO_LEARN_SKILL);
						return;
					}
					
					SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
					sm.addItemName(costid);
					sm.addItemNumber(costcount);
					sendPacket(sm);
				}
				else
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_SP_TO_LEARN_SKILL);
					return;
				}
				break;
			}
			default:
			{
				_log.warn("Recived Wrong Packet Data in Aquired Skill - unk1:" + _skillType);
				sendAF();
				return;
			}
		}
		
		player.addSkill(skill, true);
		
		if (_log.isDebugEnabled())
			_log.debug("Learned skill " + _id + " for " + _requiredSp + " SP.");
		
		player.setSp(player.getSp() - _requiredSp);
		
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.SP, player.getSp());
		sendPacket(su);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.LEARNED_SKILL_S1);
		sm.addSkillName(_id);
		sendPacket(sm);
		
		// update all the shortcuts to this skill
		if (_level > 1)
		{
			L2ShortCut[] allShortCuts = player.getAllShortCuts();
			
			for (L2ShortCut sc : allShortCuts)
			{
				if (sc.getId() == _id && sc.getType() == L2ShortCut.TYPE_SKILL)
				{
					L2ShortCut newsc = new L2ShortCut(sc.getSlot(), sc.getPage(), sc.getType(), sc.getId(), _level, 1);
					sendPacket(new ShortCutRegister(newsc));
					player.registerShortCut(newsc);
				}
			}
		}
		
		if (_skillType == 4)
		{
			Quest[] qlst = trainer.getTemplate().getEventQuests(Quest.QuestEventType.ON_SKILL_LEARN);
			qlst[0].notifyAcquireSkillList(trainer, player);
		}
		else if (trainer instanceof L2FishermanInstance)
			((L2FishermanInstance)trainer).showSkillList(player, true);
		else if (trainer instanceof L2TransformManagerInstance && L2TransformSkillLearn.isTransformSkill(_id))
			((L2TransformManagerInstance)trainer).showTransformSkillList(player, true);
		else if (trainer instanceof L2TransformManagerInstance && L2CertificationSkillsLearn.isCertificationSkill(_id))
			((L2TransformManagerInstance)trainer).showCertificationSkillsList(player, true);
		else if (trainer instanceof L2StarCollectorInstance)
			((L2StarCollectorInstance)trainer).showCollectionSkillList(player, true);
		else
			((L2NpcInstance)trainer).showSkillList(player, player.getSkillLearningClassId(), true);
		
		if (_id >= 1368 && _id <= 1372) //if skill is expand - send packet :)
			sendPacket(new ExStorageMaxCount(player));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__6C_REQUESTAQUIRESKILL;
	}
}
