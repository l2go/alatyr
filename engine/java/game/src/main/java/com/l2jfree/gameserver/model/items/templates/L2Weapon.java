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
package com.l2jfree.gameserver.model.items.templates;


import java.util.List;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.tools.random.Rnd;

/**
 * This class is dedicated to the management of weapons.
 * 
 * @version $Revision: 1.4.2.3.2.5 $ $Date: 2005/04/02 15:57:51 $
 */
public final class L2Weapon extends L2Equip
{
	private final int _soulShotCount;
	private final int _spiritShotCount;
	private final int _pDam;
	private final int _rndDam;
	private final int _critical;
	private final double _hitModifier;
	private final int _avoidModifier;
	private final int _shieldDef;
	private final double _shieldDefRate;
	private final int _atkSpeed;
	private final int _atkReuse;
	private final int _mpConsume;
	private final int _mDam;
	private final int _changeWeaponId;
	
	// Attached skills (e.g. Special Abilities)
	private L2Skill[] _onCastSkills;
	private int[] _onCastChances;
	private L2Skill[] _onCritSkills;
	private int[] _onCritChances;
	
	/**
	 * Constructor for Weapon.<BR>
	 * <BR>
	 * <BR>
	 * <U><I>Variables filled :</I></U><BR>
	 * <LI>_soulShotCount & _spiritShotCount</LI>
	 * <LI>_pDam & _mDam & _rndDam</LI>
	 * <LI>_critical</LI>
	 * <LI>_hitModifier</LI>
	 * <LI>_avoidModifier</LI>
	 * <LI>_shieldDes & _shieldDefRate</LI>
	 * <LI>_atkSpeed & _AtkReuse</LI>
	 * <LI>_mpConsume</LI>
	 * <LI>_races & _classes & _sex</LI>
	 * <LI>_sIds & _sLvls</LI>
	 * 
	 * @param type : L2ArmorType designating the type of armor
	 * @param set : StatsSet designating the set of couples (key,value) characterizing the weapon
	 * @see L2Item constructor
	 */
	public L2Weapon(L2WeaponType type, StatsSet set)
	{
		super(type, set);
		_soulShotCount = set.getInteger("soulshots");
		_spiritShotCount = set.getInteger("spiritshots");
		_pDam = set.getInteger("p_dam");
		_rndDam = set.getInteger("rnd_dam");
		_critical = set.getInteger("critical");
		_hitModifier = set.getDouble("hit_modify");
		_avoidModifier = set.getInteger("avoid_modify");
		_shieldDef = set.getInteger("shield_def");
		_shieldDefRate = set.getDouble("shield_def_rate");
		_atkSpeed = set.getInteger("atk_speed");
		_atkReuse = set.getInteger("atk_reuse", initAtkReuse(type, _atkSpeed));
		_mpConsume = set.getInteger("mp_consume");
		_mDam = set.getInteger("m_dam");
		_changeWeaponId = set.getInteger("change_weaponId");
		
		String[] onCastSkillDefs = set.getString("skills_onCast").split(";");
		String[] onCritSkillDefs = set.getString("skills_onCrit").split(";");
		
		List<WeaponSkill> onCastSkills = null;
		List<WeaponSkill> onCritSkills = null;
		
		// OnCast skills (chance)
		if (onCastSkillDefs != null && onCastSkillDefs.length > 0)
		{
			onCastSkills = parseChanceSkills(onCastSkillDefs, "onCast", "weapon");
		}
		
		// OnCrit skills (chance)
		if (onCritSkillDefs != null && onCritSkillDefs.length > 0)
		{
			onCritSkills = parseChanceSkills(onCritSkillDefs, "onCrit", "weapon");
		}
		
		if (onCastSkills != null && !onCastSkills.isEmpty())
		{
			_onCastSkills = new L2Skill[onCastSkills.size()];
			_onCastChances = new int[onCastSkills.size()];
			int i = 0;
			for (WeaponSkill ws : onCastSkills)
			{
				_onCastSkills[i] = ws.skill;
				_onCastChances[i] = ws.chance;
				i++;
			}
		}
		if (onCritSkills != null && !onCritSkills.isEmpty())
		{
			_onCritSkills = new L2Skill[onCritSkills.size()];
			_onCritChances = new int[onCritSkills.size()];
			int i = 0;
			for (WeaponSkill ws : onCritSkills)
			{
				_onCritSkills[i] = ws.skill;
				_onCritChances[i] = ws.chance;
				i++;
			}
		}
	}
	
	private static int initAtkReuse(L2WeaponType type, int atkSpeed)
	{
		// http://www.l2p.bravehost.com/endL2P/misc.html
		// Normal bows have a base Weapon Delay of 1500 - Like Draconic Bow (atkSpd == 293)
		// Yumi bows have a base Weapon Delay of 820 - Like Soul Bow (atkSpd == 227)
		//
		// Standing still and with no SA, normal bows and yumi bows shoot the exact same number of shots per second.
		// So time required for one attack:
		// (333.3 / atkSpeed) * (1500 + weaponDelay) = x (constant)
		//
		// (333.3 / 293) * (1500 + 1500) = x = 3413
		// (333.3 / 227) * (1500 + 820) = x = 3406
		//
		// weaponDelay = x * (atkSpeed / 333.3) - 1500
		// weaponDelay = 3000 / 293 * atkSpeed - 1500
		
		if (type == L2WeaponType.BOW)
		{
			if (atkSpeed == 293)
				return 1500;
			if (atkSpeed == 227)
				return 820;
			
			return (int)(3000.0 / 293.0 * atkSpeed - 1500.0);
			//throw new IllegalArgumentException("Wrong bow parameters!");
		}
		else if (type == L2WeaponType.CROSSBOW)
		{
			return 1200;
		}
		
		return 0;
	}
	
