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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.FourSepulchersManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.AntharasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaiumManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaylorManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.FrintezzaManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.SailrenManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.ValakasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.VanHalterManager;
import com.l2jfree.gameserver.instancemanager.lastimperialtomb.LastImperialTombManager;

public class L2BossZone extends L2Zone
{
	@Override
	protected void register()
	{
		switch (getBoss())
		{
			case ANTHARAS:
				AntharasManager.getInstance().registerZone(this);
				break;
			case BAIUM:
				BaiumManager.getInstance().registerZone(this);
				break;
			case BAYLOR:
				BaylorManager.getInstance().registerZone(this);
				break;
			case FRINTEZZA:
				FrintezzaManager.getInstance().registerZone(this);
				break;
			case FOURSEPULCHERS:
				FourSepulchersManager.getInstance().registerZone(this);
				break;
			case LASTIMPERIALTOMB:
				LastImperialTombManager.getInstance().registerZone(this);
				break;
			case SAILREN:
				SailrenManager.getInstance().registerZone(this);
				break;
			case VALAKAS:
				ValakasManager.getInstance().registerZone(this);
				break;
			case VANHALTER:
				VanHalterManager.getInstance().registerZone(this);
				break;
		}
	}
	
	@Override
	protected void onEnter(L2Creature character)
	{
		switch (getBoss())
		{
			case SUNLIGHTROOM:
			{
				character.setInsideZone(FLAG_SUNLIGHTROOM, true);
				break;
			}
			case FRINTEZZA:
			{
				if (character instanceof L2Player)
					FrintezzaManager.getInstance().setScarletSpawnTask();
				break;
			}
		}
		
		character.setInsideZone(FLAG_NOSUMMON, true);
		
		super.onEnter(character);
	}
	
	@Override
	protected void onExit(L2Creature character)
	{
		switch (getBoss())
		{
			case SUNLIGHTROOM:
			{
				character.setInsideZone(FLAG_SUNLIGHTROOM, false);
				break;
			}
		}
		
		character.setInsideZone(FLAG_NOSUMMON, false);
		
		super.onExit(character);
	}
}
