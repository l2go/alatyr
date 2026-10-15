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

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SortedWareHouseWithdrawalList;
import com.l2jfree.gameserver.network.packets.server.SortedWareHouseWithdrawalList.WarehouseListType;
import com.l2jfree.gameserver.network.packets.server.WareHouseDepositList;
import com.l2jfree.gameserver.network.packets.server.WareHouseWithdrawalList;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
 * @author l3x
 */
public class L2CastleWarehouseInstance extends L2NpcInstance
{
	protected static final int COND_ALL_FALSE = 0;
	protected static final int COND_BUSY_BECAUSE_OF_SIEGE = 1;
	protected static final int COND_OWNER = 2;
	
	/**
	 * @param template
	 */
	public L2CastleWarehouseInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isWarehouse()
	{
		return true;
	}
	
	private void showRetrieveWindow(L2Player player, WarehouseListType itemtype, byte sortorder)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		player.setActiveWarehouse(player.getWarehouse());
		
		if (player.getActiveWarehouse().getSize() == 0)
		{
			player.sendPacket(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
			return;
		}
		
		if (itemtype != null)
			player.sendPacket(new SortedWareHouseWithdrawalList(player, WareHouseWithdrawalList.PRIVATE, itemtype,
					sortorder));
		else
			player.sendPacket(new WareHouseWithdrawalList(player, WareHouseWithdrawalList.PRIVATE));
	}
	
	private void showDepositWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		player.setActiveWarehouse(player.getWarehouse());
		player.tempInventoryDisable();
		
