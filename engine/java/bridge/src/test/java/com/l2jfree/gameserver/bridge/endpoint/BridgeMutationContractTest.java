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
				"recordReceipt(item, item.getCount())", "recordReceipt(item, -item.getCount())",
				"item.getItemId() == PlayerInventory.ADENA_ID", "((PlayerInventory)this).getOwner()");
		assertThat(source("gameobjects/stat/PlayerStat.java")).contains("int before = getSp()",
				"int after = getSp()", "BridgeEffect.sp((long)after - before, before, after)");
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
