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
package com.l2jfree.gameserver.gameobjects;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.gameobjects.knownlist.PlayableKnownList;
import com.l2jfree.gameserver.gameobjects.stat.PlayableStat;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.taskmanager.MovementController;
import com.l2jfree.gameserver.taskmanager.PacketBroadcaster.BroadcastMode;
import com.l2jfree.lang.L2Math;
import com.l2jfree.tools.random.Rnd;

/**
 * This class represents all Playable characters in the world.<BR><BR>
 * 
 * L2Playable :<BR><BR>
 * <li>L2Player</li>
 * <li>L2Summon</li><BR><BR>
 * 
 */
public abstract class L2Playable extends L2Creature
{
	@SuppressWarnings("hiding")
	public static final L2Playable[] EMPTY_ARRAY = new L2Playable[0];
	
	private boolean _isNoblesseBlessed = false; // For Noblesse Blessing skill, restores buffs after death
	private boolean _getCharmOfLuck = false; // Charm of Luck - During a Raid/Boss war, decreased chance for death penalty
	private boolean _isPhoenixBlessed = false; // For Soul of The Phoenix or Salvation buffs
	private boolean _isSilentMoving = false; // Silent Move
	private boolean _protectionBlessing = false; // Blessed by Blessing of Protection
	private boolean _lockedTarget = false;
	
	/**
	 * Constructor of L2Playable (use L2Creature constructor).<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the L2Creature constructor to create an empty _skills slot and link copy basic Calculator set to this L2Playable </li><BR><BR>
	 * 
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2CreatureTemplate to apply to the L2Playable
	 * 
	 */
	public L2Playable(int objectId, L2CreatureTemplate template)
	{
		super(objectId, template);
		getKnownList(); // Init knownlist
		getStat(); // Init stats
		getStatus(); // Init status
		setIsInvul(false);
	}
	
	@Override
	public abstract PlayableKnownList getKnownList();
	
	@Override
	public abstract PlayableStat getStat();
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (killer != null && killer.getActingPlayer() != null)
			killer.getActingPlayer().onKillUpdatePvPKarma(this);
		