		player.sendPacket(new WareHouseDepositList(player, WareHouseDepositList.PRIVATE));
	}
	
	private void showDepositWindowClan(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if (player.getClan() != null)
		{
			if (player.getClan().getLevel() == 0)
			{
				player.sendPacket(SystemMessageId.ONLY_LEVEL_1_CLAN_OR_HIGHER_CAN_USE_WAREHOUSE);
			}
			else
			{
				if (!L2Clan.checkPrivileges(player, L2Clan.CP_CL_VIEW_WAREHOUSE))
					player.sendPacket(SystemMessageId.ONLY_CLAN_LEADER_CAN_RETRIEVE_ITEMS_FROM_CLAN_WAREHOUSE);
				player.setActiveWarehouse(player.getClan().getWarehouse());
				player.tempInventoryDisable();
				player.sendPacket(new WareHouseDepositList(player, WareHouseDepositList.CLAN));
			}
		}
	}
	
	private void showWithdrawWindowClan(L2Player player, WarehouseListType itemtype, byte sortorder)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if (!L2Clan.checkPrivileges(player, L2Clan.CP_CL_VIEW_WAREHOUSE))
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_THE_RIGHT_TO_USE_CLAN_WAREHOUSE);
			return;
		}
		
		if (player.getClan().getLevel() == 0)
		{
			player.sendPacket(SystemMessageId.ONLY_LEVEL_1_CLAN_OR_HIGHER_CAN_USE_WAREHOUSE);
		}
		else
		{
			player.setActiveWarehouse(player.getClan().getWarehouse());
			if (itemtype != null)
				player.sendPacket(new SortedWareHouseWithdrawalList(player, WareHouseWithdrawalList.CLAN, itemtype,
						sortorder));
			else
				player.sendPacket(new WareHouseWithdrawalList(player, WareHouseWithdrawalList.CLAN));
		}
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (player.getActiveEnchantItem() != null)
		{
			Util.handleIllegalPlayerAction(player, "Player " + player.getName()
					+ " trying to use enchant exploit, ban this player!", IllegalPlayerAction.PUNISH_KICK);
			return;
		}
		
		String param[] = command.split("_");
		
		if (command.startsWith("WithdrawP"))
		{
			if (Config.ENABLE_WAREHOUSESORTING_PRIVATE)
			{
				String htmFile = "data/html/custom/WhSortedP.htm";
				String htmContent = HtmCache.getInstance().getHtm(htmFile);
				if (htmContent != null)
				{
					NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(getObjectId());
					npcHtmlMessage.setHtml(htmContent);
					npcHtmlMessage.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(npcHtmlMessage);
				}
				else
				{
					_log.warn("Missing htm: " + htmFile + " !");
				}
			}
			else
				showRetrieveWindow(player, null, (byte)0);
		}
		else if (command.startsWith("WithdrawSortedP") && Config.ENABLE_WAREHOUSESORTING_PRIVATE)
		{
			if (param.length > 2)
				showRetrieveWindow(player, WarehouseListType.valueOf(param[1]),
						SortedWareHouseWithdrawalList.getOrder(param[2]));
			else if (param.length > 1)
				showRetrieveWindow(player, WarehouseListType.valueOf(param[1]), SortedWareHouseWithdrawalList.A2Z);
			else
				showRetrieveWindow(player, WarehouseListType.ALL, SortedWareHouseWithdrawalList.A2Z);
		}
		else if (command.equals("DepositP"))
		{
			showDepositWindow(player);
		}
		else if (command.equals("WithdrawC"))
		{
			if (Config.ENABLE_WAREHOUSESORTING_CLAN)
			{
				String htmFile = "data/html/custom/WhSortedC.htm";
				String htmContent = HtmCache.getInstance().getHtm(htmFile);
				if (htmContent != null)
				{
					NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(getObjectId());
					npcHtmlMessage.setHtml(htmContent);
					npcHtmlMessage.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(npcHtmlMessage);
				}
				else
				{
					_log.warn("Missing htm: " + htmFile + "!");
				}
			}
			else
				showWithdrawWindowClan(player, null, (byte)0);
		}
		else if (command.startsWith("WithdrawSortedC") && Config.ENABLE_WAREHOUSESORTING_CLAN)
		{
			if (param.length > 2)
				showWithdrawWindowClan(player, WarehouseListType.valueOf(param[1]),
						SortedWareHouseWithdrawalList.getOrder(param[2]));
			else if (param.length > 1)
				showWithdrawWindowClan(player, WarehouseListType.valueOf(param[1]), SortedWareHouseWithdrawalList.A2Z);
			else
				showWithdrawWindowClan(player, WarehouseListType.ALL, SortedWareHouseWithdrawalList.A2Z);
		}
		else if (command.equals("DepositC"))
		{
			showDepositWindowClan(player);
		}
		else if (command.startsWith("Chat"))
		{
			int val = 0;
			try
			{
				val = Integer.parseInt(command.substring(5));
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			catch (NumberFormatException nfe)
			{
			}
			showChatWindow(player, val);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	@Override
	public void showChatWindow(L2Player player, int val)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		String filename = "data/html/castlewarehouse/castlewarehouse-no.htm";
		
		int condition = validateCondition(player);
		if (condition > COND_ALL_FALSE)
		{
			if (condition == COND_BUSY_BECAUSE_OF_SIEGE)
				filename = "data/html/castlewarehouse/castlewarehouse-busy.htm"; // Busy because of siege
			else if (condition == COND_OWNER) // Clan owns castle
			{
				if (val == 0)
					filename = "data/html/castlewarehouse/castlewarehouse.htm";
				else
					filename = "data/html/castlewarehouse/castlewarehouse-" + val + ".htm";
			}
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
	
	protected int validateCondition(L2Player player)
	{
		if (player.isGM())
			return COND_OWNER;
		if (getCastle() != null && getCastle().getCastleId() > 0)
		{
			if (player.getClan() != null)
			{
				if (getCastle().getSiege().getIsInProgress())
					return COND_BUSY_BECAUSE_OF_SIEGE; // Busy because of siege
				else if (getCastle().getOwnerId() == player.getClanId()) // Clan owns castle
					return COND_OWNER;
			}
		}
		return COND_ALL_FALSE;
	}
}
