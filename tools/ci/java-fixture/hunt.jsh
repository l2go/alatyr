// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go
// Runs the existing Java calculator against the same explicit vectors as Go.

import java.nio.file.*;
import java.util.*;
import com.l2jfree.gameserver.model.rewards.HuntRewards;

String parityPlan(String[] f) {
    List<HuntRewards.Side> sides = new ArrayList<>();
    if (!f[6].equals("-")) for (String text : f[6].split(";")) {
        String[] v = text.split(":", -1);
        List<HuntRewards.Member> members = new ArrayList<>();
        if (!v[3].equals("-")) for (String member : v[3].split(",")) {
            String[] m = member.split("@");
            members.add(new HuntRewards.Member(Long.parseLong(m[0]), Integer.parseInt(m[1]), m.length == 3 ? Double.parseDouble(m[2]) : 1, 1));
        }
        long id = Long.parseLong(v[0]);
        sides.add(new HuntRewards.Side(id, Double.parseDouble(v[1]), Integer.parseInt(v[2]), id == 0, members, Integer.parseInt(v[4])));
    }
    List<HuntRewards.Drop> drops = new ArrayList<>();
    long coins = Long.parseLong(f[5]);
    if (coins > 0) drops.add(new HuntRewards.Drop(57, coins));
    if (!f[7].equals("-")) for (String text : f[7].split(";")) {
        String[] d = text.split(":");
        drops.add(new HuntRewards.Drop(Integer.parseInt(d[0]), Long.parseLong(d[1])));
    }
    var input = new HuntRewards.Snapshot("test-world", Long.parseLong(f[1]), Integer.parseInt(f[2]), Long.parseLong(f[3]), Integer.parseInt(f[4]), 0, 0, sides);
    var out = HuntRewards.calculate(input, drops);
    StringJoiner s = new StringJoiner(";"), p = new StringJoiner(";"), i = new StringJoiner(";");
    for (var side : out.sides()) {
        s.add("S" + side.sideId() + ":" + side.coins() + ":" + side.nextCoinCursor());
        for (var member : side.players()) p.add("P" + member.playerId() + ":" + member.xp() + ":" + member.sp() + ":" + member.coins());
    }
    for (var item : out.items()) i.add("I" + item.drop().itemId() + ":" + item.drop().count() + ":" + item.sideId() + ":" + item.unclaimed());
    return s + "|" + p + "|" + i;
}

try {
    int checked = 0;
    for (String line : Files.readAllLines(Path.of(System.getenv("JAVA_PARITY_VECTORS")))) {
        if (line.isBlank() || line.startsWith("#")) continue;
        String[] fields = line.split("\t", -1);
        if (fields.length != 9 || !parityPlan(fields).equals(fields[8])) throw new AssertionError("Hunt vector: " + fields[0]);
        checked++;
    }
    if (checked != 14) throw new AssertionError("Hunt vector count");
    System.out.println("HUNT_PARITY_OK=" + checked);
} catch (Throwable failure) {
    System.err.println("Hunt parity failed: " + failure);
    System.exit(1);
}
/exit
