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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
 * This class represents a packet that is sent when a player is dropping an item
 *
 * @version $Revision: 1.11.2.1.2.7 $ $Date: 2005/04/02 21:25:21 $
 */
public class RequestDropItem extends L2ClientPacket
{
	private static final String _C__REQUESTDROPITEM = "[C] 17 RequestDropItem c[dqddd]";
	
	private int _objectId;
	private long _count;
	private int _x;
	private int _y;
	private int _z;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_count = readCompQ();
		_x = readD();
		_y = readD();
		_z = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null || activeChar.isDead())
			return;
		
		// Flood protect drop to avoid packet lag, do not add any messages here
		if (!FloodProtector.tryPerformAction(activeChar, Protected.DROPITEM))
			return;
		else if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		else if (Config.GM_DISABLE_TRANSACTION && activeChar.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& activeChar.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_DROP_ITEMS);
			return;
		}
		
		L2ItemInstance item = activeChar.checkItemManipulation(_objectId, _count, "Drop");
		if (failIfItemMissing(item))
			return;

		if (_count > item.getCount() || _count < 1)
		{
			sendAF();
			return;
		}
		else if (!item.isStackable() && _count > 1)
		{
			sendAF();
			Util.handleIllegalPlayerAction(activeChar,
					"[RequestDropItem] count > 1 but item is not stackable! ban! oid: " + _objectId + " owner: "
							+ activeChar.getName(), IllegalPlayerAction.PUNISH_KICK);
			return;
		}
		else if (!canDrop(item)) // TODO: merge to restriction
			return;
		else if (!GlobalRestrictions.canDestroyItem(activeChar, item.getItemId(), item))
		{
			sendAF();
			return;
		}
		
		if (_log.isDebugEnabled())
			_log.debug("requested drop item " + _objectId + "(" + item.getCount() + ") at " + _x + "/" + _y + "/" + _z);
		
		if (item.isEquipped())
		{
			L2ItemInstance[] unequiped =
					activeChar.getInventory().unEquipItemInBodySlotAndRecord(item.getItem().getBodyPart());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequiped)
				iu.addModifiedItem(element);
			sendPacket(iu);
			
			// must be sent explicitly after IU
			sendPacket(new UserInfo(activeChar));
			
			activeChar.broadcastUserInfo();
		}
		
		if (MercTicketManager.getInstance().isTicket(item.getItemId()))
		{
			MercTicketManager.getInstance().reqPosition(activeChar, item);
			sendAF();
			return;
		}
		
		L2ItemInstance dropedItem = activeChar.dropItem("Drop", _objectId, _count, _x, _y, _z, activeChar, false);
		
		sendAF();
		
		if (_log.isDebugEnabled())
			_log.debug("dropping " + _objectId + " item(" + _count + ") at: " + _x + " " + _y + " " + _z);
		if (dropedItem != null && dropedItem.getItemId() == PlayerInventory.ADENA_ID && dropedItem.getCount() >= 1000000)
		{
			String msg =
					"Character (" + activeChar.getName() + ") has dropped (" + dropedItem.getCount() + ")adena at ("
							+ _x + "," + _y + "," + _z + ")";
			_log.warn(msg);
			GmListTable.broadcastMessageToGMs(msg);
		}
	}
	
	boolean failIfItemMissing(final L2ItemInstance item)
	{
		if (item != null)
			return false;

		sendAF();
		return true;
	}

	private final boolean canDrop(L2ItemInstance item)
	{
		L2Player activeChar = getActiveChar();
		
		if (activeChar.isProcessingTransaction() || activeChar.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			return false;
		}
		else if (activeChar.isFishing())
		{
			requestFailed(SystemMessageId.CANNOT_DO_WHILE_FISHING_2);
			return false;
		}
		// Cannot discard item that the skill is consuming
		else if (activeChar.isCastingNow() && activeChar.getCurrentSkill() != null
				&& activeChar.getCurrentSkill().getSkill().getItemConsumeId() == item.getItemId())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (activeChar.isFlying())
		{
			sendAF();
			return false;
		}
		else if (activeChar.isCastingSimultaneouslyNow() && activeChar.getLastSimultaneousSkillCast() != null
				&& activeChar.getLastSimultaneousSkillCast().getItemConsumeId() == item.getItemId())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (!activeChar.isInsideRadius(_x, _y, 150, false) || Math.abs(_z - activeChar.getZ()) > 50)
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_DISTANCE_TOO_FAR);
			return false;
		}
		
		else if (item == null)
		{
			_log.warn("Error while droping item for char " + activeChar.getName() + " (validity check).");
			sendAF();
			return false;
		}
		else if (!activeChar.isGM() && !Config.ALLOW_DISCARDITEM)
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (!(activeChar.isGM() && Config.GM_TRADE_RESTRICTED_ITEMS) && !item.isDropable())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (!(activeChar.isGM() && Config.GM_TRADE_RESTRICTED_ITEMS) && item.getItemType() == L2EtcItemType.QUEST)
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (!activeChar.isGM() && activeChar.isInvul() && Config.PLAYER_SPAWN_PROTECTION > 0)
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (Config.ALT_STRICT_HERO_SYSTEM && item.isHeroItem())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return false;
		}
		else if (L2Item.TYPE2_QUEST == item.getItem().getType2())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_EXCHANGE_ITEM);
			return false;
		}
		
		return true;
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTDROPITEM;
	}
}
