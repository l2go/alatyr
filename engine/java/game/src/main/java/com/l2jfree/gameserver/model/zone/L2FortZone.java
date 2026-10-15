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
package com.l2jfree.gameserver.model.zone;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.FortSiege;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;

public class L2FortZone extends SiegeableEntityZone
{
	@Override
	protected void register() throws Exception
	{
		_entity = initFort();
		// Forts: One zone for multiple purposes (could expand this later and add defender spawn areas)
		_entity.registerZone(this);
		_entity.registerHeadquartersZone(this);
	}
	
	@Override
	protected void onEnter(L2Creature character)
	{
		super.onEnter(character);
		
		character.setInsideZone(FLAG_FORT, true);
		
		if (character instanceof L2Player)
		{
			L2Player player = (L2Player)character;
			L2Clan clan = player.getClan();
			if (clan != null)
			{
				FortSiege s = getSiege();
				if (s.getIsInProgress() && (s.checkIsAttacker(clan) || s.checkIsDefender(clan)))
				{
					player.startFameTask(Config.FORTRESS_ZONE_FAME_TASK_FREQUENCY * 1000,
							Config.FORTRESS_ZONE_FAME_AQUIRE_POINTS);
				}
			}
		}
	}
	
	@Override
	protected void onExit(L2Creature character)
	{
		super.onExit(character);
		
		character.setInsideZone(FLAG_FORT, false);
		
		if (character instanceof L2Player)
			((L2Player)character).stopFameTask();
	}
	
	@Override
	protected void onDieInside(L2Creature character)
	{
		// debuff participants only if they die inside siege zone
		if (character instanceof L2Player && isSiegeInProgress())
		{
			int lvl;
			L2Effect effect = character.getFirstEffect(5660);
			if (effect != null)
				lvl = Math.min(effect.getLevel() + 1, SkillTable.getInstance().getMaxLevel(5660));
			else
				lvl = 1;
			
			L2Skill skill = SkillTable.getInstance().getInfo(5660, lvl);
			if (skill != null)
				skill.getEffects(character, character);
		}
		super.onDieInside(character);
	}
}
