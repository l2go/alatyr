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


import java.util.List;
import com.l2jfree.gameserver.datatables.AugmentationData;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.funcs.FuncAdd;
import com.l2jfree.gameserver.model.skills.funcs.FuncOwner;

/**
 * Used to store an augmentation and its boni
 *
 * @author  durgus
 */
public final class L2Augmentation
{
	private int _effectsId = 0;
	private AugmentationStatBoni _boni = null;
	private L2Skill _skill = null;
	
	public L2Augmentation(int effects, L2Skill skill)
	{
		_effectsId = effects;
		_boni = new AugmentationStatBoni(_effectsId);
		_skill = skill;
	}
	
	public L2Augmentation(int effects, int skill, int skillLevel)
	{
		this(effects, SkillTable.getInstance().getInfo(skill, skillLevel));
	}
	
	// =========================================================
	// Nested Class
	
	public final class AugmentationStatBoni implements FuncOwner
	{
		private final Func[] _statFuncs;
		
		private volatile boolean _active = false;
		
		public AugmentationStatBoni(int augmentationId)
		{
			List<AugmentationData.AugStat> as = AugmentationData.getInstance().getAugStatsById(augmentationId);
			
			_statFuncs = new Func[as.size()];
			
			int i = 0;
			for (AugmentationData.AugStat aStat : as)
			{
				_statFuncs[i] = new FuncAdd(aStat.getStat(), 0x40, this, aStat.getValue(), null);
				i++;
			}
		}
		
		public void applyBonus(L2Player player)
		{
			// make sure the bonuses are not applied twice..
			if (_active)
				return;
			
			player.addStatFuncs(_statFuncs);
			
			_active = true;
		}
		
		public void removeBonus(L2Player player)
		{
			// make sure the bonuses are not removed twice
			if (!_active)
				return;
			
			player.removeStatsOwner(this);
			
			_active = false;
		}
		
		@Override
		public String getFuncOwnerName()
		{
			return null;
		}
		
		@Override
		public L2Skill getFuncOwnerSkill()
		{
			return null;
		}
	}
	
	public int getAttributes()
	{
		return _effectsId;
	}
	
	/**
	 * Get the augmentation "id" used in serverpackets.
	 * @return augmentationId
	 */
	public int getAugmentationId()
	{
		return _effectsId;
	}
	
	public L2Skill getSkill()
	{
		return _skill;
	}
	
	/**
	 * Applies the bonuses to the player.
	 * @param player
	 */
	public void applyBonus(L2Player player)
	{
		_boni.applyBonus(player);
		
		// add the skill if any
		if (_skill != null)
		{
			player.addSkill(_skill);
		}
	}
	
	/**
	 * Removes the augmentation bonuses from the player.
	 * @param player
	 */
	public void removeBonus(L2Player player)
	{
		_boni.removeBonus(player);
		
		// remove the skill if any
		if (_skill != null)
			player.removeSkill(_skill);
	}
}
