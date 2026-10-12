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

import static com.l2jfree.gameserver.gameobjects.L2Npc.INTERACTION_DISTANCE;
import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.MAX_ADENA;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2CastleChamberlainInstance;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager.SeedProduction;
import com.l2jfree.gameserver.model.L2Manor;
import com.l2jfree.gameserver.model.ManorOrderLimits;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format: (ch) dd [ddd]
 * @author -Wooden-
 *
 * d - manor id
 * d - size
 * [
 * d - seed id
 * d - sales
 * d - price
 * ]
 * @author l3x
 * 
 */
public class RequestSetSeed extends L2ClientPacket
{
	private static final String _C__D0_0A_REQUESTSETSEED = "[C] D0:0A RequestSetSeed";
	
	private static final int BATCH_LENGTH = 12; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 20;
	
	private int _manorId;
	
	private Seed _items[] = null;
	
	@Override
	protected void readImpl()
	{
		_manorId = readD();
		int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		
		_items = new Seed[count];
		for (int i = 0; i < count; i++)
		{
			int itemId = readD();
			long sales = readCompQ();
			long price = readCompQ();
			if (itemId < 1 || sales < 0 || price < 0)
			{
				_items = null;
				return;
			}
			_items[i] = new Seed(itemId, sales, price);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_items == null || player.getClan() == null)
		{
			sendAF();
			return;
		}
		
		// check player privileges
		if (!L2Clan.checkPrivileges(player, L2Clan.CP_CS_MANOR_ADMIN))
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		// check castle owner
		Castle currentCastle = CastleManager.getInstance().getCastleById(_manorId);
		if (currentCastle.getOwnerId() != player.getClanId())
		{
			sendAF();
			return;
		}
		
		L2Object manager = player.getTarget();
		
		if (!(manager instanceof L2CastleChamberlainInstance))
			manager = player.getLastFolkNPC();
		
		if (!(manager instanceof L2CastleChamberlainInstance))
		{
			sendAF();
			return;
		}
		
		if (((L2CastleChamberlainInstance)manager).getCastle() != currentCastle)
		{
			sendAF();
			return;
		}
		
		if (!player.isInsideRadius(manager, INTERACTION_DISTANCE, true, false))
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		List<SeedProduction> seeds = new ArrayList<SeedProduction>(_items.length);
		for (Seed i : _items)
		{
			SeedProduction s = i.getSeed();
			if (s == null)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			seeds.add(s);
		}
		
		CastleManager.getInstance().getCastleById(_manorId).setSeedProduction(seeds, CastleManorManager.PERIOD_NEXT);
		if (Config.ALT_MANOR_SAVE_ALL_ACTIONS)
			CastleManager.getInstance().getCastleById(_manorId).saveSeedData(CastleManorManager.PERIOD_NEXT);
		
		sendAF();
	}
	
	private class Seed
	{
		private final int _itemId;
		private final long _sales;
		private final long _price;
		
		public Seed(int id, long s, long p)
		{
			_itemId = id;
			_sales = s;
			_price = p;
		}
		
		public SeedProduction getSeed()
		{
			L2Manor manor = L2Manor.getInstance();
			if (!manor.getSeedsForCastle(_manorId).contains(_itemId))
				return null;
			if (!ManorOrderLimits.allows(_sales, _price, manor.getSeedSaleLimit(_itemId), manor.getSeedBasicPrice(_itemId),
					MAX_ADENA))
				return null;
			
			return CastleManorManager.getInstance().getNewSeedProduction(_itemId, _sales, _price, _sales);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_0A_REQUESTSETSEED;
	}
}
