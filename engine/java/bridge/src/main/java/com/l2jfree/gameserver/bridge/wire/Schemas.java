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

import java.util.HashMap;
import java.util.Map;

/**
 * The frozen core registry and the bridge/1 extensions. Field numbers and wire types are the shared schema.
 */
final class Schemas
{
	static final long PING = 1;
	static final long HELLO = 2;
	static final long PONG = 3;
	static final long WELCOME = 4;
	static final long CHECKPOINT = 6;
	static final long FACT = 8;
	static final long INTENT = 10;
	static final long RESULT = 12;
	static final long CONTROL = 14;
	static final long BYE = 16;
	static final long OPEN = 256;
	static final long OPENED = 258;
	static final long RESUME = 260;
	static final long CLOSE = 262;
	static final long INTENT_ERROR = 264;
	
	static final long PERSON = 1;
	static final long BOT = 2;
	static final long AGENT = 3;
	static final long ACT = 1;
	static final long OBSERVE = 2;
	static final long BEGIN = 1;
	static final long END = 2;
	static final long TAKE_CONTROL = 1;
	static final long GRANTED = 2;
	static final long LOST = 3;
	static final long RESYNC = 4;
	static final long AUTHORED = 1;
	static final long SYSTEM = 2;
	static final long PLAYER = 3;
	static final long UNCLASSIFIED = 4;
	
	private Schemas()
	{
	}
	
	static Map<Long, Map<Integer, Spec>> neutral(Limits bounds)
	{
		Map<Integer, Spec> advertised = new HashMap<>();
		for (int i = 1; i <= 34; i++)
		{
			Spec spec = Spec.essential(Shape.UINT);
			spec.required = true;
			advertised.put(i, spec);
		}
		Map<Long, Map<Integer, Spec>> registry = envelopes(advertised, bounds);
		Spec i32 = Spec.essential(Shape.INT);
		i32.bits = 32;
		Spec u31 = Spec.essential(Shape.UINT);
		u31.bits = 31;
		Spec text = obj(textSchema());
		text.essential = false;
		Spec position = Spec.essential(Shape.POSITION);
		Spec ref = obj(map(req(i32), Spec.required(Shape.UINT)));
		Map<Integer, Spec> option = map(positive(Spec.required(Shape.UINT)), req(text));
		Map<Integer, Spec> good = new HashMap<>();
		good.put(1, positive(Spec.essential(Shape.UINT)));
		good.put(2, i32);
		good.put(3, req(i32));
		good.put(4, Spec.required(Shape.INT));
		good.put(5, Spec.essential(Shape.INT));
		good.put(6, text);
		good.put(7, i32);
		Map<Integer, Spec> stats = new HashMap<>();
		for (int n = 1; n <= 7; n++)
			stats.put(n, Spec.essential(Shape.INT));
		
		Map<Integer, Spec> entry = map(Spec.required(Shape.INT), Spec.required(Shape.STRING), req(text));
		Map<Integer, Spec> quest = new HashMap<>();
		quest.put(1, req(i32));
		quest.put(2, req(i32));
		quest.put(3, req(text));
		quest.put(4, Spec.required(Shape.STRING));
		Map<Integer, Spec> skill = new HashMap<>();
		skill.put(1, req(i32));
		skill.put(2, req(i32));
		skill.put(3, Spec.required(Shape.BOOL));
		skill.put(4, Spec.required(Shape.BOOL));
		Map<Integer, Spec> use = map(req(i32), req(i32));
		Map<Integer, Spec> lesson = new HashMap<>();
		lesson.put(1, positive(Spec.required(Shape.UINT)));
		lesson.put(2, req(i32));
		lesson.put(3, Spec.required(Shape.INT));
		lesson.put(4, req(i32));
		lesson.put(5, Spec.required(Shape.INT));
		Spec entries = list(entry);
		entries.essential = false;
		Map<Integer, Spec> fact = registry.get(FACT);
		fact.put(10, ref);
		fact.put(11, Spec.essential(Shape.STRING));
		fact.put(12, text);
		fact.put(13, position);
		fact.put(14, position);
		fact.put(15, u31);
		fact.put(16, ref);
		fact.put(17, i32);
		fact.put(18, Spec.essential(Shape.BOOL));
		fact.put(19, Spec.essential(Shape.BOOL));
		fact.put(20, text);
		fact.put(21, list(option));
		fact.put(22, i32);
		fact.put(23, Spec.essential(Shape.INT));
		fact.put(24, list(good));
		fact.put(25, obj(stats));
		fact.put(26, Spec.optional(Shape.STRING));
		fact.put(27, Spec.essential(Shape.INT));
		fact.put(28, entries);
		fact.put(29, list(quest));
		fact.put(30, list(skill));
		fact.put(31, Spec.essential(Shape.BOOL));
		fact.put(32, positive(Spec.essential(Shape.UINT)));
		fact.put(33, positive(Spec.essential(Shape.UINT)));
		fact.put(34, list(lesson));
		Map<Integer, Spec> intent = registry.get(INTENT);
		intent.put(10, ref);
		intent.put(11, position);
		intent.put(12, Spec.essential(Shape.UINT));
		intent.put(13, Spec.essential(Shape.UINT));
		intent.put(14, i32);
		intent.put(15, Spec.essential(Shape.UINT));
		intent.put(16, Spec.essential(Shape.INT));
		intent.put(17, Spec.essential(Shape.INT));
		intent.put(18, obj(use));
		intent.put(19, Spec.essential(Shape.BOOL));
		intent.put(20, ref);
		return registry;
	}
	
