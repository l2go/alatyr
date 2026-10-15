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
package com.l2jfree.gameserver.gameobjects.stat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.skills.Calculator;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;

public class CreatureStat
{
	protected final static Logger _log = LoggerFactory.getLogger(CreatureStat.class);
	
	// =========================================================
	// Data Field
	protected final L2Creature _activeChar;
	private long _exp = 0;
	private int _sp = 0;
	private byte _level = 1;
	
	// =========================================================
	// Constructor
	public CreatureStat(L2Creature activeChar)
	{
		_activeChar = activeChar;
	}
	
	// =========================================================
	// Method - Public
	/**
	 * Calculate the new value of the state with modifiers that will be applied
	 * on the targeted L2Creature.<BR>
	 * <BR>
	 * 
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * A L2Creature owns a table of Calculators called <B>_calculators</B>.
	 * Each Calculator (a calculator per state) own a table of Func object. A
	 * Func object is a mathematic function that permit to calculate the
	 * modifier of a state (ex : REGENERATE_HP_RATE...) : <BR>
	 * <BR>
	 * 
	 * FuncAtkAccuracy -> Math.sqrt(_player.getDEX())*6+_player.getLevel()<BR>
	 * <BR>
	 * 
	 * When the calc method of a calculator is launched, each mathematic
	 * function is called according to its priority <B>_order</B>. Indeed, Func
	 * with lowest priority order is executed firsta and Funcs with the same
	 * order are executed in unspecified order. The result of the calculation is
	 * stored in the value property of an Env class instance.<BR>
	 * <BR>
	 * 
	 * @param stat The stat to calculate the new value with modifiers
	 * @param init The initial value of the stat before applying modifiers
	 * @param target The L2Charcater whose properties will be used in the
	 *            calculation (ex : CON, INT...)
	 * @param skill The L2Skill whose properties will be used in the calculation
	 *            (ex : Level...)
	 * 
	 */
	public final double calcStat(Stats stat, double init, L2Creature target, L2Skill skill)
	{
		int id = stat.ordinal();
		
		Calculator c = _activeChar.getCalculators()[id];
		
		// If no Func object found, no modifier is applied
		if (c == null || c.size() == 0)
			return init;
		
		// Create and init an Env object to pass parameters to the Calculator
		Env env = new Env();
		env.player = _activeChar;
		env.target = target;
		env.skill = skill;
		env.value = init;
		
		// Launch the calculation
		c.calc(env);
		// avoid some troubles with negative stats (some stats should never be negative)
		if (env.value <= 0)
		{
			switch (stat)
			{
				case MAX_HP:
				case MAX_MP:
				case MAX_CP:
				case MAGIC_DEFENCE:
				case POWER_DEFENCE:
				case POWER_ATTACK:
				case MAGIC_ATTACK:
				case POWER_ATTACK_SPEED:
				case MAGIC_ATTACK_SPEED:
				case SHIELD_DEFENCE:
				case STAT_CON:
				case STAT_DEX:
				case STAT_INT:
				case STAT_MEN:
				case STAT_STR:
				case STAT_WIT:
					env.value = 1;
			}
		}
		
		return env.value;
	}
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	/**
	 * Return the Accuracy (base+modifier) of the L2Creature in function of the
	 * Weapon Expertise Penalty.
	 */
	public int getAccuracy()
	{
		return (int)(calcStat(Stats.ACCURACY_COMBAT, 0, null, null)/* / _activeChar.getWeaponExpertisePenalty()*/);
	}
	
	public L2Creature getActiveChar()
	{
		return _activeChar;
	}
	
	/**
	 * Return the Attack Speed multiplier (base+modifier) of the L2Creature to
	 * get proper animations.
	 */
	public final float getAttackSpeedMultiplier()
	{
		return (float)((1.1) * getPAtkSpd() / _activeChar.getTemplate().getBasePAtkSpd());
	}
	
	/** Return the CON of the L2Creature (base+modifier). */
	public final int getCON()
	{
		return (int)calcStat(Stats.STAT_CON, _activeChar.getTemplate().getBaseCON(), null, null);
	}
	
