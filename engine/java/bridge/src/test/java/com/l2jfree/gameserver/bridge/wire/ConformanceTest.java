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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Reads the shared conformance files. Those files are the only expected bytes.
 * Copyright 2026 l2go.
 */
class ConformanceTest
{
	private static final String V02_EXPECTED = "Hello min=max=1, opaque profile identifier, kind=person, mode=act, client absent.";
	private static final String CORE_SHA = "cf2c431fec2ec7bd01850eaba2a682af75015a6beceb552a0df5024039ba5545";
	private static final String PROFILE_SHA = "de3d5c23c4250ee1eceaafe942f5714338c1b79043aa713ece9912dd0d9f22bc";
	private static final String[] CORE_DIGESTS = {
			"V01 a81032f4c7cb2ff28379c1faa67ae37ba8be55fd6518ad99dc9bb1fdb0a516ce",
			"V02 3b2c0aa68d31604f1bbda71c4b662708eb3f48501c7e5bac8205ee5e29bd2179",
			"V03 12d66414a297c93c861c39473e2c0e1759fcdcee15292d1f5eb68ec11718b537",
			"V04 38ebc48abf73634c3293fb9253e7bcbb764a11e13e38511ab6eb11206440d837",
			"V05 835ee63c60744aeb310539361811c665b4c761d066c92c5c5a1399121b219d21",
			"V06 214a26567645fe0e51af38dc62865c95d48473eacb116fc704943dcfdf9cde53",
			"V07 2d34f240584a03e7364662a597ef94be2a0c64a6efc88391a03cc2e725751319",
			"V08 52c5d10d1300cb24f097ac7d5f54b45730a0ca105b2c0a5f310ed34483f0dfc7",
			"V09 7fa47851e551640f781278842f19cb7bf862177879b83858449f21d597746b9f",
			"V10 bd8c50a917d0fe59aca384afb277f06a04e1582a13148cbf91ba3fe8a1bda0ac",
			"V11 08da7c45cb204377e7e42249cda5713fa865116ddbb4cb5a1949b2e5b438a6ab",
			"V12 486934517ff4aa8f7fcb7c761e088977c51ee146f5fc581670610e4d20d529b5",
			"V13 d1b2e85831fd79f9cb8488db7a3ef485dcb6ba24b8c9c1da4d3d5d295e153ec1",
			"V14 d4b2d6a7faede382c5f2295bdf7c31fa945ee379e19984ed5c04fdbff350cf60",
			"V15 93b75ef2b6c777f9a4e037572576a8d085a5ac67676bf4657914b932d0cdc2a7",
			"V16 b407f1b7448a23216a035718459eef8a42533d61969a3b9ea65d06ebcc648757",
			"V17 7c05bbfb05bd9324ba7d0a58fdffcdffb284ee5c05e2cc416fbedc8fbbb3d364",
			"V18 218f6dc0a0bab83a9f388bf376c447731428d68d420f3fc93230a670e2ec3404",
			"V19 cb116f45656dc80588a048e486c51083eb1641526ac7cfe3cc8c4d1f460ef38a",
			"V20 9a7ba7565a7d68d30dfed7ce6b39a2871c5ba5e024c8db45dfe9312affca21d6",
			"V21 a8e773690eb56edd1780e1b85524dfbaca2b1f56cd9bdc4ee098b45204cfb11e",
			"V22 75850912ad0edc1de570350997c6bd77715303fc66262fd10d833716baee8052",
			"V23 af36abae946444fba12ec90e658a2d18870d8d7aaf2d8bb45f4cd4d147d1c310",
			"V24 1b0eb10f8931d229cf836fac019536dba2f0b3000b7ba79c71a5187a4a515412",
			"V25 e341461d9ef1b3d06e8f106eea7b6debc2c2b5a9aa4c5c6bec10bf589b17b897",
			"V26 e43372baea8874416bb49fb3813f6d653026b8737b80d719d8b37e810bcb0d5b",
			"V27 f11e750376b2a8aaffeb014b81bfdb0dab43c4bd4cce6215135cd94a762742c6",
			"V28 f13b24b3bab46b41d2b6e5c03695d9de0e5d0ee961b1218a4ece04286688cc04",
	};
	private static final String[] PROFILE_DIGESTS = {
			"BV01 frame ok 84578cbde18e9dbd54e43d04f7e864aaf95c318621684cba6e98308a08ccc363",
			"BV02 frame ok 5fe588dbd5ef08505da13073d4f40bb7098ea6b1f7e9a8b59d7c08086c97487d",
			"BV03 frame ok f1d77a2e76a78472e807068c77c554fd12015ad63e5e657aab30b1ccf7d6fcb2",
			"BV04 frame ok 40281c2035a896b9b3e87710549db8c99ecdf19e68d791e3c71acd1d5e9a0354",
			"BV05 frame ok 915ced4a33da32f93758c54c711185068e05d7575869ea3d457ce1ce5e86a983",
			"BV06 frame ok 2eed051f0c88afd26001109470cbbb581a18f970e8dcfd91ec51d99d459f8842",
			"BV07 frame ok 662c272b4ee37d8974964b96996fd965db363ddede00f715df9dedc3b0b7a463",
			"BV08 frame frame 064f2156b6f8cb11d1dc3e5434b8e8e849c49c467b32fd091751d5e57c393b10",
			"BV09 stream ok 39f293671ee0e9abc2227a11c22f2c75a46b3eb6e7ab2799324c2b68c524c8e8",
			"BV10 frame unsupported 871269896d3d39596d9ff423e3c462336a96adee308fe1ba34830d0587a01b5d",
			"BV11 frame ok 4a6edddd686470bb1e20b1a3f67d7b6ed2accd25c4fc5374fd83ff43c67636bf",
			"BV12 frame ok 5a4a920a56ebe087e5540bab149d4508c834aa6fbb90fd66913089aa4e722b31",
			"BV13 frame frame 2971be652d98a0fec6ce7b8a5bf70196cd19b7696bc5f6dcd6f9ee08ad4c04c2",
			"BV14 frame frame bbab44a4343b07277b2dc118b350a37e1a6ad97daa7c2839a2d75a06e6f4b242",
			"BV15 frame frame 02b855e30ba887630433e81cbdd29b3f947110f555756da3261c9d703c3b456d",
			"BV16 frame ok f4998c2a2a9607591b660ecff5e8680f2f4f60e79f205e0f7f0fbb2771560179",
	};
	
