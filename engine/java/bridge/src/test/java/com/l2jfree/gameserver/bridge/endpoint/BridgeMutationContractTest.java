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
		assertThat(source("gameobjects/stat/PlayerStat.java")).contains("int before = getSp()",
				"int after = getSp()", "BridgeEffect.sp((long)after - before, before, after)");
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
		assertThat(source("gameobjects/L2Creature.java"))
				.doesNotContain("_bridgeTargetLock", "_bridgeSkillLock");
		assertThat(source("gameobjects/stat/PlayerStat.java")).doesNotContain("_bridgeSpLock");
		String inventory = source("gameobjects/itemcontainer/PlayerInventory.java");
		int publish = inventory.indexOf("public void publishCommittedMultisell");
		assertThat(inventory.indexOf("EffectLedger.incomplete(getOwner())", publish))
				.isBetween(publish, inventory.indexOf("item.setCount(after)", publish));
	}

	@Test
	void actorHooksPrecedeCallbacksAndLearningCostsPrecedeConsumption() throws Exception
	{
		String creature = source("gameobjects/L2Creature.java");
		assertThat(creature.indexOf("recordSkillReceipt(oldSkill, newSkill)"))
				.isLessThan(creature.indexOf("skillChanged(oldSkill, newSkill)"));
		assertThat(creature).contains("previous != newTarget", "recordSkillReceipt(oldSkill, null)");
		String learning = source("network/packets/client/RequestAquireSkill.java");
		assertThat(learning).contains("expectLearningCost(player, 0", "expectLearningCost(player, 1",
				"expectLearningCost(player, 2", "expectLearningCost(player, 3", "expectLearningCost(player, 4",
				"expectLearningCost(player, 5");
		assertThat(learning.indexOf("expectLearningCost(player, 1"))
				.isLessThan(learning.indexOf("destroyItemByItemId(\"Consume\", costid, 1"));
	}
}
