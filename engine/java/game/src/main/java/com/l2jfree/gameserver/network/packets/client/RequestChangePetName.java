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
import com.l2jfree.gameserver.datatables.PetNameTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;

/**
 * This class represents a packet sent by the client when a pet's name is set.
 * 
 * @version $Revision: 1.3.4.4 $ $Date: 2005/04/06 16:13:48 $
 */
public class RequestChangePetName extends L2ClientPacket
{
	private static final String REQUESTCHANGEPETNAME__C__89 = "[C] 89 RequestChangePetName";
	
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Creature activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		final L2Summon pet = activeChar.getPet();
		if (pet == null)
		{
			requestFailed(SystemMessageId.PETS_ARE_NOT_AVAILABLE_AT_THIS_TIME);
			return;
		}
		else if (pet.getName() != null && pet.getName().trim().length() != 0)
		{
			requestFailed(SystemMessageId.NAMING_YOU_CANNOT_SET_NAME_OF_THE_PET);
			return;
		}
		else if (PetNameTable.doesPetNameExist(_name, pet.getTemplate().getNpcId()))
		{
			requestFailed(SystemMessageId.NAMING_ALREADY_IN_USE_BY_ANOTHER_PET);
			return;
		}
		else if (_name.length() < 3 || _name.length() > 8)
		{
			requestFailed(SystemMessageId.NAMING_PETNAME_UP_TO_8CHARS);
			return;
		}
		else if (!Config.PET_NAME_PATTERN.matcher(_name).matches())
		{
			requestFailed(SystemMessageId.NAMING_PETNAME_CONTAINS_INVALID_CHARS);
			return;
		}
		// the pattern might have been changed by the admin
		else if (_name.contains(" "))
		{
			requestFailed(SystemMessageId.NAMING_THERE_IS_A_SPACE);
			return;
		}
		
		pet.setName(_name);
		pet.broadcastFullInfo();
		
		// set the flag on the control item to say that the pet has a name
		if (pet instanceof L2PetInstance)
		{
			L2ItemInstance controlItem = pet.getOwner().getInventory().getItemByObjectId(pet.getControlItemId());
			if (controlItem != null)
			{
				controlItem.setCustomType2(1);
				controlItem.updateDatabase();
				InventoryUpdate iu = new InventoryUpdate();
				iu.addModifiedItem(controlItem);
				sendPacket(iu);
				iu = null;
			}
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return REQUESTCHANGEPETNAME__C__89;
	}
}