	@Test
	void coreFixtures() throws Exception
	{
		byte[] file = readRequired("bridge.coreVectors");
		assertThat(file.length).isEqualTo(5645);
		assertThat(sha256(file)).isEqualTo(CORE_SHA);
		Map<String, Object> root = object(Json.parse(new String(file, StandardCharsets.UTF_8)));
		assertThat(root.keySet()).containsExactly("source_commit", "source_sha256", "vectors");
		List<Fixture> fixtures = fixtures(root.get("vectors"), false);
		assertThat(fixtures).hasSize(CORE_DIGESTS.length);
		Codec codec = Codec.neutral();
		Map<String, byte[]> frames = new LinkedHashMap<>();
		Map<String, Envelope> decoded = new LinkedHashMap<>();
		for (int i = 0; i < fixtures.size(); i++)
		{
			Fixture fixture = fixtures.get(i);
			String[] digest = CORE_DIGESTS[i].split(" ");
			assertThat(fixture.id).isEqualTo(digest[0]);
			assertThat(sha256(fixture.bytes)).isEqualTo(digest[1]);
			frames.put(fixture.id, fixture.bytes);
			if (fixture.id.equals("V02"))
				assertThat(fixture.expected).isEqualTo(V02_EXPECTED);
		}
		for (Fixture fixture : fixtures)
		{
			switch (fixture.id)
			{
				case "V05" -> decoded.put(fixture.id, v05(codec, fixture.bytes, frames.get("V03"), decoded.get("V03")));
				case "V06" -> v06(codec, fixture.bytes);
				case "V10" -> v10(codec, fixture.bytes);
				case "V11" -> expect(codec, fixture.bytes, false, WireException.Kind.UNSUPPORTED);
				case "V12", "V13", "V18", "V19", "V20" -> expect(codec, fixture.bytes, false, WireException.Kind.FRAME);
				case "V21" -> expect(codec, fixture.bytes, true, WireException.Kind.FRAME);
				case "V17" -> decoded.put(fixture.id, stream(codec, fixture.bytes));
				default -> decoded.put(fixture.id, only(roundTrip(codec, fixture.bytes)));
			}
		}
		assertCoreValues(decoded);
		assertSame(decoded.get("V17"), decoded.get("V02"));
	}
	
