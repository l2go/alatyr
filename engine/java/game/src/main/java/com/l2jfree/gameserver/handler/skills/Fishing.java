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
package com.l2jfree.gameserver.handler.skills;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class Fishing implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.FISHING };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player player = (L2Player)activeChar;
		
		if (!Config.ALLOW_FISHING)
		{
			player.sendMessage("Fishing is disabled.");
			return;
		}
		if (player.isFishing())
		{
			if (player.getFishCombat() != null)
				player.getFishCombat().doDie(false);
			else
				player.endFishing(false);
			// Cancel fishing
			player.sendPacket(SystemMessageId.FISHING_ATTEMPT_CANCELLED);
			return;
		}
		if (player.isInBoat())
		{
			// You can't fish while you are on boat
			player.sendPacket(SystemMessageId.CANNOT_FISH_ON_BOAT);
			return;
		}
		if (!player.isInsideZone(L2Zone.FLAG_FISHING) || player.isInsideZone(L2Zone.FLAG_PEACE))
		{
			// You can't fish here
			player.sendPacket(SystemMessageId.CANNOT_FISH_HERE);
			return;
		}
		if (player.isInsideZone(L2Zone.FLAG_WATER))
		{
			// You can't fish in water
			player.sendPacket(SystemMessageId.CANNOT_FISH_UNDER_WATER);
			return;
		}
		
		// Calculate point of a float
		int d = Rnd.get(50) + 150;
		double angle = Util.convertHeadingToDegree(player.getHeading());
		double radian = Math.toRadians(angle);
		
		int dx = (int)(d * Math.cos(radian));
		int dy = (int)(d * Math.sin(radian));
		
		int x = activeChar.getX() + dx;
		int y = activeChar.getY() + dy;
		
		L2Zone water = ZoneManager.getInstance().isInsideZone(L2Zone.ZoneType.Water, x, y);
		
		// Float must be in water
		if (water == null)
		{
			player.sendPacket(SystemMessageId.CANNOT_FISH_HERE);
			return;
		}
		
		int z = water.getMaxZ(x, y, activeChar.getZ());
		
		if (Config.GEODATA > 0
				&& !GeoData.getInstance().canSeeTarget(activeChar.getX(), activeChar.getY(), activeChar.getZ(), x, y,
						z, activeChar.getInstanceId())
				|| (Config.GEODATA == 0 && (Util.calculateDistance(activeChar.getX(), activeChar.getY(),
						activeChar.getZ(), x, y, z, true) > d * 1.73)))
		{
			player.sendPacket(SystemMessageId.CANNOT_FISH_HERE);
			return;
		}
		if (player.isInCraftMode() || player.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.CANNOT_FISH_WHILE_USING_RECIPE_BOOK);
			return;
		}
		L2Weapon weaponItem = player.getActiveWeaponItem();
		if ((weaponItem == null || weaponItem.getItemType() != L2WeaponType.ROD))
		{
			//Fishing poles are not installed
			player.sendPacket(SystemMessageId.FISHING_POLE_NOT_EQUIPPED);
			return;
		}
		L2ItemInstance lure = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (lure == null)
		{
			//Bait not equiped.
			player.sendPacket(SystemMessageId.BAIT_ON_HOOK_BEFORE_FISHING);
			return;
		}
		player.setLure(lure);
		L2ItemInstance lure2 = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		
		if (lure2 == null || lure2.getCount() < 1) //Not enough bait.
		{
			player.sendPacket(SystemMessageId.NOT_ENOUGH_BAIT);
			player.sendPacket(new ItemList(player, false));
		}
		else
		// Has enough bait, consume 1 and update inventory. Start fishing follows.
		{
			lure2 =
					player.getInventory().destroyItem("Consume",
							player.getInventory().getPaperdollObjectId(Inventory.PAPERDOLL_LHAND), 1, player, null);
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(lure2);
			player.sendPacket(iu);
		}
		
		// Client itself find z coord of a float
		player.startFishing(x, y, z + 10);
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
