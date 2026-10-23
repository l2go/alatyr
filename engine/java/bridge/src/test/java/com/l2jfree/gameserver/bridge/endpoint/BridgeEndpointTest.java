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
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * Disabled settings do not bind. A wrong secret is refused before any account is admitted.
 */
class BridgeEndpointTest
{
	@Test
	void intentsAreAllowedOnlyAfterAnAuthenticatedOpening()
	{
		Envelope intent = Envelope.of(Schemas.INTENT, List.of(Field.u(1, true, 1), Field.u(2, true, 7), Field.u(3, true, 1)));
		BridgeDialogue unopened = new BridgeDialogue(SECRET);
		assertThat(unopened.accept(List.of(intent)).close).isTrue();
		BridgeDialogue opened = new BridgeDialogue(SECRET);
		Envelope open = Envelope.of(Schemas.OPEN, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
				Field.data(3, true, Codec.profileName()), Field.data(4, true, SECRET),
				Field.data(5, true, "guest".getBytes(StandardCharsets.UTF_8)),
				Field.data(6, true, "Guest".getBytes(StandardCharsets.UTF_8)), Field.u(7, true, 1)));
		assertThat(opened.accept(List.of(open)).admit).isTrue();
		assertThat(opened.accept(List.of(intent)).close).isFalse();
	}

	private static final byte[] SECRET = new byte[32];
	
	static
	{
		for (int i = 0; i < SECRET.length; i++)
			SECRET[i] = (byte) (i + 1);
	}
	
	@Test
	void aMissingFileStaysDisabled() throws Exception
	{
		Path directory = Files.createTempDirectory("bridge-config");
		BridgeSettings settings = BridgeSettings.load(directory);
		assertThat(settings.enabled()).isFalse();
		assertThat(settings.port()).isEqualTo(BridgeSettings.DEFAULT_PORT);
		assertThat(settings.secret()).isEmpty();
	}
	
	@Test
	void anEnabledListenerRejectsABadSecretBeforeItBinds()
	{
		Properties properties = new Properties();
		properties.setProperty("bridge.enabled", "true");
		properties.setProperty("bridge.secret", "abcd");
		assertThatThrownBy(() -> BridgeSettings.parse(properties)).isInstanceOf(IllegalStateException.class);
	}
	
	@Test
	void aWrongSecretClosesWithoutAdmission()
	{
		BridgeDialogue dialogue = new BridgeDialogue(SECRET);
		byte[] other = SECRET.clone();
		other[0] ^= 1;
		BridgeReply reply = dialogue.accept(List.of(open(other)));
		assertThat(reply.admit).isFalse();
		assertThat(reply.close).isTrue();
		assertThat(reply.records).hasSize(1);
		assertThat(reply.records.get(0).type()).isEqualTo(Schemas.BYE);
		assertThat(reply.records.get(0).field(1).value().u()).isEqualTo(BridgeFrames.BYE_AUTH);
	}
	
	@Test
	void anOpeningCombinedWithAnotherRecordIsAFrameError()
	{
		BridgeDialogue dialogue = new BridgeDialogue(SECRET);
		BridgeReply reply = dialogue.accept(List.of(open(SECRET), BridgeFrames.pong(1)));
		assertThat(reply.admit).isFalse();
		assertThat(reply.close).isTrue();
		assertThat(reply.records.get(0).field(1).value().u()).isEqualTo(BridgeFrames.BYE_FRAME);
	}
	
	@Test
	void anAcceptedOpeningEncodesACheckpointAndThenOpened()
	{
		assertThat(Codec.bridge().encode(BridgeFrames.accepted(7, 1))).isNotEmpty();
	}
	
	@Test
	void anEnabledEmptySecretOrBadPortFailsClosed()
	{
		Properties emptySecret = new Properties();
		emptySecret.setProperty("bridge.enabled", "true");
		emptySecret.setProperty("bridge.secret", "");
		assertThatThrownBy(() -> BridgeSettings.parse(emptySecret)).isInstanceOf(IllegalStateException.class);
		
		Properties badPort = new Properties();
		badPort.setProperty("bridge.port", "0");
		assertThatThrownBy(() -> BridgeSettings.parse(badPort)).isInstanceOf(IllegalStateException.class);
		
		Properties disabled = new Properties();
		disabled.setProperty("bridge.enabled", "false");
		disabled.setProperty("bridge.secret", "");
		assertThat(BridgeSettings.parse(disabled).enabled()).isFalse();
	}
	
	@Test
	void aSecondCloseIsEmpty()
	{
		BridgeDialogue dialogue = new BridgeDialogue(SECRET);
		dialogue.accept(List.of(open(SECRET)));
		Envelope close = Envelope.of(Schemas.CLOSE, List.of(Field.u(1, true, 1), Field.u(2, true, 1), Field.u(3, true, 0)));
		BridgeReply first = dialogue.accept(List.of(close));
		assertThat(first.records.get(0).field(1).value().u()).isEqualTo(BridgeFrames.BYE_NORMAL);
		BridgeReply second = dialogue.accept(List.of(close));
		assertThat(second.records).isEmpty();
		assertThat(second.close).isTrue();
		assertThat(second.admit).isFalse();
	}
	
	@Test
	void aMatchingSecretAsksForAdmissionAndDoesNotAnswerYet()
	{
		BridgeDialogue dialogue = new BridgeDialogue(SECRET);
		BridgeReply reply = dialogue.accept(List.of(open(SECRET)));
		assertThat(reply.admit).isTrue();
		assertThat(reply.close).isFalse();
		assertThat(reply.account).isEqualTo("guest");
		assertThat(reply.character).isEqualTo("Guest");
		assertThat(reply.records).isEmpty();
	}
	
	private static Envelope open(byte[] secret)
	{
		return Envelope.of(Schemas.OPEN, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
				Field.data(3, true, Codec.profileName()), Field.data(4, true, secret),
				Field.data(5, true, "guest".getBytes(StandardCharsets.UTF_8)),
				Field.data(6, true, "Guest".getBytes(StandardCharsets.UTF_8)), Field.u(7, true, 1)));
	}
}
