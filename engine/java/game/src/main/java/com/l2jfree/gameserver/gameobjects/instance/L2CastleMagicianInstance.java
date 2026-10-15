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
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

/**
 * @author  Kerberos
 */
public class L2CastleMagicianInstance extends L2NpcInstance
{
	protected static final int COND_ALL_FALSE = 0;
	protected static final int COND_BUSY_BECAUSE_OF_SIEGE = 1;
	protected static final int COND_OWNER = 2;
	
	private static final int[] TalismanIds = { 9914, 9915, 9917, 9918, 9919, 9920, 9921, 9922, 9923, 9924, 9926, 9927,
			9928, 9930, 9931, 9932, 9933, 9934, 9935, 9936, 9937, 9938, 9939, 9940, 9941, 9942, 9943, 9944, 9945, 9946,
			9947, 9948, 9949, 9950, 9951, 9952, 9953, 9954, 9955, 9956, 9957, 9958, 9959, 9960, 9961, 9962, 9963, 9964,
			9965, 9966, 10141, 10142, 10158 };
	
	/**
	* @param template
	*/
	public L2CastleMagicianInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void showChatWindow(L2Player player, int val)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		String filename = "data/html/castlemagician/magician-no.htm";
		
		int condition = validateCondition(player);
		if (condition > COND_ALL_FALSE)
		{
			if (condition == COND_BUSY_BECAUSE_OF_SIEGE)
				filename = "data/html/castlemagician/magician-busy.htm"; // Busy because of siege
			else if (condition == COND_OWNER) // Clan owns castle
			{
				if (val == 0)
					filename = "data/html/castlemagician/magician.htm";
				else
					filename = "data/html/castlemagician/magician-" + val + ".htm";
			}
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", String.valueOf(getName() + " " + getTitle()));
		player.sendPacket(html);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.equals("gotoleader"))
		{
			Castle castle = getCastle();
			if (!castle.isGateOpen())
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/castlemagician/magician-nogate.htm");
				player.sendPacket(html);
				return;
			}
			player.teleToLocation(castle.getGateX(), castle.getGateY(), castle.getGateZ());
			player.stopMove(new L2CharPosition(castle.getGateX(), castle.getGateY(), castle.getGateZ(), player
					.getHeading()));
			player.sendPacket(ActionFailed.STATIC_PACKET);
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
		else if (command.startsWith("ExchangeKE"))
		{
			String filename = null;
			int item = TalismanIds[Rnd.get(TalismanIds.length)];
			
			if (player.destroyItemByItemId("ExchangeKE", 9912, 10, this, false))
			{
				SystemMessage msg = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
				msg.addItemName(9912);
				msg.addNumber(10);
				player.sendPacket(msg);
				
				player.addItem("ExchangeKE", item, 1, player, true);
				
				filename = "data/html/castlemagician/magician-KE-exchange.htm";
			}
			else
				filename = "data/html/castlemagician/magician-no-KE.htm";
			
			showChatWindow(player, filename);
		}
		else
			super.onBypassFeedback(player, command);
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