	@Test
	void bridgeFixtures() throws Exception
	{
		byte[] file = readRequired("bridge.profileVectors");
		assertThat(file.length).isEqualTo(3269);
		assertThat(sha256(file)).isEqualTo(PROFILE_SHA);
		Map<String, Object> root = object(Json.parse(new String(file, StandardCharsets.UTF_8)));
		assertThat(root.keySet()).containsExactly("version", "profile", "license", "vectors");
		assertThat(root.get("version")).isEqualTo(1L);
		assertThat(root.get("profile")).isEqualTo("bridge/1");
		assertThat(root.get("license")).isEqualTo("Apache-2.0");
		List<Fixture> fixtures = fixtures(root.get("vectors"), true);
		assertThat(fixtures).hasSize(PROFILE_DIGESTS.length);
		Codec codec = Codec.bridge();
		Map<String, Envelope> decoded = new LinkedHashMap<>();
		for (int i = 0; i < fixtures.size(); i++)
		{
			Fixture fixture = fixtures.get(i);
			String[] digest = PROFILE_DIGESTS[i].split(" ");
			assertThat(fixture.id).isEqualTo(digest[0]);
			assertThat(fixture.transport).isEqualTo(digest[1]);
			assertThat(fixture.expected).isEqualTo(digest[2]);
			assertThat(sha256(fixture.bytes)).isEqualTo(digest[3]);
			boolean stream = fixture.transport.equals("stream");
			switch (fixture.expected)
			{
				case "ok" ->
				{
					Envelope envelope = stream ? stream(codec, fixture.bytes) : only(roundTrip(codec, fixture.bytes));
					decoded.put(fixture.id, envelope);
				}
				case "frame" -> expect(codec, fixture.bytes, stream, WireException.Kind.FRAME);
				case "unsupported" -> expect(codec, fixture.bytes, stream, WireException.Kind.UNSUPPORTED);
				default -> throw new AssertionError(fixture.id);
			}
		}
		assertBridgeValues(decoded);
		assertSame(decoded.get("BV09"), decoded.get("BV01"));
	}
	
	@Test
	void profileRulesRejectConstructedFrames()
	{
		// The neutral registry can emit these. Delivery 1 is legal for the core and illegal for the profile.
		profileFrame("sent local", result(1, 0, 1, -1, null), true);
		profileFrame("done without effects", result(2, 0, 2, -1, null), true);
		profileFrame("unrecoverable without the pair", result(5, 20, 0, -1, null), true);
		
		// Essential bridge fields are unsupported on the neutral registry, so the bytes come from the bridge
		// registry with the profile check held back.
		Field coin = coinEffect(1);
		profileFrame("effects before done", result(1, 0, 2, -1, coin), false);
		profileFrame("bridge reason zero", result(2, 0, 2, 0, emptyEffects()), false);
		for (int code : new int[] { 11, 12, 13, 19 })
			profileFrame("reason " + code, result(3, 16, 0, code, null), false);
		
		profileFrame("world pair", result(3, 10, 0, 1, null), false);
		profileFrame("later world pair", result(3, 10, 0, 14, null), false);
		profileFrame("invalid pair", result(3, 16, 0, 6, null), false);
		profileFrame("not ready pair", result(3, 16, 0, 7, null), false);
		profileFrame("rate pair", result(3, 16, 0, 8, null), false);
		profileFrame("transport pair", result(3, 10, 0, 9, null), false);
		profileFrame("closing pair", result(3, 10, 0, 10, null), false);
		profileFrame("backend pair", result(4, 17, 2, 20, null), false);
		profileFrame("too old pair", result(5, 20, 0, 1, null), false);
		
		profileFrame("generation alone", fact(Field.u(66, true, 1)), false);
		profileFrame("own teleport alone", fact(Field.u(67, true, 1)), false);
		profileFrame("own teleport false", fact(Field.u(67, true, 0)), false);
		profileFrame("own teleport false with generation", fact(Field.u(66, true, 1), Field.u(67, true, 0)), false);
	}
	
	@Test
	void aLaterStructuralFailurePrecedesAnEarlierProfileFailure()
	{
		Envelope mismatched = new Envelope(Schemas.RESULT, List.of(Field.u(1, true, 1), Field.u(2, true, 3),
				Field.u(3, true, 16), Field.u(5, true, 1), Field.u(6, true, 1), Field.u(7, true, 0),
				Field.data(64, true, ascii("x.l2jfree/bridge"))), false, 0, null);
		byte[] alone = Codec.structured().encode(List.of(mismatched));
		assertThatThrownBy(() -> Codec.bridge().decode(alone)).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(WireException.Kind.FRAME);
		
		// Type 1000 is even and absent from the registry, so the structural walk fails before the profile check.
		byte[] frame = Codec.withTrailingRecord(alone, 1000);
		assertThatThrownBy(() -> Codec.bridge().decode(frame)).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(WireException.Kind.UNSUPPORTED);
	}
	
