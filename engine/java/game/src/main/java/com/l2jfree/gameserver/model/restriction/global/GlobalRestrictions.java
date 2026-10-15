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
package com.l2jfree.gameserver.model.restriction.global;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeFlagInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author NB4L1
 */
public final class GlobalRestrictions
{
	private GlobalRestrictions()
	{
	}
	
	private static enum RestrictionMode implements Comparator<GlobalRestriction>
	{
		isRestricted,
		canInviteToParty,
		canCreateEffect,
		isInvul,
		isProtected,
		canTarget,
		canRequestRevive,
		canTeleport,
		canUseItemHandler,
		canDropItem,
		canDestroyItem,
		getCombatState,
		canStandUp,
		canPickUp,
		getNameColor,
		getTitleColor,
		// TODO
		
		isInsideZone,
		calcDamage,
		getTargetList,
		// TODO
		
		levelChanged,
		effectCreated,
		playerLoggedIn,
		playerDisconnected,
		playerKilled,
		playerRevived,
		isInsideZoneStateChanged,
		onBypassFeedback,
		onAction,
		useVoicedCommand,
		instanceChanged,
		// TODO
		;
		
		private final Method _method;
		
		private RestrictionMode()
		{
			for (Method method : GlobalRestriction.class.getMethods())
			{
				if (name().equals(method.getName()))
				{
					_method = method;
					return;
				}
			}
			
			throw new InternalError();
		}
		
		private boolean equalsMethod(Method method)
		{
			if (!_method.getName().equals(method.getName()))
				return false;
			
			if (!_method.getReturnType().equals(method.getReturnType()))
				return false;
			
			return Arrays.equals(_method.getParameterTypes(), method.getParameterTypes());
		}
		
		private static final RestrictionMode[] VALUES = RestrictionMode.values();
		
		private static RestrictionMode parse(Method method)
		{
			for (RestrictionMode mode : VALUES)
				if (mode.equalsMethod(method))
					return mode;
			
			return null;
		}
		
		@Override
		public int compare(GlobalRestriction o1, GlobalRestriction o2)
		{
			return Double.compare(getPriority(o2), getPriority(o1));
		}
		
		private double getPriority(GlobalRestriction restriction)
		{
			RestrictionPriority a1 = getMatchingMethod(restriction.getClass()).getAnnotation(RestrictionPriority.class);
			if (a1 != null)
				return a1.value();
			
			RestrictionPriority a2 = restriction.getClass().getAnnotation(RestrictionPriority.class);
			if (a2 != null)
				return a2.value();
			
			return RestrictionPriority.DEFAULT_PRIORITY;
		}
		
		private Method getMatchingMethod(Class<? extends GlobalRestriction> clazz)
		{
			for (Method method : clazz.getMethods())
				if (equalsMethod(method))
					return method;
			
			throw new InternalError();
		}
	}
	
	private static final GlobalRestriction[][] _restrictions = new GlobalRestriction[RestrictionMode.VALUES.length][0];
	
	public synchronized static void activate(GlobalRestriction restriction)
	{
		for (Method method : restriction.getClass().getMethods())
		{
			RestrictionMode mode = RestrictionMode.parse(method);
			
			if (mode == null)
				continue;
			
			if (method.getAnnotation(DisabledRestriction.class) != null)
				continue;
			
			GlobalRestriction[] restrictions = _restrictions[mode.ordinal()];
			
			if (!ArrayUtils.contains(restrictions, restriction))
				restrictions = ArrayUtils.add(restrictions, restriction);
			
			Arrays.sort(restrictions, mode);
			
			_restrictions[mode.ordinal()] = restrictions;
		}
	}
	
	public synchronized static void deactivate(GlobalRestriction restriction)
	{
		for (RestrictionMode mode : RestrictionMode.VALUES)
		{
			GlobalRestriction[] restrictions = _restrictions[mode.ordinal()];
			
			for (int index; (index = ArrayUtils.indexOf(restrictions, restriction)) != -1;)
				restrictions = ArrayUtils.remove(restrictions, index);
			
			_restrictions[mode.ordinal()] = restrictions;
		}
	}
	
	static
	{
		activate(new CursedWeaponRestriction());
		activate(new DuelRestriction());
		activate(new EradicationRestriction());
		activate(new JailRestriction());
		activate(new MercenaryTicketRestriction());
		activate(new OlympiadRestriction());
		activate(new ProtectionBlessingRestriction());
		activate(new InstanceEquipmentRestriction());
	}
	
