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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * One client plays the authored world's loop: leave the peace zone, fight, pick up, buy, drink,
 * learn, leave, and enter again. Actors and items are chosen by template id. Display names are
 * never read.
 */
final class SeedLoop
{
	private static final int FIGHTER_CLASS_ID = 0;
	private static final int BEAST = 18003;
	private static final int TRAINER = 30001;
	private static final int MERCHANT = 30002;
	private static final int COIN = 57;
	private static final int POTION = 1060;
	private static final int SKILL = 257;
	private static final int SKILL_LEVEL = 1;
	private static final int SHOP = 731;
	private static final int COIN_PRICE = 12;
	private static final int SP_COST = 12;
	private static final int COINS_NEEDED = 12;
	private static final int[] STARTER = { 1, 23, 28, 39 };

	/** East edge of peace zone 731. Beasts of template 18003 stand east of it. */
	private static final int PEACE_EAST = -70837;
	private static final int MERCHANT_X = -71057;
	private static final int MERCHANT_Y = 258243;
	private static final int TRAINER_X = -71057;
	private static final int TRAINER_Y = 257883;
	private static final int GROUND_Z = -3104;

	private static final int RUN_SPEED = 128;
	private static final int STEP = 700;
	private static final int MELEE = 80;
	private static final int INTERACT = 120;
	private static final long ATTACK_PAUSE_MILLIS = 1500L;
	private static final long KILL_LIMIT_MILLIS = 60000L;
	private static final int ANNOUNCED_CONNECT_MILLIS = 3000;
	private static final int LOGOUT_ATTEMPTS = 3;
	private static final long LOGOUT_WAIT_MILLIS = 5000L;
	private static final long CLOSE_WAIT_MILLIS = 5000L;
	private static final int ALREADY_IN_USE = 7;
	private static final int RELOGIN_ATTEMPTS = 10;

	/**
	 * Spawn coordinates of template 18003, ordered by x then y. Used only to walk toward a beast that
	 * is not in the known list yet. The target itself is chosen from NpcInfo.
	 */
	private static final int[][] BEAST_SPAWNS = beastSpawns();

	private SeedLoop()
	{
	}

	static void play(SmokeClient.Config config, Deadline deadline, Reporter reporter, String account, String password,
			String characterName, int sessionIndex) throws SmokeException
	{
		LoginFlow.Session session = LoginFlow.selectWorld(config, deadline, reporter, account, password);
		View view = new View(sessionIndex);
		try (GameConnection game = connect(config, deadline, reporter, session.world()))
		{
			enter(config, deadline, reporter, game, session, account, characterName, true);
			absorb(game, view, deadline, 3000L);
			waitForSelf(game, view, deadline);
			askItems(game, view, deadline);
			checkStarter(view);
			fight(game, view, deadline, reporter, true);
			buyPotion(game, view, deadline, reporter);
			drink(game, view, deadline, reporter);
			fight(game, view, deadline, reporter, false);
			learn(game, view, deadline, reporter);
			String before = view.snapshot();
			reporter.info(tag(view) + " snapshot-before " + before);
			reporter.info(tag(view) + " kills=" + view.kills + " longest-target-wait-ms=" + view.longestWaitMillis);
			logout(game, deadline);
		}

		LoginFlow.Session again = selectWorldAgain(config, deadline, reporter, account, password);
		View after = new View(sessionIndex);
		try (GameConnection game = connect(config, deadline, reporter, again.world()))
		{
			enter(config, deadline, reporter, game, again, account, characterName, false);
			absorb(game, after, deadline, 3000L);
			waitForSelf(game, after, deadline);
			askItems(game, after, deadline);
			askSkills(game, after, deadline);
			String seen = after.snapshot();
			reporter.info(tag(view) + " snapshot-after " + seen);
			if (!view.snapshot().equals(seen))
			{
				throw mismatch("re-enter", view.snapshot(), seen);
			}
			logout(game, deadline);
		}
	}

