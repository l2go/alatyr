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

import java.util.ArrayList;
import java.util.List;

/**
 * The packets of the game protocol (port 7777) that the smoke test uses.
 */
final class GamePackets
{
	// server to client
	static final int OP_CHAR_SELECTION_INFO = 0x09;
	static final int OP_LOGIN_FAIL = 0x0a;
	static final int OP_CHAR_SELECTED = 0x0b;
	static final int OP_NEW_CHARACTER_SUCCESS = 0x0d;
	static final int OP_CHARACTER_CREATE_SUCCESS = 0x0f;
	static final int OP_CHARACTER_CREATE_FAIL = 0x10;
	/** ActionFailed.writeImpl writes this opcode and no further fields. */
	static final int OP_ACTION_FAILED = 0x1f;
	static final int OP_SERVER_CLOSE = 0x20;
	static final int OP_USER_INFO = 0x32;
	static final int OP_KEY_PACKET = 0x2e;
	static final int OP_LEAVE_WORLD = 0x84;

	// client to server
	static final int OP_LOGOUT = 0x00;
	static final int OP_NEW_CHARACTER = 0x0c;
	static final int OP_PROTOCOL_VERSION = 0x0e;
	static final int OP_ENTER_WORLD = 0x11;
	static final int OP_CHARACTER_SELECTED = 0x12;
	static final int OP_NEW_CHARACTER_INIT = 0x13;
	static final int OP_AUTH_LOGIN = 0x2b;

	/** Bytes the client appends to ProtocolVersion and the server skips when StrictFinal is set. */
	private static final int PROTOCOL_VERSION_PADDING = 260;
	private static final int AUTH_LOGIN_PADDING = 16;
	private static final int ENTER_WORLD_PADDING = 104;

	/** Size of one template in NewCharacterSuccess: 20 words. */
	private static final int TEMPLATE_SIZE = 20 * 4;

	private GamePackets()
	{
	}

	/** KeyPacket: whether the protocol revision is accepted and the first eight bytes of the key. */
	record KeyPacket(boolean protocolOk, byte[] key)
	{
	}

	/**
	 * The first entry of CharSelectionInfo is read for its name, the account, and the session id; the
	 * rest of the list is not parsed.
	 */
	record CharacterList(int count, String firstName, String firstAccount, int firstSessionId)
	{
	}

	/** One character template of NewCharacterSuccess. */
	record Template(int race, int classId, int str, int dex, int con, int intelligence, int wit, int men)
	{
	}

	/** 0x0e: the protocol revision, then 260 bytes the server skips in strict mode. Never encrypted. */
	static byte[] protocolVersion(int revision)
	{
		return new PacketWriter().writeC(OP_PROTOCOL_VERSION).writeD(revision).writeZeros(PROTOCOL_VERSION_PADDING)
				.toByteArray();
	}

	/**
	 * 0x2b: the account name, the two play-ok words in the order play 2, play 1, then the two login-ok
	 * words, then 16 bytes the server skips in strict mode.
	 */
	static byte[] authLogin(String account, int playOk1, int playOk2, int loginOk1, int loginOk2)
	{
		return new PacketWriter().writeC(OP_AUTH_LOGIN).writeS(account).writeD(playOk2).writeD(playOk1)
				.writeD(loginOk1).writeD(loginOk2).writeZeros(AUTH_LOGIN_PADDING).toByteArray();
	}

	/** 0x13: asks for the character templates. */
	static byte[] newCharacterInit()
	{
		return new PacketWriter().writeC(OP_NEW_CHARACTER_INIT).toByteArray();
	}

	/**
	 * 0x0c: the name, race, sex, class, the six base stats in the order INT, STR, CON, MEN, DEX, WIT,
	 * hair style, hair color, and face. The server takes the name, sex, class, and the three look values.
	 */
	static byte[] newCharacter(String name, Template template, int sex, int hairStyle, int hairColor, int face)
	{
		return new PacketWriter().writeC(OP_NEW_CHARACTER).writeS(name).writeD(template.race()).writeD(sex)
				.writeD(template.classId()).writeD(template.intelligence()).writeD(template.str())
				.writeD(template.con()).writeD(template.men()).writeD(template.dex()).writeD(template.wit())
				.writeD(hairStyle).writeD(hairColor).writeD(face).toByteArray();
	}

