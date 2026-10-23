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

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.bridge.wire.SemanticFacts;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;

class BridgeCheckpointOutputTest
{
	@Test
	void aSnapshotLargerThanTheQueueDrainsWholeFramesBeforeEndAndResults() throws Exception
	{
		var facts = new ArrayList<Envelope>();
		for (int i = 1; i <= 800; i++)
			facts.add(SemanticFacts.envelope(BridgeFact.of(1, BridgeField.number(64, i),
					BridgeField.text(12, "x".repeat(2048), 4)), i, 0, 2, "objects"));
		var opened = BridgeFrames.accepted(7, 2).get(2);
		var output = new BridgeCheckpointOutput(facts, 2, List.of(BridgeResults.transportLost(1, 7)), opened);
		assertThat(output.remainingBytes()).isGreaterThan(BridgeLoopback.OUT_QUEUE_BYTES);
		ArrayDeque<byte[]> queue = new ArrayDeque<>();
		var delivered = new ArrayList<Envelope>();
		int held = 0;
		while (!output.finished() || !queue.isEmpty())
		{
			byte[] frame;
			while ((frame = output.next(BridgeLoopback.OUT_QUEUE_BYTES - held)) != null)
			{
				queue.add(frame);
				held += frame.length;
			}
			assertThat(held).isLessThanOrEqualTo(BridgeLoopback.OUT_QUEUE_BYTES);
			frame = queue.remove();
			held -= frame.length;
			delivered.addAll(Codec.bridge().read(new ByteArrayInputStream(frame)));
		}
		assertThat(delivered).hasSize(804);
		assertThat(delivered.get(0).field(1).value().u()).isEqualTo(Schemas.BEGIN);
		assertThat(delivered.get(801).type()).isEqualTo(Schemas.CHECKPOINT);
		assertThat(delivered.get(801).field(1).value().u()).isEqualTo(Schemas.END);
		assertThat(delivered.get(802).type()).isEqualTo(Schemas.RESULT);
		assertThat(delivered.get(803).type()).isEqualTo(Schemas.OPENED);
		assertThat(output.remainingBytes()).isZero();
	}

	@Test
	void noRoomDoesNotAdvanceAndCancellationCannotEmitReady()
	{
		var output = new BridgeCheckpointOutput(List.of(), 2, List.of(), null);
		int before = output.remainingBytes();
		assertThat(output.next(0)).isNull();
		assertThat(output.remainingBytes()).isEqualTo(before);
		assertThat(output.next(BridgeLoopback.OUT_QUEUE_BYTES)).isNotNull();
		output.cancel();
		assertThat(output.next(BridgeLoopback.OUT_QUEUE_BYTES)).isNull();
		assertThat(output.finished()).isTrue();
	}
}
