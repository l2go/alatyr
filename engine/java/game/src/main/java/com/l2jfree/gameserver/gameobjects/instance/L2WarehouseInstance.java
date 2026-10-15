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

import java.util.Map;

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerFreight;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Town;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.PackageToList;
import com.l2jfree.gameserver.network.packets.server.SortedWareHouseWithdrawalList;
import com.l2jfree.gameserver.network.packets.server.SortedWareHouseWithdrawalList.WarehouseListType;
import com.l2jfree.gameserver.network.packets.server.WareHouseDepositList;
import com.l2jfree.gameserver.network.packets.server.WareHouseWithdrawalList;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.4.10 $ $Date: 2005/04/06 16:13:41 $
 */
public final class L2WarehouseInstance extends L2NpcInstance
{
	private int _closestTownId = -1;
	
	public L2WarehouseInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isWarehouse()
	{
		return true;
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		return "data/html/warehouse/" + pom + ".htm";
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
		
		if (_log.isDebugEnabled())
			_log.debug("Showing stored items");
		
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
		if (_log.isDebugEnabled())
			_log.debug("Showing items to deposit");
		
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
				if (_log.isDebugEnabled())
					_log.debug("Showing items to deposit - clan");
				player.sendPacket(new WareHouseDepositList(player, WareHouseDepositList.CLAN));
			}
		}
	}
	
	private void showWithdrawWindowClan(L2Player player, WarehouseListType itemtype, byte sortorder)
	{
		if (player.getClan() == null || player.getClan().getLevel() == 0)
		{
			player.sendPacket(SystemMessageId.ONLY_LEVEL_1_CLAN_OR_HIGHER_CAN_USE_WAREHOUSE);
		}
		else if (!L2Clan.checkPrivileges(player, L2Clan.CP_CL_VIEW_WAREHOUSE))
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_THE_RIGHT_TO_USE_CLAN_WAREHOUSE);
			return;
		}
		else
		{
			player.setActiveWarehouse(player.getClan().getWarehouse());
			if (_log.isDebugEnabled())
				_log.debug("Showing items to deposit - clan");
			
			if (itemtype != null)
				player.sendPacket(new SortedWareHouseWithdrawalList(player, WareHouseWithdrawalList.CLAN, itemtype,
						sortorder));
			else
				player.sendPacket(new WareHouseWithdrawalList(player, WareHouseWithdrawalList.CLAN));
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showWithdrawWindowFreight(L2Player player, WarehouseListType itemtype, byte sortorder)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if (_log.isDebugEnabled())
			_log.debug("Showing freightened items");
		
		PlayerFreight freight = player.getFreight();
		
		if (freight != null)
		{
			if (freight.getSize() > 0)
			{
				if (Config.ALT_GAME_FREIGHTS)
				{
					freight.setActiveLocation(0);
				}
				else
				{
					freight.setActiveLocation(getClosestTown());
				}
				player.setActiveWarehouse(freight);
				
				if (itemtype != null)
					player.sendPacket(new SortedWareHouseWithdrawalList(player, WareHouseWithdrawalList.FREIGHT,
							itemtype, sortorder));
				else
					player.sendPacket(new WareHouseWithdrawalList(player, WareHouseWithdrawalList.FREIGHT));
			}
			else
			{
				player.sendPacket(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
			}
		}
		else
		{
			if (_log.isDebugEnabled())
				_log.debug("no items freightened");
		}
	}
	
	private void showDepositWindowFreight(L2Player player)
	{
		// No other chars in the account of this player
		if (player.getAccountChars().size() == 0)
		{
			player.sendPacket(SystemMessageId.CHARACTER_DOES_NOT_EXIST);
		}
		// One or more chars other than this player for this account
		else
		{
			
			Map<Integer, String> chars = player.getAccountChars();
			
			if (chars.size() < 1)
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			player.sendPacket(new PackageToList(chars));
			
			if (_log.isDebugEnabled())
				_log.debug("Showing destination chars to freight - char src: " + player.getName());
		}
	}
	
	private void showDepositWindowFreight(L2Player player, int obj_Id)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		PlayerFreight freight = new PlayerFreight(null);
		
		freight.doQuickRestore(obj_Id);
		
		if (Config.ALT_GAME_FREIGHTS)
			freight.setActiveLocation(0);
		else
			freight.setActiveLocation(getClosestTown());
		
		player.setActiveWarehouse(freight);
		player.tempInventoryDisable();
		
		if (_log.isDebugEnabled())
			_log.debug("Showing items to freight");
		player.sendPacket(new WareHouseDepositList(player, WareHouseDepositList.FREIGHT));
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		// Lil check to prevent enchant exploit
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
		else if (command.startsWith("WithdrawSortedP"))
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
		else if (command.startsWith("WithdrawC"))
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
					_log.warn("Missing htm: " + htmFile + " !");
				}
			}
			else
				showWithdrawWindowClan(player, null, (byte)0);
		}
		else if (command.startsWith("WithdrawSortedC"))
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
		else if (command.startsWith("WithdrawF"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				if (Config.ENABLE_WAREHOUSESORTING_FREIGHT)
				{
					String htmFile = "data/html/custom/WhSortedF.htm";
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
					showWithdrawWindowFreight(player, null, (byte)0);
			}
		}
		else if (command.startsWith("WithdrawSortedF"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				if (param.length > 2)
					showWithdrawWindowFreight(player, WarehouseListType.valueOf(param[1]),
							SortedWareHouseWithdrawalList.getOrder(param[2]));
				else if (param.length > 1)
					showWithdrawWindowFreight(player, WarehouseListType.valueOf(param[1]),
							SortedWareHouseWithdrawalList.A2Z);
				else
					showWithdrawWindowFreight(player, WarehouseListType.ALL, SortedWareHouseWithdrawalList.A2Z);
			}
		}
		else if (command.startsWith("DepositF"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				showDepositWindowFreight(player);
			}
		}
		else if (command.startsWith("FreightChar"))
		{
			if (Config.ALLOW_FREIGHT && param.length > 1)
				showDepositWindowFreight(player, Integer.parseInt(param[1]));
		}
		else
		{
			// This class dont know any other commands, let forward
			// the command to the parent class
			super.onBypassFeedback(player, command);
		}
	}
	
	private int getClosestTown()
	{
		if (_closestTownId < 0)
		{
			Town town = TownManager.getInstance().getClosestTown(this);
			if (town != null)
			{
				_closestTownId = town.getTownId();
			}
			else
				_closestTownId = 0;
		}
		
		return _closestTownId;
	}
}
