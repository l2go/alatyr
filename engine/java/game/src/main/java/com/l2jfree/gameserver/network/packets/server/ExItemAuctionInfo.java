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

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class ExItemAuctionInfo extends L2ServerPacket
{
	private static final String _S__FE_68_EXITEMAUCTIONINFO = "[S] FE:68 ExItemAuctionInfo";
	
	/* ONLY AN EXAMPLE
		private static final int AUCTION_LENGTH = 60000;
		private static volatile int ID = 0;

		private final AuctionInfo _auction;

		// Just an example constructor
		public ExItemAuctionInfo(L2Player player)
		{
			L2ItemInstance chest = player.getInventory().getPaperdollItem(PlayerInventory.PAPERDOLL_CHEST);
			_auction = new AuctionInfo(chest, 1, 25000000);
		}

		private class AuctionInfo {
			private final int _id;
			private final int _count;
			private final long _started;
			private final L2ItemInstance _template;
			private int _state;
			private long _bid;

			private AuctionInfo(L2ItemInstance item, int count, long bid) {
				_id = ID++;
				_count = count;
				_started = System.currentTimeMillis();
				_template = item;
				_state = 1;
				_bid = bid;
			}

			private final int getTimeLeft() {
				return (int) ((AUCTION_LENGTH - (System.currentTimeMillis() - _started)) / 1000);
			}
		}
	*/
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x68);
		/*
				int timeLeft = _auction.getTimeLeft();
				if (timeLeft < 0)
				{
					writeC(0x00);
					return;
				}
				else
					writeC(_auction._state);
				writeD(_auction._id); // auction ID
				writeCompQ(_auction._bid); // current bid
				writeD(timeLeft); // time left in seconds
				writeD(_auction._template.getItemId()); // item ID
				writeCompQ(_auction._count); // count

				writeH(_auction._template.getItem().getType2());
				writeH(_auction._template.getCustomType1());
				writeD(_auction._template.getItem().getBodyPart());

				writeH(_auction._template.getEnchantLevel());
				writeH(_auction._template.getCustomType2());

				if (_auction._template.isAugmented())
					writeH(_auction._template.getAugmentation().getAugmentationId());
				else
					writeH(0x00);
				writeH(_auction._template.getMana());

				writeElementalInfo(_auction._template);
				writeH(_auction._template.isTimeLimitedItem() ?
						(int) (_auction._template.getRemainingTime() / 1000) : -1);
				*/
	}
	
	@Override
	public String getType()
	{
		return _S__FE_68_EXITEMAUCTIONINFO;
	}
}
