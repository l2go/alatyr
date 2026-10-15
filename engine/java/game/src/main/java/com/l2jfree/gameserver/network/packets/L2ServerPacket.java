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
package com.l2jfree.gameserver.network.packets;

import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.lang.L2Math;
import com.l2jfree.network.SendablePacket;

/**
 * @author KenM
 */
public abstract class L2ServerPacket extends SendablePacket<L2Client, L2ClientPacket, L2ServerPacket>
{
	protected static final Logger _log = LoggerFactory.getLogger(L2ServerPacket.class);
	
	protected L2ServerPacket()
	{
	}
	
	@Override
	protected final void write(L2Client client)
	{
		writeImpl(client, client.getActiveChar());
	}
	
	public void prepareToSend(L2Client client, L2Player activeChar)
	{
	}
	
	public void packetSent(L2Client client, L2Player activeChar)
	{
	}
	
	protected void writeImpl()
	{
	}
	
	protected void writeImpl(L2Client client, L2Player activeChar)
	{
		writeImpl();
	}
	
	public boolean canBeSentTo(L2Client client, L2Player activeChar)
	{
		return true;
	}
	
	//TODO: HACK TO BYPASS THE EXPLOIT CHECKS WHICH CAN BE REMOVED NOW
	protected final void writeCompQ(long value)
	{
		if (Config.PACKET_FINAL)
			writeQ(value);
		else
			writeD(L2Math.limit(Integer.MIN_VALUE, value, Integer.MAX_VALUE));
	}
	
	protected final void writeCompH(int value)
	{
		if (Config.PACKET_FINAL)
			writeH(value);
		else
			writeD(value);
	}
	
	public interface ElementalOwner
	{
		public byte getAttackElementType();
		
		public int getAttackElementPower();
		
		public int getElementDefAttr(byte element);
	}
	
	protected final void writeElementalInfo(ElementalOwner owner)
	{
		writeCompH(owner.getAttackElementType());
		writeCompH(owner.getAttackElementPower());
		for (byte i = 0; i < 6; i++)
		{
			writeCompH(owner.getElementDefAttr(i));
		}
	}
	
	protected final void writePlayerElementAttribute(L2Player player)
	{
		byte attackAttribute = player.getAttackElement();
		writeCompH(attackAttribute);
		writeCompH(player.getAttackElementValue(attackAttribute));
		writeCompH(player.getDefenseElementValue(Elementals.FIRE));
		writeCompH(player.getDefenseElementValue(Elementals.WATER));
		writeCompH(player.getDefenseElementValue(Elementals.WIND));
		writeCompH(player.getDefenseElementValue(Elementals.EARTH));
		writeCompH(player.getDefenseElementValue(Elementals.HOLY));
		writeCompH(player.getDefenseElementValue(Elementals.DARK));
	}
	
	private static final int[] PAPERDOLL_SLOTS_WITH_JEWELS = initPaperdollSlots(true);
	private static final int[] PAPERDOLL_SLOTS_WITHOUT_JEWELS = initPaperdollSlots(false);
	
	public static int[] getPaperdollSlots(boolean writeJewels)
	{
		return writeJewels ? PAPERDOLL_SLOTS_WITH_JEWELS : PAPERDOLL_SLOTS_WITHOUT_JEWELS;
	}
	
	private static int[] initPaperdollSlots(boolean writeJewels)
	{
		IntStream.Builder slots = IntStream.builder();
		
		slots.add(Inventory.PAPERDOLL_UNDER);
		if (writeJewels)
		{
			slots.add(Inventory.PAPERDOLL_REAR);
			slots.add(Inventory.PAPERDOLL_LEAR);
			slots.add(Inventory.PAPERDOLL_NECK);
			slots.add(Inventory.PAPERDOLL_RFINGER);
			slots.add(Inventory.PAPERDOLL_LFINGER);
		}
		slots.add(Inventory.PAPERDOLL_HEAD);
		slots.add(Inventory.PAPERDOLL_RHAND);
		slots.add(Inventory.PAPERDOLL_LHAND);
		slots.add(Inventory.PAPERDOLL_GLOVES);
		slots.add(Inventory.PAPERDOLL_CHEST);
		slots.add(Inventory.PAPERDOLL_LEGS);
		slots.add(Inventory.PAPERDOLL_FEET);
		slots.add(Inventory.PAPERDOLL_BACK);
		slots.add(Inventory.PAPERDOLL_LRHAND);
		slots.add(Inventory.PAPERDOLL_HAIR);
		slots.add(Inventory.PAPERDOLL_HAIR2);
		slots.add(Inventory.PAPERDOLL_RBRACELET);
		slots.add(Inventory.PAPERDOLL_LBRACELET);
		slots.add(Inventory.PAPERDOLL_DECO1);
		slots.add(Inventory.PAPERDOLL_DECO2);
		slots.add(Inventory.PAPERDOLL_DECO3);
		slots.add(Inventory.PAPERDOLL_DECO4);
		slots.add(Inventory.PAPERDOLL_DECO5);
		slots.add(Inventory.PAPERDOLL_DECO6);
		if (Config.PACKET_FINAL)
			slots.add(Inventory.PAPERDOLL_BELT); // CT2.3
			
		return slots.build().toArray();
	}
	
	protected final void writePaperdollObjectIds(PlayerInventory inv, boolean writeJewels)
	{
		for (int slot : L2ServerPacket.getPaperdollSlots(writeJewels))
			writeD(inv.getPaperdollObjectId(slot));
	}
	
	protected final void writePaperdollItemDisplayIds(PlayerInventory inv, boolean writeJewels)
	{
		for (int slot : L2ServerPacket.getPaperdollSlots(writeJewels))
			writeD(inv.getPaperdollItemDisplayId(slot));
	}
	
	protected final void writePaperdollAugmentationIds(PlayerInventory inv, boolean writeJewels)
	{
		for (int slot : L2ServerPacket.getPaperdollSlots(writeJewels))
			writeD(inv.getPaperdollAugmentationId(slot));
	}
}
