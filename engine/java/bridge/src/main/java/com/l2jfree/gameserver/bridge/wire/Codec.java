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
package com.l2jfree.gameserver.bridge.wire;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded frames for the frozen core registry and, when asked, the bridge/1 profile.
 * <p>
 * Decoding is pure: a failure discards every record from the frame. Encoding sorts fields and then decodes
 * its own output, so a value that cannot be read is not written. The bridge profile check runs inside
 * decoding, which means encoding rejects the same profile failures.
 */
public final class Codec
{
	private static final long SENT = 1;
	private static final long DONE = 2;
	private static final long REFUSED = 3;
	private static final long UNKNOWN = 4;
	private static final long UNRECOVERABLE = 5;
	private static final long NONE = 0;
	private static final long TIMEOUT = 17;
	private static final long WORLD_CLOSED = 18;
	private static final long INTERRUPTED = 19;
	private static final long TOO_OLD = 20;
	private static final long TARGET_UNCONFIRMED = 23;
	private static final long BACKEND_ERROR = 24;
	private static final long NO_DELIVERY = 0;
	private static final long LOCAL = 1;
	private static final long BACKEND = 2;
	private static final long PARTIAL = 3;
	private static final byte[] BRIDGE_PROFILE = "bridge/1".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] BRIDGE_NAMESPACE = "x.l2jfree/bridge".getBytes(StandardCharsets.US_ASCII);
	private static final int[][] OPENED_LIMITS = {
			{ 1, 65536 },
			{ 2, 4096 },
			{ 3, 1024 },
			{ 4, 3 },
			{ 5, 4096 },
			{ 6, 64 },
			{ 7, 8 },
			{ 8, 262144 },
			{ 14, 8 },
			{ 15, 1024 },
			{ 16, 128 },
			{ 17, 5000 },
			{ 20, 5000 },
			{ 21, 5000 },
			{ 22, 10000 },
			{ 23, 15000 },
			{ 24, 30000 },
			{ 26, 2097152 },
			{ 27, 65536 },
			{ 28, 4 },
			{ 29, 2 },
			{ 30, 2097152 },
	};
	
	private final Limits limits;
	private final Map<Long, Map<Integer, Spec>> registry;
	private final boolean bridgeProfile;
	
	private Codec(Limits limits, Map<Long, Map<Integer, Spec>> registry, boolean bridgeProfile)
	{
		if (limits.frameBytes < 1 || limits.stringBytes < 1 || limits.listItems < 1 || limits.depth < 1
				|| limits.records < 1 || limits.fields < 1 || limits.identifierBytes < 1 || limits.worldBytes < 1
				|| limits.clientBytes < 1)
			throw config();
		
		this.limits = limits;
		this.registry = new HashMap<>();
		for (Map.Entry<Long, Map<Integer, Spec>> entry : registry.entrySet())
		{
			if (entry.getKey() == 0)
				throw config();
			
			this.registry.put(entry.getKey(), copySchema(entry.getValue(), 0));
		}
		this.bridgeProfile = bridgeProfile;
	}
	
	/**
	 * @return a codec for the frozen core registry used by the historical vectors
	 */
	public static Codec neutral()
	{
		Limits limits = Limits.profile();
		return new Codec(limits, Schemas.neutral(limits), false);
	}
	
	/**
	 * @return a codec for bridge/1. Profile failures reject the whole frame on read and on write.
	 */
	public static Codec bridge()
	{
		Limits limits = Limits.profile();
		return new Codec(limits, Schemas.bridge(limits), true);
	}
	
	/**
	 * @return the bridge registry without the profile check. Tests use it to build a frame the neutral registry
	 *         cannot emit, because an essential field outside that registry is unsupported there.
	 */
	static Codec structured()
	{
		Limits limits = Limits.profile();
		return new Codec(limits, Schemas.bridge(limits), false);
	}
	
	/**
	 * @param frame one complete frame, without a stream length prefix
	 * @return the records. An empty list means every record in the frame was an unknown odd type.
	 */
	public List<Envelope> decode(byte[] frame)
	{
		if (frame.length == 0 || frame.length > limits.frameBytes)
			throw frameError();
		
		List<Envelope> records = new ArrayList<>();
		int pos = 0;
		int count = 0;
		while (pos < frame.length)
		{
			int start = pos;
			count++;
			if (count > limits.records)
				throw frameError();
			
			UOut type = readU(frame, pos);
			if (type.error())
				throw frameError();
			
			pos = type.pos();
			Taken body = take(frame, pos);
			if (body.error())
				throw frameError();
			
			pos = body.pos();
			Map<Integer, Spec> schema = registry.get(type.value());
			if (schema == null)
			{
				if ((type.value() & 1L) == 0)
					throw unsupported();
				
				continue;
			}
			Outcome parsed = fields(body.bytes(), schema, 0);
			if (parsed.failure == Failure.UNSUPPORTED)
				throw unsupported();
			
			if (parsed.missing)
			{
				if (type.value() == Schemas.INTENT && parsed.unsupported && parsed.missingDepth == 0)
					throw unsupported();
				
				throw frameError();
			}
			if (parsed.failure == Failure.FRAME)
				throw frameError();
			
			if (parsed.unsupported && type.value() != Schemas.FACT && type.value() != Schemas.INTENT)
				throw unsupported();
			
			byte[] hash = null;
			if (parsed.unsupported && type.value() == Schemas.INTENT)
				hash = sha256(canonicalOpaque(body.bytes(), schema));
			
			Envelope envelope = new Envelope(type.value(), List.copyOf(parsed.fields), parsed.unsupported, pos - start, hash);
			validateEnvelope(envelope);
			if (bridgeProfile)
				checkBridge(envelope);
			
			records.add(envelope);
		}
		return List.copyOf(records);
	}
	
	/**
	 * Reads one length-prefixed frame. The length is rejected before the body is allocated.
	 * A stream that ends before the first prefix byte is end of stream. A short frame is a frame error.
	 *
	 * @param in the stream
	 * @return the records inside the frame
	 */
	public List<Envelope> read(InputStream in)
	{
		byte[] prefix = new byte[10];
		int size = 0;
		while (true)
		{
			if (size == prefix.length)
				throw frameError();
			
			int read;
			try
			{
				read = in.read();
			}
			catch (IOException e)
			{
				throw frameError();
			}
			if (read < 0)
			{
				if (size == 0)
					throw new UncheckedIOException(new EOFException("end of stream"));
				
				throw frameError();
			}
			prefix[size++] = (byte) read;
			if ((prefix[size - 1] & 0xff) < 128)
				break;
		}
		UOut length = readU(Arrays.copyOf(prefix, size), 0);
		if (length.error() || length.pos() != size || length.value() == 0
				|| Long.compareUnsigned(length.value(), limits.frameBytes) > 0)
			throw frameError();
		
		byte[] body = new byte[(int) length.value()];
		try
		{
			int off = 0;
			while (off < body.length)
			{
				int n = in.read(body, off, body.length - off);
				if (n < 0)
					throw frameError();
				
				off += n;
			}
		}
		catch (WireException e)
		{
			throw e;
		}
		catch (IOException e)
		{
			throw frameError();
		}
		return decode(body);
	}
	
	/**
	 * @param records at least one record
	 * @return the canonical frame
	 */
	public byte[] encode(List<Envelope> records)
	{
		if (records.isEmpty() || records.size() > limits.records)
			throw frameError();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Envelope envelope : records)
		{
			Map<Integer, Spec> schema = registry.get(envelope.type());
			if (schema == null || envelope.unsupported())
				throw unsupported();
			
			byte[] body = encodeFields(envelope.fields(), schema, 0);
			ByteArrayOutputStream header = new ByteArrayOutputStream();
			appendU(header, envelope.type());
			appendU(header, body.length);
			appendBounded(out, header.toByteArray());
			appendBounded(out, body);
		}
		byte[] encoded = out.toByteArray();
		decode(encoded);
		return encoded;
	}
	
	/**
	 * Writes a length prefix and one frame.
	 *
	 * @param out the stream
	 * @param records the records
	 */
	public void write(OutputStream out, List<Envelope> records)
	{
		byte[] body = encode(records);
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		appendU(header, body.length);
		try
		{
			out.write(header.toByteArray());
			out.write(body);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
	
	private Outcome fields(byte[] body, Map<Integer, Spec> schema, int depth)
	{
		Outcome outcome = new Outcome();
		Map<Integer, Boolean> seen = new HashMap<>();
		int pos = 0;
		while (pos < body.length)
		{
			UOut key = readU(body, pos);
			if (key.error())
				return Outcome.frame();
			
			pos = key.pos();
			long number = key.value() >>> 4;
			int wire = (int) (key.value() & 7L);
			boolean essential = (key.value() & 8L) != 0;
			if (number == 0 || Long.compareUnsigned(number, (1L << 28) - 1) > 0 || wire > WireType.FIXED64.ordinal()
					|| seen.containsKey((int) number) || seen.size() >= limits.fields)
				return Outcome.frame();
			
			seen.put((int) number, Boolean.TRUE);
			Value value = new Value();
			byte[] raw = null;
			if (wire == WireType.UNSIGNED.ordinal() || wire == WireType.SIGNED.ordinal())
			{
				UOut scalar = readU(body, pos);
				if (scalar.error())
					return Outcome.frame();
				
				pos = scalar.pos();
				if (wire == WireType.SIGNED.ordinal())
					value.setS(zigzagDecode(scalar.value()));
				else
					value.setU(scalar.value());
			}
			else if (wire == WireType.LENGTH_DELIMITED.ordinal())
			{
				Taken taken = take(body, pos);
				if (taken.error())
					return Outcome.frame();
				
				pos = taken.pos();
				raw = taken.bytes();
			}
			else if (wire == WireType.FIXED64.ordinal())
			{
				if (body.length - pos < 8)
					return Outcome.frame();
				
				value.setFixed(Arrays.copyOfRange(body, pos, pos + 8));
				pos += 8;
			}
			else
				return Outcome.frame();
			
			Spec spec = schema.get((int) number);
			if (spec == null)
			{
				outcome.unsupported = outcome.unsupported || essential;
				continue;
			}
			if (wire != wireType(spec.shape).ordinal() || spec.essential != essential)
				return Outcome.frame();
			
			Outcome parsed = value(value, raw, spec, depth);
			if (parsed.missing || parsed.failure != Failure.NONE)
			{
				if (parsed.missing)
					parsed.unsupported = outcome.unsupported || parsed.unsupported;
				
				parsed.fields = null;
				return parsed;
			}
			outcome.unsupported = outcome.unsupported || parsed.unsupported;
			outcome.fields.add(new Field((int) number, essential, WireType.values()[wire], value));
		}
		for (Map.Entry<Integer, Spec> entry : schema.entrySet())
		{
			if (entry.getValue().required && !seen.containsKey(entry.getKey()))
			{
				outcome.missing = true;
				outcome.missingDepth = depth;
				outcome.fields = null;
				return outcome;
			}
		}
		return outcome;
	}
	
	private Outcome value(Value value, byte[] raw, Spec spec, int depth)
	{
		Outcome outcome = new Outcome();
		switch (spec.shape)
		{
			case UINT:
			case BOOL:
				if (spec.nonzero && value.u() == 0)
					return Outcome.frame();
				
				if (spec.shape == Shape.BOOL && Long.compareUnsigned(value.u(), 1) > 0)
					return Outcome.frame();
				
				if (spec.bits > 0 && spec.bits < 64 && value.u() >>> spec.bits != 0)
					return Outcome.frame();
				
				if (spec.enums != null && !contains(spec.enums, value.u()))
					return Outcome.unsupported();
				
				break;
			case INT:
				if (spec.bits > 0 && spec.bits < 64)
				{
					long limit = 1L << (spec.bits - 1);
					if (value.s() < -limit || value.s() >= limit)
						return Outcome.frame();
				}
				break;
			case STRING:
			case IDENTIFIER:
			case LANGUAGE:
			case BYTES:
				int limit = limits.stringBytes;
				if (spec.shape == Shape.BYTES)
					limit = limits.frameBytes;
				
				if ((spec.shape == Shape.IDENTIFIER || spec.shape == Shape.LANGUAGE) && spec.maxBytes == 0)
					limit = Math.min(limit, limits.identifierBytes);
				
				if (spec.maxBytes > 0)
					limit = Math.min(limit, spec.maxBytes);
				
				if (raw.length > limit)
					return Outcome.frame();
				
				if (spec.shape != Shape.BYTES && !utf8(raw))
					return Outcome.frame();
				
				if ((spec.shape == Shape.IDENTIFIER || spec.shape == Shape.LANGUAGE) && hasNul(raw))
					return Outcome.frame();
				
				if (spec.shape == Shape.LANGUAGE && !LanguageTag.valid(raw))
					return Outcome.frame();
				
				value.setData(raw.clone());
				break;
			case POSITION:
				int cursor = 0;
				int[] xyz = new int[3];
				for (int i = 0; i < xyz.length; i++)
				{
					UOut scalar = readU(raw, cursor);
					if (scalar.error())
						return Outcome.frame();
					
					cursor = scalar.pos();
					long coord = zigzagDecode(scalar.value());
					if (coord < Integer.MIN_VALUE || coord > Integer.MAX_VALUE)
						return Outcome.frame();
					
					xyz[i] = (int) coord;
				}
				if (cursor != raw.length)
					return Outcome.frame();
				
				value.setXyz(xyz[0], xyz[1], xyz[2]);
				break;
			case OBJECT:
				if (depth + 1 > limits.depth)
					return Outcome.frame();
				
				Outcome nested = fields(raw, spec.object, depth + 1);
				value.setFields(nested.fields == null ? List.of() : List.copyOf(nested.fields));
				return nested;
			case LIST:
				if (depth + 1 > limits.depth)
					return Outcome.frame();
				
				List<Value> items = new ArrayList<>();
				int pos = 0;
				boolean unknown = false;
				while (pos < raw.length)
				{
					if (items.size() >= limits.listItems)
						return Outcome.frame();
					
					Taken element = take(raw, pos);
					if (element.error())
						return Outcome.frame();
					
					pos = element.pos();
					Value item = new Value();
					Spec elementSpec = spec.element;
					if (elementSpec.shape == Shape.UINT || elementSpec.shape == Shape.BOOL || elementSpec.shape == Shape.INT)
					{
						UOut scalar = readU(element.bytes(), 0);
						if (scalar.error() || scalar.pos() != element.bytes().length)
							return Outcome.frame();
						
						if (elementSpec.shape == Shape.INT)
							item.setS(zigzagDecode(scalar.value()));
						else
							item.setU(scalar.value());
					}
					Outcome parsed = value(item, element.bytes(), elementSpec, depth + 1);
					if (parsed.missing || parsed.failure != Failure.NONE)
					{
						// A list element reports the error and drops the nested unsupported flag.
						parsed.unsupported = false;
						parsed.fields = null;
						return parsed;
					}
					unknown = unknown || parsed.unsupported;
					items.add(item);
				}
				value.setItems(List.copyOf(items));
				outcome.unsupported = unknown;
				return outcome;
			default:
				break;
		}
		return outcome;
	}
	
	private byte[] encodeFields(List<Field> fields, Map<Integer, Spec> schema, int depth)
	{
		if (fields.size() > limits.fields)
			throw frameError();
		
		List<Field> sorted = new ArrayList<>(fields);
		sorted.sort(Comparator.comparingInt(Field::number));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Field field : sorted)
		{
			if (field.number() == 0 || field.number() > (1 << 28) - 1 || field.type().ordinal() > WireType.FIXED64.ordinal())
				throw frameError();
			
			Spec spec = schema.get(field.number());
			if (spec == null)
			{
				if (field.essential())
					throw unsupported();
				
				spec = new Spec();
				spec.shape = Shape.BYTES;
				if (field.type() == WireType.UNSIGNED)
					spec.shape = Shape.UINT;
				else if (field.type() == WireType.SIGNED)
					spec.shape = Shape.INT;
				else if (field.type() == WireType.FIXED64)
					spec.shape = Shape.FIXED;
			}
			long key = (long) field.number() << 4 | field.type().ordinal();
			if (field.essential())
				key |= 8L;
			
			ByteArrayOutputStream encoded = new ByteArrayOutputStream();
			appendU(encoded, key);
			switch (field.type())
			{
				case UNSIGNED:
					appendU(encoded, field.value().u());
					break;
				case SIGNED:
					appendS(encoded, field.value().s());
					break;
				case FIXED64:
					encoded.writeBytes(field.value().fixed());
					break;
				case LENGTH_DELIMITED:
					byte[] raw = encodeValue(field.value(), spec, depth);
					appendU(encoded, raw.length);
					appendBounded(encoded, raw);
					break;
				default:
					throw frameError();
			}
			appendBounded(out, encoded.toByteArray());
		}
		return out.toByteArray();
	}
	
	private byte[] encodeValue(Value value, Spec spec, int depth)
	{
		switch (spec.shape)
		{
			case OBJECT:
				if (depth + 1 > limits.depth)
					throw frameError();
				
				return encodeFields(value.fields(), spec.object, depth + 1);
			case LIST:
				if (depth + 1 > limits.depth || value.items().size() > limits.listItems || spec.element == null)
					throw frameError();
				
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				for (Value item : value.items())
				{
					byte[] raw;
					if (spec.element.shape == Shape.UINT || spec.element.shape == Shape.BOOL)
					{
						ByteArrayOutputStream scalar = new ByteArrayOutputStream();
						appendU(scalar, item.u());
						raw = scalar.toByteArray();
					}
					else if (spec.element.shape == Shape.INT)
					{
						ByteArrayOutputStream scalar = new ByteArrayOutputStream();
						appendS(scalar, item.s());
						raw = scalar.toByteArray();
					}
					else
						raw = encodeValue(item, spec.element, depth + 1);
					
					ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
					appendU(wrapped, raw.length);
					appendBounded(wrapped, raw);
					appendBounded(out, wrapped.toByteArray());
				}
				return out.toByteArray();
			case POSITION:
				ByteArrayOutputStream position = new ByteArrayOutputStream();
				appendS(position, value.x());
				appendS(position, value.y());
				appendS(position, value.z());
				return position.toByteArray();
			default:
				if (value.data().length > limits.frameBytes)
					throw frameError();
				
				return value.data();
		}
	}
	
	private void validateEnvelope(Envelope envelope)
	{
		switch ((int) envelope.type())
		{
			case (int) Schemas.HELLO:
				if (Long.compareUnsigned(u(envelope, 1), u(envelope, 2)) > 0)
					throw frameError();
				
				break;
			case (int) Schemas.WELCOME:
				if (u(envelope, 1) == 0 || u(envelope, 4) == 0)
					throw frameError();
				
				break;
			case (int) Schemas.CHECKPOINT:
				if (u(envelope, 2) == 0 || u(envelope, 1) == Schemas.BEGIN && u(envelope, 3) != 0)
					throw frameError();
				
				break;
			case (int) Schemas.FACT:
				if (u(envelope, 2) == 0 || u(envelope, 4) == 0 || s(envelope, 3) < 0)
					throw frameError();
				
				break;
			case (int) Schemas.INTENT:
				if (u(envelope, 1) == 0)
					throw frameError();
				
				break;
			case (int) Schemas.CONTROL:
				switch ((int) u(envelope, 1))
				{
					case (int) Schemas.TAKE_CONTROL:
						if (has(envelope, 2) || has(envelope, 3))
							throw frameError();
						
						break;
					case (int) Schemas.GRANTED:
						if (!has(envelope, 2) || !has(envelope, 3) || u(envelope, 2) == 0 || u(envelope, 3) == 0)
							throw frameError();
						
						break;
					case (int) Schemas.LOST:
					case (int) Schemas.RESYNC:
						if (!has(envelope, 2) || u(envelope, 2) == 0 || has(envelope, 3))
							throw frameError();
						
						break;
					default:
						break;
				}
				break;
			case (int) Schemas.RESULT:
				if (u(envelope, 1) == 0)
					throw frameError();
				
				long stage = u(envelope, 2);
				long reason = u(envelope, 3);
				long delivery = u(envelope, 7);
				if (stage == SENT)
				{
					if (reason != NONE || delivery != LOCAL && delivery != BACKEND)
						throw frameError();
				}
				else if (stage == DONE)
				{
					if (reason != NONE || delivery != BACKEND)
						throw frameError();
				}
				else if (stage == REFUSED)
				{
					if (reason == NONE || delivery != NO_DELIVERY)
						throw frameError();
				}
				else if (stage == UNRECOVERABLE)
				{
					if (reason != TOO_OLD || delivery != NO_DELIVERY)
						throw frameError();
				}
				else if (stage == UNKNOWN)
				{
					if (delivery != BACKEND && delivery != PARTIAL)
						throw frameError();
					
					if (reason != TIMEOUT && reason != WORLD_CLOSED && reason != INTERRUPTED && reason != TARGET_UNCONFIRMED
							&& reason != BACKEND_ERROR)
						throw frameError();
				}
				break;
			default:
				break;
		}
	}
	
	private void checkBridge(Envelope envelope)
	{
		if (envelope.type() == Schemas.OPEN)
			checkOpen(envelope);
		else if (envelope.type() == Schemas.OPENED)
			checkOpened(envelope);
		else if (envelope.type() == Schemas.RESUME)
			checkResume(envelope);
		else if (envelope.type() == Schemas.CLOSE)
			checkClose(envelope);
		else if (envelope.type() == Schemas.INTENT_ERROR)
			checkIntentError(envelope);
		else if (envelope.type() == Schemas.RESULT)
			checkResult(envelope);
		else if (envelope.type() == Schemas.FACT)
			checkFact(envelope);
	}
	
	private void checkOpen(Envelope envelope)
	{
		long min = u(envelope, 1);
		long max = u(envelope, 2);
		byte[] secret = data(envelope, 4);
		byte[] account = data(envelope, 5);
		byte[] character = data(envelope, 6);
		if (Long.compareUnsigned(min, max) > 0 || secret.length != 32 || account.length == 0 || character.length == 0
				|| hasNul(account) || hasNul(character) || u(envelope, 7) != 1)
			throw frameError();
		
		if (Long.compareUnsigned(min, 1) > 0 || Long.compareUnsigned(max, 1) < 0 || !Arrays.equals(data(envelope, 3), BRIDGE_PROFILE))
			throw unsupported();
	}
	
	private void checkOpened(Envelope envelope)
	{
		if (Long.compareUnsigned(u(envelope, 5), 21) > 0)
			throw unsupported();
		
		long status = u(envelope, 1);
		if (status == 1)
		{
			for (int number : new int[] { 2, 3, 4, 6, 7, 8 })
			{
				if (!has(envelope, number))
					throw frameError();
			}
			if (u(envelope, 5) != 0 || u(envelope, 7) != 1 || !Arrays.equals(data(envelope, 8), BRIDGE_PROFILE)
					|| data(envelope, 3).length > 256)
				throw frameError();
			
			for (int[] limit : OPENED_LIMITS)
			{
				Field field = Field.find(envelope.field(6).value().fields(), limit[0]);
				if (field == null || field.value().u() != limit[1])
					throw frameError();
			}
		}
		else if (status == 2)
		{
			if (u(envelope, 5) == 0)
				throw frameError();
			
			for (int number : new int[] { 2, 3, 4, 6, 7, 8 })
			{
				if (has(envelope, number))
					throw frameError();
			}
		}
		else
			throw unsupported();
	}
	
	private static void checkResume(Envelope envelope)
	{
		// The next epoch is a session rule. The codec only requires a positive epoch and the profile string.
		if (u(envelope, 1) == 0 || u(envelope, 2) == 0 || data(envelope, 3).length != 32
				|| !Arrays.equals(data(envelope, 4), BRIDGE_PROFILE))
			throw frameError();
	}
	
	private static void checkClose(Envelope envelope)
	{
		if (u(envelope, 3) != 0)
			throw frameError();
	}
	
	private static void checkIntentError(Envelope envelope)
	{
		long code = u(envelope, 2);
		if (code != 22 && code != 256)
			throw unsupported();
	}
	
	private void checkResult(Envelope envelope)
	{
		boolean namespace = has(envelope, 64);
		boolean bridgeReason = has(envelope, 65);
		if (namespace != bridgeReason || namespace && !Arrays.equals(data(envelope, 64), BRIDGE_NAMESPACE))
			throw frameError();
		
		long stage = u(envelope, 2);
		long reason = u(envelope, 3);
		long delivery = u(envelope, 7);
		if ((stage == SENT || stage == DONE) && delivery != BACKEND)
			throw frameError();
		
		if (stage == UNRECOVERABLE && (!bridgeReason || u(envelope, 65) != 21))
			throw frameError();
		
		if (bridgeReason)
		{
			long code = u(envelope, 65);
			if (Long.compareUnsigned(code, 21) > 0)
				throw unsupported();
			
			// 0 is success without this pair. 11-13 belong to Opened. 19 is diagnostic only.
			if (code == 0 || code == 11 || code == 12 || code == 13 || code == 19 || !profilePair(code, reason))
				throw frameError();
		}
		if (has(envelope, 66) != (stage == DONE))
			throw frameError();
		
		if (has(envelope, 66))
		{
			for (Value effect : envelope.field(66).value().items())
				checkEffect(effect.fields());
		}
	}
	
	/**
	 * @param bridgeReason field 65
	 * @param coreReason field 3
	 * @return whether the pair is one the profile allows on a Result
	 */
	private static boolean profilePair(long bridgeReason, long coreReason)
	{
		if (bridgeReason >= 1 && bridgeReason <= 5 || bridgeReason >= 14 && bridgeReason <= 18)
			return coreReason == 16;
		
		if (bridgeReason == 6)
			return coreReason == 10 || coreReason == 6;
		
		if (bridgeReason == 7)
			return coreReason == 1;
		
		if (bridgeReason == 8)
			return coreReason == 7;
		
		if (bridgeReason == 9 || bridgeReason == 10)
			return coreReason == 16;
		
		if (bridgeReason == 20)
			return coreReason == 24;
		
		if (bridgeReason == 21)
			return coreReason == 20;
		
		return false;
	}
	
	private void checkFact(Envelope envelope)
	{
		if (has(envelope, 10) || has(envelope, 16) || has(envelope, 32))
			throw frameError();
		
		boolean generation = has(envelope, 66);
		boolean own = has(envelope, 67);
		if (generation != own || own && u(envelope, 67) != 1 || generation && u(envelope, 66) == 0)
			throw frameError();
		
		if (has(envelope, 21) && has(envelope, 68))
			throw frameError();
		
		if (has(envelope, 72))
		{
			for (Value arg : envelope.field(72).value().items())
				checkMessage(arg.fields());
		}
	}
	
	private void checkEffect(List<Field> fields)
	{
		long kind = u(fields, 1);
		int[] allowed;
		if (kind == 1)
			allowed = new int[] { 1, 2, 3, 4 };
		else if (kind == 2)
			allowed = new int[] { 1, 4 };
		else if (kind == 3)
			allowed = new int[] { 1, 5, 6, 7 };
		else if (kind == 4)
			allowed = new int[] { 1, 4, 6, 7 };
		else if (kind == 5)
			allowed = new int[] { 1, 8 };
		else
			throw unsupported();
		
		for (Field field : fields)
		{
			if (!contains(allowed, field.number()))
				throw frameError();
		}
		for (int number : allowed)
		{
			if (number != 1 && Field.find(fields, number) == null)
				throw frameError();
		}
		if ((kind == 1 || kind == 2 || kind == 4) && s(fields, 4) == 0)
			throw frameError();
		
		if (kind == 3 || kind == 4)
		{
			long oldLevel = s(fields, 6);
			long newLevel = s(fields, 7);
			if (oldLevel < 0 || newLevel < 0 || oldLevel == newLevel)
				throw frameError();
		}
		if (kind == 5 && s(fields, 8) < 0)
			throw frameError();
	}
	
	private void checkMessage(List<Field> fields)
	{
		long type = u(fields, 1);
		int[] allowed;
		if (type == 0)
			allowed = new int[] { 1, 2 };
		else if (type == 1 || type == 2 || type == 3 || type == 5 || type == 6)
			allowed = new int[] { 1, 3 };
		else if (type == 4)
			allowed = new int[] { 1, 3, 4 };
		else if (type == 7)
			allowed = new int[] { 1, 3, 4, 5 };
		else
			throw unsupported();
		
		for (Field field : fields)
		{
			if (!contains(allowed, field.number()))
				throw frameError();
		}
		for (int number : allowed)
		{
			if (Field.find(fields, number) == null)
				throw frameError();
		}
	}
	
	private Map<Integer, Spec> copySchema(Map<Integer, Spec> source, int depth)
	{
		Map<Integer, Spec> out = new HashMap<>();
		for (Map.Entry<Integer, Spec> entry : source.entrySet())
		{
			int number = entry.getKey();
			if (number == 0 || number > (1 << 28) - 1)
				throw config();
			
			out.put(number, copySpec(entry.getValue(), depth));
		}
		return out;
	}
	
	private Spec copySpec(Spec spec, int depth)
	{
		if (spec.shape.ordinal() > Shape.FIXED.ordinal() || depth > limits.depth + 1 || spec.maxBytes < 0 || spec.bits < 0
				|| spec.bits > 64)
			throw config();
		
		Spec copy = spec.copy();
		copy.enums = spec.enums == null ? null : spec.enums.clone();
		if (spec.shape == Shape.OBJECT)
		{
			if (spec.object == null)
				throw config();
			
			copy.object = copySchema(spec.object, depth + 1);
		}
		if (spec.shape == Shape.LIST)
		{
			if (spec.element == null)
				throw config();
			
			copy.element = copySpec(spec.element, depth + 1);
		}
		return copy;
	}
	
	private byte[] canonicalOpaque(byte[] body, Map<Integer, Spec> schema)
	{
		List<Part> parts = new ArrayList<>();
		int pos = 0;
		while (pos < body.length)
		{
			int start = pos;
			UOut key = readU(body, pos);
			if (key.error())
				break;
			
			pos = key.pos();
			byte[] normalized = null;
			boolean replace = false;
			int wire = (int) (key.value() & 7L);
			if (wire == WireType.UNSIGNED.ordinal() || wire == WireType.SIGNED.ordinal())
			{
				UOut ignored = readU(body, pos);
				pos = ignored.pos();
			}
			else if (wire == WireType.FIXED64.ordinal())
				pos += 8;
			else if (wire == WireType.LENGTH_DELIMITED.ordinal())
			{
				Taken raw = take(body, pos);
				pos = raw.pos();
				Spec spec = schema.get((int) (key.value() >>> 4));
				if (spec != null && spec.shape == Shape.OBJECT)
				{
					normalized = canonicalOpaque(raw.bytes(), spec.object);
					replace = true;
				}
				if (spec != null && spec.shape == Shape.LIST && spec.element.shape == Shape.OBJECT)
				{
					ByteArrayOutputStream list = new ByteArrayOutputStream();
					int at = 0;
					while (at < raw.bytes().length)
					{
						Taken element = take(raw.bytes(), at);
						at = element.pos();
						byte[] opaque = canonicalOpaque(element.bytes(), spec.element.object);
						appendU(list, opaque.length);
						list.writeBytes(opaque);
					}
					normalized = list.toByteArray();
					replace = true;
				}
			}
			byte[] chunk = Arrays.copyOfRange(body, start, pos);
			if (replace)
			{
				ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
				appendU(rewritten, key.value());
				appendU(rewritten, normalized.length);
				rewritten.writeBytes(normalized);
				chunk = rewritten.toByteArray();
			}
			parts.add(new Part(key.value() >>> 4, chunk));
		}
		parts.sort(Comparator.comparingLong(part -> part.number()));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Part part : parts)
			out.writeBytes(part.bytes());
		
		return out.toByteArray();
	}
	
	private void appendBounded(ByteArrayOutputStream out, byte[] more)
	{
		if (more.length > limits.frameBytes - out.size())
			throw frameError();
		
		out.writeBytes(more);
	}
	
	private static void appendU(ByteArrayOutputStream out, long value)
	{
		long rest = value;
		while (true)
		{
			if (Long.compareUnsigned(rest, 0x7fL) <= 0)
			{
				out.write((int) rest);
				return;
			}
			out.write((int) ((rest & 0x7fL) | 0x80L));
			rest >>>= 7;
		}
	}
	
	private static void appendS(ByteArrayOutputStream out, long value)
	{
		appendU(out, value << 1 ^ value >> 63);
	}
	
	private static long zigzagDecode(long value)
	{
		return value >>> 1 ^ -(value & 1L);
	}
	
	private static UOut readU(byte[] bytes, int pos)
	{
		long value = 0;
		for (int i = 0; i < 10; i++)
		{
			if (pos >= bytes.length)
				return new UOut(0, pos, true);
			
			int current = bytes[pos++] & 0xff;
			if (i == 9 && current > 1)
				return new UOut(0, pos, true);
			
			value |= (long) (current & 127) << i * 7;
			if (current < 128)
			{
				if (i > 0 && current == 0)
					return new UOut(0, pos, true);
				
				return new UOut(value, pos, false);
			}
		}
		return new UOut(0, pos, true);
	}
	
	private static Taken take(byte[] bytes, int pos)
	{
		UOut length = readU(bytes, pos);
		if (length.error() || Long.compareUnsigned(length.value(), (long) bytes.length - length.pos()) > 0)
			return new Taken(null, pos, true);
		
		int start = length.pos();
		int end = start + (int) length.value();
		return new Taken(Arrays.copyOfRange(bytes, start, end), end, false);
	}
	
	private static WireType wireType(Shape shape)
	{
		if (shape == Shape.UINT || shape == Shape.BOOL)
			return WireType.UNSIGNED;
		
		if (shape == Shape.INT)
			return WireType.SIGNED;
		
		if (shape == Shape.FIXED)
			return WireType.FIXED64;
		
		return WireType.LENGTH_DELIMITED;
	}
	
	private static boolean utf8(byte[] raw)
	{
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try
		{
			decoder.decode(ByteBuffer.wrap(raw));
			return true;
		}
		catch (CharacterCodingException e)
		{
			return false;
		}
	}
	
	private static boolean hasNul(byte[] raw)
	{
		for (byte current : raw)
		{
			if (current == 0)
				return true;
		}
		return false;
	}
	
	private static boolean contains(long[] values, long value)
	{
		for (long candidate : values)
		{
			if (candidate == value)
				return true;
		}
		return false;
	}
	
	private static boolean contains(int[] values, int value)
	{
		for (int candidate : values)
		{
			if (candidate == value)
				return true;
		}
		return false;
	}
	
	private static boolean has(Envelope envelope, int number)
	{
		return envelope.field(number) != null;
	}
	
	private static long u(Envelope envelope, int number)
	{
		Field field = envelope.field(number);
		return field == null ? 0 : field.value().u();
	}
	
	private static long s(Envelope envelope, int number)
	{
		Field field = envelope.field(number);
		return field == null ? 0 : field.value().s();
	}
	
	private static byte[] data(Envelope envelope, int number)
	{
		Field field = envelope.field(number);
		return field == null ? new byte[0] : field.value().data();
	}
	
	private static long u(List<Field> fields, int number)
	{
		Field field = Field.find(fields, number);
		return field == null ? 0 : field.value().u();
	}
	
	private static long s(List<Field> fields, int number)
	{
		Field field = Field.find(fields, number);
		return field == null ? 0 : field.value().s();
	}
	
	private static byte[] sha256(byte[] body)
	{
		try
		{
			return MessageDigest.getInstance("SHA-256").digest(body);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	private static WireException frameError()
	{
		return new WireException(WireException.Kind.FRAME);
	}
	
	private static WireException unsupported()
	{
		return new WireException(WireException.Kind.UNSUPPORTED);
	}
	
	private static WireException config()
	{
		return new WireException(WireException.Kind.CONFIG);
	}
	
	private enum Failure
	{
		NONE,
		FRAME,
		UNSUPPORTED
	}
	
	private static final class Outcome
	{
		private List<Field> fields = new ArrayList<>();
		private boolean unsupported;
		private Failure failure = Failure.NONE;
		private boolean missing;
		private int missingDepth;
		
		private static Outcome frame()
		{
			Outcome outcome = new Outcome();
			outcome.failure = Failure.FRAME;
			outcome.fields = null;
			return outcome;
		}
		
		private static Outcome unsupported()
		{
			Outcome outcome = new Outcome();
			outcome.failure = Failure.UNSUPPORTED;
			outcome.fields = null;
			return outcome;
		}
	}
	
	private record UOut(long value, int pos, boolean error)
	{
	}
	
	private record Taken(byte[] bytes, int pos, boolean error)
	{
	}
	
	private record Part(long number, byte[] bytes)
	{
	}
}
