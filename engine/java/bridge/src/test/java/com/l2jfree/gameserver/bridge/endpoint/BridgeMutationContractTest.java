/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Keeps the receipt hooks at central mutations rather than duplicating wrapper accounting. */
class BridgeMutationContractTest
{
	private static String source(String relative) throws Exception
	{
		return Files.readString(Path.of("../game/src/main/java/com/l2jfree/gameserver", relative));
	}

	@Test
	void inventoryHooksCoverStackMembershipDestroyAndTransfer() throws Exception
	{
		String container = source("gameobjects/itemcontainer/ItemContainer.java");
		assertThat(container).contains("changeCountWithReceipt(olditem", "changeCountWithReceipt(item",
				"changeCountWithReceipt(sourceitem", "target.changeCountWithReceipt(targetitem",
				"recordReceipt(item, -count)", "recordReceipt(sourceitem, -count)",
				"item.getItemId() == PlayerInventory.ADENA_ID", "((PlayerInventory)this).getOwner()");
		assertThat(source("gameobjects/stat/CreatureStat.java")).contains("int before = _sp",
				"int after = _sp", "BridgeEffect.sp((long)after - before, before, after)");
	}

	@Test
	void ledgerHooksAcquireNoLocksAndMembershipCountsAreParameters() throws Exception
	{
		String container = source("gameobjects/itemcontainer/ItemContainer.java");
		String hooks = container.substring(container.indexOf("private void changeCountWithReceipt"),
				container.indexOf("/** Coordinates a prepared exchange"));
		assertThat(hooks).contains("recordReceipt(L2ItemInstance item, long delta)")
				.contains("item.changeCountAndGetDelta(process, count, actor, reference)", "recordReceipt(item, delta)")
				.doesNotContain("synchronized", ".lock(", "new Lock");
		assertThat(source("model/items/L2ItemInstance.java"))
				.contains("public synchronized long changeCountAndGetDelta", "return getCount() - before");
		assertThat(source("network/bridge/EffectLedger.java"))
				.doesNotContain("synchronized", ".lock(", "new Lock");
		String inventory = source("gameobjects/itemcontainer/PlayerInventory.java");
		int publish = inventory.indexOf("public void publishCommittedMultisell");
		assertThat(inventory.indexOf("EffectLedger.incomplete(getOwner())", publish))
				.isBetween(publish, inventory.indexOf("item.setCount(after)", publish));
	}

	@Test
	void statAndTargetMutationSectionsAreLeafLocks() throws Exception
	{
		for (String path : new String[] { "gameobjects/stat/CreatureStat.java", "gameobjects/base/SubClass.java",
				"gameobjects/L2Creature.java" })
		{
			String text = source(path);
			String lock = path.endsWith("L2Creature.java") ? "_bridgeTargetLock" : "_bridgeStatLock";
			assertThat(text).contains("private final Object " + lock + " = new Object()");
			int start = text.indexOf("synchronized (" + lock + ")");
			int brace = text.indexOf('{', start);
			int end = brace + 1;
			int depth = 1;
			while (depth != 0)
			{
				char c = text.charAt(end++);
				if (c == '{') depth++;
				if (c == '}') depth--;
			}
			String section = text.substring(brace + 1, end - 1);
			assertThat(section).doesNotContain("synchronized", ".lock(", "getSp(", "setSp(", "getActiveChar(",
					"getKnownList(", "getObjectId(", "isBridgeSession(", "getClassIndex(", "updateDatabase(");
			// Only ledger/immutable effect constructors may be called inside these sections.
			var calls = java.util.regex.Pattern.compile("([A-Za-z][A-Za-z0-9]*)\\(").matcher(section);
			while (calls.find())
				assertThat(calls.group(1)).isIn("if", "isActive", "record", "sp", "target", "incompleteUnattributedMutation");
			String assignment = path.endsWith("L2Creature.java") ? "_target = newTarget;" : path.endsWith("SubClass.java")
					? "_sp = spValue;" : "_sp = value;";
			assertThat(text.indexOf(assignment)).isBetween(brace, end);
			assertThat(text.indexOf(assignment, text.indexOf(assignment) + 1)).isEqualTo(-1);
		}
		assertThat(source("gameobjects/stat/PlayerStat.java")).contains("setSp(value, getActiveChar())");
	}

	@Test
	void dropPathsInvalidateUnrepresentedOwnerReceiptsBeforeCallbacks() throws Exception
	{
		String inventory = source("gameobjects/itemcontainer/Inventory.java");
		int whole = inventory.indexOf("public L2ItemInstance dropItem(String process, L2ItemInstance item");
		int partial = inventory.indexOf("public L2ItemInstance dropItem(String process, int objectId");
		assertThat(inventory.indexOf("EffectLedger.incomplete(inventory.getOwner())", whole))
				.isBetween(whole, inventory.indexOf("item.setOwnerId(process, 0", whole));
		assertThat(inventory.indexOf("EffectLedger.incomplete(inventory.getOwner())", partial))
				.isBetween(partial, inventory.indexOf("item.changeCount(process, -count", partial));
	}

	@Test
	void actorHooksPrecedeCallbacksAndLearningCostsPrecedeConsumption() throws Exception
	{
		String creature = source("gameobjects/L2Creature.java");
		assertThat(creature.indexOf("recordSkillReceipt(oldSkill, newSkill)"))
				.isLessThan(creature.indexOf("skillChanged(oldSkill, newSkill)"));
		assertThat(creature).contains("previous != current", "recordSkillReceipt(oldSkill, null)");
		String learning = source("network/packets/client/RequestAquireSkill.java");
		assertThat(learning).contains("expectLearningCost(player, 0", "expectLearningCost(player, 1",
				"expectLearningCost(player, 2", "expectLearningCost(player, 3", "expectLearningCost(player, 4",
				"expectLearningCost(player, 5");
		assertThat(learning.indexOf("expectLearningCost(player, 1"))
				.isLessThan(learning.indexOf("destroyItemByItemId(\"Consume\", costid, 1"));
	}
}
