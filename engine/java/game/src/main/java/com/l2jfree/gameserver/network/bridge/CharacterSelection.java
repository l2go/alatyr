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
package com.l2jfree.gameserver.network.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 * Loads the character in a slot and publishes it on the client. The selection packet sends its own character-selected
 * and action-failed packets afterwards; this class does not.
 */
public final class CharacterSelection
{
	private static final Logger _log = LoggerFactory.getLogger(CharacterSelection.class);
	
	private CharacterSelection()
	{
	}
	
	/**
	 * @return true when the character is in the world and the client is in game
	 */
	public static boolean enter(L2Client client, int slot)
	{
		if (client == null)
			throw new IllegalArgumentException("client");
		
		// A repeated selection does nothing.
		if (client.getActiveChar() != null)
			return false;
		
		final L2Player cha = client.loadCharFromDisk(slot);
		
		if (cha == null)
		{
			_log.error(client + ": character couldn't be loaded (slot:" + slot + ")");
			client.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Keep the loaded character until the one disconnect cleans it up, before it is published.
		if (client.isBridge())
			client.noteBridgeOpeningPlayer(cha);
		
		if ((cha.getAccessLevel() < 0) || (Config.SERVER_GMONLY && cha.getAccessLevel() <= 0))
		{
			// The ordinary client is disconnected here. A bridge opening keeps the loaded character noted and
			// reports the refusal itself, so this method does not start the full disconnect.
			if (!client.isBridge())
				new Disconnection(client, cha).defaultSequence(false);
			return false;
		}
		
		cha.setRunning();
		cha.standUp();
		cha.refreshOverloaded();
		cha.setOnlineStatus(true);
		
		boolean published = client.bridgePublish(() ->
		{
			L2World.getInstance().storeObject(cha);
			L2World.getInstance().addOnlinePlayer(cha);
			cha.setClient(client);
			client.setActiveChar(cha);
			if (client.isBridge())
				client.finishBridgeOpening();
			client.setState(GameClientState.IN_GAME);
		});
		if (!published)
		{
			client.discardOpeningPlayer(cha);
			try
			{
				cha.deleteMe();
			}
			catch (RuntimeException ignored)
			{
				// The opening lost the race with shutdown. The loaded character must not stay in the world.
			}
			return false;
		}
		return true;
	}
}
