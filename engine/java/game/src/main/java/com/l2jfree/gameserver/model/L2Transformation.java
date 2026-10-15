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
package com.l2jfree.gameserver.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;

/**
 * @author KenM
 */
public abstract class L2Transformation
{
	protected static final Logger _log = LoggerFactory.getLogger(L2Transformation.class);
	
	private final int _id;
	private final double _collisionRadius;
	private final double _collisionHeight;
	private final boolean _isStance;
	
	public static final int TRANSFORM_ZARICHE = 301;
	public static final int TRANSFORM_AKAMANAH = 302;
	
	/**
	 * @param id Internal id that server will use to associate this transformation
	 * @param collisionRadius Collision Radius of the player while transformed
	 * @param collisionHeight Collision Height of the player while transformed
	 */
	public L2Transformation(int id, double collisionRadius, double collisionHeight)
	{
		_id = id;
		_collisionRadius = collisionRadius;
		_collisionHeight = collisionHeight;
		_isStance = false;
	}
	
	/**
	 * @param id Internal id that server will use to associate this transformation Used for stances
	 */
	public L2Transformation(int id)
	{
		_id = id;
		_collisionRadius = -1; // unused
		_collisionHeight = -1; // unused
		_isStance = true;
	}
	
	/**
	 * @return Returns the id.
	 */
	public final int getId()
	{
		return _id;
	}
	
	/**
	 * @return Returns the graphicalId.
	 */
	public int getGraphicalId()
	{
		return getId();
	}
	
	/**
	 * Return true if this is a stance (vanguard/inquisitor)
	 * 
	 * @return
	 */
	public final boolean isStance()
	{
		return _isStance;
	}
	
	/**
	 * @return Returns the collisionRadius.
	 */
	public final double getCollisionRadius(L2Player player)
	{
		if (isStance())
			return player.getBaseTemplate().getCollisionRadius();
		return _collisionRadius;
	}
	
	/**
	 * @return Returns the collisionHeight.
	 */
	public final double getCollisionHeight(L2Player player)
	{
		if (isStance())
			return player.getBaseTemplate().getCollisionHeight();
		return _collisionHeight;
	}
	
	// Scriptable Events
	public void onTransform(L2Player player)
	{
		if (player.getTransformationId() != getId() || player.isCursedWeaponEquipped())
			return;
		
		if (isStance())
		{
			// Update transformation ID into database and player instance variables.
			player.transformInsertInfo();
		}
		
		// give transformation skills
		transformedSkills(player);
		
		// transformation dispelling skills
		switch (getId())
		{
			case 301: // Zariche
			case 302: // Akamanah
				// can't be dispelled
				break;
			
			case 107: // GatekeeperAlternate
			case 319: // Gatekeeper
				addSkill(player, 8248, 1); // Cancel Gatekeeper Transformation
				break;
			
			case 106: // LightPurpleManedHorse
			case 109: // TawnyManedLion
			case 110: // SteamBeatle
				addSkill(player, 839, 1); // Dismount
				break;
			
			default:
				if (isStance())
					addSkill(player, 838, 1); // Switch Stance
				else
					addSkill(player, 619, 1); // Transform Dispel
				break;
		}
		
		// negative passive skills
		switch (getId())
		{
			case 301: // Zariche
			case 302: // Akamanah
				// doesn't have
				break;
			
			case 8: // AurabirdFalcon
			case 9: // AurabirdOwl
			case 260: // FlyingFinalForm
				// doesn't have
				break;
			
			case 108: // PumpkinGhost
			case 114: // SnowKing
			case 115: // ScareCrow
			case 116: // TinGolem
				addSkill(player, 5437, 2); // Dissonance
				break;
			
			default:
				addSkill(player, 5491, 1); // Decrease Bow/Crossbow Attack Speed
				break;
		}
	}
	
	protected abstract void transformedSkills(L2Player player);
	
	public void onUntransform(L2Player player)
	{
		// remove transformation skills
		removeSkills(player);
		
		// transformation dispelling skills
		switch (getId())
		{
			case TRANSFORM_ZARICHE: // Zariche
			case TRANSFORM_AKAMANAH: // Akamanah
				// can't be dispelled
				break;
			
			case 107: // GatekeeperAlternate
			case 319: // Gatekeeper
				removeSkill(player, 8248); // Cancel Gatekeeper Transformation
				break;
			
			case 106: // LightPurpleManedHorse
			case 109: // TawnyManedLion
			case 110: // SteamBeatle
				removeSkill(player, 839); // Dismount
				break;
			
			default:
				if (isStance())
					removeSkill(player, 838); // Switch Stance
				else
					removeSkill(player, 619); // Transform Dispel
				break;
		}
		
		// negative passive skills
		switch (getId())
		{
			case TRANSFORM_ZARICHE: // Zariche
			case TRANSFORM_AKAMANAH: // Akamanah
				// doesn't have
				break;
			
			case 8: // AurabirdFalcon
			case 9: // AurabirdOwl
			case 260: // FlyingFinalForm
				// doesn't have
				break;
			
			case 108: // PumpkinGhost
			case 114: // SnowKing
			case 115: // ScareCrow
			case 116: // TinGolem
				removeSkill(player, 5437); // Dissonance
				break;
			
			default:
				removeSkill(player, 5491); // Decrease Bow/Crossbow Attack Speed
				break;
		}
	}
	
	protected abstract void removeSkills(L2Player player);
	
	protected final void addSkill(L2Player player, int skillId, int skillLevel)
	{
		if (skillLevel == -1)
			return;
		
		L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
		if (skill == null)
			_log.warn("Transformed skill " + skillId + " " + skillLevel + " not found!");
		else
		{
			player.addSkill(skill, false);
			player.addTransformAllowedSkill(skillId);
		}
	}
	
	protected final void addSkills(L2Player player, int... skillIds)
	{
		for (int skillId : skillIds)
			addSkill(player, skillId, 1);
	}
	
	protected final void removeSkill(L2Player player, int skillId)
	{
		player.removeSkill(skillId);
	}
	
	protected final void removeSkills(L2Player player, int... skillIds)
	{
		for (int skillId : skillIds)
			removeSkill(player, skillId);
	}
	
	// Override if necessary
	public void onLevelUp(L2Player player)
	{
	}
	
	/**
	 * Returns true if transformation can do melee attack
	 */
	public boolean canDoMeleeAttack()
	{
		return true;
	}
	
	/**
	 * Returns true if transformation can start follow target when trying to cast an skill out of range
	 */
	public boolean canStartFollowToCast()
	{
		return true;
	}
	
	/**
	 * Returns true if the standard action buttons must be hidden while transformed. (most except Vanguard, Inquisitor
	 * etc.)
	 */
	public boolean hidesActionButtons()
	{
		return true;
	}
}
