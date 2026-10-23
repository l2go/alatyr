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

import java.util.List;
package com.l2jfree.gameserver.bridge.wire;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeValue;

/** Maps copied game observations through the frozen schema, without changing the codec. */
public final class SemanticFacts
{
	private static final Map<Integer, Spec> SCHEMA = Schemas.bridge(Limits.profile()).get(Schemas.FACT);

	private SemanticFacts() {}

	public static Envelope envelope(BridgeFact fact, long sequence, long ms, long epoch, String domain)
	{
		var fields = new ArrayList<Field>();
		fields.add(Field.u(1, true, fact.kind()));
		fields.add(Field.u(2, true, sequence));
		fields.add(Field.s(3, true, ms));
		fields.add(Field.u(4, true, epoch));
		fields.add(Field.data(5, true, utf8(domain)));
		fields.add(Field.u(6, true, 1));
		fields.addAll(fields(fact.fields(), SCHEMA));
		return Envelope.of(Schemas.FACT, fields);
	}

	private static List<Field> fields(List<BridgeField> source, Map<Integer, Spec> schema)
	{
		var fields = new ArrayList<Field>();
		for (BridgeField field : source)
		{
			Spec spec = schema.get(field.number());
			if (spec == null)
				throw new IllegalArgumentException("unregistered semantic field");
			WireType type = switch (spec.shape)
			{
				case UINT, BOOL -> WireType.UNSIGNED;
				case INT -> WireType.SIGNED;
				default -> WireType.LENGTH_DELIMITED;
			};
			fields.add(new Field(field.number(), spec.essential, type, value(field.value(), spec)));
		}
		fields.sort(java.util.Comparator.comparingInt(Field::number));
		return List.copyOf(fields);
	}

	private static Value value(BridgeValue source, Spec spec)
	{
		return switch (spec.shape)
		{
			case UINT -> Value.ofU(((BridgeValue.Number)source).value());
			case INT -> Value.ofS(((BridgeValue.Number)source).value());
			case BOOL -> Value.ofU(((BridgeValue.Flag)source).value() ? 1 : 0);
			case STRING, IDENTIFIER -> Value.ofData(utf8(((BridgeValue.StringValue)source).value()));
			case POSITION ->
			{
				var position = (BridgeValue.Position)source;
				yield Value.at(position.x(), position.y(), position.z());
			}
			case OBJECT -> Value.ofFields(fields(((BridgeValue.ObjectValue)source).fields(), spec.object));
			case LIST ->
			{
				var items = new ArrayList<Value>();
				for (BridgeValue item : ((BridgeValue.ListValue)source).items())
					items.add(value(item, spec.element));
				yield Value.ofItems(List.copyOf(items));
			}
			default -> throw new IllegalArgumentException("unsupported semantic shape");
		};
	}

	private static byte[] utf8(String source)
	{
		try
		{
			var bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(source));
			byte[] copy = new byte[bytes.remaining()];
			bytes.get(copy);
			return copy;
		}
		catch (CharacterCodingException e)
		{
			throw new IllegalArgumentException("invalid observed UTF-8", e);
		}
	}
}