	private static void enter(SmokeClient.Config config, Deadline deadline, Reporter reporter, GameConnection game,
			LoginFlow.Session session, String account, String characterName, boolean create) throws SmokeException
	{
		reporter.begin("world: protocol version");
		game.send(GamePackets.protocolVersion(config.protocolRevision()));
		GamePackets.KeyPacket keyPacket = GamePackets.parseKeyPacket(game.expect(GamePackets.OP_KEY_PACKET, "KeyPacket"));
		if (!keyPacket.protocolOk())
			throw new SmokeException("the world refused the protocol revision " + config.protocolRevision());

		game.cipher().setKey(GameCipher.fullKey(keyPacket.key()));
		game.cipher().enable();
		reporter.ok("revision " + config.protocolRevision() + " accepted, game cipher on");

		reporter.begin("world: authentication");
		game.send(GamePackets.authLogin(account, session.playOk1(), session.playOk2(), session.loginOk1(),
				session.loginOk2()));
		GamePackets.CharacterList list = GamePackets.parseCharacterList(game.expect(GamePackets.OP_CHAR_SELECTION_INFO,
				"CharSelectionInfo"));
		if (create)
		{
			if (list.count() != 0)
				throw new SmokeException("a new account already has " + list.count() + " characters");

			reporter.begin("world: character templates");
			game.send(GamePackets.newCharacterInit());
			List<GamePackets.Template> templates = GamePackets.parseTemplates(game.expect(
					GamePackets.OP_NEW_CHARACTER_SUCCESS, "NewCharacterSuccess"));
			GamePackets.Template fighter = null;
			for (GamePackets.Template template : templates)
			{
				if (template.classId() == FIGHTER_CLASS_ID)
				{
					fighter = template;
					break;
				}
			}
			if (fighter == null)
				throw new SmokeException("the world offers no template for the class " + FIGHTER_CLASS_ID);

			reporter.ok(templates.size() + " templates");
			reporter.begin("world: character creation");
			game.send(GamePackets.newCharacter(characterName, fighter, 0, 0, 0, 0));
			game.expect(GamePackets.OP_CHARACTER_CREATE_SUCCESS, "CharacterCreateSuccess");
			list = GamePackets.parseCharacterList(game.expect(GamePackets.OP_CHAR_SELECTION_INFO, "CharSelectionInfo"));
			if (list.count() != 1)
				throw new SmokeException("the character list has " + list.count() + " characters after creation");

			reporter.ok("character created");
		}
		else if (list.count() != 1)
		{
			throw mismatch("re-enter character list", 1, list.count());
		}

		reporter.begin("world: character selection");
		game.send(GamePackets.characterSelected(0));
		game.expect(GamePackets.OP_CHAR_SELECTED, "CharSelected");
		reporter.ok("slot 0 selected");

		reporter.begin("world: enter world");
		game.send(GamePackets.enterWorld());
		reporter.ok(create ? "entered" : "entered again");
	}

	private static void checkStarter(View view) throws SmokeException
	{
		if (view.level != 1)
			throw mismatch("enter level", 1, view.level);
		if (view.exp != 0L)
			throw mismatch("enter exp", 0, view.exp);
		if (view.sp != 0)
			throw mismatch("enter sp", 0, view.sp);
		if (view.count(COIN) != 0L)
			throw mismatch("enter coins", 0, view.count(COIN));
		if (view.count(POTION) != 0L)
			throw mismatch("enter potions", 0, view.count(POTION));

		List<Integer> equipped = view.equippedIds();
		List<Integer> expected = List.of(STARTER[0], STARTER[1], STARTER[2], STARTER[3]);
		if (!equipped.equals(expected))
			throw mismatch("enter equipped", expected, equipped);
		if (view.items.size() != expected.size())
			throw mismatch("enter inventory size", expected.size(), view.items.size());
	}

	/** Fights template 18003 until the coins are enough, or until level 2 and the SP for the skill. */
	private static void fight(GameConnection game, View view, Deadline deadline, Reporter reporter, boolean forCoins)
			throws SmokeException
	{
		int guard = 0;
		while (forCoins ? view.count(COIN) < COINS_NEEDED : view.level < 2 || view.sp < SP_COST)
		{
			if (guard++ > 40)
			{
				throw new SmokeException("step " + (forCoins ? "coins" : "level") + ": expected "
						+ (forCoins ? COINS_NEEDED + " coins" : "level 2 and " + SP_COST + " SP") + " actual coins "
						+ view.count(COIN) + " level " + view.level + " sp " + view.sp + " after " + view.kills
						+ " kills");
			}
			Actor target = takeTarget(game, view, deadline, reporter);
			attack(game, view, deadline, target);
			pickup(game, view, deadline, target.objectId);
		}
	}

	/**
	 * The first target of session i is the i-th visible beast of template 18003, ordered by coordinates.
	 * Later targets stay on that same ordering so the sessions do not all take the nearest one.
	 */
	private static Actor takeTarget(GameConnection game, View view, Deadline deadline, Reporter reporter)
			throws SmokeException
	{
		long started = System.nanoTime();
		boolean first = view.kills == 0;
		while (true)
		{
			if (deadline.expired())
				throw new SmokeException("step target: the time limit ended while waiting for template " + BEAST);

			List<Actor> alive = view.aliveBeasts();
			int index = first ? view.sessionIndex : -1;
			if (!first && !alive.isEmpty())
				index = (view.sessionIndex + view.kills) % alive.size();

			if (index >= 0 && alive.size() > index)
			{
				view.noteWait(started);
				Actor chosen = alive.get(index);
				if (first && !view.firstAnnounced)
				{
					view.firstAnnounced = true;
					reporter.info(tag(view) + " first-target objectId=" + chosen.objectId);
				}
				return chosen;
			}
			int spawn = Math.min(first ? view.sessionIndex : view.sessionIndex + view.kills, BEAST_SPAWNS.length - 1);
			stepToward(game, view, deadline, BEAST_SPAWNS[spawn][0], BEAST_SPAWNS[spawn][1], GROUND_Z);
		}
	}

