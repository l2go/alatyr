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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSummon;
import com.l2jfree.gameserver.model.zone.L2Zone;

public class L2SiegeSummonInstance extends L2SummonInstance
{
	public L2SiegeSummonInstance(int objectId, L2NpcTemplate template, L2Player owner, L2SkillSummon skill)
	{
		super(objectId, template, owner, skill);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		Siege siege = SiegeManager.getInstance().getSiege(this);
		if (!getOwner().isGM() && (siege == null || !siege.getIsInProgress()) && !isInsideZone(L2Zone.FLAG_SIEGE))
		{
			unSummon(getOwner());
			getOwner().sendMessage("Summon was unsummoned because it exited siege zone");
		}
	}
}