	/** Return the Critical Hit rate (base+modifier) of the L2Creature. */
	public int getCriticalHit(L2Creature target)
	{
		int criticalHit = (int)calcStat(Stats.CRITICAL_RATE, _activeChar.getTemplate().getBaseCritRate(), target, null);
		
		// Set a cap of Critical Hit at ALT_PCRITICAL_CAP
		if (criticalHit > Config.ALT_PCRITICAL_CAP)
			criticalHit = Config.ALT_PCRITICAL_CAP;
		
		return criticalHit;
	}
	
	/** Return the DEX of the L2Creature (base+modifier). */
	public final int getDEX()
	{
		return (int)calcStat(Stats.STAT_DEX, _activeChar.getTemplate().getBaseDEX(), null, null);
	}
	
	/** Return the Attack Evasion rate (base+modifier) of the L2Creature. */
	public int getEvasionRate(L2Creature target)
	{
		int val = (int)(calcStat(Stats.EVASION_RATE, 0, target, null)/* / _activeChar.getArmourExpertisePenalty()*/);
		
		return val;
	}
	
	public long getExp()
	{
		return _exp;
	}
	
	public void setExp(long value)
	{
		_exp = value;
	}
	
	/** Return the INT of the L2Creature (base+modifier). */
	public int getINT()
	{
		return (int)calcStat(Stats.STAT_INT, _activeChar.getTemplate().getBaseINT(), null, null);
	}
	
	public byte getLevel()
	{
		return _level;
	}
	
	public void setLevel(byte value)
	{
		_level = value;
	}
	
	/** Return the Magical Attack range (base+modifier) of the L2Creature. */
	public final int getMagicalAttackRange(L2Skill skill)
	{
		if (skill != null)
			return (int)calcStat(Stats.MAGIC_ATTACK_RANGE, skill.getCastRange(), null, skill);
		
		return _activeChar.getTemplate().getBaseAtkRange();
	}
	
	public int getMaxCp()
	{
		return (int)calcStat(Stats.MAX_CP, _activeChar.getTemplate().getBaseCpMax(), null, null);
	}
	
	public int getMaxHp()
	{
		return (int)calcStat(Stats.MAX_HP, _activeChar.getTemplate().getBaseHpMax(), null, null);
	}
	
	public int getMaxMp()
	{
		return (int)calcStat(Stats.MAX_MP, _activeChar.getTemplate().getBaseMpMax(), null, null);
	}
	
	/**
	 * Return the MAtk (base+modifier) of the L2Creature for a skill used in
	 * function of abnormal effects in progress.<BR>
	 * <BR>
	 * 
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li>Calculate Magic damage</li> <BR>
	 * <BR>
	 * 
	 * @param target The L2Creature targeted by the skill
	 * @param skill The L2Skill used against the target
	 */
	public int getMAtk(L2Creature target, L2Skill skill)
	{
		float bonusAtk = 1;
		if (_activeChar.isChampion())
			bonusAtk = Config.CHAMPION_ATK;
		
		// Get the base MAtk of the L2Creature
		double attack = _activeChar.getTemplate().getBaseMAtk() * bonusAtk;
		
		// Get the skill type to calculate its effect in function of base stats
		// of the L2Creature target
		/*
		 * Stats stat = skill == null ? null : skill.getStat();
		 * 
		 * if (stat != null) { switch (stat) { case AGGRESSION: attack +=
		 * _activeChar.getTemplate().getBaseAggression(); break; case BLEED:
		 * attack += _activeChar.getTemplate().getBaseBleed(); break; case
		 * POISON: attack += _activeChar.getTemplate().getBasePoison(); break;
		 * case STUN: attack += _activeChar.getTemplate().getBaseStun(); break;
		 * case ROOT: attack += _activeChar.getTemplate().getBaseRoot(); break;
		 * case MOVEMENT: attack += _activeChar.getTemplate().getBaseMovement();
		 * break; case CONFUSION: attack +=
		 * _activeChar.getTemplate().getBaseConfusion(); break; case SLEEP:
		 * attack += _activeChar.getTemplate().getBaseSleep(); break; case FIRE:
		 * attack += _activeChar.getTemplate().getBaseFire(); break; case WIND:
		 * attack += _activeChar.getTemplate().getBaseWind(); break; case WATER:
		 * attack += _activeChar.getTemplate().getBaseWater(); break; case
		 * EARTH: attack += _activeChar.getTemplate().getBaseEarth(); break;
		 * case HOLY: attack += _activeChar.getTemplate().getBaseHoly(); break;
		 * case DARK: attack += _activeChar.getTemplate().getBaseDark(); break;
		 * } }
		 */
		
		// Add the power of the skill to the attack effect
		if (skill != null)
			attack += skill.getPower();
		
		// Calculate modifiers Magic Attack
		return (int)calcStat(Stats.MAGIC_ATTACK, attack, target, skill);
	}
	
