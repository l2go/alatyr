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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.entity.AbstractSiege;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * sample
 * 0b
 * 952a1048     objectId
 * 00000000 00000000 00000000 00000000 00000000 00000000

 * format  dddddd   rev 377
 * format  ddddddd   rev 417
 * 
 * @version $Revision: 1.3.2.1.2.5 $ $Date: 2005/03/27 18:46:18 $
 */
public class Die extends L2ServerPacket
{
	private static final String _S__00_DIE = "[S] 00 Die [dddddddd]";
	public static final int FEATHER_OF_BLESSING_1 = 10649;
	public static final int FEATHER_OF_BLESSING_2 = 13300;
	public static final int PHOENIX_FEATHER = 13128;
	
	private final int _charObjId;
	private final boolean _fallDown;
	private final int _sweepable;
	
	private final int _showVillage;
	private final int _showClanhall;
	private final int _showCastle;
	private int _showFlag;
	private final int _showFeather;
	private final int _showFortress;
	
	public Die(L2Creature cha)
	{
		_charObjId = cha.getObjectId();
		_fallDown = cha.mustFallDownOnDeath();
		if (cha instanceof L2Attackable)
			_sweepable = ((L2Attackable)cha).isSweepActive() ? 1 : 0;
		else
			_sweepable = 0;
		if (cha instanceof L2Player)
		{
			L2Player player = cha.getActingPlayer();
			if (!GlobalRestrictions.canRequestRevive(player))
			{
				_showVillage = 0;
				_showClanhall = 0;
				_showCastle = 0;
				_showFlag = 0;
				if (player.getAccessLevel() >= Config.GM_FIXED)
					_showFeather = 1;
				else
					_showFeather = 0;
				_showFortress = 0;
				return;
			}
			
			_showVillage = 1;
			_showFlag = 0;
			L2Clan clan = player.getClan();
			if (clan != null)
			{
				_showClanhall = clan.getHasHideout() > 0 ? 1 : 0;
				_showCastle = clan.getHasCastle() > 0 ? 1 : 0;
				_showFortress = clan.getHasFort() > 0 ? 1 : 0;
				L2SiegeClan sc = null;
				AbstractSiege as = SiegeManager.getInstance().getSiege(player);
				if (as != null && as.getIsInProgress())
				{
					sc = as.getAttackerClan(clan);
					if (sc != null && sc.getNumFlags() > 0)
						_showFlag = 1;
				}
				as = FortSiegeManager.getInstance().getSiege(player);
				if (as != null && as.getIsInProgress())
				{
					sc = as.getAttackerClan(clan);
					if (sc != null && sc.getNumFlags() > 0)
						_showFlag = 1;
				}
				as = CCHManager.getInstance().getSiege(player);
				if (as != null && as.getIsInProgress())
				{
					sc = as.getAttackerClan(clan);
					if (sc != null && sc.getNumFlags() > 0)
						_showFlag = 1;
				}
			}
			else
			{
				_showClanhall = 0;
				_showCastle = 0;
				_showFortress = 0;
			}
			
			if (player.getAccessLevel() >= Config.GM_FIXED
					|| player.getInventory().getItemByItemId(FEATHER_OF_BLESSING_1) != null
					|| player.getInventory().getItemByItemId(FEATHER_OF_BLESSING_2) != null
					|| player.getInventory().getItemByItemId(PHOENIX_FEATHER) != null)
				_showFeather = 1;
			else
				_showFeather = 0;
		}
		else
		{
			_showVillage = 0;
			_showClanhall = 0;
			_showCastle = 0;
			_showFortress = 0;
			_showFlag = 0;
			_showFeather = 0;
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		if (!_fallDown)
			return;
		
		writeC(0x00);
		writeD(_charObjId);
		writeD(_showVillage);
		writeD(_showClanhall);
		writeD(_showCastle);
		writeD(_showFlag);
		writeD(_sweepable);
		writeD(_showFeather);
		writeD(_showFortress);
	}
	
	@Override
	public String getType()
	{
		return _S__00_DIE;
	}
}
