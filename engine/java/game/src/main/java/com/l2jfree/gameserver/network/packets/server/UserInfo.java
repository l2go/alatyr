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

import com.l2jfree.gameserver.network.bridge.BridgeActors;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.FactSink;
import com.l2jfree.gameserver.network.bridge.SemanticPacket;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.gameobjects.status.PlayerStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.gameobjects.view.PlayerView;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * 0000: 04 03 15 00 00 77 ff 00 00 80 f1 ff ff 00 00 00    .....w..........
 * 0010: 00 2a 89 00 4c 43 00 61 00 6c 00 61 00 64 00 6f    .*..LC.a.l.a.d.o
 * 0020: 00 6e 00 00 00 01 00 00 00 00 00 00 00 19 00 00    .n..............
 * 0030: 00 0d 00 00 00 ee 81 02 00 15 00 00 00 18 00 00    ................
 * 0040: 00 19 00 00 00 25 00 00 00 17 00 00 00 28 00 00    .....%.......(..
 * 0050: 00 14 01 00 00 14 01 00 00 02 01 00 00 02 01 00    ................
 * 0060: 00 fa 09 00 00 81 06 00 00 26 34 00 00 2e 00 00    .........&4.....
 * 0070: 00 00 00 00 00 db 9f a1 41 93 26 64 41 de c8 31    ........A.&dA..1
 * 0080: 41 ca 73 c0 41 d5 22 d0 41 83 bd 41 41 81 56 10    A.s.A.".A..AA.V.
 * 0090: 41 00 00 00 00 27 7d 30 41 69 aa e0 40 b4 fb d3    A....'}0Ai..@...
 * 00a0: 41 91 f9 63 41 00 00 00 00 81 56 10 41 00 00 00    A..cA.....V.A...
 * 00b0: 00 71 00 00 00 71 00 00 00 76 00 00 00 74 00 00    .q...q...v...t..
 * 00c0: 00 74 00 00 00 2a 00 00 00 e8 02 00 00 00 00 00    .t...*..........
 * 00d0: 00 5f 04 00 00 ac 01 00 00 cf 01 00 00 62 04 00    ._...........b..
 * 00e0: 00 00 00 00 00 e8 02 00 00 0b 00 00 00 52 01 00    .............R..
 * 00f0: 00 4d 00 00 00 2a 00 00 00 2f 00 00 00 29 00 00    .M...*.../...)..
 * 0100: 00 12 00 00 00 82 01 00 00 52 01 00 00 53 00 00    .........R...S..
 * 0110: 00 00 00 00 00 00 00 00 00 7a 00 00 00 55 00 00    .........z...U..
 * 0120: 00 32 00 00 00 32 00 00 00 00 00 00 00 00 00 00    .2...2..........
 * 0130: 00 00 00 00 00 00 00 00 00 a4 70 3d 0a d7 a3 f0    ..........p=....
 * 0140: 3f 64 5d dc 46 03 78 f3 3f 00 00 00 00 00 00 1e    ?d].F.x.?.......
 * 0150: 40 00 00 00 00 00 00 38 40 02 00 00 00 01 00 00    @......8@.......
 * 0160: 00 00 00 00 00 00 00 00 00 00 00 c1 0c 00 00 01    ................
 * 0170: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00    ................
 * 0180: 00 00 00 00                                        ....
 *
 *
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSdddcccdd (h)
 * dddddSddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd  ffffddddSdddddcccddh (h) c dc hhdh
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSdddddcccddh (h) c dc hhdh ddddc c dcc cddd d (from 654)
 * but it actually reads
 * dddddSdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddffffddddSdddddcccddh (h) c dc *dddddddd* hhdh ddddc dcc cddd d
 * 																					*...*: here i am not sure at least it looks like it reads that much data (32 bytes), not sure about the format inside because it is not read thanks to the ususal parsing function
 *
 * dddddSddddQddddddddddddddddddddddddddddddddddddddddddddddddhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhddddddddddddddddddddffffddddSdddddcccddh [h] c dc d hhdh ddddc c dcc cddd d c dd d d
 * dddddSddddQddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhddddddddddddddddddddffffddddSdddddcccddh

 * @version $Revision: 1.14.2.4.2.12 $ $Date: 2005/04/11 10:05:55 $
 */