	/**
	 * Return the MAtk Speed (base+modifier) of the L2Creature in function of
	 * the Armour Expertise Penalty.
	 */
	public int getMAtkSpd()
	{
		float bonusSpdAtk = 1;
		if (_activeChar.isChampion())
			bonusSpdAtk = Config.CHAMPION_SPD_ATK;
		
		int val =
				(int)calcStat(Stats.MAGIC_ATTACK_SPEED, _activeChar.getTemplate().getBaseMAtkSpd() * bonusSpdAtk, null,
						null);
		
		return val;
	}
	
	/** Return the Magic Critical Hit rate (base+modifier) of the L2Creature. */
	public final int getMCriticalHit(L2Creature target, L2Skill skill)
	{
		double mrate = calcStat(Stats.MCRITICAL_RATE, _activeChar.getTemplate().getBaseMCritRate(), target, skill);
		
		// Set a cap of Critical Hit at ALT_MCRITICAL_CAP
		if (mrate > Config.ALT_MCRITICAL_CAP)
			mrate = Config.ALT_MCRITICAL_CAP;
		
		return (int)mrate;
	}
	
	/**
	 * Return the MDef (base+modifier) of the L2Creature against a skill in
	 * function of abnormal effects in progress.<BR>
	 * <BR>
	 * 
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li>Calculate Magic damage</li> <BR>
	 * <BR>
	 * 
	 * @param target The L2Creature targeted by the skill
	 * @param skill The L2Skill used against the target
	 */
	public int getMDef(L2Creature target, L2Skill skill)
	{
		// Get the base MAtk of the L2Creature
		double defence = _activeChar.getTemplate().getBaseMDef();
		
		// Calculate modifier for Raid Bosses
		if (_activeChar.isRaid())
			defence *= Config.RAID_MDEFENCE_MULTIPLIER;
		
		// Calculate modifiers Magic Attack
		return (int)calcStat(Stats.MAGIC_DEFENCE, defence, target, skill);
	}
	
	/** Return the MEN of the L2Creature (base+modifier). */
	public final int getMEN()
	{
		return (int)calcStat(Stats.STAT_MEN, _activeChar.getTemplate().getBaseMEN(), null, null);
	}
	
	public float getMovementSpeedMultiplier()
	{
		int base = getBaseRunSpd();
		
		if (base == 0)
			return 1;
		
		return getRunSpeed() * 1f / base;
	}
	
	/**
	 * Return the RunSpeed (base+modifier) or WalkSpeed (base+modifier) of the
	 * L2Creature in function of the movement type.
	 */
	public final float getMoveSpeed()
	{
		// TODO: Merge this
		if (_activeChar instanceof L2BoatInstance)
			return ((L2BoatInstance)_activeChar).boatSpeed;
		if (_activeChar instanceof L2AirShipInstance)
			return ((L2AirShipInstance)_activeChar).boatSpeed;
		
		if (_activeChar.isRunning())
			return getRunSpeed();
		return getWalkSpeed();
	}
	
	/** Return the MReuse rate (base+modifier) of the L2Creature. */
	public final double getMReuseRate(L2Skill skill)
	{
		return calcStat(Stats.MAGIC_REUSE_RATE, _activeChar.getTemplate().getBaseMReuseRate(), null, skill);
	}
	
	/** Return the PReuse rate (base+modifier) of the L2Creature. */
	public final double getPReuseRate(L2Skill skill)
	{
		return calcStat(Stats.PHYS_REUSE_RATE, _activeChar.getTemplate().getBaseMReuseRate(), null, skill);
	}
	