	/** 0x12: the slot, then a short and three words the server reads and ignores. */
	static byte[] characterSelected(int slot)
	{
		return new PacketWriter().writeC(OP_CHARACTER_SELECTED).writeD(slot).writeH(0).writeD(0).writeD(0).writeD(0)
				.toByteArray();
	}

	/** 0x11: 104 bytes the server skips. */
	static byte[] enterWorld()
	{
		return new PacketWriter().writeC(OP_ENTER_WORLD).writeZeros(ENTER_WORLD_PADDING).toByteArray();
	}

	/** 0x00. */
	static byte[] logout()
	{
		return new PacketWriter().writeC(OP_LOGOUT).toByteArray();
	}

	/** Reads KeyPacket after its opcode: the revision flag, eight key bytes, and more the client ignores. */
	static KeyPacket parseKeyPacket(PacketReader in) throws SmokeException
	{
		boolean ok = in.readC() == 1;
		byte[] key = in.readB(8);
		return new KeyPacket(ok, key);
	}

	/**
	 * Reads CharSelectionInfo after its opcode: the number of characters, a word with the value 7, a zero
	 * byte, then per character the name, the character id, the account name, and the session id.
	 */
	static CharacterList parseCharacterList(PacketReader in) throws SmokeException
	{
		int count = in.readD();
		in.skip(5);
		if (count <= 0)
			return new CharacterList(Math.max(count, 0), null, null, 0);

		String name = in.readS();
		in.skip(4);
		String account = in.readS();
		int session = in.readD();
		return new CharacterList(count, name, account, session);
	}

