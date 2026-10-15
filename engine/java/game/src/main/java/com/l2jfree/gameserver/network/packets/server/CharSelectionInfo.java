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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.model.CharSelectInfoPackage;
import com.l2jfree.gameserver.model.CursedWeapon;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class CharSelectionInfo extends L2ServerPacket
{
	private static final String _S__09_CHARSELECTINFO =
			"[S] 09 CharSelectInfo [ddc (sdsddd dddd ddd ff d q ddddd dddddddddddddddddddddddddddddddddd ff ddd hh d)]";
	
	private final String _loginName;
	private final int _sessionId;
	private final CharSelectInfoPackage[] _characterPackages;
	
	public CharSelectionInfo(L2Client client)
	{
		_sessionId = client.getSessionId().playOk1();
		_loginName = client.getAccountName();
		_characterPackages = loadCharacterSelectInfo();
		
		client.setCharSelection(_characterPackages);
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x09);
		
		int size = _characterPackages.length;
		writeD(size);
		writeD(0x07);
		writeC(0x00);
		
		long lastAccess = 0L;
		int activeId = 0;
		for (int i = 0; i < size; i++)
		{
			CharSelectInfoPackage infoPack = _characterPackages[i];
			if (lastAccess < infoPack.getLastAccess())
			{
				lastAccess = infoPack.getLastAccess();
				activeId = i;
			}
		}
		
		for (int i = 0; i < size; i++)
		{
			CharSelectInfoPackage charInfoPackage = _characterPackages[i];
			
			writeS(charInfoPackage.getName());
			writeD(charInfoPackage.getCharId());
			writeS(_loginName);
			writeD(_sessionId);
			writeD(charInfoPackage.getClanId());
			writeD(0x00); // ??
			
			writeD(charInfoPackage.getSex());
			writeD(charInfoPackage.getRace());
			
			if (charInfoPackage.getClassId() == charInfoPackage.getBaseClassId())
				writeD(charInfoPackage.getClassId());
			else
				writeD(charInfoPackage.getBaseClassId());
			
			writeD(0x01); // active ?? (no difference between 0 and 1)
			
			writeD(charInfoPackage.getX());
			writeD(charInfoPackage.getY());
			writeD(charInfoPackage.getZ());
			
			writeF(charInfoPackage.getCurrentHp()); // hp cur
			writeF(charInfoPackage.getCurrentMp()); // mp cur
			
			writeD(charInfoPackage.getSp());
			writeQ(charInfoPackage.getExp());
			writeD(charInfoPackage.getLevel());
			
			writeD(charInfoPackage.getKarma()); // karma
			writeD(charInfoPackage.getPkKills());
			
			for (int k = 0; k < 8; k++)
				writeD(0x00);
			
			for (int slot : L2ServerPacket.getPaperdollSlots(true))
				writeD(charInfoPackage.getPaperdollItemDisplayId(slot));
			
			writeD(charInfoPackage.getHairStyle());
			writeD(charInfoPackage.getHairColor());
			writeD(charInfoPackage.getFace());
			
			writeF(charInfoPackage.getMaxHp()); // hp max
			writeF(charInfoPackage.getMaxMp()); // mp max
			
			long deleteTime = charInfoPackage.getDeleteTimer();
			int deletedays = 0;
			if (deleteTime > 0)
				deletedays = (int)((deleteTime - System.currentTimeMillis()) / 1000);
			writeD(deletedays); // days left before
			writeD(charInfoPackage.getClassId());
			writeD(i == activeId);
			writeC(charInfoPackage.getEnchantEffect() > 127 ? 127 : charInfoPackage.getEnchantEffect());
			writeD(charInfoPackage.getAugmentationId());
			
			writeD(charInfoPackage.getTransformationId());
		}
	}
	
	private CharSelectInfoPackage[] loadCharacterSelectInfo()
	{
		L2Player.disconnectIfOnline(_loginName);
		
		CharSelectInfoPackage charInfopackage;
		List<CharSelectInfoPackage> characterList = new ArrayList<CharSelectInfoPackage>();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, name, level, max_hp, current_hp, max_mp, current_mp, face, hair_style, hair_color, is_female, x, y, z, exp, sp, karma, clan_id, race_id, active_class_id, delete_at, last_access_at, base_class_id, transformation_id FROM player WHERE account_name=?");
			statement.setString(1, _loginName);
			ResultSet charList = statement.executeQuery();
			
			while (charList.next())// fills the package
			{
				charInfopackage = restoreChar(charList);
				if (charInfopackage != null)
					characterList.add(charInfopackage);
			}
			
			charList.close();
			statement.close();
			
			return characterList.toArray(new CharSelectInfoPackage[characterList.size()]);
		}
		catch (Exception e)
		{
			_log.warn("Could not restore char info: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return new CharSelectInfoPackage[0];
	}
	
	private void loadCharacterSubclassInfo(CharSelectInfoPackage charInfopackage, int ObjectId, int activeClassId)
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT exp, sp, level FROM player_subclass WHERE player_id=? AND class_id=?");
			statement.setInt(1, ObjectId);
			statement.setInt(2, activeClassId);
			ResultSet charList = statement.executeQuery();
			
			if (charList.next())
			{
				charInfopackage.setExp(charList.getLong("exp"));
				charInfopackage.setSp(charList.getInt("sp"));
				charInfopackage.setLevel(charList.getInt("level"));
			}
			
			charList.close();
			statement.close();
			
		}
		catch (Exception e)
		{
			_log.warn("Could not restore char subclass info: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
	}
	
	private CharSelectInfoPackage restoreChar(ResultSet chardata) throws Exception
	{
		int objectId = chardata.getInt("id");
		
		L2Player.disconnectIfOnline(objectId);
		
		String name = chardata.getString("name");
		
		// See if the char must be deleted (NULL: no deletion pending, 0)
		Timestamp deleteAt = chardata.getTimestamp("delete_at");
		long deletetime = deleteAt == null ? 0L : deleteAt.getTime();
		if (deletetime > 0)
		{
			if (System.currentTimeMillis() > deletetime)
			{
				L2Clan clan = ClanTable.getInstance().getClan(chardata.getInt("clan_id"));
				if (clan != null)
					clan.removeClanMember(objectId, 0);
				
				L2Client.deleteCharByObjId(objectId);
				return null;
			}
		}
		
		CharSelectInfoPackage charInfopackage = new CharSelectInfoPackage(objectId, name);
		charInfopackage.setLevel(chardata.getInt("level"));
		charInfopackage.setMaxHp(chardata.getInt("max_hp"));
		charInfopackage.setCurrentHp(chardata.getDouble("current_hp"));
		charInfopackage.setMaxMp(chardata.getInt("max_mp"));
		charInfopackage.setCurrentMp(chardata.getDouble("current_mp"));
		charInfopackage.setKarma(chardata.getInt("karma"));
		
		charInfopackage.setFace(chardata.getInt("face"));
		charInfopackage.setHairStyle(chardata.getInt("hair_style"));
		charInfopackage.setHairColor(chardata.getInt("hair_color"));
		charInfopackage.setSex(chardata.getBoolean("is_female") ? 1 : 0);
		
		charInfopackage.setExp(chardata.getLong("exp"));
		charInfopackage.setSp(chardata.getInt("sp"));
		charInfopackage.setClanId(chardata.getInt("clan_id")); // NULL (no clan) reads as 0
		
		charInfopackage.setRace(chardata.getInt("race_id"));
		charInfopackage.setX(chardata.getInt("x"));
		charInfopackage.setY(chardata.getInt("y"));
		charInfopackage.setZ(chardata.getInt("z"));
		
		final int baseClassId = chardata.getInt("base_class_id");
		final int activeClassId = chardata.getInt("active_class_id");
		
		// if is in subclass, load subclass exp, sp, lvl info
		if (baseClassId != activeClassId)
			loadCharacterSubclassInfo(charInfopackage, objectId, activeClassId);
		
		charInfopackage.setClassId(activeClassId);
		
		// Get the augmentation id for equipped weapon
		int weaponObjId = charInfopackage.getPaperdollObjectId(Inventory.PAPERDOLL_LRHAND);
		if (weaponObjId < 1)
			weaponObjId = charInfopackage.getPaperdollObjectId(Inventory.PAPERDOLL_RHAND);
		
		int weaponId = charInfopackage.getPaperdollItemId(Inventory.PAPERDOLL_LRHAND);
		if (weaponId < 1)
			weaponId = charInfopackage.getPaperdollItemId(Inventory.PAPERDOLL_RHAND);
		
		int transformId = chardata.getInt("transformation_id"); // NULL (not transformed) reads as 0
		
		//cursed weapon check
		if (CursedWeaponsManager.getInstance().isCursed(weaponId))
		{
			CursedWeapon cw = CursedWeaponsManager.getInstance().getCursedWeapon(weaponId);
			if (cw.getTransformId() < 1)
				charInfopackage.setTransformationId(0);
			else
				charInfopackage.setTransformationId(cw.getTransformId());
		}
		else if (transformId > 0)
		{
			charInfopackage.setTransformationId(transformId);
		}
		else
		{
			charInfopackage.setTransformationId(0);
		}
		
		if (weaponObjId > 0)
		{
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement =
						con.prepareStatement("SELECT augmentation_attributes FROM item_attribute WHERE item_id=?");
				statement.setInt(1, weaponObjId);
				ResultSet result = statement.executeQuery();
				if (result.next())
				{
					int augment = result.getInt("augmentation_attributes"); // NULL (not augmented) reads as 0
					charInfopackage.setAugmentationId((augment == -1) ? 0 : augment);
				}
				
				result.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.warn("Could not restore augmentation info: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
		/*
		 * Check if the base class is set to zero and alse doesn't match
		 * with the current active class, otherwise send the base class ID.
		 * 
		 * This prevents chars created before base class was introduced
		 * from being displayed incorrectly.
		 */
		if (baseClassId == 0 && activeClassId > 0)
			charInfopackage.setBaseClassId(activeClassId);
		else
			charInfopackage.setBaseClassId(baseClassId);
		
		charInfopackage.setDeleteTimer(deletetime);
		Timestamp lastAccess = chardata.getTimestamp("last_access_at");
		charInfopackage.setLastAccess(lastAccess == null ? 0L : lastAccess.getTime());
		return charInfopackage;
	}
	
	@Override
	public String getType()
	{
		return _S__09_CHARSELECTINFO;
	}
}