	static Map<Long, Map<Integer, Spec>> bridge(Limits bounds)
	{
		Map<Long, Map<Integer, Spec>> registry = neutral(bounds);
		Spec i32 = Spec.essential(Shape.INT);
		i32.bits = 32;
		Spec text64 = Spec.essential(Shape.STRING);
		text64.maxBytes = 4096;
		Spec generation = Spec.essential(Shape.UINT);
		generation.nonzero = true;
		Map<Integer, Spec> fact = registry.get(FACT);
		fact.put(64, i32);
		fact.put(65, i32.copy());
		fact.put(66, generation);
		fact.put(67, Spec.essential(Shape.BOOL));
		fact.put(68, savedOptions());
		fact.put(69, Spec.essential(Shape.UINT));
		fact.put(70, Spec.essential(Shape.BOOL));
		fact.put(71, Spec.essential(Shape.UINT));
		fact.put(72, messageArgs());
		fact.put(73, i32.copy());
		fact.put(74, Spec.essential(Shape.INT));
		Map<Integer, Spec> intent = registry.get(INTENT);
		intent.put(64, i32.copy());
		intent.put(66, i32.copy());
		Spec saved = Spec.essential(Shape.STRING);
		saved.maxBytes = 4096;
		intent.put(68, saved);
		Map<Integer, Spec> result = registry.get(RESULT);
		result.put(64, Spec.essential(Shape.STRING));
		result.put(65, Spec.essential(Shape.UINT));
		result.put(66, effectList());
		Spec account = Spec.required(Shape.STRING);
		account.maxBytes = 128;
		Spec secret = Spec.required(Shape.BYTES);
		Map<Integer, Spec> open = new HashMap<>();
		open.put(1, Spec.required(Shape.UINT));
		open.put(2, Spec.required(Shape.UINT));
		open.put(3, Spec.required(Shape.STRING));
		open.put(4, secret);
		open.put(5, account);
		open.put(6, account.copy());
		open.put(7, Spec.required(Shape.UINT));
		registry.put(OPEN, open);
		Spec world = Spec.essential(Shape.STRING);
		world.maxBytes = 256;
		registry.put(OPENED, opened(world));
		Map<Integer, Spec> resume = new HashMap<>();
		resume.put(1, Spec.required(Shape.UINT));
		resume.put(2, Spec.required(Shape.UINT));
		resume.put(3, secret.copy());
		resume.put(4, Spec.required(Shape.STRING));
		registry.put(RESUME, resume);
		Spec text = Spec.optional(Shape.OBJECT);
		text.object = textSchema();
		Map<Integer, Spec> close = new HashMap<>();
		close.put(1, Spec.required(Shape.UINT));
		close.put(2, Spec.required(Shape.UINT));
		close.put(3, Spec.required(Shape.UINT));
		close.put(4, text);
		registry.put(CLOSE, close);
		Map<Integer, Spec> error = new HashMap<>();
		error.put(1, Spec.required(Shape.UINT));
		error.put(2, Spec.required(Shape.UINT));
		error.put(3, text.copy());
		registry.put(INTENT_ERROR, error);
		return registry;
	}
	