		return true;
	}
	
	public boolean checkIfPvP(L2Creature target)
	{
		if (target == null)
			return false; // Target is null
		if (target == this)
			return false; // Target is self
		if (!(target instanceof L2Playable))
			return false; // Target is not a L2Playable
			
		L2Player player = null;
		if (this instanceof L2Player)
			player = (L2Player)this;
		else if (this instanceof L2Summon)
			player = ((L2Summon)this).getOwner();
		
		if (player == null)
			return false; // Active player is null
		if (player.getKarma() != 0)
			return false; // Active player has karma
			
		L2Player targetPlayer = null;
		if (target instanceof L2Player)
			targetPlayer = (L2Player)target;
		else if (target instanceof L2Summon)
			targetPlayer = ((L2Summon)target).getOwner();
		
		if (targetPlayer == null)
			return false; // Target player is null
			
		if (targetPlayer == this)
			return false; // Target player is self
			
		return targetPlayer.getKarma() == 0;
	}
	
	/**
	 * Return True.<BR><BR>
	 */
	@Override
	public boolean isAttackable()
	{
		return true;
	}
	
	@Override
	public final void sendDamageMessage(L2Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss)
	{
		// All messages are verified on retail
		L2Player attOwner = getActingPlayer();
		L2Player trgOwner = target.getActingPlayer();
		if (miss)
		{
			attOwner.sendPacket(new SystemMessage(SystemMessageId.C1_ATTACK_WENT_ASTRAY).addCharName(this));
			target.sendAvoidMessage(this);
			return;
		}
		
		if (pcrit)
		{
			attOwner.sendPacket(new SystemMessage(SystemMessageId.C1_HAD_CRITICAL_HIT).addCharName(this));
			
			if (this instanceof L2Player && target instanceof L2Npc)
			{
				// Soul Mastery skill
				final L2Skill skill = getKnownSkill(L2Skill.SKILL_SOUL_MASTERY);
				
				if (skill != null && Rnd.get(100) < skill.getCritChance())
					attOwner.absorbSoulFromNpc(skill, target);
			}
		}
		
		if (mcrit)
			sendPacket(SystemMessageId.CRITICAL_HIT_MAGIC);
		
		if (trgOwner != null && attOwner != trgOwner)
		{
			if (attOwner.isInOlympiadMode() && target instanceof L2Player && trgOwner.isInOlympiadMode()
					&& trgOwner.getOlympiadGameId() == attOwner.getOlympiadGameId())
			{
				Olympiad.getInstance().notifyCompetitorDamage(attOwner, damage, attOwner.getOlympiadGameId());
			}
		}
		
		final SystemMessage sm;
		if (target.isInvul() && !(target instanceof L2NpcInstance))
		{
			sm = SystemMessageId.ATTACK_WAS_BLOCKED.getSystemMessage();
		}
		// Still needs retail verification
		/*else if (this instanceof L2Player &&
				(target instanceof L2DoorInstance ||
						target instanceof L2ControlTowerInstance))
		{
			sm = new SystemMessage(SystemMessageId.YOU_DID_S1_DMG);
			sm.addNumber(damage);
		}*/
		else
		{
			sm = new SystemMessage(SystemMessageId.C1_GAVE_C2_DAMAGE_OF_S3);
			sm.addCharName(this);
			sm.addCharName(target);
			sm.addNumber(damage);
		}
		attOwner.sendPacket(sm);
	}
	
	@Override
	public final void sendAvoidMessage(L2Creature attacker)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_EVADED_C2_ATTACK);
		sm.addCharName(this);
		sm.addCharName(attacker);
		getActingPlayer().sendPacket(sm);
	}
	
	@Override
	public final void sendResistedMyMagicMessage(L2Creature target)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_ATTACK_FAILED);
		sm.addCharName(this);
		getActingPlayer().sendPacket(sm);
		target.sendResistedAgainstMagicMessage(this);
	}
	
	@Override
	public final void sendResistedAgainstMagicMessage(L2Creature attacker)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_RESISTED_C2_MAGIC);
		sm.addCharName(this);
		sm.addCharName(attacker);
		getActingPlayer().sendPacket(sm);
	}
	
	@Override
	public final void sendResistedMyMagicSlightlyMessage(L2Creature target)
	{
		// Perhaps this method is not retail and we should use
		//sendResistedMyMagicMessage(L2Creature, boolean) & sendResistedAgainstMagicWeaklyMessage(L2Creature, boolean)
		SystemMessage sm = new SystemMessage(SystemMessageId.DAMAGE_DECREACE_C1_RESISTED_C2_MAGIC);
		sm.addCharName(target);
		sm.addCharName(this);
		getActingPlayer().sendPacket(sm);
		target.sendResistedAgainstMagicWeaklyMessage(this);
	}
	
	@Override
	public final void sendResistedAgainstMagicWeaklyMessage(L2Creature attacker)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_WEAKLY_RESISTED_C2_MAGIC);
		sm.addCharName(this);
		sm.addCharName(attacker);
		getActingPlayer().sendPacket(sm);
	}
	
	// Support for Noblesse Blessing skill, where buffs are retained after resurrect
	public final boolean isNoblesseBlessed()
	{
		return _isNoblesseBlessed;
	}
	
	public final void setIsNoblesseBlessed(boolean value)
	{
		_isNoblesseBlessed = value;
		updateAbnormalEffect();
	}
	
	public final void startNoblesseBlessing()
	{
		setIsNoblesseBlessed(true);
	}
	
	public final void stopNoblesseBlessing(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.NOBLESSE_BLESSING);
		
		setIsNoblesseBlessed(false);
	}
	
	// Support for Soul of the Phoenix and Salvation skills
	public final boolean isPhoenixBlessed()
	{
		return _isPhoenixBlessed;
	}
	
	public final void setIsPhoenixBlessed(boolean value)
	{
		_isPhoenixBlessed = value;
		updateAbnormalEffect();
	}
	
	public final void startPhoenixBlessing()
	{
		setIsPhoenixBlessed(true);
	}
	
	public final void stopPhoenixBlessing(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.PHOENIX_BLESSING);
		
		setIsPhoenixBlessed(false);
	}
	
	/**
	 * Set the Silent Moving mode Flag.<BR><BR>
	 */
	public void setSilentMoving(boolean flag)
	{
		_isSilentMoving = flag;
	}
	
	/**
	 * Return True if the Silent Moving mode is active.<BR><BR>
	 */
	public boolean isSilentMoving()
	{
		return _isSilentMoving;
	}
	
	// For Newbie Protection Blessing skill, keeps you safe from an attack by a chaotic character >= 10 levels apart from you
	public final boolean getProtectionBlessing()
	{
		return _protectionBlessing;
	}
	
	public final void setProtectionBlessing(boolean value)
	{
		_protectionBlessing = value;
		updateAbnormalEffect();
	}
	
	public void startProtectionBlessing()
	{
		setProtectionBlessing(true);
	}
	
	/**
	* @param effect
	*/
	public void stopProtectionBlessing(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.PROTECTION_BLESSING);
		
		setProtectionBlessing(false);
	}
	
	// Charm of Luck - During a Raid/Boss war, decreased chance for death penalty
	public final boolean getCharmOfLuck()
	{
		return _getCharmOfLuck;
	}
	
	public final void setCharmOfLuck(boolean value)
	{
		_getCharmOfLuck = value;
		updateAbnormalEffect();
	}
	
	public final void startCharmOfLuck()
	{
		setCharmOfLuck(true);
	}
	
	public final void stopCharmOfLuck(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.CHARM_OF_LUCK);
		
		setCharmOfLuck(false);
	}
	
	public final void updateEffectIcons()
	{
		addPacketBroadcastMask(BroadcastMode.UPDATE_EFFECT_ICONS);
	}
	
	@Override
	protected boolean shouldAddPacketBroadcastMask()
	{
		return true;
	}
	
	public abstract void updateEffectIconsImpl();
	
	@Override
	public final void onForcedAttack(L2Player player)
	{
		final L2Player targetPlayer = getActingPlayer();
		
		if (player.getOlympiadGameId() != targetPlayer.getOlympiadGameId())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isInOlympiadMode() && targetPlayer.isInOlympiadMode())
		{
			if (!player.isOlympiadStart())
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		super.onForcedAttack(player);
	}
	
	public final void removeFromLists(L2Object[] array)
	{
		if (hasAI())
			getAI().stopFollow();
		getStatus().stopHpMpRegeneration();
		DecayTaskManager.getInstance().cancelDecayTask(this);
		AttackStanceTaskManager.getInstance().removeAttackStanceTask(this);
		MovementController.getInstance().remove(this);
		
		for (L2Object obj : array)
		{
			if (obj == null)
				return;
			
			if (obj.getKnownList().getKnownObject(getObjectId()) == this)
				obj.getKnownList().removeKnownObject(this);
			
			if (obj instanceof L2Creature)
			{
				L2Creature cha = (L2Creature)obj;
				
				cha.getAttackByList().remove(this);
				
				if (cha.getTarget() == this)
					cha.setTarget(null);
				
				if (cha.hasAI())
					cha.getAI().removeReferencesOf(this);
				
				if (obj instanceof L2Attackable)
				{
					L2Attackable mob = (L2Attackable)obj;
					
					mob.getAggroList().remove(this);
					mob.getAbsorbersList().remove(this);
					
					if (mob.getOverhitAttacker() == this)
						mob.setOverhitAttacker(null);
					
					if (mob.getSeeder() == this)
						mob.setSeeder(null);
				}
			}
		}
		
		//TODO
	}
	
	public void setLockedTarget(boolean value)
	{
		_lockedTarget = value;
	}
	
	@Override
	public void setTarget(L2Object newTarget)
	{
		// not sure about this
		if (!canChangeLockedTarget(newTarget))
			return;
		
		super.setTarget(newTarget);
	}
	
	public boolean canChangeLockedTarget(L2Object newTarget)
	{
		if (getTarget() != newTarget && _lockedTarget)
		{
			if (newTarget == null)
				getActingPlayer().sendPacket(SystemMessageId.FAILED_DISABLE_TARGET);
			else
				getActingPlayer().sendPacket(SystemMessageId.FAILED_CHANGE_TARGET);
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		return true;
	}
	
	public static final int WEIGHT_PENALTY_MIN = 0;
	public static final int WEIGHT_PENALTY_MAX = 4;
	
	private boolean _isOverloaded = false;
	
	@Override
	public final boolean isOverloaded()
	{
		return _isOverloaded;
	}
	
	public final void setIsOverloaded(boolean value)
	{
		_isOverloaded = value;
	}
	
	public long getCurrentLoad()
	{
		return 0;
	}
	
	public int getMaxLoad()
	{
		return 0;
	}
	
	public int getWeightPenalty()
	{
		return 0;
	}
	
	public void setWeightPenalty(int value)
	{
	}
	
	public final double getWeightProc()
	{
		if (getActingPlayer().getDietMode())
			return 0;
		
		double maxLoad = getMaxLoad();
		if (maxLoad <= 0)
			return 0;
		
		double currentLoad = getCurrentLoad();
		if (currentLoad >= maxLoad)
			return 1;
		
		currentLoad -= calcStat(Stats.WEIGHT_LIMIT, 0, this, null);
		
		return L2Math.limit(0D, currentLoad / maxLoad, 1D);
	}
	
	public final int getExpectedWeightPenalty()
	{
		final double weightproc = getWeightProc();
		
		if (weightproc < 0.500)
			return 0/*WEIGHT_PENALTY_MIN*/;
		else if (weightproc < 0.666)
			return 1;
		else if (weightproc < 0.800)
			return 2;
		else if (weightproc < 1.000)
			return 3;
		else
			return 4/*WEIGHT_PENALTY_MAX*/;
	}
	
	public final void refreshOverloaded()
	{
		final int newWeightPenalty = getExpectedWeightPenalty();
		
		L2Skill skill = getKnownSkill(4270);
		int skillLevel = skill == null ? 0 : skill.getLevel();
		
		if (getWeightPenalty() != newWeightPenalty || skillLevel != newWeightPenalty)
		{
			setWeightPenalty(newWeightPenalty);
			
			if (newWeightPenalty > 0)
				addSkill(4270, newWeightPenalty);
			else
				removeSkill(skill);
			
			if (this instanceof L2Player)
			{
				L2Player player = (L2Player)this;
				
				player.sendEtcStatusUpdate();
				player.broadcastUserInfo();
			}
		}
		
		setIsOverloaded(newWeightPenalty == WEIGHT_PENALTY_MAX);
	}
	
	public final L2Skill addSkill(int skillId, int skillLvl)
	{
		return addSkill(SkillTable.getInstance().getInfo(skillId, skillLvl));
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public final L2Skill addSkill(L2Skill newSkill)
	{
		return super.addSkill(newSkill);
	}
	
	public final L2Skill removeSkill(int skillId)
	{
		return removeSkill(getKnownSkill(skillId));
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public L2Skill removeSkill(L2Skill skill)
	{
		return super.removeSkill(skill);
	}
	
	public final void useMagic(L2Skill skill, boolean forceUse, boolean dontMove)
	{
		useMagic(new SkillUsageRequest(skill, forceUse, dontMove));
	}
	
	public void useMagic(SkillUsageRequest request)
	{
		final L2Skill skill = request.getSkill();
		
		if (skill.isToggle() || skill.isPotion())
		{
			doSimultaneousCast(skill);
			return;
		}
		
		// Notify the AI with AI_INTENTION_CAST and target
		getAI().setIntention(CtrlIntention.AI_INTENTION_CAST, request);
	}
	
	protected abstract boolean checkUseMagicConditions(L2Skill skill, boolean forceUse);
	
	/**
	 * <b>WARNING:</b> for players and summons this mustn't be called except through AI!
	 */
	@Override
	public void doCast(L2Skill skill)
	{
		final SkillUsageRequest request = getCurrentSkill();
		
		if (request == null)
		{
			_log.warn("Missing 'getCurrentSkill()'!", new IllegalStateException());
			useMagic(new SkillUsageRequest(skill));
			return;
		}
		
		if (request.getSkill() != skill)
		{
			_log.warn("Different 'request.getSkill()' and 'skill'!", new IllegalStateException());
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (!checkUseMagicConditions(request.getSkill(), request.isCtrlPressed()))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		super.doCast(skill);
	}
	
	@Override
	public void doSimultaneousCast(L2Skill skill)
	{
		if (!checkUseMagicConditions(skill, false))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		super.doSimultaneousCast(skill);
	}
	
	public final SkillUsageRequest getCurrentSkill()
	{
		return getAI().getCurrentSkill();
	}
	
	private long _skillQueueProtectionTime = 0;
	
	public void setSkillQueueProtectionTime(long time)
	{
		_skillQueueProtectionTime = time;
	}
	
	public long getSkillQueueProtectionTime()
	{
		return _skillQueueProtectionTime;
	}
}