public final class UserInfo extends L2ServerPacket implements SemanticPacket
{
	@Override
	public void describe(FactSink sink, com.l2jfree.gameserver.gameobjects.L2Player receiver)
	{
		if (receiver != _activeChar)
			throw new IllegalArgumentException("userinfo receiver");
		var view = _activeChar.getView();
		var stat = _activeChar.getStat();
		var status = _activeChar.getStatus();
		BridgeActors.appeared(sink, view.getObjectId(), "player", _activeChar.getAppearance().getVisibleName(),
				view.getX(), view.getY(), view.getZ(), view.getRunSpd(), view.getWalkSpd(),
				view.getMovementSpeedMultiplier(), _activeChar.isRunning(), !_activeChar.isAlikeDead(), true, null);
		BridgeActors.movement(sink, _activeChar);
		sink.emit(BridgeFact.of(16, BridgeField.number(64, view.getObjectId()),
				BridgeField.object(25, BridgeField.number(1, (int)status.getCurrentHp()),
						BridgeField.number(2, stat.getMaxHp()), BridgeField.number(3, (int)status.getCurrentMp()),
						BridgeField.number(4, stat.getMaxMp()), BridgeField.number(5, _activeChar.getLevel()),
						BridgeField.number(6, _activeChar.getExp()), BridgeField.number(7, _activeChar.getSp()))));
	}

	private static final String _S__04_USERINFO = "[S] 04 UserInfo";
	
	private final L2Player _activeChar;
	private final int _relation;
	
	public UserInfo(L2Player cha)
	{
		cha.getView().refresh();
		
		_activeChar = cha;
		
		int relation = cha.isClanLeader() ? 0x40 : 0;
		
		if (cha.getSiegeState() == L2Player.SIEGE_STATE_ATTACKER)
			relation |= 0x180;
		else if (cha.getSiegeState() == L2Player.SIEGE_STATE_DEFENDER)
			relation |= 0x80;
		
		_relation = relation;
	}
	
	@Override
	public void packetSent(L2Client client, L2Player activeChar)
	{
		if (Config.PACKET_FINAL)
		{
			_activeChar.sendPacket(new ExBrExtraUserInfo(_activeChar));
			_activeChar.sendPacket(new ExVitalityPointInfo(_activeChar.getVitalityPoints()));
		}
	}
	
	@Override
	public boolean canBeSentTo(L2Client client, L2Player activeChar)
	{
		return _activeChar == activeChar;
	}
	