	private static void attack(GameConnection game, View view, Deadline deadline, Actor target) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(KILL_LIMIT_MILLIS);
		while (System.nanoTime() < until)
		{
			if (deadline.expired())
				throw new SmokeException("step attack: the time limit ended");

			Actor seen = view.npcs.get(target.objectId);
			if (seen == null || seen.dead)
			{
				view.kills++;
				return;
			}
			if (view.x <= PEACE_EAST)
			{
				stepToward(game, view, deadline, PEACE_EAST + 200, view.y, view.z);
				continue;
			}
			if (distance(view.x, view.y, seen.x, seen.y) > MELEE)
			{
				stepToward(game, view, deadline, seen.x, seen.y, seen.z);
				continue;
			}
			game.send(GamePackets.action(seen.objectId, view.x, view.y, view.z));
			game.send(GamePackets.attack(seen.objectId, view.x, view.y, view.z));
			absorb(game, view, deadline, ATTACK_PAUSE_MILLIS);
		}
		Actor seen = view.npcs.get(target.objectId);
		throw new SmokeException("step attack: template " + BEAST + " object " + target.objectId
				+ " did not die; still " + (seen == null ? "gone" : "alive at " + seen.x + "," + seen.y));
	}

	private static void pickup(GameConnection game, View view, Deadline deadline, int dropperId) throws SmokeException
	{
		absorb(game, view, deadline, 1500L);
		List<GroundItem> mine = new ArrayList<>();
		for (GroundItem drop : view.drops.values())
		{
			// DropItem carries the attacker that dropped the item, which for a reward is the beast.
			if (drop.ownerId == dropperId && (drop.itemId == COIN || drop.itemId == POTION))
				mine.add(drop);
		}
		for (GroundItem drop : mine)
		{
			long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (view.drops.containsKey(drop.objectId) && System.nanoTime() < limit)
			{
				if (deadline.expired())
					throw new SmokeException("step pickup: the time limit ended");

				if (distance(view.x, view.y, drop.x, drop.y) > INTERACT)
				{
					stepToward(game, view, deadline, drop.x, drop.y, drop.z);
					continue;
				}
				game.send(GamePackets.action(drop.objectId, view.x, view.y, view.z));
				game.send(GamePackets.action(drop.objectId, view.x, view.y, view.z));
				absorb(game, view, deadline, 800L);
			}
		}
		askItems(game, view, deadline);
	}

	private static void buyPotion(GameConnection game, View view, Deadline deadline, Reporter reporter)
			throws SmokeException
	{
		long beforeCoins = view.count(COIN);
		long beforePotions = view.count(POTION);
		Actor merchant = approachNpc(game, view, deadline, MERCHANT, MERCHANT_X, MERCHANT_Y);
		String command = "npc_" + merchant.objectId + "_Buy " + SHOP;
		talk(game, view, deadline, merchant, "Buy " + SHOP);
		game.send(GamePackets.bypass(command));
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (view.shopListId != SHOP && System.nanoTime() < until)
		{
			if (!poll(game, view, deadline, 300L))
				continue;
		}
		if (view.shopListId != SHOP)
			throw new SmokeException("step buy: the shop list " + SHOP + " did not open");

		game.send(GamePackets.buy(SHOP, POTION, 1));
		askItems(game, view, deadline);
		if (view.count(COIN) != beforeCoins - COIN_PRICE)
			throw mismatch("buy coins", beforeCoins - COIN_PRICE, view.count(COIN));
		if (view.count(POTION) != beforePotions + 1)
			throw mismatch("buy potions", beforePotions + 1, view.count(POTION));

		view.coinsBeforeBuy = beforeCoins;
		view.coinsAfterBuy = view.count(COIN);
		reporter.info(tag(view) + " coins before-buy=" + view.coinsBeforeBuy + " after-buy=" + view.coinsAfterBuy);
	}

	private static void drink(GameConnection game, View view, Deadline deadline, Reporter reporter) throws SmokeException
	{
		long before = view.count(POTION);
		Integer objectId = view.objectIdOf(POTION);
		if (objectId == null)
			throw new SmokeException("step drink: expected a potion in the inventory, actual none");

		game.send(GamePackets.useItem(objectId));
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
		while (view.count(POTION) == before && System.nanoTime() < until)
			poll(game, view, deadline, 300L);

		askItems(game, view, deadline);
		if (view.count(POTION) != before - 1)
			throw mismatch("drink potions", before - 1, view.count(POTION));

		view.potionsBeforeDrink = before;
		view.potionsAfterDrink = view.count(POTION);
		reporter.info(tag(view) + " potions before-drink=" + view.potionsBeforeDrink + " after-drink="
				+ view.potionsAfterDrink);
	}

	private static void learn(GameConnection game, View view, Deadline deadline, Reporter reporter)
			throws SmokeException
	{
		int before = view.sp;
		Actor trainer = approachNpc(game, view, deadline, TRAINER, TRAINER_X, TRAINER_Y);
		talk(game, view, deadline, trainer, "SkillList");
		game.send(GamePackets.bypass("npc_" + trainer.objectId + "_SkillList"));
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!view.skillOffered && System.nanoTime() < until)
			poll(game, view, deadline, 300L);

		if (!view.skillOffered)
			throw new SmokeException("step learn: the trainer did not offer skill " + SKILL);

		game.send(GamePackets.acquireSkill(SKILL, SKILL_LEVEL));
		until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while ((view.skillLevel(SKILL) != SKILL_LEVEL || view.sp == before) && System.nanoTime() < until)
			poll(game, view, deadline, 300L);

		askSkills(game, view, deadline);
		if (view.skillLevel(SKILL) != SKILL_LEVEL)
			throw mismatch("learn skill " + SKILL, SKILL_LEVEL, view.skillLevel(SKILL));
		if (view.sp != before - SP_COST)
			throw mismatch("learn sp", before - SP_COST, view.sp);

		view.spBeforeLearn = before;
		view.spAfterLearn = view.sp;
		reporter.info(tag(view) + " sp before-learn=" + view.spBeforeLearn + " after-learn=" + view.spAfterLearn);
	}

	private static Actor approachNpc(GameConnection game, View view, Deadline deadline, int template, int x, int y)
			throws SmokeException
	{
		long started = System.nanoTime();
		while (true)
		{
			if (deadline.expired())
				throw new SmokeException("step approach " + template + ": the time limit ended");

			Actor npc = view.nearest(template);
			int tx = npc == null ? x : npc.x;
			int ty = npc == null ? y : npc.y;
			if (npc != null && distance(view.x, view.y, npc.x, npc.y) <= INTERACT)
			{
				view.noteWait(started);
				return npc;
			}
			stepToward(game, view, deadline, tx, ty, GROUND_Z);
		}
	}

	/** Opens the dialog. The bypass is sent only after the html that authorises it has arrived. */
	private static void talk(GameConnection game, View view, Deadline deadline, Actor npc, String command)
			throws SmokeException
	{
		view.dialog = null;
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while ((view.dialog == null || !view.dialog.contains(command)) && System.nanoTime() < until)
		{
			if (deadline.expired())
				throw new SmokeException("step talk: the time limit ended");

			game.send(GamePackets.action(npc.objectId, view.x, view.y, view.z));
			game.send(GamePackets.action(npc.objectId, view.x, view.y, view.z));
			absorb(game, view, deadline, 1000L);
		}
		if (view.dialog == null || !view.dialog.contains(command))
			throw new SmokeException("step talk: template " + npc.templateId + " did not open a dialog containing "
					+ command);
	}

	private static void stepToward(GameConnection game, View view, Deadline deadline, int x, int y, int z)
			throws SmokeException
	{
		double dx = x - view.x;
		double dy = y - view.y;
		double len = Math.hypot(dx, dy);
		int destX = x;
		int destY = y;
		if (len > STEP)
		{
			destX = view.x + (int)Math.round(dx / len * STEP);
			destY = view.y + (int)Math.round(dy / len * STEP);
		}
		view.acceptedMove = false;
		game.send(GamePackets.moveBackward(destX, destY, z, view.x, view.y, view.z));
		// The server tells the client the destination and then expects the client to walk there. A move
		// that finishes on its own does not send StopMove, so the position is taken from that destination
		// once the travel time has passed.
		long travel = (long)(Math.min(len, STEP) / RUN_SPEED * 1000L) + 1500L;
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(travel);
		while (System.nanoTime() < until)
		{
			if (deadline.expired())
				throw new SmokeException("step move: the time limit ended");

			poll(game, view, deadline, 200L);
			if (distance(view.x, view.y, destX, destY) <= MELEE)
				return;
		}
		if (view.acceptedMove && distance(view.x, view.y, view.acceptedX, view.acceptedY) > MELEE)
		{
			view.x = view.acceptedX;
			view.y = view.acceptedY;
			view.z = view.acceptedZ;
		}
	}

	private static void waitForSelf(GameConnection game, View view, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (view.objectId == 0 && System.nanoTime() < until)
			poll(game, view, deadline, 300L);

		if (view.objectId == 0)
			throw new SmokeException("step enter: UserInfo did not arrive");
	}

	/**
	 * Asks for the inventory again until an ItemList arrives. Opening a shop disables the inventory for a
	 * short time, and a list sent by the purchase itself can satisfy one request, so a single request can
	 * be ignored. Later requests are sent until the disable has ended.
	 */
	private static void askItems(GameConnection game, View view, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
		while (System.nanoTime() < until)
		{
			if (deadline.expired())
				throw new SmokeException("step inventory: the time limit ended");

			int epoch = view.itemEpoch;
			game.send(GamePackets.requestItemList());
			long slice = Math.min(until, System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
			while (view.itemEpoch == epoch && System.nanoTime() < slice)
				poll(game, view, deadline, 300L);

			if (view.itemEpoch != epoch)
				return;
		}
		throw new SmokeException("step inventory: ItemList did not arrive");
	}

	private static void askSkills(GameConnection game, View view, Deadline deadline) throws SmokeException
	{
		int epoch = view.skillEpoch;
		game.send(GamePackets.requestSkillList());
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
		while (view.skillEpoch == epoch && System.nanoTime() < until)
			poll(game, view, deadline, 300L);

		if (view.skillEpoch == epoch)
			throw new SmokeException("step skills: SkillList did not arrive");
	}

	private static void absorb(GameConnection game, View view, Deadline deadline, long millis) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		while (System.nanoTime() < until)
		{
			if (deadline.expired())
				throw new SmokeException("the time limit of " + deadline.seconds() + " seconds ended");

			long slice = Math.min(200L, TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime()));
			if (slice <= 0L)
				return;

			poll(game, view, deadline, slice);
		}
	}

	/** @return false when the wait ended with no packet */
	private static boolean poll(GameConnection game, View view, Deadline deadline, long millis) throws SmokeException
	{
		try
		{
			apply(view, game.receive(millis));
			return true;
		}
		catch (SmokeException e)
		{
			if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
				return false;

			throw e;
		}
	}

	private static void apply(View view, byte[] payload) throws SmokeException
	{
		PacketReader in = new PacketReader(payload);
		int opcode = in.readC();
		try
		{
			applyOpcode(view, in, opcode);
		}
		catch (SmokeException e)
		{
			// Some server packets share a layout only up to a point, or are shorter than the full
			// record. Missing the tail is not a failed step; the next full packet refreshes the view.
			if (e.getMessage() != null && e.getMessage().startsWith("the packet is too short"))
				return;

			throw e;
		}
	}

	private static void applyOpcode(View view, PacketReader in, int opcode) throws SmokeException
	{
		switch (opcode)
		{
			case GamePackets.OP_USER_INFO ->
			{
				GamePackets.UserView user = GamePackets.parseUserInfo(in);
				view.objectId = user.objectId();
				view.x = user.x();
				view.y = user.y();
				view.z = user.z();
				view.level = user.level();
				view.exp = user.exp();
				view.sp = user.sp();
				view.hp = user.currentHp();
				view.maxHp = user.maxHp();
			}
			case GamePackets.OP_NPC_INFO ->
			{
				GamePackets.NpcView npc = GamePackets.parseNpcInfo(in);
				if (npc.templateId() == BEAST || npc.templateId() == MERCHANT || npc.templateId() == TRAINER)
				{
					view.npcs.put(npc.objectId(), new Actor(npc.objectId(), npc.templateId(), npc.x(), npc.y(), npc.z(),
							npc.dead()));
				}
			}
			case GamePackets.OP_STATUS_UPDATE ->
			{
				GamePackets.StatusView status = GamePackets.parseStatusUpdate(in);
				if (status.objectId() == view.objectId)
				{
					for (GamePackets.StatusAttribute attribute : status.attributes())
					{
						if (attribute.id() == 0x01)
							view.level = attribute.value();
						else if (attribute.id() == 0x02)
							view.exp = attribute.value();
						else if (attribute.id() == 0x0d)
							view.sp = attribute.value();
						else if (attribute.id() == 0x09)
							view.hp = attribute.value();
					}
				}
				else
				{
					Actor npc = view.npcs.get(status.objectId());
					if (npc != null)
					{
						for (GamePackets.StatusAttribute attribute : status.attributes())
						{
							if (attribute.id() == 0x09 && attribute.value() <= 0)
								npc.dead = true;
						}
					}
				}
			}
			case GamePackets.OP_ITEM_LIST ->
			{
				view.replaceItems(GamePackets.parseItemList(in));
			}
			case GamePackets.OP_INVENTORY_UPDATE ->
			{
				for (GamePackets.ItemChange change : GamePackets.parseInventoryUpdate(in))
					view.applyChange(change);
			}
			case GamePackets.OP_DROP_ITEM ->
			{
				GamePackets.GroundItem drop = GamePackets.parseDropItem(in);
				view.drops.put(drop.objectId(), new GroundItem(drop.objectId(), drop.itemId(), drop.x(), drop.y(),
						drop.z(), drop.ownerId()));
			}
			case GamePackets.OP_SPAWN_ITEM ->
			{
				GamePackets.GroundItem drop = GamePackets.parseSpawnItem(in);
				if (!view.drops.containsKey(drop.objectId()))
				{
					view.drops.put(drop.objectId(), new GroundItem(drop.objectId(), drop.itemId(), drop.x(), drop.y(),
							drop.z(), 0));
				}
			}
			case GamePackets.OP_DIE ->
			{
				int objectId = GamePackets.parseDie(in);
				if (objectId == view.objectId)
					throw new SmokeException("step attack: the character died");

				Actor npc = view.npcs.get(objectId);
				if (npc != null)
					npc.dead = true;
			}
			case GamePackets.OP_DELETE_OBJECT ->
			{
				int objectId = GamePackets.parseDeleteObject(in);
				Actor npc = view.npcs.remove(objectId);
				if (npc != null && npc.templateId == BEAST)
					view.npcs.put(objectId, new Actor(objectId, BEAST, npc.x, npc.y, npc.z, true));

				view.drops.remove(objectId);
			}
			case GamePackets.OP_MOVE_TO_LOCATION ->
			{
				int[] move = GamePackets.parseMoveToLocation(in);
				if (move[0] == view.objectId)
				{
					view.acceptedMove = true;
					view.acceptedX = move[1];
					view.acceptedY = move[2];
					view.acceptedZ = move[3];
					view.x = move[4];
					view.y = move[5];
					view.z = move[6];
				}
				else
				{
					Actor npc = view.npcs.get(move[0]);
					if (npc != null)
					{
						npc.x = move[1];
						npc.y = move[2];
						npc.z = move[3];
					}
				}
			}
			case GamePackets.OP_STOP_MOVE, GamePackets.OP_VALIDATE_LOCATION ->
			{
				int[] place = opcode == GamePackets.OP_STOP_MOVE ? GamePackets.parseStopMove(in)
						: GamePackets.parseValidateLocation(in);
				if (place[0] == view.objectId)
				{
					view.x = place[1];
					view.y = place[2];
					view.z = place[3];
				}
				else
				{
					Actor npc = view.npcs.get(place[0]);
					if (npc != null)
					{
						npc.x = place[1];
						npc.y = place[2];
						npc.z = place[3];
					}
				}
			}
			case GamePackets.OP_SKILL_LIST ->
			{
				view.skills.clear();
				for (GamePackets.SkillView skill : GamePackets.parseSkillList(in))
					view.skills.put(skill.id(), skill.level());

				view.skillEpoch++;
			}
			case GamePackets.OP_ACQUIRE_SKILL_LIST ->
			{
				view.skillOffered = false;
				for (GamePackets.SkillOffer offer : GamePackets.parseAcquireSkillList(in))
				{
					if (offer.id() == SKILL && offer.level() == SKILL_LEVEL)
						view.skillOffered = true;
				}
			}
			case GamePackets.OP_BUY_LIST ->
			{
				GamePackets.ShopView shop = GamePackets.parseBuyList(in);
				view.shopListId = shop.listId();
			}
			case GamePackets.OP_NPC_HTML ->
			{
				view.dialog = GamePackets.parseNpcHtml(in).html();
			}
			case GamePackets.OP_SERVER_CLOSE -> throw new SmokeException("the world closed the session");
			default ->
			{
				// The cipher has already consumed the packet. Nothing else in it is part of the loop.
			}
		}
	}

	private static LoginFlow.Session selectWorldAgain(SmokeClient.Config config, Deadline deadline, Reporter reporter,
			String account, String password) throws SmokeException
	{
		reporter.begin("login: second login after the logout");
		SmokeException last = null;
		for (int attempt = 1; attempt <= RELOGIN_ATTEMPTS && !deadline.expired(); attempt++)
		{
			try
			{
				LoginFlow.Session session = LoginFlow.selectWorld(config, deadline, reporter, account, password);
				reporter.ok(attempt == 1 ? "the account was free at once" : "the account was free after " + attempt
						+ " attempts");
				return session;
			}
			catch (SmokeException e)
			{
				boolean retry = e.kind() == SmokeException.Kind.TIMEOUT || e.kind() == SmokeException.Kind.CLOSED
						|| (e.kind() == SmokeException.Kind.REFUSED && e.reason() == ALREADY_IN_USE);
				if (!retry || deadline.expired())
					throw e;

				last = e;
				reporter.info("attempt " + attempt + " of " + RELOGIN_ATTEMPTS + ": " + e.getMessage()
						+ "; trying again");
				deadline.sleep(1500L);
			}
		}
		if (last == null)
			throw new SmokeException("the time limit ended before the second login");

		throw new SmokeException("the account was still in use after " + RELOGIN_ATTEMPTS + " attempts ("
				+ last.getMessage() + ")");
	}

	private static GameConnection connect(SmokeClient.Config config, Deadline deadline, Reporter reporter,
			LoginPackets.WorldEntry world) throws SmokeException
	{
		reporter.begin("world: connect");
		String announced = world.host();
		boolean usable = !announced.isEmpty() && !"0.0.0.0".equals(announced);
		if (usable && !announced.equals(config.host()))
		{
			try
			{
				FramedConnection connection = FramedConnection.open("world", announced, config.worldPort(), deadline,
						false, ANNOUNCED_CONNECT_MILLIS);
				reporter.ok("connected to the announced address " + announced + ":" + config.worldPort());
				return new GameConnection(connection);
			}
			catch (SmokeException e)
			{
				if (deadline.expired())
					throw e;
			}
		}
		FramedConnection connection = FramedConnection.open("world", config.host(), config.worldPort(), deadline);
		reporter.ok("connected to " + config.host() + ":" + config.worldPort());
		return new GameConnection(connection);
	}

	private static void logout(GameConnection game, Deadline deadline) throws SmokeException
	{
		boolean left = false;
		for (int attempt = 1; attempt <= LOGOUT_ATTEMPTS && !left; attempt++)
		{
			game.send(GamePackets.logout());
			left = awaitLeave(game, deadline);
		}
		if (!left)
			throw new SmokeException("the world did not answer Logout with LeaveWorld");

		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_WAIT_MILLIS);
		while (true)
		{
			long leftMillis = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (leftMillis <= 0L)
				throw new SmokeException("the world did not close the connection after LeaveWorld");

			try
			{
				game.receive(leftMillis);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.CLOSED)
					return;

				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					throw new SmokeException("the world did not close the connection after LeaveWorld");

				throw e;
			}
		}
	}

	private static boolean awaitLeave(GameConnection game, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOGOUT_WAIT_MILLIS);
		while (true)
		{
			long left = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (left <= 0L)
				return false;

			byte[] payload;
			try
			{
				payload = game.receive(left);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					return false;

				throw e;
			}
			if ((payload[0] & 0xff) == GamePackets.OP_LEAVE_WORLD)
				return true;
		}
	}

	private static SmokeException mismatch(String step, Object expected, Object actual)
	{
		return new SmokeException("step " + step + ": expected " + expected + " actual " + actual);
	}

	private static long distance(int x1, int y1, int x2, int y2)
	{
		long dx = (long)x1 - x2;
		long dy = (long)y1 - y2;
		return (long)Math.hypot(dx, dy);
	}

	private static String tag(View view)
	{
		return "seed-loop session " + view.sessionIndex;
	}

	private static int[][] beastSpawns()
	{
		int[] xs = { -70357, -70117, -69877, -69637, -69397, -69157 };
		int[] ys = { 256863, 257103, 257343, 257583, 257823, 258063, 258303, 258543 };
		int[][] spawns = new int[xs.length * ys.length][2];
		int n = 0;
		for (int x : xs)
		{
			for (int y : ys)
				spawns[n++] = new int[] { x, y };
		}
		return spawns;
	}

	private static final class Actor
	{
		private final int objectId;
		private final int templateId;
		private int x;
		private int y;
		private int z;
		private boolean dead;

		private Actor(int objectId, int templateId, int x, int y, int z, boolean dead)
		{
			this.objectId = objectId;
			this.templateId = templateId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dead = dead;
		}
	}

	private static final class GroundItem
	{
		private final int objectId;
		private final int itemId;
		private final int x;
		private final int y;
		private final int z;
		private final int ownerId;

		private GroundItem(int objectId, int itemId, int x, int y, int z, int ownerId)
		{
			this.objectId = objectId;
			this.itemId = itemId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.ownerId = ownerId;
		}
	}

	private static final class Held
	{
		private final int objectId;
		private final int itemId;
		private long count;
		private boolean equipped;

		private Held(int objectId, int itemId, long count, boolean equipped)
		{
			this.objectId = objectId;
			this.itemId = itemId;
			this.count = count;
			this.equipped = equipped;
		}
	}

	private static final class View
	{
		private final int sessionIndex;
		private final Map<Integer, Actor> npcs = new LinkedHashMap<>();
		private final Map<Integer, GroundItem> drops = new LinkedHashMap<>();
		private final Map<Integer, Held> items = new LinkedHashMap<>();
		private final Map<Integer, Integer> skills = new TreeMap<>();

		private int objectId;
		private int x;
		private int y;
		private int z = GROUND_Z;
		private int level;
		private long exp;
		private int sp;
		private int hp;
		private int maxHp;
		private int itemEpoch;
		private int skillEpoch;
		private int shopListId;
		private boolean skillOffered;
		private String dialog;
		private int kills;
		private boolean firstAnnounced;
		private boolean acceptedMove;
		private int acceptedX;
		private int acceptedY;
		private int acceptedZ;
		private long longestWaitMillis;
		private long coinsBeforeBuy;
		private long coinsAfterBuy;
		private long potionsBeforeDrink;
		private long potionsAfterDrink;
		private int spBeforeLearn;
		private int spAfterLearn;

		private View(int sessionIndex)
		{
			this.sessionIndex = sessionIndex;
		}

		private void noteWait(long started)
		{
			long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
			if (waited > longestWaitMillis)
				longestWaitMillis = waited;
		}

		private List<Actor> aliveBeasts()
		{
			List<Actor> alive = new ArrayList<>();
			for (Actor npc : npcs.values())
			{
				if (npc.templateId == BEAST && !npc.dead)
					alive.add(npc);
			}
			alive.sort(Comparator.comparingInt((Actor npc) -> npc.x).thenComparingInt(npc -> npc.y)
					.thenComparingInt(npc -> npc.objectId));
			return alive;
		}

		private Actor nearest(int template)
		{
			Actor best = null;
			long bestDistance = Long.MAX_VALUE;
			for (Actor npc : npcs.values())
			{
				if (npc.templateId != template || npc.dead)
					continue;

				long gap = distance(x, y, npc.x, npc.y);
				if (gap < bestDistance)
				{
					best = npc;
					bestDistance = gap;
				}
			}
			return best;
		}

		private void replaceItems(List<GamePackets.ItemView> rows)
		{
			items.clear();
			for (GamePackets.ItemView row : rows)
			{
				items.put(row.objectId(), new Held(row.objectId(), row.itemId(), row.count(), row.equipped()));
			}
			itemEpoch++;
		}

		private void applyChange(GamePackets.ItemChange change)
		{
			if (change.change() == 3)
				items.remove(change.item().objectId());
			else
			{
				items.put(change.item().objectId(), new Held(change.item().objectId(), change.item().itemId(),
						change.item().count(), change.item().equipped()));
			}
		}

		private long count(int itemId)
		{
			long total = 0L;
			for (Held item : items.values())
			{
				if (item.itemId == itemId)
					total += item.count;
			}
			return total;
		}

		private Integer objectIdOf(int itemId)
		{
			for (Held item : items.values())
			{
				if (item.itemId == itemId && item.count > 0)
					return item.objectId;
			}
			return null;
		}

		private List<Integer> equippedIds()
		{
			List<Integer> ids = new ArrayList<>();
			for (Held item : items.values())
			{
				if (item.equipped)
					ids.add(item.itemId);
			}
			ids.sort(Integer::compareTo);
			return ids;
		}

		private int skillLevel(int skillId)
		{
			return skills.getOrDefault(skillId, 0);
		}

		private String snapshot()
		{
			Map<Integer, long[]> byTemplate = new TreeMap<>();
			for (Held item : items.values())
			{
				int itemId = item.itemId;
				long[] row = byTemplate.computeIfAbsent(itemId, id -> new long[2]);
				row[0] += item.count;
				if (item.equipped)
					row[1] = 1L;
			}
			StringBuilder itemsText = new StringBuilder();
			for (Map.Entry<Integer, long[]> entry : byTemplate.entrySet())
			{
				if (itemsText.length() > 0)
					itemsText.append(',');

				itemsText.append(entry.getKey()).append(':').append(entry.getValue()[0]).append(':')
						.append(entry.getValue()[1]);
			}
			StringBuilder skillsText = new StringBuilder();
			for (Map.Entry<Integer, Integer> entry : skills.entrySet())
			{
				if (skillsText.length() > 0)
					skillsText.append(',');

				skillsText.append(entry.getKey()).append(':').append(entry.getValue());
			}
			return "level=" + level + " exp=" + exp + " sp=" + sp + " adena=" + count(COIN) + " items=" + itemsText
					+ " skills=" + skillsText;
		}
	}
}