	@Test
	void legalProfileFramesRoundTrip()
	{
		keeps(result(1, 0, 2, -1, null));
		keeps(result(2, 0, 2, -1, emptyEffects()));
		keeps(result(2, 0, 2, -1, coinEffect(-13)));
		keeps(result(3, 6, 0, 6, null));
		keeps(result(3, 10, 0, 6, null));
		keeps(result(3, 1, 0, 7, null));
		keeps(result(3, 7, 0, 8, null));
		keeps(result(3, 16, 0, 1, null));
		keeps(result(3, 16, 0, 9, null));
		keeps(result(3, 16, 0, 10, null));
		keeps(result(3, 16, 0, 18, null));
		keeps(result(4, 17, 2, -1, null));
		keeps(result(4, 19, 3, -1, null));
		keeps(result(4, 24, 2, 20, null));
		keeps(result(4, 24, 3, 20, null));
		keeps(result(5, 20, 0, 21, null));
		keeps(fact(Field.u(66, true, 4), Field.u(67, true, 1)));
	}
	
	@Test
	void extremeUnsignedAndSignedRoundTrip()
	{
		Codec codec = Codec.bridge();
		long full = 0xFFFF_FFFF_FFFF_FFFFL;
		Envelope ping = new Envelope(Schemas.PING, List.of(Field.u(1, true, full)), false, 0, null);
		Envelope decoded = only(codec.decode(codec.encode(List.of(ping))));
		assertThat(numbers(decoded.fields())).containsExactly(1);
		assertThat(u(decoded, 1)).isEqualTo(full);
		for (long extreme : new long[] { Long.MIN_VALUE, Long.MAX_VALUE })
		{
			Envelope intent = new Envelope(Schemas.INTENT, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
					Field.u(3, true, 1), Field.s(16, true, extreme)), false, 0, null);
			Envelope back = only(codec.decode(codec.encode(List.of(intent))));
			assertThat(numbers(back.fields())).containsExactly(1, 2, 3, 16);
			assertThat(s(back, 16)).isEqualTo(extreme);
		}
	}
	
	private static void profileFrame(String name, Envelope envelope, boolean neutralBytes)
	{
		List<Envelope> records = List.of(envelope);
		assertThatThrownBy(() -> Codec.bridge().encode(records)).as(name).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(WireException.Kind.FRAME);
		Codec source = neutralBytes ? Codec.neutral() : Codec.structured();
		byte[] frame = source.encode(records);
		assertThatThrownBy(() -> Codec.bridge().decode(frame)).as(name).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(WireException.Kind.FRAME);
	}
	
	private static void keeps(Envelope envelope)
	{
		Codec codec = Codec.bridge();
		assertSame(envelope, only(codec.decode(codec.encode(List.of(envelope)))));
	}
	
	private static Envelope result(long stage, long reason, long delivery, int bridgeCode, Field effects)
	{
		List<Field> fields = new ArrayList<>();
		fields.add(Field.u(1, true, 1));
		fields.add(Field.u(2, true, stage));
		fields.add(Field.u(3, true, reason));
		fields.add(Field.u(5, true, 1));
		fields.add(Field.u(6, true, 1));
		fields.add(Field.u(7, true, delivery));
		if (bridgeCode >= 0)
		{
			fields.add(Field.data(64, true, ascii("x.l2jfree/bridge")));
			fields.add(Field.u(65, true, bridgeCode));
		}
		if (effects != null)
			fields.add(effects);
		
		return new Envelope(Schemas.RESULT, List.copyOf(fields), false, 0, null);
	}
	
	private static Field emptyEffects()
	{
		return Field.items(66, true, List.of());
	}
	
	private static Field coinEffect(long delta)
	{
		Value coin = Value.ofFields(List.of(Field.u(1, true, 2), Field.s(4, true, delta)));
		return Field.items(66, true, List.of(coin));
	}
	
	private static Envelope fact(Field... extra)
	{
		List<Field> fields = new ArrayList<>();
		fields.add(Field.u(1, true, 1));
		fields.add(Field.u(2, true, 1));
		fields.add(Field.s(3, true, 0));
		fields.add(Field.u(4, true, 1));
		fields.add(Field.data(5, true, ascii("town")));
		fields.add(Field.u(6, true, 1));
		fields.addAll(List.of(extra));
		return new Envelope(Schemas.FACT, List.copyOf(fields), false, 0, null);
	}
	
	private static void assertCoreValues(Map<String, Envelope> decoded)
	{
		Envelope v01 = decoded.get("V01");
		assertThat(u(v01, 1)).isZero();
		Envelope v02 = decoded.get("V02");
		assertThat(u(v02, 1)).isEqualTo(1);
		assertThat(u(v02, 2)).isEqualTo(1);
		assertThat(v02.field(3)).isNotNull();
		assertThat(u(v02, 4)).isEqualTo(1);
		assertThat(u(v02, 5)).isEqualTo(1);
		assertThat(v02.field(6)).isNull();
		Envelope v03 = decoded.get("V03");
		assertThat(u(v03, 1)).isEqualTo(23);
		assertThat(v03.field(30)).isNull();
		assertThat(v03.field(31)).isNull();
		Envelope v04 = decoded.get("V04");
		assertThat(v04.field(30).value().items()).isEmpty();
		assertThat(u(v04, 31)).isEqualTo(1);
		Envelope v07 = decoded.get("V07");
		assertThat(u(v07, 1)).isEqualTo(1);
		assertThat(u(v07, 2)).isEqualTo(1);
		assertThat(v07.field(11).value().x()).isZero();
		assertThat(v07.field(11).value().y()).isZero();
		assertThat(v07.field(11).value().z()).isZero();
		Envelope v08 = decoded.get("V08");
		assertThat(u(v08, 1)).isEqualTo(1);
		assertThat(u(v08, 2)).isEqualTo(1);
		assertThat(u(v08, 3)).isZero();
		assertThat(u(v08, 5)).isEqualTo(11);
		assertThat(u(v08, 6)).isEqualTo(5);
		assertThat(u(v08, 7)).isEqualTo(2);
		Envelope v09 = decoded.get("V09");
		assertThat(u(v09, 1)).isEqualTo(2);
		assertThat(u(v09, 2)).isEqualTo(1);
		assertThat(u(v09, 3)).isEqualTo(1);
		Field stats = decoded.get("V14").field(25);
		assertThat(stats.value().fields()).hasSize(1);
		assertThat(stats.value().fields().get(0).type()).isEqualTo(WireType.SIGNED);
		assertThat(stats.value().fields().get(0).value().s()).isZero();
		boolean xp = false;
		for (Field member : decoded.get("V15").field(25).value().fields())
		{
			assertThat(member.type()).isEqualTo(WireType.SIGNED);
			if (member.value().s() == 5_000_000_000L)
				xp = true;
		}
		assertThat(xp).isTrue();
		assertThat(u(decoded.get("V16"), 1)).isZero();
		Envelope v22 = decoded.get("V22");
		assertThat(u(v22, 2)).isEqualTo(2);
		assertThat(s(v22.field(10).value().fields(), 1)).isEqualTo(7);
		assertThat(u(v22.field(10).value().fields(), 2)).isEqualTo(1);
	}
	
	private static void assertBridgeValues(Map<String, Envelope> decoded)
	{
		Envelope open = decoded.get("BV01");
		assertThat(u(open, 1)).isEqualTo(1);
		assertThat(u(open, 2)).isEqualTo(1);
		assertBytes(open.field(3).value().data(), ascii("bridge/1"));
		assertBytes(open.field(4).value().data(), fixtureSecret());
		assertBytes(open.field(5).value().data(), ascii("guest"));
		assertBytes(open.field(6).value().data(), ascii("Guest"));
		assertThat(u(open, 7)).isEqualTo(1);
		Envelope refused = decoded.get("BV02");
		assertThat(u(refused, 1)).isEqualTo(2);
		assertThat(u(refused, 5)).isEqualTo(11);
		for (int number : new int[] { 2, 3, 4, 6, 7, 8 })
			assertThat(refused.field(number)).isNull();
		
		Envelope resume = decoded.get("BV03");
		assertThat(u(resume, 1)).isEqualTo(7);
		assertThat(u(resume, 2)).isEqualTo(2);
		assertBytes(resume.field(3).value().data(), fixtureSecret());
		assertBytes(resume.field(4).value().data(), ascii("bridge/1"));
		Envelope close = decoded.get("BV04");
		assertThat(u(close, 1)).isEqualTo(7);
		assertThat(u(close, 2)).isEqualTo(1);
		assertThat(u(close, 3)).isZero();
		assertThat(u(decoded.get("BV05"), 1)).isEqualTo(1);
		assertThat(u(decoded.get("BV05"), 2)).isEqualTo(22);
		Envelope buy = decoded.get("BV06");
		assertThat(u(buy, 1)).isEqualTo(1);
		assertThat(u(buy, 2)).isEqualTo(2);
		assertThat(u(buy, 3)).isZero();
		assertThat(u(buy, 5)).isEqualTo(7);
		assertThat(u(buy, 6)).isEqualTo(7);
		assertThat(u(buy, 7)).isEqualTo(2);
		assertThat(buy.field(64)).isNull();
		assertThat(buy.field(65)).isNull();
		assertThat(buy.field(66).value().items()).hasSize(2);
		List<Field> item = buy.field(66).value().items().get(0).fields();
		assertThat(numbers(item)).containsExactly(1, 2, 3, 4);
		assertThat(u(item, 1)).isEqualTo(1);
		assertThat(s(item, 2)).isEqualTo(1060);
		assertThat(s(item, 3)).isEqualTo(70);
		assertThat(s(item, 4)).isEqualTo(2);
		List<Field> coin = buy.field(66).value().items().get(1).fields();
		assertThat(numbers(coin)).containsExactly(1, 4);
		assertThat(u(coin, 1)).isEqualTo(2);
		assertThat(s(coin, 4)).isEqualTo(-13);
		Envelope corrected = decoded.get("BV07");
		assertThat(corrected.field(13).value().x()).isEqualTo(1);
		assertThat(corrected.field(13).value().y()).isEqualTo(2);
		assertThat(corrected.field(13).value().z()).isEqualTo(3);
		assertThat(u(corrected, 66)).isEqualTo(4);
		assertThat(s(corrected, 64)).isEqualTo(7);
		assertThat(u(corrected, 67)).isEqualTo(1);
		assertThat(corrected.field(10)).isNull();
		assertThat(corrected.field(32)).isNull();
		Envelope checkpoint = decoded.get("BV11");
		assertThat(u(checkpoint, 1)).isEqualTo(Schemas.END);
		assertThat(u(checkpoint, 2)).isEqualTo(1);
		assertThat(checkpoint.field(3)).isNotNull();
		assertThat(u(checkpoint, 3)).isZero();
		assertThat(checkpoint.field(4).value().items()).hasSize(1);
		assertBytes(checkpoint.field(4).value().items().get(0).data(), ascii("objects"));
		assertThat(u(decoded.get("BV12"), 2)).isEqualTo(4);
		assertThat(s(decoded.get("BV12"), 64)).isEqualTo(7);
		assertThat(u(decoded.get("BV16"), 2)).isEqualTo(256);
	}
	
	private static Envelope v05(Codec codec, byte[] frame, byte[] v03, Envelope known)
	{
		List<Envelope> records = codec.decode(frame);
		Envelope envelope = only(records);
		assertSame(envelope, known);
		byte[] canonical = codec.encode(records);
		// V03's fixture is the known-fields view already stored in the shared file.
		assertBytes(canonical, v03);
		assertThat(canonical.length).isLessThan(frame.length);
		assertBytes(codec.encode(codec.decode(canonical)), canonical);
		return envelope;
	}
	
	private static void v06(Codec codec, byte[] frame)
	{
		List<Envelope> records = codec.decode(frame);
		assertThat(records).hasSize(1);
		assertThat(records.get(0).type()).isEqualTo(8);
		assertThat(records.get(0).unsupported()).isTrue();
		expectEncode(codec, records, WireException.Kind.UNSUPPORTED);
	}
	
	private static void v10(Codec codec, byte[] frame)
	{
		List<Envelope> records = codec.decode(frame);
		assertThat(records).isEmpty();
		expectEncode(codec, records, WireException.Kind.FRAME);
	}
	
	private static List<Envelope> roundTrip(Codec codec, byte[] frame)
	{
		List<Envelope> records = codec.decode(frame);
		assertBytes(codec.encode(records), frame);
		assertBytes(codec.encode(codec.decode(frame)), frame);
		return records;
	}
	
	private static Envelope stream(Codec codec, byte[] prefixed)
	{
		List<Envelope> records = codec.read(new ByteArrayInputStream(prefixed));
		byte[] inner = inner(prefixed);
		assertBytes(codec.encode(records), inner);
		assertBytes(codec.encode(codec.decode(inner)), inner);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		codec.write(out, records);
		assertBytes(out.toByteArray(), prefixed);
		return only(records);
	}
	
	private static byte[] inner(byte[] prefixed)
	{
		int i = 0;
		while (i < prefixed.length && (prefixed[i] & 0xff) >= 128)
			i++;
		
		assertThat(i).isLessThan(prefixed.length);
		return Arrays.copyOfRange(prefixed, i + 1, prefixed.length);
	}
	
	private static void expect(Codec codec, byte[] frame, boolean stream, WireException.Kind kind)
	{
		if (stream)
		{
			assertThatThrownBy(() -> codec.read(new ByteArrayInputStream(frame))).isInstanceOf(WireException.class)
					.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(kind);
			return;
		}
		assertThatThrownBy(() -> codec.decode(frame)).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(kind);
	}
	
	private static void expectEncode(Codec codec, List<Envelope> records, WireException.Kind kind)
	{
		assertThatThrownBy(() -> codec.encode(records)).isInstanceOf(WireException.class)
				.extracting(thrown -> ((WireException) thrown).kind()).isEqualTo(kind);
	}
	
	private static Envelope only(List<Envelope> records)
	{
		assertThat(records).hasSize(1);
		return records.get(0);
	}
	
	private static void assertSame(Envelope left, Envelope right)
	{
		assertThat(left.type()).isEqualTo(right.type());
		assertThat(left.unsupported()).isEqualTo(right.unsupported());
		assertFields(left.fields(), right.fields());
	}
	
	private static void assertFields(List<Field> left, List<Field> right)
	{
		assertThat(numbers(left)).isEqualTo(numbers(right));
		for (Field field : left)
		{
			Field other = Field.find(right, field.number());
			assertThat(other.essential()).isEqualTo(field.essential());
			assertThat(other.type()).isEqualTo(field.type());
			assertValue(field.value(), other.value());
		}
	}
	
	private static void assertValue(Value left, Value right)
	{
		assertThat(left.u()).isEqualTo(right.u());
		assertThat(left.s()).isEqualTo(right.s());
		assertBytes(left.data(), right.data());
		assertThat(left.x()).isEqualTo(right.x());
		assertThat(left.y()).isEqualTo(right.y());
		assertThat(left.z()).isEqualTo(right.z());
		assertBytes(left.fixed(), right.fixed());
		assertFields(left.fields(), right.fields());
		assertThat(left.items()).hasSameSizeAs(right.items());
		for (int i = 0; i < left.items().size(); i++)
			assertValue(left.items().get(i), right.items().get(i));
	}
	
	private static List<Integer> numbers(List<Field> fields)
	{
		List<Integer> numbers = new ArrayList<>();
		for (Field field : fields)
			numbers.add(field.number());
		
		numbers.sort(Integer::compareTo);
		return numbers;
	}
	
	private static long u(Envelope envelope, int number)
	{
		assertThat(envelope.field(number)).isNotNull();
		return envelope.field(number).value().u();
	}
	
	private static long u(List<Field> fields, int number)
	{
		Field field = Field.find(fields, number);
		assertThat(field).isNotNull();
		return field.value().u();
	}
	
	private static long s(Envelope envelope, int number)
	{
		assertThat(envelope.field(number)).isNotNull();
		return envelope.field(number).value().s();
	}
	
	private static long s(List<Field> fields, int number)
	{
		Field field = Field.find(fields, number);
		assertThat(field).isNotNull();
		return field.value().s();
	}
	
	private static void assertBytes(byte[] actual, byte[] expected)
	{
		assertThat(Arrays.equals(actual, expected)).isTrue();
	}
	
	private static byte[] fixtureSecret()
	{
		byte[] secret = new byte[32];
		for (int i = 0; i < secret.length; i++)
			secret[i] = (byte) (i + 1);
		
		return secret;
	}
	
	private static byte[] ascii(String text)
	{
		return text.getBytes(StandardCharsets.US_ASCII);
	}
	
	private static byte[] readRequired(String property) throws Exception
	{
		String path = System.getProperty(property);
		if (path == null || path.isBlank())
			throw new AssertionError(property + " is not set");
		
		Path file = Path.of(path);
		if (!Files.isRegularFile(file))
			throw new AssertionError(property + " is missing");
		
		return Files.readAllBytes(file);
	}
	
	private static String sha256(byte[] bytes) throws NoSuchAlgorithmException
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
	
	private static List<Fixture> fixtures(Object value, boolean profile)
	{
		List<Fixture> fixtures = new ArrayList<>();
		for (Object item : list(value))
		{
			Map<String, Object> vector = object(item);
			if (profile)
				assertThat(vector.keySet()).containsExactly("id", "transport", "hex", "expected");
			else
				assertThat(vector.keySet()).containsExactly("id", "hex", "expected");
			
			fixtures.add(new Fixture((String) vector.get("id"), profile ? (String) vector.get("transport") : "frame",
					hex((String) vector.get("hex")), (String) vector.get("expected")));
		}
		return fixtures;
	}
	
	private static byte[] hex(String text)
	{
		String cleaned = text.replace(" ", "");
		assertThat(cleaned.length() % 2).isZero();
		return HexFormat.of().parseHex(cleaned);
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Object> object(Object value)
	{
		assertThat(value).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
	}
	
	@SuppressWarnings("unchecked")
	private static List<Object> list(Object value)
	{
		assertThat(value).isInstanceOf(List.class);
		return (List<Object>) value;
	}
	
	private record Fixture(String id, String transport, byte[] bytes, String expected)
	{
	}
	
	/**
	 * The fixture documents are small JSON texts. No JSON library is added for them.
	 */
	private static final class Json
	{
		private final String text;
		private int pos;
		
		private Json(String text)
		{
			this.text = text;
		}
		
		private static Object parse(String text)
		{
			Json json = new Json(text);
			json.skip();
			Object value = json.value();
			json.skip();
			if (json.pos != text.length())
				throw new IllegalArgumentException("trailing JSON");
			
			return value;
		}
		
		private Object value()
		{
			skip();
			if (pos >= text.length())
				throw new IllegalArgumentException("truncated JSON");
			
			char c = text.charAt(pos);
			if (c == '{')
				return object();
			if (c == '[')
				return array();
			if (c == '"')
				return string();
			if (c == '-' || c >= '0' && c <= '9')
				return number();
			
			throw new IllegalArgumentException("JSON value");
		}
		
		private Map<String, Object> object()
		{
			pos++;
			Map<String, Object> fields = new LinkedHashMap<>();
			skip();
			if (peek('}'))
			{
				pos++;
				return fields;
			}
			while (true)
			{
				skip();
				String key = string();
				if (fields.containsKey(key))
					throw new IllegalArgumentException("duplicate JSON field");
				
				skip();
				if (!peek(':'))
					throw new IllegalArgumentException("JSON colon");
				
				pos++;
				fields.put(key, value());
				skip();
				if (peek('}'))
				{
					pos++;
					return fields;
				}
				if (!peek(','))
					throw new IllegalArgumentException("JSON comma");
				
				pos++;
			}
		}
		
		private List<Object> array()
		{
			pos++;
			List<Object> items = new ArrayList<>();
			skip();
			if (peek(']'))
			{
				pos++;
				return items;
			}
			while (true)
			{
				items.add(value());
				skip();
				if (peek(']'))
				{
					pos++;
					return items;
				}
				if (!peek(','))
					throw new IllegalArgumentException("JSON comma");
				
				pos++;
			}
		}
		
		private String string()
		{
			if (!peek('"'))
				throw new IllegalArgumentException("JSON string");
			
			pos++;
			StringBuilder out = new StringBuilder();
			while (pos < text.length())
			{
				char c = text.charAt(pos++);
				if (c == '"')
					return out.toString();
				
				if (c != '\\')
				{
					out.append(c);
					continue;
				}
				if (pos >= text.length())
					break;
				
				char escaped = text.charAt(pos++);
				switch (escaped)
				{
					case '"', '\\', '/' -> out.append(escaped);
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'n' -> out.append('\n');
					case 'r' -> out.append('\r');
					case 't' -> out.append('\t');
					case 'u' ->
					{
						if (pos + 4 > text.length())
							throw new IllegalArgumentException("JSON unicode");
						
						out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						pos += 4;
					}
					default -> throw new IllegalArgumentException("JSON escape");
				}
			}
			throw new IllegalArgumentException("truncated JSON string");
		}
		
		private Long number()
		{
			int start = pos;
			if (peek('-'))
				pos++;
			
			if (pos >= text.length() || text.charAt(pos) < '0' || text.charAt(pos) > '9')
				throw new IllegalArgumentException("JSON number");
			
			while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9')
				pos++;
			
			if (pos < text.length() && (text.charAt(pos) == '.' || text.charAt(pos) == 'e' || text.charAt(pos) == 'E'))
				throw new IllegalArgumentException("JSON fraction");
			
			return Long.parseLong(text.substring(start, pos));
		}
		
		private void skip()
		{
			while (pos < text.length())
			{
				char c = text.charAt(pos);
				if (c != ' ' && c != '\n' && c != '\r' && c != '\t')
					return;
				
				pos++;
			}
		}
		
		private boolean peek(char c)
		{
			return pos < text.length() && text.charAt(pos) == c;
		}
	}
}
