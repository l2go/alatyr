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
package com.l2jfree.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Parsers and builders for the packets the seed loop adds, checked against fixture bytes. */
class GamePacketsTest
{
	@Test
	void clientPacketsUseTheFinalHandlerOpcodes()
	{
		assertThat(GamePackets.moveBackward(10, 20, 30, 1, 2, 3)[0]).isEqualTo((byte)0x0f);
		assertThat(GamePackets.action(7, 1, 2, 3)[0]).isEqualTo((byte)0x1f);
		assertThat(GamePackets.attack(7, 1, 2, 3)[0]).isEqualTo((byte)0x32);
		assertThat(GamePackets.bypass("npc_7_Buy 731")[0]).isEqualTo((byte)0x23);
		assertThat(GamePackets.buy(731, 1060, 1)[0]).isEqualTo((byte)0x40);
		assertThat(GamePackets.useItem(99)[0]).isEqualTo((byte)0x19);
		assertThat(GamePackets.requestSkillList()).containsExactly((byte)0x50);
		assertThat(GamePackets.acquireSkill(257, 1)[0]).isEqualTo((byte)0x7c);
		assertThat(GamePackets.requestItemList()).containsExactly((byte)0x14);
	}

	@Test
	void buyCarriesTheListTheItemAndAnEightByteCount() throws SmokeException
	{
		PacketReader in = new PacketReader(GamePackets.buy(731, 1060, 1));
		
		assertThat(in.readC()).isEqualTo(0x40);
		assertThat(in.readD()).isEqualTo(731);
		assertThat(in.readD()).isEqualTo(1);
		assertThat(in.readD()).isEqualTo(1060);
		assertThat(in.readQ()).isEqualTo(1L);
		assertThat(in.remaining()).isZero();
	}

