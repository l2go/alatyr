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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.gameobjects.status.PlayerStatus;
import com.l2jfree.gameserver.gameobjects.view.PlayerView;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSddd   rev420
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSdddcccddhh  rev478
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSdddcccddhhddd rev551
 * @version $Revision: 1.2.2.2.2.8 $ $Date: 2005/03/27 15:29:39 $
 */
public final class GMViewCharacterInfo extends L2ServerPacket
{
	private static final String _S__8F_GMVIEWCHARINFO = "[S] 8F GMViewCharacterInfo";
	
	private final L2Player _activeChar;
	
	public GMViewCharacterInfo(L2Player character)
	{
		character.getView().refresh();
		
		_activeChar = character;
	}
	
	@Override
	protected final void writeImpl()
	{
		final PlayerView view = _activeChar.getView();
		final PlayerAppearance appearance = _activeChar.getAppearance();
		final PlayerInventory _inv = _activeChar.getInventory();
		final PlayerStat stat = _activeChar.getStat();
		final PlayerStatus status = _activeChar.getStatus();
		
		writeC(0x95);
		
		writeD(view.getX());
		writeD(view.getY());
		writeD(view.getZ());
		writeD(view.getHeading());
		writeD(view.getObjectId());
		writeS(_activeChar.getName());
		writeD(_activeChar.getRace().ordinal());
		writeD(appearance.getSex() ? 1 : 0);
		writeD(_activeChar.getClassId().getId());
		writeD(_activeChar.getLevel());
		writeQ(_activeChar.getExp());
		writeD(stat.getSTR());
		writeD(stat.getDEX());
		writeD(stat.getCON());
		writeD(stat.getINT());
		writeD(stat.getWIT());
		writeD(stat.getMEN());
		writeD(stat.getMaxHp());
		writeD((int)status.getCurrentHp());
		writeD(stat.getMaxMp());
		writeD((int)status.getCurrentMp());
		writeD(_activeChar.getSp());
		writeD(Inventory.toStatusLoad(_activeChar.getCurrentLoad()));
		writeD(_activeChar.getMaxLoad());
		writeD(_activeChar.getPkKills());
		
		writePaperdollObjectIds(_inv, true);
		writePaperdollItemDisplayIds(_inv, true);
		
		if (Config.PACKET_FINAL)
		{
			writeD(0); // T3 Unknown
			writeD(0); // T3 Unknown
		}
		
		// c6 new h's
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeD(_inv.getPaperdollAugmentationId(Inventory.PAPERDOLL_RHAND));
		writeH(0x00);
		
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeD(_inv.getPaperdollAugmentationId(Inventory.PAPERDOLL_LRHAND));
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		// end of c6 new h's
		
		// start of T1 new h's
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		writeH(0x00);
		// end of T1 new h's
		if (Config.PACKET_FINAL)
			writeD(0x00); // T3 Unknown
			
		writeD(view.getPAtk());
		writeD(view.getPAtkSpd());
		writeD(view.getPDef());
		writeD(view.getEvasionRate());
		writeD(view.getAccuracy());
		writeD(view.getCriticalHit());
		writeD(view.getMAtk());
		
		writeD(view.getMAtkSpd());
		writeD(view.getPAtkSpd());
		
		writeD(view.getMDef());
		
		writeD(_activeChar.getPvpFlag()); // 0-non-pvp  1-pvp = violett name
		writeD(_activeChar.getKarma());
		
		writeD(view.getRunSpd());
		writeD(view.getWalkSpd());
		writeD(view.getSwimRunSpd()); // swimspeed
		writeD(view.getSwimWalkSpd()); // swimspeed
		writeD(view.getFlRunSpd());
		writeD(view.getFlWalkSpd());
		writeD(view.getFlyRunSpd());
		writeD(view.getFlyWalkSpd());
		writeF(view.getMovementSpeedMultiplier());
		writeF(view.getAttackSpeedMultiplier()); //2.9);//
		writeF(view.getCollisionRadius()); // scale
		writeF(view.getCollisionHeight()); // y offset ??!? fem dwarf 4033
		writeD(appearance.getHairStyle());
		writeD(appearance.getHairColor());
		writeD(appearance.getFace());
		writeD(_activeChar.isGM() ? 0x01 : 0x00); // builder level
		
		writeS(_activeChar.getTitle());
		writeD(_activeChar.getClanId()); // pledge id
		writeD(_activeChar.getClanCrestId()); // pledge crest id
		writeD(_activeChar.getAllyId()); // ally id
		writeC(_activeChar.getMountType()); // mount type
		writeC(_activeChar.getPrivateStoreType());
		writeC(_activeChar.hasDwarvenCraft() ? 1 : 0);
		writeD(_activeChar.getPkKills());
		writeD(_activeChar.getPvpKills());
		
		writeH(_activeChar.getEvaluations());
		writeH(_activeChar.getEvalPoints()); //Blue value for name (0 = white, 255 = pure blue)
		writeD(_activeChar.getClassId().getId());
		writeD(0x00); // special effects? circles around player...
		writeD(stat.getMaxCp());
		writeD((int)status.getCurrentCp());
		
		writeC(_activeChar.isRunning() ? 0x01 : 0x00); //changes the Speed display on Status Window
		
		writeC(321);
		
		writeD(_activeChar.getPledgeClass()); //changes the text above CP on Status Window
		
		writeC(_activeChar.isNoble() ? 0x01 : 0x00);
		writeC(_activeChar.isHero() ? 0x01 : 0x00);
		
		writeD(view.getNameColor());
		writeD(view.getTitleColor());
		
		writePlayerElementAttribute(_activeChar);
		
		writeD(_activeChar.getFame());
		writeD(_activeChar.getVitalityPoints());
	}
	
	@Override
	public String getType()
	{
		return _S__8F_GMVIEWCHARINFO;
	}
}
