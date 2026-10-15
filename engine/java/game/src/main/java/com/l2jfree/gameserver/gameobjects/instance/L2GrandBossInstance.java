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

import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.GrandBossStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.model.skills.L2Skill;

/**
 * This class manages all Bosses.
 * 
 * @version $Revision: 1.0.0.0 $ $Date: 2006/06/16 $
 */
public class L2GrandBossInstance extends L2Boss
{
	protected boolean _isInSocialAction = false;
	
	public boolean isInSocialAction()
	{
		return _isInSocialAction;
	}
	
	public void setIsInSocialAction(boolean value)
	{
		_isInSocialAction = value;
	}
	
	/**
	 * Constructor for L2GrandBossInstance. This represent all grandbosses:
	 * <ul>
	 * <li>29001    Queen Ant</li>
	 * <li>29014    Orfen</li>
	 * <li>29019    Antharas</li>
	 * <li>29067    Antharas</li>
	 * <li>29068    Antharas</li>
	 * <li>29020    Baium</li>
	 * <li>29022    Zaken</li>
	 * <li>29028    Valakas</li>
	 * <li>29006    Core</li>
	 * <li>29045    Frintezza</li>
	 * <li>29046    Scarlet Van Halisha 1st Morph</li>
	 * <li>29047    Scarlet Van Halisha 3rd Morph</li>
	 * </ul>
	 * <br>
	 * <b>For now it's (mostly) nothing more than a L2Monster but there'll be a scripting<br>
	 * engine for AI soon and we could add special behaviour for those boss</b><br>
	 * <br>
	 * @param objectId ID of the instance
	 * @param template L2NpcTemplate of the instance
	 */
	public L2GrandBossInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		GrandBossSpawnManager.getInstance().updateStatus(this, true);
		return true;
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new GrandBossStatus(this);
	}
	
	@Override
	public GrandBossStatus getStatus()
	{
		return (GrandBossStatus)_status;
	}
	
	@Override
	public void doAttack(L2Creature target)
	{
		if (_isInSocialAction)
			return;
		super.doAttack(target);
	}
	
	@Override
	public void doCast(L2Skill skill)
	{
		if (_isInSocialAction)
			return;
		super.doCast(skill);
	}
	
	@Override
	public void onSpawn()
	{
		setIsRaid(true);
		setIsNoRndWalk(true);
		//if (getNpcId() == 29020 || getNpcId() == 29028) // baium and valakas are all the time in passive mode, theirs attack AI handled in AI scripts
		//super.disableCoreAI(true);
		super.onSpawn();
	}
	
	@Override
	public float getVitalityPoints(int damage)
	{
		return -super.getVitalityPoints(damage) / 100;
	}
	
	@Override
	public boolean useVitalityRate()
	{
		return false;
	}
}
