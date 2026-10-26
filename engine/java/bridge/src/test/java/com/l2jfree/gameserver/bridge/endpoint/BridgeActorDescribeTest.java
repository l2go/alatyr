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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance;
import com.l2jfree.gameserver.gameobjects.view.PlayerView;
import com.l2jfree.gameserver.network.bridge.BridgeValue;
import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.network.packets.server.CharInfo;
import com.l2jfree.gameserver.network.packets.server.NpcSay;

class BridgeActorDescribeTest
{
	@Test
	void anotherPlayersAppearanceDoesNotReadHiddenStatsOrInventory()
	{
		var actor = BridgeTestPlayers.player();
		var view = mock(PlayerView.class);
		var appearance = mock(PlayerAppearance.class);
		when(actor.getView()).thenReturn(view);
		when(actor.getAppearance()).thenReturn(appearance);
		when(appearance.getVisibleName()).thenReturn("Visible name");
		when(view.getObjectId()).thenReturn(70);
		when(view.getRunSpd()).thenReturn(120);
		when(view.getWalkSpd()).thenReturn(80);
		when(view.getMovementSpeedMultiplier()).thenReturn(1.0f);
		var sink = new BridgeTestFacts();
		new CharInfo(actor).describe(sink, null);
		assertThat(sink.facts).hasSize(1);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 25)).isNull();
		verify(actor, never()).getStat();
		verify(actor, never()).getInventory();
	}

	@Test
	void anNpcKeepsAnEmptyTransmittedNameInsteadOfItsHiddenTemplateName()
	{
		var npc = mock(L2Npc.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
		when(npc.getObjectId()).thenReturn(70);
		when(npc.getTemplate().isServerSideName()).thenReturn(false);
		when(npc.getTemplate().getName()).thenReturn("Not transmitted");
		when(npc.getStat().getMovementSpeedMultiplier()).thenReturn(1.0f);
		var sink = new BridgeTestFacts();
		new AbstractNpcInfo.NpcInfo(npc).describe(sink, null);
		var name = (BridgeValue.ObjectValue)BridgeTestFacts.field(sink.facts.get(0), 12).value();
		assertThat(name.fields()).contains(com.l2jfree.gameserver.network.bridge.BridgeField.string(1, ""));
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 25)).isNull();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void repeatedPlayerAndNpcDescriptionsRefreshMovementAndForgetAStoppedDestination(boolean npc)
	{
		var actor = npc ? mock(L2Npc.class, org.mockito.Mockito.RETURNS_DEEP_STUBS) : BridgeTestPlayers.player();
		when(actor.getObjectId()).thenReturn(70);
		when(actor.isMoving()).thenReturn(true);
		when(actor.getXdestination()).thenReturn(100);
		when(actor.getYdestination()).thenReturn(200);
		when(actor.getZdestination()).thenReturn(300);
		var sink = new BridgeTestFacts();
		com.l2jfree.gameserver.network.bridge.SemanticPacket packet;
		if (npc)
		{
			when(actor.getStat().getMovementSpeedMultiplier()).thenReturn(1.0f);
			packet = new AbstractNpcInfo.NpcInfo((L2Npc)actor);
		}
		else
		{
			var player = (com.l2jfree.gameserver.gameobjects.L2Player)actor;
			var view = mock(PlayerView.class);
			when(player.getView()).thenReturn(view);
			when(player.getAppearance()).thenReturn(mock(PlayerAppearance.class));
			when(player.getAppearance().getVisibleName()).thenReturn("Mover");
			when(view.getObjectId()).thenReturn(70);
			when(view.getMovementSpeedMultiplier()).thenReturn(1.0f);
			packet = new CharInfo(player);
		}
		var projection = new BridgeProjection();
		for (int i = 0; i < 2; i++)
		{
			sink.facts.clear();
			packet.describe(sink, null);
			assertThat(sink.facts).extracting(com.l2jfree.gameserver.network.bridge.BridgeFact::kind).containsExactly(1, 2);
			assertThat(BridgeTestFacts.field(sink.facts.get(1), 14).value()).isEqualTo(new BridgeValue.Position(100 + i, 200, 300));
			for (var fact : sink.facts)
				projection.observe(fact, 1, 0);
			assertThat(projection.capture(0).facts()).extracting(com.l2jfree.gameserver.network.bridge.BridgeFact::kind).containsExactly(1, 2);
			when(actor.getXdestination()).thenReturn(101);
		}
		when(actor.isMoving()).thenReturn(false);
		sink.facts.clear();
		packet.describe(sink, null);
		assertThat(sink.facts).extracting(com.l2jfree.gameserver.network.bridge.BridgeFact::kind).containsExactly(1);
		projection.observe(sink.facts.getFirst(), 2, 0);
		assertThat(projection.capture(0).facts()).extracting(com.l2jfree.gameserver.network.bridge.BridgeFact::kind).containsExactly(1);
	}

	@Test
	void npcChatIsANoticeAndNeverDialogueSpeech()
	{
		var sink = new BridgeTestFacts();
		new NpcSay(70, 0, 10001, "Authored line").describe(sink, null);
		assertThat(sink.facts.get(0).kind()).isEqualTo(18);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 68)).isNull();
	}
}
