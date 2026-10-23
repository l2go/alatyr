package com.l2jfree.gameserver.network.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.packets.client.Action;
import com.l2jfree.gameserver.network.packets.client.AttackRequest;
import com.l2jfree.gameserver.network.packets.client.RequestAquireSkill;
import com.l2jfree.gameserver.network.packets.client.RequestBypassToServer;
import com.l2jfree.gameserver.network.packets.client.RequestMagicSkillUse;
import com.l2jfree.gameserver.network.packets.client.RequestRestartPoint;

class BridgePacketFactoryTest
{
	@Test
	void factoriesRejectInvalidFieldsBeforeBinding()
	{
		assertThatThrownBy(() -> Action.forBridge(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AttackRequest.forBridge(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestMagicSkillUse.forBridge(0, false)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestAquireSkill.forBridge(32001, 1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestAquireSkill.forBridge(1, 1001, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestAquireSkill.forBridge(1, 1, 3)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestBypassToServer.forBridge("\u0000")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestBypassToServer.forBridge("x".repeat(4097))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RequestBypassToServer.forBridge("\uD800")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void eachFactoryReturnsANewUnexecutedPacket()
	{
		assertThat(Action.forBridge(1)).isNotSameAs(Action.forBridge(1));
		assertThat(com.l2jfree.gameserver.network.packets.client.EnterWorld.forBridge())
				.isNotSameAs(com.l2jfree.gameserver.network.packets.client.EnterWorld.forBridge());
		assertThat(AttackRequest.forBridge(1).failed()).isFalse();
		assertThat(RequestMagicSkillUse.forBridge(1, true).failed()).isFalse();
		assertThat(RequestRestartPoint.forBridge()).isNotSameAs(RequestRestartPoint.forBridge());
		for (int type : new int[] { 0, 1, 2, 4, 6 })
			assertThat(RequestAquireSkill.forBridge(32000, 1000, type).failed()).isFalse();
	}
}