	@Override
	protected void writeImpl()
	{
		final PlayerView view = _activeChar.getView();
		final PlayerAppearance _appearance = _activeChar.getAppearance();
		final PlayerInventory _inv = _activeChar.getInventory();
		final PlayerStat stat = _activeChar.getStat();
		final PlayerStatus status = _activeChar.getStatus();
		
		writeC(0x32);
		
		writeD(view.getX());
		writeD(view.getY());
		writeD(view.getZ());
		// heading from CT2.3 no longer used inside userinfo, here is now vehicle id (boat,airship)
		writeD((_activeChar.isInAirShip() && Config.PACKET_FINAL) ? _activeChar.getAirShip().getObjectId() : 0x00);
		writeD(view.getObjectId());
		writeS(_appearance.getVisibleName());
		writeD(_activeChar.getRace().ordinal());
		writeD(_appearance.getSex() ? 1 : 0);
		
		if (_activeChar.getClassIndex() == 0)
			writeD(_activeChar.getClassId().getId());
		else
			writeD(_activeChar.getBaseClass());
		
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
		
		writeD(_activeChar.getActiveWeaponItem() != null ? 40 : 20); // 20 no weapon, 40 weapon equipped
		
		writePaperdollObjectIds(_inv, true);
		writePaperdollItemDisplayIds(_inv, true);
		writePaperdollAugmentationIds(_inv, true);
		
		if (Config.PACKET_FINAL)
		{
			writeD(_inv.getMaxTalismanCount()); // CT2.3
			writeD(_inv.getCloakStatus()); // CT2.3
		}
		
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
		writeD(_activeChar.getPvpFlag());
		writeD(_activeChar.getKarma());
		
		writeD(view.getRunSpd());
		writeD(view.getWalkSpd());
		writeD(view.getSwimRunSpd());
		writeD(view.getSwimWalkSpd());
		writeD(0);
		writeD(0);
		writeD(_activeChar.isFlying() ? view.getFlyRunSpd() : 0); // fly speed
		writeD(_activeChar.isFlying() ? view.getFlyWalkSpd() : 0); // fly speed
		
		writeF(view.getMovementSpeedMultiplier());
		writeF(view.getAttackSpeedMultiplier());
		
		writeF(view.getCollisionRadius());
		writeF(view.getCollisionHeight());
		
		writeD(_appearance.getHairStyle());
		writeD(_appearance.getHairColor());
		writeD(_appearance.getFace());
		writeD((_activeChar.getAccessLevel() >= Config.GM_ALTG_MIN_LEVEL) ? 1 : 0); // builder level
		
		String title = _appearance.getVisibleTitle();
		if (_appearance.isInvisible() && _activeChar.isGM())
			title = "Invisible";
		if (_activeChar.getPoly().isMorphed())
		{
			L2NpcTemplate polyObj = NpcTable.getInstance().getTemplate(_activeChar.getPoly().getPolyId());
			if (polyObj != null)
				title += " - " + polyObj.getName();
		}
		writeS(title);
		
		writeD(_activeChar.getClanId());
		writeD(_activeChar.getClanCrestId());
		writeD(_activeChar.getAllyId());
		writeD(_activeChar.getAllyCrestId()); // ally crest id
		// 0x40 leader rights
		// siege flags: attacker - 0x180 sword over name, defender - 0x80 shield, 0xC0 crown (|leader), 0x1C0 flag (|leader)
		writeD(_relation);
		writeC(_activeChar.getMountType()); // mount type
		writeC(_activeChar.getPrivateStoreType());
		writeC(_activeChar.hasDwarvenCraft() ? 1 : 0);
		writeD(_activeChar.getPkKills());
		writeD(_activeChar.getPvpKills());
		
		writeH(_activeChar.getCubics().size());
		for (int id : _activeChar.getCubics().keySet())
			writeH(id);
		
		writeC(_activeChar.isLookingForParty());
		
		if (_appearance.isInvisible())
		{
			writeD(_activeChar.getAbnormalEffect() | AbnormalEffect.STEALTH.getMask());
		}
		else
		{
			writeD(_activeChar.getAbnormalEffect());
		}
		writeC(_activeChar.isFlyingMounted() ? 2 : 0);
		
		writeD(_activeChar.getClanPrivileges());
		
		writeH(_activeChar.getEvaluations()); //c2  recommendations remaining
		writeH(_activeChar.getEvalPoints()); //c2  recommendations received
		writeD(_activeChar.getMountNpcId() > 0 ? _activeChar.getMountNpcId() + 1000000 : 0);
		writeH(_activeChar.getInventoryLimit());
		
		//writeH(_inv.getUnequippedSize());
		
		writeD(_activeChar.getClassId().getId());
		writeD(0x00); // special effects? circles around player...
		writeD(stat.getMaxCp());
		writeD((int)status.getCurrentCp());
		writeC(_activeChar.isMounted() ? 0 : _activeChar.getEnchantEffect());
		
		if (_activeChar.getTeam() == 1)
			writeC(0x01); //team circle around feet 1= Blue, 2 = red
		else if (_activeChar.getTeam() == 2)
			writeC(0x02); //team circle around feet 1= Blue, 2 = red
		else
			writeC(0x00); //team circle around feet 1= Blue, 2 = red
			
		writeD(_activeChar.getClanCrestLargeId());
		writeC(_activeChar.isNoble() ? 1 : 0); //0x01: symbol on char menu ctrl+I
		writeC((_activeChar.isHero() || (_activeChar.isGM() && Config.GM_HERO_AURA)) ? 1 : 0); //0x01: Hero Aura
		
		writeC(_activeChar.isFishing() ? 1 : 0); //Fishing Mode
		writeD(_activeChar.getFishx()); //fishing x
		writeD(_activeChar.getFishy()); //fishing y
		writeD(_activeChar.getFishz()); //fishing z
		writeD(view.getNameColor());
		
		//new c5
		writeC(_activeChar.isRunning() ? 0x01 : 0x00); //changes the Speed display on Status Window
		
		writeD(_activeChar.getPledgeClass()); //changes the text above CP on Status Window
		writeD(_activeChar.getSubPledgeType());
		
		writeD(view.getTitleColor());
		
		writeD(view.getCursedWeaponLevel());
		
		writeD(view.getTransformationGraphicalId());
		
		writePlayerElementAttribute(_activeChar);
		
		writeD(_activeChar.getAgathionId());
		
		// T2
		writeD(_activeChar.getFame()); // Fame
		writeD(0x01); // Unknown
		writeD(_activeChar.getVitalityPoints());
		if (Config.PACKET_FINAL)
		{
			writeD(_activeChar.getSpecialEffect());
			writeD(0x00); // CT2.3
			writeD(0x00); // CT2.3
			writeD(0x00); // CT2.3
		}
	}
	
	@Override
	public String getType()
	{
		return _S__04_USERINFO;
	}
}
