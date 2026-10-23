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
	void moveFactoryRejectsFloorCoordinateOverflow()
	{
		var actor = com.l2jfree.gameserver.gameobjects.HuntTestPlayers.mockPlayer();
		var template = org.mockito.Mockito.mock(com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate.class);
		org.mockito.Mockito.when(actor.getTemplate()).thenReturn(template);
		org.mockito.Mockito.when(template.getCollisionHeight()).thenReturn(20);
		assertThatThrownBy(() -> com.l2jfree.gameserver.network.packets.client.MoveBackwardToLocation
				.forBridge(actor, 1, 2, Integer.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
		assertThat(com.l2jfree.gameserver.network.packets.client.MoveBackwardToLocation.forBridge(actor, 1, 2, 10))
				.isNotNull();
		assertThat(com.l2jfree.gameserver.network.packets.client.MoveBackwardToLocation.forBridge(actor, 1, 2, -10))
				.isNotNull();
	}

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