	/**
	 * Returns the type of Weapon
	 * 
	 * @return L2WeaponType
	 */
	@Override
	public L2WeaponType getItemType()
	{
		return (L2WeaponType)super._type;
	}
	
	/**
	 * Returns the ID of the Etc item after applying the mask.
	 * 
	 * @return int : ID of the Weapon
	 */
	@Override
	public int getItemMask()
	{
		return getItemType().mask();
	}
	
	/**
	 * Returns the quantity of SoulShot used.
	 * 
	 * @return int
	 */
	public int getSoulShotCount()
	{
		return _soulShotCount;
	}
	
	/**
	 * Returns the quatity of SpiritShot used.
	 * 
	 * @return int
	 */
	public int getSpiritShotCount()
	{
		return _spiritShotCount;
	}
	
	/**
	 * Returns the physical damage.
	 * 
	 * @return int
	 */
	public int getPDamage()
	{
		return _pDam;
	}
	
	/**
	 * Returns the random damage inflicted by the weapon
	 * 
	 * @return int
	 */
	public int getRandomDamage()
	{
		return _rndDam;
	}
	
	/**
	 * Returns the attack speed of the weapon
	 * 
	 * @return int
	 */
	public int getAttackSpeed()
	{
		return _atkSpeed;
	}
	
	/**
	 * Return the Attack Reuse Delay of the L2Weapon.<BR>
	 * <BR>
	 * 
	 * @return int
	 */
	public int getAttackReuseDelay()
	{
		return _atkReuse;
	}
	
	/**
	 * Returns the avoid modifier of the weapon
	 * 
	 * @return int
	 */
	public int getAvoidModifier()
	{
		return _avoidModifier;
	}
	
	/**
	 * Returns the rate of critical hit
	 * 
	 * @return int
	 */
	public int getCritical()
	{
		return _critical;
	}
	
	/**
	 * Returns the hit modifier of the weapon
	 * 
	 * @return double
	 */
	public double getHitModifier()
	{
		return _hitModifier;
	}
	
	/**
	 * Returns the magical damage inflicted by the weapon
	 * 
	 * @return int
	 */
	public int getMDamage()
	{
		return _mDam;
	}
	
	/**
	 * Returns the MP consumption with the weapon
	 * 
	 * @return int
	 */
	public int getMpConsume()
	{
		return _mpConsume;
	}
	
	/**
	 * Returns the shield defense of the weapon
	 * 
	 * @return int
	 */
	public int getShieldDef()
	{
		return _shieldDef;
	}
	
	/**
	 * Returns the rate of shield defense of the weapon
	 * 
	 * @return double
	 */
	public double getShieldDefRate()
	{
		return _shieldDefRate;
	}
	
	/**
	 * Returns the Id in wich weapon this weapon can be changed
	 * 
	 * @return
	 */
	public int getChangeWeaponId()
	{
		return _changeWeaponId;
	}
	
	/**
	 * Returns effects of skills associated with the item to be triggered onHit.
	 * 
	 * @param caster : L2Creature pointing out the caster
	 * @param target : L2Creature pointing out the target
	 */
	public void getSkillEffectsByCrit(L2Creature caster, L2Creature target)
	{
		if (_onCritSkills == null)
			return;
		
		if (target.isDead())
			return;
		
		for (int i = 0; i < _onCritSkills.length; i++)
		{
			final L2Skill skill = _onCritSkills[i];
			
			// Actually onCrit skills triggered every damn time a crit occurs, and the value we are using for chance,
			// is the power of the skill so this should be reworked - requires big dp changes
			if (!(Rnd.get(100) < _onCritChances[i]))
				continue;
			
			// Launch the magic skill and calculate its effects
			SkillHandler.getInstance().useSkill(caster, skill, target);
			
			// notify quests of a skill use
			caster.notifyMobsAboutSkillCast(skill, target);
			
			if (caster instanceof L2Player)
				((L2Player)caster).sendMessage("Your weapon used a skill caused by a critical hit!");
		}
	}
	
	/**
	 * Returns effects of skills associated with the item to be triggered onCast.
	 * 
	 * @param caster : L2Creature pointing out the caster
	 * @param target : L2Creature pointing out the target
	 * @param trigger : L2Skill pointing out the skill triggering this action
	 */
	public void getSkillEffectsByCast(L2Creature caster, L2Creature target, L2Skill trigger)
	{
		if (_onCastSkills == null)
			return;
		
		if (target.isDead())
			return;
		
		for (int i = 0; i < _onCastSkills.length; i++)
		{
			final L2Skill skill = _onCastSkills[i];
			
			if (trigger.isOffensive() != skill.isOffensive())
				continue; // Trigger only same type of skill
				
			if (!(Rnd.get(100) < _onCastChances[i]))
				continue;
			
			// Launch the magic skill and calculate its effects
			SkillHandler.getInstance().useSkill(caster, skill, target);
			
			// notify quests of a skill use
			caster.notifyMobsAboutSkillCast(skill, target);
			
			if (caster instanceof L2Player)
				((L2Player)caster).sendMessage("Your weapon used a skill caused by a skill casting!");
		}
	}
}