	private static Map<Long, Map<Integer, Spec>> envelopes(Map<Integer, Spec> advertised, Limits bounds)
	{
		Spec text = Spec.optional(Shape.OBJECT);
		text.object = textSchema();
		Spec ident = Spec.required(Shape.IDENTIFIER);
		Spec world = ident.copy();
		world.maxBytes = bounds.worldBytes;
		Spec client = Spec.optional(Shape.STRING);
		client.maxBytes = bounds.clientBytes;
		Spec limits = Spec.required(Shape.OBJECT);
		limits.object = advertised;
		Spec domains = Spec.essential(Shape.LIST);
		Spec element = Spec.required(Shape.IDENTIFIER);
		domains.element = element;
		Map<Long, Map<Integer, Spec>> registry = new HashMap<>();
		registry.put(PING, Map.of(1, Spec.required(Shape.UINT)));
		registry.put(PONG, Map.of(1, Spec.required(Shape.UINT)));
		Map<Integer, Spec> hello = new HashMap<>();
		hello.put(1, Spec.required(Shape.UINT));
		hello.put(2, Spec.required(Shape.UINT));
		hello.put(3, ident);
		hello.put(4, enumU(PERSON, BOT, AGENT));
		hello.put(5, enumU(ACT, OBSERVE));
		hello.put(6, client);
		registry.put(HELLO, hello);
		Map<Integer, Spec> welcome = new HashMap<>();
		welcome.put(1, Spec.required(Shape.UINT));
		welcome.put(2, ident.copy());
		welcome.put(3, world);
		welcome.put(4, Spec.required(Shape.UINT));
		welcome.put(5, enumU(ACT, OBSERVE));
		welcome.put(6, Spec.required(Shape.UINT));
		welcome.put(7, limits);
		registry.put(WELCOME, welcome);
		Map<Integer, Spec> checkpoint = new HashMap<>();
		checkpoint.put(1, enumU(BEGIN, END));
		checkpoint.put(2, Spec.required(Shape.UINT));
		checkpoint.put(3, Spec.required(Shape.BOOL));
		checkpoint.put(4, domains);
		registry.put(CHECKPOINT, checkpoint);
		Map<Integer, Spec> fact = new HashMap<>();
		fact.put(1, Spec.required(Shape.UINT));
		fact.put(2, Spec.required(Shape.UINT));
		fact.put(3, Spec.required(Shape.INT));
		fact.put(4, Spec.required(Shape.UINT));
		fact.put(5, ident.copy());
		fact.put(6, Spec.required(Shape.BOOL));
		registry.put(FACT, fact);
		Map<Integer, Spec> intent = new HashMap<>();
		intent.put(1, Spec.required(Shape.UINT));
		intent.put(2, Spec.required(Shape.UINT));
		intent.put(3, Spec.required(Shape.UINT));
		intent.put(4, Spec.essential(Shape.UINT));
		registry.put(INTENT, intent);
		Map<Integer, Spec> result = new HashMap<>();
		result.put(1, Spec.required(Shape.UINT));
		result.put(2, enumU(1, 2, 3, 4, 5));
		long[] reasons = new long[25];
		for (int i = 0; i < reasons.length; i++)
			reasons[i] = i;
		
		result.put(3, enumU(reasons));
		result.put(4, text);
		result.put(5, Spec.required(Shape.UINT));
		result.put(6, Spec.required(Shape.UINT));
		result.put(7, enumU(0, 1, 2, 3));
		registry.put(RESULT, result);
		Map<Integer, Spec> control = new HashMap<>();
		control.put(1, enumU(TAKE_CONTROL, GRANTED, LOST, RESYNC));
		control.put(2, Spec.essential(Shape.UINT));
		control.put(3, Spec.essential(Shape.UINT));
		registry.put(CONTROL, control);
		Map<Integer, Spec> bye = new HashMap<>();
		bye.put(1, enumU(0, 1, 2, 3, 4, 5, 6, 7, 8));
		bye.put(2, text.copy());
		registry.put(BYE, bye);
		return registry;
	}
	