	/** Return the PAtk (base+modifier) of the L2Creature. */
	public int getPAtk(L2Creature target)
	{
		float bonusAtk = 1;
		if (_activeChar.isChampion())
			bonusAtk = Config.CHAMPION_ATK;
		
		return (int)calcStat(Stats.POWER_ATTACK, _activeChar.getTemplate().getBasePAtk() * bonusAtk, target, null);
	}
	
	public final double getMul(Stats stat, L2Creature target)
	{
		return calcStat(stat, 1, target, null);
	}
	
	/**
	 * Return the PAtk Speed (base+modifier) of the L2Creature in function of
	 * the Armour Expertise Penalty.
	 */
	public int getPAtkSpd()
	{
		float bonusSpdAtk = 1;
		if (_activeChar.isChampion())
			bonusSpdAtk = Config.CHAMPION_SPD_ATK;
		
		int val =
				(int)(calcStat(Stats.POWER_ATTACK_SPEED, _activeChar.getTemplate().getBasePAtkSpd() * bonusSpdAtk,
						null, null));
		
		return val;
	}
	
	/** Return the PDef (base+modifier) of the L2Creature. */
	public int getPDef(L2Creature target)
	{
		return (int)calcStat(Stats.POWER_DEFENCE, (_activeChar.isRaid()) ? _activeChar.getTemplate().getBasePDef()
				* Config.RAID_PDEFENCE_MULTIPLIER : _activeChar.getTemplate().getBasePDef(), target, null);
	}
	
	/** Return the Physical Attack range (base+modifier) of the L2Creature. */
	public final int getPhysicalAttackRange()
	{
		// Polearm handled here for now. Basically L2Player could have a function
		// similar to FuncBowAtkRange and NPC are defined in DP.
		L2Weapon weaponItem = _activeChar.getActiveWeaponItem();
		if (weaponItem != null && weaponItem.getItemType() == L2WeaponType.POLE)
			return (int)calcStat(Stats.POWER_ATTACK_RANGE, 66, null, null);
		return (int)calcStat(Stats.POWER_ATTACK_RANGE, _activeChar.getTemplate().getBaseAtkRange(), null, null);
	}
	
	/**
	 * Return the RunSpeed (base+modifier) of the L2Creature in function of the
	 * Armour Expertise Penalty.
	 */
	public int getRunSpeed()
	{
		// err we should be adding TO the persons run speed
		// not making it a constant
		double baseRunSpd = getBaseRunSpd();
		
		if (baseRunSpd == 0)
			return 0;
		
		return (int)(calcStat(Stats.RUN_SPEED, baseRunSpd, null, null) * Config.RATE_RUN_SPEED);
	}
	
	protected int getBaseRunSpd()
	{
		return _activeChar.getTemplate().getBaseRunSpd();
	}
	
	/** Return the ShieldDef rate (base+modifier) of the L2Creature. */
	public final int getShldDef()
	{
		return (int)calcStat(Stats.SHIELD_DEFENCE, 0, null, null);
	}
	
	public int getSp()
	{
		return _sp;
	}
	
	public void setSp(int value)
	{
		_sp = value;
	}
	
	/** Return the STR of the L2Creature (base+modifier). */
	public final int getSTR()
	{
		return (int)calcStat(Stats.STAT_STR, _activeChar.getTemplate().getBaseSTR(), null, null);
	}
	
	/** Return the WalkSpeed (base+modifier) of the L2Creature. */
	public int getWalkSpeed()
	{
		double baseWalkSpd = _activeChar.getTemplate().getBaseWalkSpd();
		
		if (baseWalkSpd == 0)
			return 0;
		
		return (int)calcStat(Stats.WALK_SPEED, baseWalkSpd, null, null);
	}
	
	/** Return the WIT of the L2Creature (base+modifier). */
	public final int getWIT()
	{
		return (int)calcStat(Stats.STAT_WIT, _activeChar.getTemplate().getBaseWIT(), null, null);
	}
	
