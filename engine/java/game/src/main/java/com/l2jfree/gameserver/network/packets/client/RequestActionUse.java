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

import com.l2jfree.gameserver.datatables.PetSkillsTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2SummonAI;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2StaticObjectInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureList;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.RecipeShopManageList;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * This class represents a packet sent by the client when a player uses one of the buttons
 * in the action list (sit,attack,assist,social...)
 *
 * @version $Revision: 1.11.2.7.2.9 $ $Date: 2005/04/06 16:13:48 $
 */
public class RequestActionUse extends L2ClientPacket
{
	private static final String _C__45_REQUESTACTIONUSE = "[C] 45 RequestActionUse";
	
	private int _actionId;
	private boolean _ctrlPressed;
	private boolean _shiftPressed;
	
	/**
	 * packet type id 0x45
	 * format:      cddc
	 */
	@Override
	protected void readImpl()
	{
		_actionId = readD();
		_ctrlPressed = (readD() == 1);
		_shiftPressed = (readC() == 1);
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		// removes spawn protection
		activeChar.onActionRequest();
		
		if (_log.isDebugEnabled())
			_log.debug(activeChar.getName() + " request Action use: id " + _actionId + " 2:" + _ctrlPressed + " 3:"
					+ _shiftPressed);
		
		if (activeChar.isAlikeDead() || activeChar.isOutOfControl() || activeChar.isTryingToSitOrStandup())
		{
			sendAF();
			return;
		}
		
		// don't allow to do some action if player is transformed
		if (activeChar.isTransformed())
		{
			switch (_actionId)
			{
				case 0:
				case 10:
				case 28:
				case 37:
				case 51:
				case 61:
				{
					sendAF();
					return;
				}
			}
		}
		
		L2Summon pet = activeChar.getPet();
		L2Object target = activeChar.getTarget();
		
		if (_log.isDebugEnabled())
			_log.info("Requested Action ID: " + String.valueOf(_actionId));
		
		switch (_actionId)
		{
			case 0:
				if (activeChar.getMountType() != 0)
					break;
				
				if (target instanceof L2StaticObjectInstance && !activeChar.isSitting())
				{
					if (((L2StaticObjectInstance)target).onSit(activeChar))
					{
						sendAF();
						return;
					}
				}
				
				if (activeChar.isSitting())
				{
					activeChar.resetThrone();
					activeChar.standUp(false); // User requested - Checks if animation already running.
				}
				else
					activeChar.sitDown(false); // User requested - Checks if animation already running.
					
				if (_log.isDebugEnabled())
					_log.debug("new wait type: " + (activeChar.isSitting() ? "STANDING" : "SITTING"));
				
				break;
			case 1:
				if (activeChar.isRunning())
					activeChar.setWalking();
				else
					activeChar.setRunning();
				
				if (_log.isDebugEnabled())
					_log.debug("new move type: " + (activeChar.isRunning() ? "RUNNING" : "WALKIN"));
				break;
			case 10:
				// Private Store Sell
				activeChar.tryOpenPrivateSellStore(false);
				break;
			case 15:
			case 21: // pet follow/stop
				if (pet != null && !pet.isOutOfControl())
					((L2SummonAI)pet.getAI()).notifyFollowStatusChange();
				break;
			case 16:
			case 22: // pet attack
				if (target != null && pet != null && pet != target && activeChar != target
						&& !pet.isAttackingDisabled() && !pet.isOutOfControl())
				{
					if (pet instanceof L2PetInstance && (pet.getLevel() - activeChar.getLevel() > 20))
					{
						requestFailed(SystemMessageId.PET_TOO_HIGH_TO_CONTROL);
						return;
					}
					else if (activeChar.isInOlympiadMode() && !activeChar.isOlympiadStart())
					{
						// if L2Player is in Olympiad and the match isn't already started, send a Server->Client packet ActionFailed
						sendAF();
						return;
					}
					
					if (L2Creature.isInsidePeaceZone(pet, target))
					{
						requestFailed(SystemMessageId.TARGET_IN_PEACEZONE);
						return;
					}
					
					if (pet.getNpcId() == 12564 || pet.getNpcId() == 12621)
					{
						// sin eater and wyvern can't attack with attack button
						sendAF();
						return;
					}
					
					if (!pet.canChangeLockedTarget(target))
						return;
					
					if (target.isAutoAttackable(activeChar) || _ctrlPressed)
					{
						if (target instanceof L2DoorInstance)
						{
							if (((L2DoorInstance)target).isAttackable(activeChar)
									&& pet.getNpcId() != L2Summon.SWOOP_CANNON_ID)
								pet.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
						}
						// siege golem AI doesn't support attacking other than doors at the moment
						else if (pet.getNpcId() != L2Summon.SIEGE_GOLEM_ID)
							pet.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
					}
					else
					{
						pet.setFollowStatus(false);
						pet.getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, target);
					}
				}
				break;
			case 17:
			case 23: // pet - cancel action
				if (pet != null && !pet.isMovementDisabled() && !pet.isOutOfControl())
					pet.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
				break;
			case 19: // pet unsummon
				if (pet != null)
				{
					//returns pet to control item
					if (pet.isDead())
						activeChar.sendPacket(SystemMessageId.DEAD_PET_CANNOT_BE_RETURNED);
					//else if (pet.isOutOfControl())
					//	activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					else if (pet.isAttackingNow() || pet.isInCombat() || pet.isRooted() || pet.isBetrayed())
						activeChar.sendPacket(SystemMessageId.PET_CANNOT_SENT_BACK_DURING_BATTLE);
					else
					{
						// if it is a pet and not a summon
						if (pet instanceof L2PetInstance)
						{
							if (pet.isHungry())
								activeChar.sendPacket(SystemMessageId.YOU_CANNOT_RESTORE_HUNGRY_PETS);
							else
								pet.unSummon(activeChar);
						}
					}
				}
				//everything handled
				break;
			case 38: // pet mount
				// mount
				activeChar.mountPlayer(pet);
				break;
			case 28:
				activeChar.tryOpenPrivateBuyStore();
				break;
			case 32: // Wild Hog Cannon - Mode Change
				useSkill(4230);
				break;
			case 36: // Soulless - Toxic Smoke
				useSkill(4259);
				break;
			case 37: // Manufacture - Dwarven
				if (activeChar.getPrivateStoreType() != 0)
				{
					activeChar.setPrivateStoreType(L2Player.STORE_PRIVATE_NONE);
					activeChar.broadcastUserInfo();
				}
				if (activeChar.isSitting())
					activeChar.standUp();
				
				if (activeChar.getCreateList() == null)
					activeChar.setCreateList(new L2ManufactureList());
				
				activeChar.sendPacket(new RecipeShopManageList(activeChar, true));
				break;
			case 39: // Soulless - Parasite Burst
				useSkill(4138);
				break;
			case 41: // Wild Hog Cannon - Attack
				useSkill(4230);
				break;
			case 42: // Kai the Cat - Self Damage Shield
				useSkill(4378, activeChar);
				break;
			case 43: // Unicorn Merrow - Hydro Screw
				useSkill(4137);
				break;
			case 44: // Big Boom - Boom Attack
				useSkill(4139, activeChar.getTarget());
				break;
			case 45: // Unicorn Boxer - Master Recharge
				useSkill(4025, activeChar);
				break;
			case 46: // Mew the Cat - Mega Storm Strike
				useSkill(4261);
				break;
			case 47: // Silhouette - Steal Blood
				useSkill(4260);
				break;
			case 48: // Mechanic Golem - Mech. Cannon
				useSkill(4068);
				break;
			case 51: // Manufacture -  non-dwarven
				if (activeChar.getPrivateStoreType() != 0)
				{
					activeChar.setPrivateStoreType(L2Player.STORE_PRIVATE_NONE);
					activeChar.broadcastUserInfo();
				}
				if (activeChar.isSitting())
					activeChar.standUp();
				
				if (activeChar.getCreateList() == null)
					activeChar.setCreateList(new L2ManufactureList());
				activeChar.sendPacket(new RecipeShopManageList(activeChar, false));
				break;
			case 52: // unsummon
				if (pet != null && pet instanceof L2SummonInstance)
				{
					if (pet.isOutOfControl())
						sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					else if (pet.isAttackingNow() || pet.isInCombat())
						sendPacket(SystemMessageId.PET_CANNOT_SENT_BACK_DURING_BATTLE);
					else if (pet.isDead())
						sendPacket(SystemMessageId.DEAD_PET_CANNOT_BE_RETURNED);
					else
						pet.unSummon(activeChar);
				}
				break;
			case 53: // move to target
				if (target != null && pet != null && pet != target && !pet.isMovementDisabled()
						&& !pet.isOutOfControl())
				{
					pet.setFollowStatus(false);
					pet.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO,
							new L2CharPosition(target.getX(), target.getY(), target.getZ(), 0));
				}
				break;
			case 54: // move to target hatch/strider
				if (target != null && pet != null && pet != target && !pet.isMovementDisabled()
						&& !pet.isOutOfControl())
				{
					pet.setFollowStatus(false);
					pet.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO,
							new L2CharPosition(target.getX(), target.getY(), target.getZ(), 0));
				}
				break;
			case 61:
				// Private Store Package Sell
				activeChar.tryOpenPrivateSellStore(true);
				break;
			case 64:
				// Teleport bookmark button
			case 65:
				// Bot report Button.
				sendPacket(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
				break;
			case 96: // Quit Party Command Channel
				_log.info("96 Accessed");
				break;
			case 97: // Request Party Command Channel Info
				//if (!PartyCommandManager.getInstance().isPlayerInChannel(activeChar))
				//return;
				_log.info("97 Accessed");
				//PartyCommandManager.getInstance().getActiveChannelInfo(activeChar);
				break;
			case 1000: // Siege Golem - Siege Hammer
				if (target instanceof L2DoorInstance)
					useSkill(4079);
				break;
			case 1001: // Sin Eater - Ultimate Bombastic Buster
				break;
			case 1003: // Wind Hatchling/Strider - Wild Stun
				useSkill(4710);
				break;
			case 1004: // Wind Hatchling/Strider - Wild Defense
				useSkill(4711, activeChar);
				break;
			case 1005: // Star Hatchling/Strider - Bright Burst
				useSkill(4712);
				break;
			case 1006: // Star Hatchling/Strider - Bright Heal
				useSkill(4713, activeChar);
				break;
			case 1007: // Cat Queen - Blessing of Queen
				useSkill(4699, activeChar);
				break;
			case 1008: // Cat Queen - Gift of Queen
				useSkill(4700, activeChar);
				break;
			case 1009: // Cat Queen - Cure of Queen
				useSkill(4701);
				break;
			case 1010: // Unicorn Seraphim - Blessing of Seraphim
				useSkill(4702, activeChar);
				break;
			case 1011: // Unicorn Seraphim - Gift of Seraphim
				useSkill(4703, activeChar);
				break;
			case 1012: // Unicorn Seraphim - Cure of Seraphim
				useSkill(4704);
				break;
			case 1013: // Nightshade - Curse of Shade
				useSkill(4705);
				break;
			case 1014: // Nightshade - Mass Curse of Shade
				useSkill(4706);
				break;
			case 1015: // Nightshade - Shade Sacrifice
				useSkill(4707);
				break;
			case 1016: // Cursed Man - Cursed Blow
				useSkill(4709);
				break;
			case 1017: // Cursed Man - Cursed Strike/Stun
				useSkill(4708);
				break;
			case 1031: // Feline King - Slash
				useSkill(5135);
				break;
			case 1032: // Feline King - Spinning Slash
				useSkill(5136);
				break;
			case 1033: // Feline King - Grip of the Cat
				useSkill(5137);
				break;
			case 1034: // Magnus the Unicorn - Whiplash
				useSkill(5138);
				break;
			case 1035: // Magnus the Unicorn - Tridal Wave
				useSkill(5139);
				break;
			case 1036: // Spectral Lord - Corpse Kaboom
				useSkill(5142);
				break;
			case 1037: // Spectral Lord - Dicing Death
				useSkill(5141);
				break;
			case 1038: // Spectral Lord - Force Curse
				useSkill(5140);
				break;
			case 1039: // Swoop Cannon - Cannon Fodder
				if (!(target instanceof L2DoorInstance))
					useSkill(5110);
				break;
			case 1040: // Swoop Cannon - Big Bang
				if (!(target instanceof L2DoorInstance))
					useSkill(5111);
				break;
			case 1041: // Great Wolf - Bite Attack
				useSkill(5442);
				break;
			case 1042: // Great Wolf - Maul
				useSkill(5444);
				break;
			case 1043: // Great Wolf - Cry of the Wolf
				useSkill(5443);
				break;
			case 1044: // Great Wolf - Awakening
				useSkill(5445);
				break;
			case 1045: // Great Wolf - Howl
				useSkill(5584);
				break;
			// Added by Skatershi
			case 1046: // Strider - Roar
				useSkill(5585);
				break;
			// CT2.3
			case 1047: // Bite (Divine Beast)
				useSkill(5580);
				break;
			case 1048: // Stun Attack (Divine Beast)
				useSkill(5581);
				break;
			case 1049: // Fire Breath (Divine Beast)
				useSkill(5582);
				break;
			case 1050: // Roar (Divine Beast)
				useSkill(5583);
				break;
			case 1051: // Bless the Body (Feline Queen)
				useSkill(5638);
				break;
			case 1052: // Bless the Soul (Feline Queen)
				useSkill(5639);
				break;
			case 1053: // Haste (Feline Queen)
				useSkill(5640);
				break;
			case 1054: // Acumen (Seraphim Unicorn)
				useSkill(5643);
				break;
			case 1055: // Clarity (Seraphim Unicorn)
				useSkill(5647);
				break;
			case 1056: // Empower (Seraphim Unicorn)
				useSkill(5648);
				break;
			case 1057: // Wild Magic (Seraphim Unicorn)
				useSkill(5646);
				break;
			case 1058: // Death Whisper (Nightshade)
				useSkill(5652);
				break;
			case 1059: // Focus (Nightshade)
				useSkill(5653);
				break;
			case 1060: // Guidance (Nightshade)
				useSkill(5654);
				break;
			case 1061: // Death Blow
				useSkill(5745);
				break;
			case 1062: // Double Attack
				useSkill(5746);
				break;
			case 1063: // Spin Attack
				useSkill(5747);
				break;
			case 1064: // Meteor Shower
				useSkill(5748);
				break;
			case 1065: // Awakening
				useSkill(5753);
				break;
			case 1066: // Thunder Bolt
				useSkill(5749);
				break;
			case 1067: // Flash
				useSkill(5750);
				break;
			case 1068: // Lightning Wave
				useSkill(5751);
				break;
			case 1069: // Flare
				useSkill(5752);
				break;
			case 1070: // Buff Control
				useSkill(5771);
				break;
			case 1071: // Power Strike
				useSkill(5761);
				break;
			case 1072: // Piercing Attack
				useSkill(6046);
				break;
			case 1073: // Whirlwind
				useSkill(6047);
				break;
			case 1074: // Lance Smash
				useSkill(6048);
				break;
			case 1075: // Battle Cry
				useSkill(6049);
				break;
			case 1076: // Power Smash
				useSkill(6050);
				break;
			case 1077: // Energy Burst
				useSkill(6051);
				break;
			case 1078: // Shockwave
				useSkill(6052);
				break;
			case 1079: // Howl
				useSkill(6053);
				break;
			case 1080: // Rush (Phoenix)
				useSkill(6041);
				break;
			case 1081: // Cleanse (Phoenix)
				useSkill(6042);
				break;
			case 1082: // Flame Feather (Phoenix)
				useSkill(6043);
				break;
			case 1083: // Flame Beak (Phoenix)
				useSkill(6044);
				break;
			case 1084: // Switch State
				useSkill(6054);
				break;
			case 1086: // Cancel (Panther)
				useSkill(6094);
				break;
			case 1087: // Dark Claw (Panther)
				useSkill(6095);
				break;
			case 1088: // Fatal Claw (Panther)
				useSkill(6096);
				break;
			// CT2.3 Social Packets
			case 12:
				useSocial(2, activeChar);
				break;
			case 13:
				useSocial(3, activeChar);
				break;
			case 14:
				useSocial(4, activeChar);
				break;
			case 24:
				useSocial(6, activeChar);
				break;
			case 25:
				useSocial(5, activeChar);
				break;
			case 26:
				useSocial(7, activeChar);
				break;
			case 29:
				useSocial(8, activeChar);
				break;
			case 30:
				useSocial(9, activeChar);
				break;
			case 31:
				useSocial(10, activeChar);
				break;
			case 33:
				useSocial(11, activeChar);
				break;
			case 34:
				useSocial(12, activeChar);
				break;
			case 35:
				useSocial(13, activeChar);
				break;
			case 62:
				useSocial(14, activeChar);
				break;
			case 63:
				break;
			case 66:
				useSocial(15, activeChar);
				break;
			case 67:
			case 68:
			case 69:
			case 70:
				sendPacket(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
				break;
			case 71:
				break;
			case 72:
				break;
			case 73:
				break;
			
			default:
				_log.warn(activeChar.getName() + ": unhandled action type " + _actionId);
		}
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/*
	 * Cast a skill for active pet/servitor.
	 * Target is specified as a parameter but can be
	 * overwrited or ignored depending on skill type.
	 */
	private void useSkill(int skillId, L2Object target)
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getPrivateStoreType() != 0)
			return;
		
		L2Summon activeSummon = activeChar.getPet();
		
		if (activeSummon != null && !activeSummon.isOutOfControl())
		{
			if (activeSummon instanceof L2PetInstance && (activeSummon.getLevel() - activeChar.getLevel() > 20))
			{
				sendPacket(SystemMessageId.PET_TOO_HIGH_TO_CONTROL);
				return;
			}
			
			int lvl = PetSkillsTable.getInstance().getAvailableLevel(activeSummon, skillId);
			if (lvl == 0)
				return;
			
			L2Skill skill = SkillTable.getInstance().getInfo(skillId, lvl);
			if (skill == null)
				return;
			if (skill.isOffensive() && activeChar == target)
				return;
			
			activeSummon.setTarget(target);
			activeSummon.useMagic(skill, _ctrlPressed, _shiftPressed);
		}
	}
	
	/**
	 * Cast a skill for active pet/servitor.
	 * Target is retrieved from owner' target,
	 * then validated by overloaded method useSkill(int, L2Creature).
	 */
	private void useSkill(int skillId)
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		useSkill(skillId, activeChar.getTarget());
	}
	
	private void useSocial(int socialId, L2Player activeChar)
	{
		if (activeChar.isFishing())
		{
			sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		if (activeChar.getPrivateStoreType() == 0 && activeChar.getActiveRequester() == null
				&& !activeChar.isAlikeDead() && !activeChar.isCastingNow() && !activeChar.isCastingSimultaneouslyNow()
				&& (!activeChar.isAllSkillsDisabled() || activeChar.isInDuel())
				&& activeChar.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE
				&& FloodProtector.tryPerformAction(activeChar, Protected.SOCIAL))
		{
			activeChar.broadcastPacket(new SocialAction(activeChar.getObjectId(), socialId));
		}
	}
	
	@Override
	public String getType()
	{
		return _C__45_REQUESTACTIONUSE;
	}
}