	private static Map<Integer, Spec> opened(Spec world)
	{
		Spec limits = Spec.essential(Shape.OBJECT);
		Map<Integer, Spec> fields = new HashMap<>();
		int[] numbers = { 1, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17, 20, 21, 22, 23, 24, 26, 27, 28, 29, 30 };
		for (int number : numbers)
		{
			Spec spec = Spec.essential(Shape.UINT);
			spec.required = true;
			fields.put(number, spec);
		}
		limits.object = fields;
		Map<Integer, Spec> opened = new HashMap<>();
		opened.put(1, Spec.required(Shape.UINT));
		opened.put(2, Spec.essential(Shape.UINT));
		opened.put(3, world);
		opened.put(4, Spec.essential(Shape.UINT));
		opened.put(5, Spec.required(Shape.UINT));
		opened.put(6, limits);
		opened.put(7, Spec.essential(Shape.UINT));
		opened.put(8, Spec.essential(Shape.STRING));
		return opened;
	}
	
	private static Spec savedOptions()
	{
		Map<Integer, Spec> option = new HashMap<>();
		option.put(1, positive(Spec.required(Shape.UINT)));
		Spec label = Spec.required(Shape.OBJECT);
		label.object = textSchema();
		option.put(2, label);
		Spec action = Spec.required(Shape.STRING);
		action.maxBytes = 4096;
		option.put(3, action);
		return list(option);
	}
	
	private static Spec messageArgs()
	{
		Map<Integer, Spec> arg = new HashMap<>();
		arg.put(1, Spec.required(Shape.UINT));
		arg.put(2, Spec.essential(Shape.STRING));
		arg.put(3, Spec.essential(Shape.INT));
		arg.put(4, Spec.essential(Shape.INT));
		arg.put(5, Spec.essential(Shape.INT));
		return list(arg);
	}
	
	private static Spec effectList()
	{
		Spec i32 = Spec.essential(Shape.INT);
		i32.bits = 32;
		Spec s64 = Spec.essential(Shape.INT);
		Map<Integer, Spec> effect = new HashMap<>();
		effect.put(1, Spec.required(Shape.UINT));
		effect.put(2, i32);
		effect.put(3, i32.copy());
		effect.put(4, s64);
		effect.put(5, i32.copy());
		effect.put(6, s64.copy());
		effect.put(7, s64.copy());
		effect.put(8, i32.copy());
		return list(effect);
	}
	
	static Map<Integer, Spec> textSchema()
	{
		Map<Integer, Spec> text = new HashMap<>();
		text.put(1, Spec.required(Shape.STRING));
		text.put(2, enumU(AUTHORED, SYSTEM, PLAYER, UNCLASSIFIED));
		text.put(3, Spec.optional(Shape.LANGUAGE));
		return text;
	}
	
	private static Spec obj(Map<Integer, Spec> schema)
	{
		Spec spec = Spec.essential(Shape.OBJECT);
		spec.object = schema;
		return spec;
	}
	
	private static Spec list(Map<Integer, Spec> schema)
	{
		Spec element = Spec.required(Shape.OBJECT);
		element.object = schema;
		Spec spec = Spec.essential(Shape.LIST);
		spec.element = element;
		return spec;
	}
	
	private static Spec req(Spec spec)
	{
		Spec copy = spec.copy();
		copy.required = true;
		copy.essential = true;
		return copy;
	}
	
	private static Spec positive(Spec spec)
	{
		Spec copy = spec.copy();
		copy.nonzero = true;
		return copy;
	}
	
	private static Spec enumU(long... values)
	{
		Spec spec = Spec.required(Shape.UINT);
		spec.enums = values;
		return spec;
	}
	
	private static Map<Integer, Spec> map(Spec first, Spec second)
	{
		Map<Integer, Spec> schema = new HashMap<>();
		schema.put(1, first);
		schema.put(2, second);
		return schema;
	}
	
	private static Map<Integer, Spec> map(Spec first, Spec second, Spec third)
	{
		Map<Integer, Spec> schema = map(first, second);
		schema.put(3, third);
		return schema;
	}
}