	/** Return the mpConsume. */
	public final int getMpConsume(L2Skill skill)
	{
		if (skill == null)
			return 1;
		
		int mpconsume = skill.getMpConsume();
		
		if (!Config.ALT_DANCE_MP_CONSUME && skill.isDanceOrSong())
		{
			int count = _activeChar.getDanceCount(skill.isDance(), skill.isSong());
			if (count > 0)
				mpconsume += (count * skill.getNextDanceMpCost());
		}
		
		return calculateMpConsumption(mpconsume, skill);
	}
	
	/** Return the mpInitialConsume. */
	public final int getMpInitialConsume(L2Skill skill)
	{
		if (skill == null)
			return 1;
		
		return calculateMpConsumption(skill.getMpInitialConsume(), skill);
	}
	
	private int calculateMpConsumption(int init, L2Skill skill)
	{
		final double value = calcStat(Stats.MP_CONSUME, init, null, skill);
		
		if (skill.isDanceOrSong())
		{
			return (int)calcStat(Stats.DANCE_CONSUME_RATE, value, null, skill);
		}
		else if (skill.isMagic())
		{
			return (int)calcStat(Stats.MAGIC_CONSUME_RATE, value, null, skill);
		}
		else
		{
			return (int)calcStat(Stats.PHYSICAL_CONSUME_RATE, value, null, skill);
		}
	}
	
	public byte getAttackElement()
	{
		L2ItemInstance weaponInstance = _activeChar.getActiveWeaponInstance();
		// 1st order - weapon element
		if (weaponInstance != null && weaponInstance.getAttackElementType() >= 0)
			return weaponInstance.getAttackElementType();
		
		// temp fix starts
		int maxValue = 0;
		byte returnAttribute = -2;
		
		for (byte attribute = 0; attribute < 6; attribute++)
		{
			final int attackElementValue = getAttackElementValue(attribute);
			
			if (attackElementValue > maxValue)
			{
				maxValue = attackElementValue;
				returnAttribute = attribute;
			}
		}
		
		return returnAttribute;
		// temp fix ends
		
		// uncomment me once deadlocks in getAllEffects() fixed
		// return _activeChar.getElementIdFromEffects();
	}
	
	public int getAttackElementValue(byte attackAttribute)
	{
		switch (attackAttribute)
		{
			case Elementals.FIRE:
				return (int)calcStat(Stats.FIRE_POWER, _activeChar.getTemplate().getBaseFire(), null, null);
			case Elementals.WATER:
				return (int)calcStat(Stats.WATER_POWER, _activeChar.getTemplate().getBaseWater(), null, null);
			case Elementals.WIND:
				return (int)calcStat(Stats.WIND_POWER, _activeChar.getTemplate().getBaseWind(), null, null);
			case Elementals.EARTH:
				return (int)calcStat(Stats.EARTH_POWER, _activeChar.getTemplate().getBaseEarth(), null, null);
			case Elementals.HOLY:
				return (int)calcStat(Stats.HOLY_POWER, _activeChar.getTemplate().getBaseHoly(), null, null);
			case Elementals.DARK:
				return (int)calcStat(Stats.DARK_POWER, _activeChar.getTemplate().getBaseDark(), null, null);
			default:
				return 0;
		}
	}
	
	public int getDefenseElementValue(byte defenseAttribute)
	{
		switch (defenseAttribute)
		{
			case Elementals.FIRE:
				return (int)calcStat(Stats.FIRE_RES, _activeChar.getTemplate().getBaseFireRes(), null, null);
			case Elementals.WATER:
				return (int)calcStat(Stats.WATER_RES, _activeChar.getTemplate().getBaseWaterRes(), null, null);
			case Elementals.WIND:
				return (int)calcStat(Stats.WIND_RES, _activeChar.getTemplate().getBaseWindRes(), null, null);
			case Elementals.EARTH:
				return (int)calcStat(Stats.EARTH_RES, _activeChar.getTemplate().getBaseEarthRes(), null, null);
			case Elementals.HOLY:
				return (int)calcStat(Stats.HOLY_RES, _activeChar.getTemplate().getBaseHolyRes(), null, null);
			case Elementals.DARK:
				return (int)calcStat(Stats.DARK_RES, _activeChar.getTemplate().getBaseDarkRes(), null, null);
			default:
				return 0;
		}
	}
}