	@Test
	void userInfoReadsLevelExperienceAndSp() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x32).writeD(1).writeD(2).writeD(3).writeD(0).writeD(44)
				.writeS("Fighter").writeD(0).writeD(0).writeD(0).writeD(1).writeQ(68L).writeZeros(6 * 4).writeD(168)
				.writeD(90).writeD(48).writeD(40).writeD(12).toByteArray();
		GamePackets.UserView user = GamePackets.parseUserInfo(reader(packet));
		
		assertThat(user.objectId()).isEqualTo(44);
		assertThat(user.name()).isEqualTo("Fighter");
		assertThat(user.x()).isEqualTo(1);
		assertThat(user.level()).isEqualTo(1);
		assertThat(user.exp()).isEqualTo(68L);
		assertThat(user.sp()).isEqualTo(12);
		assertThat(user.currentHp()).isEqualTo(90);
	}

	@Test
	void npcInfoSubtractsTheTemplateOffsetAndReadsTheDeadFlag() throws SmokeException
	{
		PacketWriter writer = new PacketWriter().writeC(0x0c).writeD(900).writeD(1_000_000 + 18003).writeD(1)
				.writeD(-70357).writeD(256863).writeD(-3104);
		writer.writeZeros(4 + 4 + 4 + 4); // heading, zero, attack speeds
		writer.writeZeros(8 * 4); // movement speeds
		writer.writeZeros(4 * 8); // multipliers and collision
		writer.writeZeros(3 * 4); // paperdoll
		writer.writeZeros(3); // name, running, combat
		writer.writeC(1); // dead
		GamePackets.NpcView npc = GamePackets.parseNpcInfo(reader(writer.toByteArray()));
		
		assertThat(npc.objectId()).isEqualTo(900);
		assertThat(npc.templateId()).isEqualTo(18003);
		assertThat(npc.x()).isEqualTo(-70357);
		assertThat(npc.y()).isEqualTo(256863);
		assertThat(npc.dead()).isTrue();
	}

	@Test
	void statusUpdateReadsAttributes() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x18).writeD(44).writeD(2).writeD(0x0d).writeD(16).writeD(0x09)
				.writeD(0).toByteArray();
		GamePackets.StatusView status = GamePackets.parseStatusUpdate(reader(packet));
		
		assertThat(status.objectId()).isEqualTo(44);
		assertThat(status.attributes()).containsExactly(new GamePackets.StatusAttribute(0x0d, 16),
				new GamePackets.StatusAttribute(0x09, 0));
	}

	@Test
	void itemListReadsTheEightByteCountAndTheEquippedFlag() throws SmokeException
	{
		PacketWriter writer = new PacketWriter().writeC(0x11).writeH(1).writeH(1);
		writeItem(writer, 50, 1, 1L, true);
		List<GamePackets.ItemView> items = GamePackets.parseItemList(reader(writer.toByteArray()));
		
		assertThat(items).containsExactly(new GamePackets.ItemView(50, 1, 1L, true));
	}

	@Test
	void inventoryUpdateReadsARemoval() throws SmokeException
	{
		PacketWriter writer = new PacketWriter().writeC(0x21).writeH(1).writeH(3);
		writeItem(writer, 50, 1060, 0L, false);
		List<GamePackets.ItemChange> changes = GamePackets.parseInventoryUpdate(reader(writer.toByteArray()));
		
		assertThat(changes).containsExactly(new GamePackets.ItemChange(3, new GamePackets.ItemView(50, 1060, 0L, false)));
	}

	@Test
	void dropItemReadsTheDropperAndTheCount() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x16).writeD(800).writeD(70).writeD(57).writeD(1).writeD(2)
				.writeD(3).writeD(1).writeQ(6L).toByteArray();
		GamePackets.GroundItem drop = GamePackets.parseDropItem(reader(packet));
		
		assertThat(drop.ownerId()).isEqualTo(800);
		assertThat(drop.objectId()).isEqualTo(70);
		assertThat(drop.itemId()).isEqualTo(57);
		assertThat(drop.count()).isEqualTo(6L);
	}

	@Test
	void skillListReadsIdAndLevel() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x5f).writeD(1).writeD(1).writeD(1).writeD(257).writeC(1)
				.toByteArray();
		
		assertThat(GamePackets.parseSkillList(reader(packet))).containsExactly(new GamePackets.SkillView(257, 1));
	}

	@Test
	void acquireSkillListReadsTheOfferedSkill() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x90).writeD(0).writeD(1).writeD(257).writeD(1).writeD(1).writeD(12)
				.writeD(2).toByteArray();
		
		assertThat(GamePackets.parseAcquireSkillList(reader(packet)))
				.containsExactly(new GamePackets.SkillOffer(257, 1, 12));
	}

	@Test
	void buyListReadsTheListIdAndThePrice() throws SmokeException
	{
		PacketWriter writer = new PacketWriter().writeC(0x07).writeQ(20L).writeD(731).writeH(1);
		writer.writeH(5).writeD(0).writeD(1060).writeQ(1L).writeH(5).writeH(0).writeD(0).writeH(0).writeH(0).writeH(0)
				.writeQ(12L).writeZeros(16);
		GamePackets.ShopView shop = GamePackets.parseBuyList(reader(writer.toByteArray()));
		
		assertThat(shop.listId()).isEqualTo(731);
		assertThat(shop.money()).isEqualTo(20L);
		assertThat(shop.offers()).containsExactly(new GamePackets.ShopOffer(1060, 12L));
	}

	@Test
	void npcHtmlReadsTheObjectAndTheText() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x19).writeD(15).writeS("<a action=\"bypass -h npc_15_Buy 731\">")
				.toByteArray();
		GamePackets.Dialog dialog = GamePackets.parseNpcHtml(reader(packet));
		
		assertThat(dialog.objectId()).isEqualTo(15);
		assertThat(dialog.html()).contains("Buy 731");
	}

	@Test
	void dieDeleteAndMovementReadTheActor() throws SmokeException
	{
		assertThat(GamePackets.parseDie(reader(new PacketWriter().writeC(0x00).writeD(9).toByteArray()))).isEqualTo(9);
		assertThat(GamePackets.parseDeleteObject(reader(new PacketWriter().writeC(0x08).writeD(9).toByteArray())))
				.isEqualTo(9);
		assertThat(GamePackets.parseStopMove(reader(new PacketWriter().writeC(0x47).writeD(4).writeD(1).writeD(2)
				.writeD(3).writeD(0).toByteArray()))).containsExactly(4, 1, 2, 3);
		assertThat(GamePackets.parseMoveToLocation(reader(new PacketWriter().writeC(0x2f).writeD(4).writeD(10)
				.writeD(20).writeD(30).writeD(1).writeD(2).writeD(3).toByteArray())))
				.containsExactly(4, 10, 20, 30, 1, 2, 3);
		assertThat(GamePackets.parseValidateLocation(reader(new PacketWriter().writeC(0x79).writeD(4).writeD(1)
				.writeD(2).writeD(3).writeD(0).toByteArray()))).containsExactly(4, 1, 2, 3);
	}

	@Test
	void spawnItemReadsTheItemWithoutAnOwner() throws SmokeException
	{
		byte[] packet = new PacketWriter().writeC(0x05).writeD(70).writeD(57).writeD(1).writeD(2).writeD(3).writeD(1)
				.writeQ(6L).toByteArray();
		GamePackets.GroundItem drop = GamePackets.parseSpawnItem(reader(packet));

		assertThat(drop.objectId()).isEqualTo(70);
		assertThat(drop.itemId()).isEqualTo(57);
		assertThat(drop.count()).isEqualTo(6L);
		assertThat(drop.ownerId()).isZero();
	}

	private static PacketReader reader(byte[] packet) throws SmokeException
	{
		PacketReader in = new PacketReader(packet);
		in.readC();
		return in;
	}

	/** One ItemList row in the final protocol: type, ids, an 8-byte count, then the fixed tail. */
	private static void writeItem(PacketWriter writer, int objectId, int itemId, long count, boolean equipped)
	{
		writer.writeH(0).writeD(objectId).writeD(itemId).writeD(0).writeQ(count).writeH(0).writeH(0)
				.writeH(equipped ? 1 : 0).writeD(0).writeH(0).writeH(0).writeD(0).writeD(0).writeZeros(16).writeD(-1);
	}
}