	/**
	 * Reads NewCharacterSuccess after its opcode: a count, then per template 20 words: race, class, and
	 * for each of STR, DEX, CON, INT, WIT, MEN the three words 0x46, value, 0x0a.
	 */
	static List<Template> parseTemplates(PacketReader in) throws SmokeException
	{
		int count = in.readD();
		List<Template> templates = new ArrayList<>();
		while (templates.size() < count && in.remaining() >= TEMPLATE_SIZE)
		{
			int race = in.readD();
			int classId = in.readD();
			int[] stats = new int[6];
			for (int i = 0; i < stats.length; i++)
			{
				in.skip(4);
				stats[i] = in.readD();
				in.skip(4);
			}
			templates.add(new Template(race, classId, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]));
		}
		return templates;
	}

	/** @return a text for the reason code of CharacterCreateFail */
	static String describeCreateFail(int reason)
	{
		return switch (reason)
		{
			case 0 -> "creation failed";
			case 1 -> "too many characters";
			case 2 -> "the name already exists";
			case 3 -> "the name is longer than 16 characters";
			case 4 -> "incorrect name";
			case 5 -> "creation is not allowed on this server";
			case 6 -> "choose another server";
			default -> "unknown reason";
		};
	}

	// The opcodes below are the first byte written by the server class, or the case of
	// L2ClientPacketHandlerFinal (PacketFinal is the server default). The comment strings on those
	// classes name older opcodes and are not the layout.

	/** StatusUpdate.writeImpl writes 0x18. */
	static final int OP_STATUS_UPDATE = 0x18;
	/** AbstractNpcInfo.NpcInfo.writeImpl writes 0x0c. The template id in the packet is the npc id plus 1000000. */
	static final int OP_NPC_INFO = 0x0c;
	/** ItemList.writeImpl writes 0x11. */
	static final int OP_ITEM_LIST = 0x11;
	/** InventoryUpdate.writeImpl writes 0x21. */
	static final int OP_INVENTORY_UPDATE = 0x21;
	/** DropItem.writeImpl writes 0x16. */
	static final int OP_DROP_ITEM = 0x16;
	/** SpawnItem.writeImpl writes 0x05. */
	static final int OP_SPAWN_ITEM = 0x05;
	/** Die.writeImpl writes 0x00. */
	static final int OP_DIE = 0x00;
	/** DeleteObject.writeImpl writes 0x08. */
	static final int OP_DELETE_OBJECT = 0x08;
	/** MoveToLocation.writeImpl writes 0x2f. */
	static final int OP_MOVE_TO_LOCATION = 0x2f;
	/** StopMove.writeImpl writes 0x47. */
	static final int OP_STOP_MOVE = 0x47;
	/** ValidateLocation.writeImpl writes 0x79. */
	static final int OP_VALIDATE_LOCATION = 0x79;
	/** SkillList.writeImpl writes 0x5f. */
	static final int OP_SKILL_LIST = 0x5f;
	/** AcquireSkillList.writeImpl writes 0x90. */
	static final int OP_ACQUIRE_SKILL_LIST = 0x90;
	/** BuyList.writeImpl writes 0x07. */
	static final int OP_BUY_LIST = 0x07;
	/** AbstractNpcHtmlMessage.writeImpl writes 0x19. */
	static final int OP_NPC_HTML = 0x19;

	/** L2ClientPacketHandlerFinal maps 0x0f to MoveBackwardToLocation. */
	static final int OP_MOVE_BACKWARD = 0x0f;
	/** L2ClientPacketHandlerFinal maps 0x1f to Action. */
	static final int OP_ACTION = 0x1f;
	/** L2ClientPacketHandlerFinal maps 0x32 to AttackRequest when PacketFinal is set. */
	static final int OP_ATTACK = 0x32;
	/** L2ClientPacketHandlerFinal maps 0x23 to RequestBypassToServer. */
	static final int OP_BYPASS = 0x23;
	/** L2ClientPacketHandlerFinal maps 0x40 to RequestBuyItem. */
	static final int OP_BUY = 0x40;
	/** L2ClientPacketHandlerFinal maps 0x19 to UseItem. */
	static final int OP_USE_ITEM = 0x19;
	/** L2ClientPacketHandlerFinal maps 0x50 to RequestSkillList. */
	static final int OP_REQUEST_SKILL_LIST = 0x50;
	/** L2ClientPacketHandlerFinal maps 0x7c to RequestAquireSkill. */
	static final int OP_ACQUIRE_SKILL = 0x7c;
	/** L2ClientPacketHandlerFinal maps 0x14 to RequestItemList. */
	static final int OP_REQUEST_ITEM_LIST = 0x14;

	/** Eight elemental shorts of writeElementalInfo when PacketFinal is set. */
	private static final int ELEMENTAL_BYTES = 16;

	/** One actor from NpcInfo: the template id has had 1000000 subtracted. */
	record NpcView(int objectId, int templateId, int x, int y, int z, boolean dead)
	{
	}

	/** The part of UserInfo the loop checks: place, level, experience, and SP. */
	record UserView(int objectId, String name, int x, int y, int z, int level, long exp, int sp, int currentHp, int maxHp)
	{
	}

	record StatusAttribute(int id, int value)
	{
	}

	record StatusView(int objectId, List<StatusAttribute> attributes)
	{
	}

	/** One row of ItemList or InventoryUpdate. Counts are the 8-byte final-protocol form. */
	record ItemView(int objectId, int itemId, long count, boolean equipped)
	{
	}

	/** InventoryUpdate change: 1 added, 2 modified, 3 removed. */
	record ItemChange(int change, ItemView item)
	{
	}

	record GroundItem(int objectId, int itemId, int x, int y, int z, long count, int ownerId)
	{
	}

	record SkillView(int id, int level)
	{
	}

	record ShopOffer(int itemId, long price)
	{
	}

	record ShopView(long money, int listId, List<ShopOffer> offers)
	{
	}

	record Dialog(int objectId, String html)
	{
	}

	record SkillOffer(int id, int level, int spCost)
	{
	}

	/** MoveBackwardToLocation.readImpl: destination, origin, then 1 for a mouse move. */
	static byte[] moveBackward(int x, int y, int z, int originX, int originY, int originZ)
	{
		return new PacketWriter().writeC(OP_MOVE_BACKWARD).writeD(x).writeD(y).writeD(z).writeD(originX)
				.writeD(originY).writeD(originZ).writeD(1).toByteArray();
	}

	/** Action.readImpl: the object id, the origin, and the shift flag. */
	static byte[] action(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(OP_ACTION).writeD(objectId).writeD(x).writeD(y).writeD(z).writeC(0)
				.toByteArray();
	}

	/** AttackRequest.readImpl: the object id, the origin, and the shift flag. */
	static byte[] attack(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(OP_ATTACK).writeD(objectId).writeD(x).writeD(y).writeD(z).writeC(0)
				.toByteArray();
	}

	/** RequestBypassToServer.readImpl: the command string, without the "bypass -h" prefix. */
	static byte[] bypass(String command)
	{
		return new PacketWriter().writeC(OP_BYPASS).writeS(command).toByteArray();
	}

	/**
	 * RequestBuyItem.readImpl in the final protocol: the list id, the number of rows, then each row as an
	 * item id and an 8-byte count.
	 */
	static byte[] buy(int listId, int itemId, long count)
	{
		return new PacketWriter().writeC(OP_BUY).writeD(listId).writeD(1).writeD(itemId).writeQ(count).toByteArray();
	}

	/** UseItem.readImpl: the item object id and a word the server ignores. */
	static byte[] useItem(int objectId)
	{
		return new PacketWriter().writeC(OP_USE_ITEM).writeD(objectId).writeD(0).toByteArray();
	}

	/** RequestSkillList has no body. */
	static byte[] requestSkillList()
	{
		return new PacketWriter().writeC(OP_REQUEST_SKILL_LIST).toByteArray();
	}

	/** RequestAquireSkill.readImpl: skill id, level, and the type. Type 0 is an ordinary skill. */
	static byte[] acquireSkill(int skillId, int level)
	{
		return new PacketWriter().writeC(OP_ACQUIRE_SKILL).writeD(skillId).writeD(level).writeD(0).toByteArray();
	}

	/** RequestItemList has no body. */
	static byte[] requestItemList()
	{
		return new PacketWriter().writeC(OP_REQUEST_ITEM_LIST).toByteArray();
	}

	/** UserInfo after the opcode, through SP. The rest of the packet is not needed. */
	static UserView parseUserInfo(PacketReader in) throws SmokeException
	{
		int x = in.readD();
		int y = in.readD();
		int z = in.readD();
		in.readD(); // vehicle id
		int objectId = in.readD();
		String name = in.readS();
		in.readD(); // race
		in.readD(); // sex
		in.readD(); // class
		int level = in.readD();
		long exp = in.readQ();
		in.skip(6 * 4); // the six base stats
		int maxHp = in.readD();
		int currentHp = in.readD();
		in.readD(); // max mp
		in.readD(); // current mp
		int sp = in.readD();
		return new UserView(objectId, name, x, y, z, level, exp, sp, currentHp, maxHp);
	}

	/** NpcInfo after the opcode, through the dead flag. The name and the title are not read. */
	static NpcView parseNpcInfo(PacketReader in) throws SmokeException
	{
		int objectId = in.readD();
		int templateId = in.readD() - 1_000_000;
		in.readD(); // attackable
		int x = in.readD();
		int y = in.readD();
		int z = in.readD();
		in.skip(4 + 4 + 4 + 4); // heading, zero, magic attack speed, physical attack speed
		in.skip(8 * 4); // eight movement speeds
		in.skip(4 * 8); // two speed multipliers and two collision sizes, each a double
		in.skip(3 * 4); // the three paperdoll item ids
		in.skip(3); // name-above, running, combat
		boolean dead = in.readC() == 1;
		return new NpcView(objectId, templateId, x, y, z, dead);
	}

	/** StatusUpdate after the opcode. */
	static StatusView parseStatusUpdate(PacketReader in) throws SmokeException
	{
		int objectId = in.readD();
		int count = in.readD();
		List<StatusAttribute> attributes = new ArrayList<>();
		for (int i = 0; i < count; i++)
			attributes.add(new StatusAttribute(in.readD(), in.readD()));
		return new StatusView(objectId, attributes);
	}

	/** ItemList after the opcode. The window flag is skipped. */
	static List<ItemView> parseItemList(PacketReader in) throws SmokeException
	{
		in.readH(); // show window
		int count = in.readH();
		List<ItemView> items = new ArrayList<>();
		for (int i = 0; i < count; i++)
			items.add(readItem(in));
		return items;
	}

	/** InventoryUpdate after the opcode. */
	static List<ItemChange> parseInventoryUpdate(PacketReader in) throws SmokeException
	{
		int count = in.readH();
		List<ItemChange> changes = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			int change = in.readH();
			changes.add(new ItemChange(change, readItem(in)));
		}
		return changes;
	}

	/** DropItem after the opcode. The first word is the creature that dropped the item. */
	static GroundItem parseDropItem(PacketReader in) throws SmokeException
	{
		int ownerId = in.readD();
		int objectId = in.readD();
		int itemId = in.readD();
		int x = in.readD();
		int y = in.readD();
		int z = in.readD();
		in.readD(); // stackable
		long count = in.readQ();
		return new GroundItem(objectId, itemId, x, y, z, count, ownerId);
	}

	/** SpawnItem after the opcode. It has no owner. */
	static GroundItem parseSpawnItem(PacketReader in) throws SmokeException
	{
		int objectId = in.readD();
		int itemId = in.readD();
		int x = in.readD();
		int y = in.readD();
		int z = in.readD();
		in.readD(); // stackable
		long count = in.readQ();
		return new GroundItem(objectId, itemId, x, y, z, count, 0);
	}

	/** Die after the opcode: the object that died. */
	static int parseDie(PacketReader in) throws SmokeException
	{
		return in.readD();
	}

	/** DeleteObject after the opcode. */
	static int parseDeleteObject(PacketReader in) throws SmokeException
	{
		return in.readD();
	}

	/** MoveToLocation after the opcode: who moved, where to, and where from. */
	static int[] parseMoveToLocation(PacketReader in) throws SmokeException
	{
		return new int[] { in.readD(), in.readD(), in.readD(), in.readD(), in.readD(), in.readD(), in.readD() };
	}

	/** StopMove after the opcode: who stopped, and where. */
	static int[] parseStopMove(PacketReader in) throws SmokeException
	{
		return new int[] { in.readD(), in.readD(), in.readD(), in.readD() };
	}

	/** ValidateLocation after the opcode: who, and where the server says they are. */
	static int[] parseValidateLocation(PacketReader in) throws SmokeException
	{
		return new int[] { in.readD(), in.readD(), in.readD(), in.readD() };
	}

	/** SkillList after the opcode. */
	static List<SkillView> parseSkillList(PacketReader in) throws SmokeException
	{
		int count = in.readD();
		List<SkillView> skills = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			in.readD(); // passive
			int level = in.readD();
			int id = in.readD();
			in.readC();
			skills.add(new SkillView(id, level));
		}
		return skills;
	}

	/** AcquireSkillList after the opcode. */
	static List<SkillOffer> parseAcquireSkillList(PacketReader in) throws SmokeException
	{
		in.readD(); // skill type
		int count = in.readD();
		List<SkillOffer> offers = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			int id = in.readD();
			int level = in.readD();
			in.readD(); // max level
			int spCost = in.readD();
			in.readD(); // requirements
			offers.add(new SkillOffer(id, level, spCost));
		}
		return offers;
	}

	/** BuyList after the opcode. */
	static ShopView parseBuyList(PacketReader in) throws SmokeException
	{
		long money = in.readQ();
		int listId = in.readD();
		int count = in.readH();
		List<ShopOffer> offers = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			in.readH(); // type 1
			in.readD(); // object id
			int itemId = in.readD();
			in.readQ(); // count
			in.readH(); // type 2
			in.readH(); // custom type
			in.readD(); // body part
			in.readH(); // enchant
			in.readH(); // custom type 2
			in.readH(); // zero
			long price = in.readQ();
			in.skip(ELEMENTAL_BYTES);
			offers.add(new ShopOffer(itemId, price));
		}
		return new ShopView(money, listId, offers);
	}

	/** NpcHtmlMessage after the opcode. */
	static Dialog parseNpcHtml(PacketReader in) throws SmokeException
	{
		int objectId = in.readD();
		String html = in.readS();
		return new Dialog(objectId, html);
	}

	/** One ItemList row, from the type-1 short through the remaining-time word. */
	private static ItemView readItem(PacketReader in) throws SmokeException
	{
		in.readH(); // type 1
		int objectId = in.readD();
		int itemId = in.readD();
		in.readD(); // slot
		long count = in.readQ();
		in.readH(); // type 2
		in.readH(); // custom type
		boolean equipped = in.readH() == 1;
		in.readD(); // body part
		in.readH(); // enchant
		in.readH(); // custom type 2
		in.readD(); // augmentation
		in.readD(); // mana
		in.skip(ELEMENTAL_BYTES);
		in.readD(); // remaining time
		return new ItemView(objectId, itemId, count, equipped);
	}
}