	/**
	 * @param activeChar
	 * @param callingRestriction
	 * @return <b>true</b> if the player shouldn't be affected by any other kind of event system,<br>
	 *         because it's already participating in one, or it's just simply in a forbidden state<br>
	 *         <b>false</b> otherwise
	 */
	public static boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		// Avoid NPE and wrong usage
		if (activeChar == null)
			return true;
		
		// Cannot mess with offline trade
		if (activeChar.isInOfflineMode()) // trading in offline mode
		{
			//no need any message
			return true;
		}
		
		// Cannot mess with observation
		if (activeChar.inObserverMode()) // normal/olympiad observing
		{
			activeChar.sendMessage("You are in observer mode!");
			return true;
		}
		
		// Cannot mess with raids or sieges
		if (activeChar.isInsideZone(L2Zone.FLAG_NOESCAPE))
		{
			// TODO: msg
			return true;
		}
		
		if (activeChar.getMountType() == 2 && activeChar.isInsideZone(L2Zone.FLAG_NOWYVERN))
		{
			// TODO: msg
			return true;
		}
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.isRestricted.ordinal()])
			if (restriction.isRestricted(activeChar, callingRestriction))
				return true;
		
		return false;
	}
	
	public static boolean canInviteToParty(L2Player activeChar, L2Player target)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canInviteToParty.ordinal()])
			if (!restriction.canInviteToParty(activeChar, target))
				return false;
		
		return true;
	}
	
	public static boolean canCreateEffect(L2Creature activeChar, L2Creature target, L2Skill skill)
	{
		if (skill.isPassive())
			return false;
		
		if (activeChar == target)
			return true;
		
		final L2Player attacker_ = L2Object.getActingPlayer(activeChar);
		final L2Player target_ = L2Object.getActingPlayer(target);
		
		final boolean isOffensive = skill.isOffensive();
		
		if (attacker_ != null && attacker_ == target_)
			return true;
		
		// doors and siege flags cannot receive any effects
		if (target instanceof L2DoorInstance || target instanceof L2SiegeFlagInstance)
			return false;
		
		if (target.isInvul())
			return false;
		
		if (isInvul(activeChar, target, skill, false, attacker_, target_, isOffensive))
			return false;
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canCreateEffect.ordinal()])
			if (!restriction.canCreateEffect(activeChar, target, skill))
				return false;
		
		return true;
	}
	
	/**
	 * Indicates if the character can't harm another, but can hit/cast a skill on it.
	 */
	public static boolean isInvul(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage)
	{
		final L2Player attacker_ = L2Object.getActingPlayer(activeChar);
		final L2Player target_ = L2Object.getActingPlayer(target);
		
		final boolean isOffensive = (skill == null || skill.isOffensive());
		
		return isInvul(activeChar, target, skill, sendMessage, attacker_, target_, isOffensive);
	}
	
	private static boolean isInvul(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		if (isProtected(activeChar, target, skill, sendMessage, attacker_, target_, isOffensive))
			return true;
		
		// L2Creature.isInvul() calls this method
		//if (target.isInvul())
		//	return true;
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.isInvul.ordinal()])
			if (restriction.isInvul(activeChar, target, skill, sendMessage, attacker_, target_, isOffensive))
				return true;
		
		return false;
	}
	
	/**
	 * Indicates if the character can't hit/cast a skill on another, but can target it.
	 */
	public static boolean isProtected(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage)
	{
		final L2Player attacker_ = L2Object.getActingPlayer(activeChar);
		final L2Player target_ = L2Object.getActingPlayer(target);
		
		final boolean isOffensive = (skill == null || skill.isOffensive());
		
		return isProtected(activeChar, target, skill, sendMessage, attacker_, target_, isOffensive);
	}
	
	private static boolean isProtected(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		if (!canTarget(activeChar, target, sendMessage, attacker_, target_))
			return true;
		
		if (attacker_ != null && target_ != null && attacker_ != target_)
		{
			if (attacker_.isGM())
			{
				if (isOffensive && attacker_.getAccessLevel() < Config.GM_CAN_GIVE_DAMAGE)
					return true;
				else if (!target_.isGM()) // TODO
					return false;
			}
			
			if (attacker_.inObserverMode() || target_.inObserverMode())
			{
				if (sendMessage)
					attacker_.sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
				return true;
			}
			else if (attacker_.getLevel() <= Config.ALT_PLAYER_PROTECTION_LEVEL)
			{
				if (sendMessage)
					attacker_.sendMessage("Your level is too low to participate in a PvP combat.");
				return true;
			}
			else if (target_.getLevel() <= Config.ALT_PLAYER_PROTECTION_LEVEL)
			{
				if (sendMessage)
					attacker_.sendMessage("Target is under newbie protection.");
				return true;
			}
		}
		
		// Checking if target has moved to peace zone
		if (isOffensive && L2Creature.isInsidePeaceZone(activeChar, target))
		{
			if (sendMessage)
				activeChar.sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
			return true;
		}
		
		if (Config.ALLOW_OFFLINE_TRADE_PROTECTION && target_ != null && target_.isInOfflineMode())
			return true;
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.isProtected.ordinal()])
			if (restriction.isProtected(activeChar, target, skill, sendMessage, attacker_, target_, isOffensive))
				return true;
		
		return false;
	}
	
	/**
	 * Indicates if the character can't even target another.
	 */
	public static boolean canTarget(L2Creature activeChar, L2Creature target, boolean sendMessage)
	{
		final L2Player attacker_ = L2Object.getActingPlayer(activeChar);
		final L2Player target_ = L2Object.getActingPlayer(target);
		
		return canTarget(activeChar, target, sendMessage, attacker_, target_);
	}
	
	private static boolean canTarget(L2Creature activeChar, L2Creature target, boolean sendMessage,
			L2Player attacker_, L2Player target_)
	{
		// if GM is invisible, exclude him from the normal gameplay
		if (target_ != null && target_.isGM() && target_.getAppearance().isInvisible())
		{
			// if there is an attacker, but it isn't playable, or not GM
			if (activeChar != null && (attacker_ == null || !attacker_.isGM()))
			{
				return false;
			}
		}
		
		if (attacker_ != null && target_ != null && attacker_ != target_)
		{
			if (Config.SIEGE_ONLY_REGISTERED)
			{
				if (!target_.canBeTargetedByAtSiege(attacker_))
				{
					if (sendMessage)
						attacker_.sendMessage("Player interaction disabled during sieges.");
					return false;
				}
			}
		}
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canTarget.ordinal()])
			if (!restriction.canTarget(activeChar, target, sendMessage, attacker_, target_))
				return false;
		
		return true;
	}
	
	public static boolean canRequestRevive(L2Player activeChar)
	{
		if (activeChar.isPendingRevive())
			return false;
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canRequestRevive.ordinal()])
			if (!restriction.canRequestRevive(activeChar))
				return false;
		
		return true;
	}
	
	public static boolean canTeleport(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canTeleport.ordinal()])
			if (!restriction.canTeleport(activeChar))
				return false;
		
		return true;
	}
	
	public static boolean canUseItemHandler(Class<? extends IItemHandler> clazz, int itemId, L2Playable activeChar,
			L2ItemInstance item)
	{
		final L2Player player = L2Object.getActingPlayer(activeChar);
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canUseItemHandler.ordinal()])
			if (!restriction.canUseItemHandler(clazz, itemId, activeChar, item, player))
				return false;
		
		return true;
	}
	
	public static boolean canDropItem(L2Player activeChar, int itemId, L2ItemInstance item)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canDropItem.ordinal()])
			if (!restriction.canDropItem(activeChar, itemId, item))
				return false;
		
		return true;
	}
	
	public static boolean canDestroyItem(L2Player activeChar, int itemId, L2ItemInstance item)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canDestroyItem.ordinal()])
			if (!restriction.canDestroyItem(activeChar, itemId, item))
				return false;
		
		return true;
	}
	
	public enum CombatState
	{
		ENEMY,
		FRIEND,
		NEUTRAL;
	}
	
	public static boolean isCombat(L2Player activeChar, L2Player target)
	{
		switch (getCombatState(activeChar, target))
		{
			case ENEMY:
			case FRIEND:
				return true;
		}
		
		return false;
	}
	
	public static CombatState getCombatState(L2Player activeChar, L2Player target)
	{
		if (activeChar == null || target == null)
			return CombatState.NEUTRAL;
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.getCombatState.ordinal()])
		{
			final CombatState state = restriction.getCombatState(activeChar, target);
			
			switch (state)
			{
				case ENEMY:
				case FRIEND:
					return state;
			}
		}
		
		return CombatState.NEUTRAL;
	}
	
	public static boolean canStandUp(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canStandUp.ordinal()])
			if (!restriction.canStandUp(activeChar))
				return false;
		
		return true;
	}
	
	public static boolean canPickUp(L2Player activeChar, L2ItemInstance item, L2PetInstance pet)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.canPickUp.ordinal()])
			if (!restriction.canPickUp(activeChar, item, pet))
				return false;
		
		return true;
	}
	
	public static int getNameColor(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.getNameColor.ordinal()])
		{
			final int value = restriction.getNameColor(activeChar);
			
			if (value != -1)
				return value;
		}
		
		return -1;
	}
	
	public static int getTitleColor(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.getTitleColor.ordinal()])
		{
			final int value = restriction.getTitleColor(activeChar);
			
			if (value != -1)
				return value;
		}
		
		return -1;
	}
	
	// TODO
	
	/**
	 * @return tri-state value:
	 *         <ul>
	 *         <li>{@link Boolean#TRUE} if inside,</li>
	 *         <li>{@link Boolean#FALSE} if not,</li>
	 *         <li><code>null</code> if not influenced</li>
	 *         </ul>
	 */
	public static Boolean isInsideZone(L2Creature activeChar, byte zone)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.isInsideZone.ordinal()])
		{
			final Boolean value = restriction.isInsideZone(activeChar, zone);
			
			if (value != null)
				return value;
		}
		
		return null;
	}
	
	public static double calcDamage(L2Creature activeChar, L2Creature target, double damage, L2Skill skill)
	{
		// PvP bonus
		if (activeChar instanceof L2Playable && target instanceof L2Playable)
		{
			if (skill == null)
			{
				damage *= activeChar.calcStat(Stats.PVP_PHYSICAL_DMG, 1, target, skill);
				damage *= target.calcStat(Stats.PVP_PHYSICAL_DEF, 1, activeChar, skill);
			}
			else if (skill.isMagic())
			{
				damage *= activeChar.calcStat(Stats.PVP_MAGICAL_DMG, 1, target, skill);
				damage *= target.calcStat(Stats.PVP_MAGICAL_DEF, 1, activeChar, skill);
			}
			else
			{
				damage *= activeChar.calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, target, skill);
				damage *= target.calcStat(Stats.PVP_PHYS_SKILL_DEF, 1, activeChar, skill);
			}
		}
		
		// +20% damage from behind attacks, +5% from side attacks
		damage *= Formulas.calcPositionRate(activeChar, target);
		damage *= Formulas.calcElemental(activeChar, target, skill);
		damage *= Formulas.calcSoulBonus(activeChar, skill);
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.calcDamage.ordinal()])
			damage = restriction.calcDamage(activeChar, target, damage, skill);
		
		return Math.max(1, damage);
	}
	
	public static List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill,
			L2Creature target)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.getTargetList.ordinal()])
		{
			final List<L2Creature> value = restriction.getTargetList(type, activeChar, skill, target);
			
			if (value != null)
				return value;
		}
		
		return null;
	}
	
	// TODO
	
	public static void levelChanged(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.levelChanged.ordinal()])
			restriction.levelChanged(activeChar);
	}
	
	public static void effectCreated(L2Effect effect)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.effectCreated.ordinal()])
			restriction.effectCreated(effect);
	}
	
	public static void playerLoggedIn(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.playerLoggedIn.ordinal()])
			restriction.playerLoggedIn(activeChar);
	}
	
	public static void playerDisconnected(L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.playerDisconnected.ordinal()])
			restriction.playerDisconnected(activeChar);
	}
	
	public static boolean playerKilled(L2Creature activeChar, L2Player target)
	{
		final L2Player killer = L2Object.getActingPlayer(activeChar);
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.playerKilled.ordinal()])
			if (restriction.playerKilled(activeChar, target, killer))
				return true;
		
		return false;
	}
	
	public static void playerRevived(L2Player player)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.playerRevived.ordinal()])
			restriction.playerRevived(player);
	}
	
	public static void isInsideZoneStateChanged(L2Creature activeChar, byte zone, boolean isInsideZone)
	{
		switch (zone)
		{
			case L2Zone.FLAG_PVP:
			{
				if (isInsideZone)
					activeChar.sendPacket(SystemMessageId.ENTERED_COMBAT_ZONE);
				else
					activeChar.sendPacket(SystemMessageId.LEFT_COMBAT_ZONE);
			}
		}
		
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.isInsideZoneStateChanged.ordinal()])
			restriction.isInsideZoneStateChanged(activeChar, zone, isInsideZone);
	}
	
	public static void instanceChanged(L2Player activeChar, int oldInstance, int newInstance)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.instanceChanged.ordinal()])
			restriction.instanceChanged(activeChar, oldInstance, newInstance);
	}
	
	public static boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.onBypassFeedback.ordinal()])
			if (restriction.onBypassFeedback(npc, activeChar, command))
				return true;
		
		return false;
	}
	
	public static boolean onAction(L2Npc npc, L2Player activeChar)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.onAction.ordinal()])
			if (restriction.onAction(npc, activeChar))
				return true;
		
		return false;
	}
	
	public static boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		for (GlobalRestriction restriction : _restrictions[RestrictionMode.useVoicedCommand.ordinal()])
			if (restriction.useVoicedCommand(command, activeChar, target))
				return true;
		
		return false;
	}
	
	// TODO
}
