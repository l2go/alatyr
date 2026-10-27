// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package org.alatyr.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.client.UseItem;

/**
 * CI-only fixture control. It never transforms a class or publishes bridge
 * evidence. Player actions execute on the existing client's FIFO; observations
 * still come from the real bridge. This class is not packaged with the server.
 */
public final class WorldFixture {
    private static final Map<Integer, L2Player> AUXILIARIES = new ConcurrentHashMap<>();

    public static void premain(String argument, Instrumentation unused) throws Exception {
        String[] config = argument.split(":", -1);
        if (config.length != 2 || !config[1].matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("fixture configuration");
        byte[] secret = config[1].getBytes(StandardCharsets.US_ASCII);
        ServerSocket server = new ServerSocket(Integer.parseInt(config[0]), 4, InetAddress.getByName("127.0.0.1"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (IOException ignored) { /* Process is exiting. */ }
        }));
        Thread.ofPlatform().daemon().name("parity-fixture").start(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    try {
                        String[] fields = line(socket).split("\t", -1);
                        if (fields.length < 3 || !MessageDigest.isEqual(secret, fields[0].getBytes(StandardCharsets.US_ASCII)))
                            throw new IllegalArgumentException("fixture authentication");
                        L2Player owner = player(Integer.parseInt(fields[1]));
                        if (owner.getClient() == null || !owner.getClient().isBridge())
                            throw new IllegalArgumentException("fixture owner");
                        CompletableFuture<String> result = new CompletableFuture<>();
                        owner.getClient().submit(() -> {
                            try { result.complete(apply(owner, fields)); }
                            catch (Throwable failure) { result.completeExceptionally(failure); }
                        });
                        write(socket, result.get(5, TimeUnit.SECONDS));
                    } catch (Exception failure) {
                        // An error never means a timed-out action did not execute.
                        // The Go test fails the scenario and never retries it.
                        write(socket, "error:" + failure.getClass().getSimpleName());
                    }
                } catch (IOException failure) {
                    if (!server.isClosed()) System.err.println("Parity fixture connection failed");
                }
            }
        });
    }

    private static String line(Socket socket) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 4096; i++) {
            int b = socket.getInputStream().read();
            if (b == '\n') return out.toString(StandardCharsets.UTF_8);
            if (b < 0 || b == '\r' || b == 0) throw new IOException("fixture frame");
            out.write(b);
        }
        throw new IOException("fixture frame limit");
    }

    private static void write(Socket socket, String result) throws IOException {
        byte[] bytes = (result + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65536) throw new IOException("fixture response limit");
        socket.getOutputStream().write(bytes);
    }

    private static void checkID(int id) {
        if (id < 1501000000 || id >= 1502000000) throw new IllegalArgumentException("fixture identity range");
    }

    private static L2Player player(int id) {
        checkID(id);
        L2Player player = AUXILIARIES.get(id);
        if (player == null) player = L2World.getInstance().getPlayer(id);
        if (player == null) throw new IllegalArgumentException("fixture player absent");
        return player;
    }

    private static L2Attackable monster(L2Player owner, int id) {
        Object object = L2World.getInstance().findObject(id);
        if (!(object instanceof L2Attackable npc) || !owner.getKnownList().knowsObject(npc))
            throw new IllegalArgumentException("fixture monster absent");
        return npc;
    }

    private static String apply(L2Player owner, String[] f) throws Exception {
        switch (f[2]) {
            case "die":
                if (!owner.doDie(null)) throw new IllegalStateException("already dead");
                return "ok";
            case "state":
                return state(f.length == 4 ? player(Integer.parseInt(f[3])) : owner);
            case "use": {
                if (f.length != 4) throw new IllegalArgumentException("use arguments");
                int id = Integer.parseInt(f[3]);
                if (owner.getInventory().getItemByObjectId(id) == null) throw new IllegalArgumentException("owned item absent");
                UseItem action = new UseItem();
                var field = UseItem.class.getDeclaredField("_objectId");
                field.setAccessible(true);
                field.setInt(action, id);
                action.bindDirect(owner.getClient());
                action.run();
                if (action.failed()) throw new IllegalStateException("use failed");
                return "ok";
            }
            case "freeze": {
                L2Attackable npc = monster(owner, Integer.parseInt(f[3]));
                npc.disableCoreAI(true);
                npc.abortAttack();
                npc.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
                return "ok";
            }
            case "damage": {
                L2Attackable npc = monster(owner, Integer.parseInt(f[3]));
                L2Player attacker = Integer.parseInt(f[5]) == 0 ? null : player(Integer.parseInt(f[5]));
                double damage = Double.parseDouble(f[4]);
                if (!Double.isFinite(damage) || damage <= 0 || damage > 1000000) throw new IllegalArgumentException("damage bound");
                npc.reduceCurrentHp(damage, attacker);
                return "ok";
            }
            case "openaux": {
                int id = Integer.parseInt(f[3]);
                checkID(id);
                if (AUXILIARIES.containsKey(id) || L2World.getInstance().getPlayer(id) != null)
                    throw new IllegalArgumentException("auxiliary already present");
                L2Player auxiliary = L2Player.load(id);
                if (auxiliary == null) throw new IllegalArgumentException("auxiliary load");
                auxiliary.spawnMe();
                AUXILIARIES.put(id, auxiliary);
                return "ok";
            }
            case "closeaux": {
                L2Player auxiliary = AUXILIARIES.remove(Integer.parseInt(f[3]));
                if (auxiliary == null) throw new IllegalArgumentException("auxiliary absent");
                auxiliary.store();
                auxiliary.deleteMe();
                return "ok";
            }
            case "party": {
                // These are reward fixtures without participant clients. Party
                // UI is intentionally outside the bridge's current dictionary.
                int distribution = Integer.parseInt(f[3]);
                if (distribution < 0 || distribution > 4 || f.length < 6 || f.length > 13)
                    throw new IllegalArgumentException("party arguments");
                List<L2Player> members = new ArrayList<>();
                for (int i = 4; i < f.length; i++) {
                    L2Player p = AUXILIARIES.get(Integer.parseInt(f[i]));
                    if (p == null || p.getClient() != null || p.getParty() != null || members.contains(p))
                        throw new IllegalArgumentException("auxiliary party membership");
                    members.add(p);
                }
                L2Party group = new L2Party(members.getFirst(), distribution);
                members.getFirst().setParty(group);
                for (int i = 1; i < members.size(); i++) members.get(i).joinParty(group);
                for (L2Player p : members) if (p.getParty() != group) throw new IllegalStateException("party join");
                return "ok";
            }
            case "leaveparty": {
                L2Player auxiliary = AUXILIARIES.get(Integer.parseInt(f[3]));
                if (auxiliary == null) throw new IllegalArgumentException("auxiliary absent");
                auxiliary.leaveParty();
                return "ok";
            }
            default: throw new IllegalArgumentException("fixture operation");
        }
    }

    private static String state(L2Player p) {
        StringBuilder out = new StringBuilder("{\"mp\":").append(p.getCurrentMp()).append(",\"hp\":").append(p.getCurrentHp())
                .append(",\"max_hp\":").append(p.getMaxHp()).append(",\"xp\":").append(p.getExp())
                .append(",\"sp\":").append(p.getSp()).append(",\"dead\":").append(p.isDead())
                .append(",\"point\":[").append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ())
                .append("],\"patk\":").append(p.getPAtk(null)).append(",\"pdef\":").append(p.getPDef(null))
                .append(",\"inventory\":[");
        boolean comma = false;
        for (var item : p.getInventory().getItems()) {
            if (comma) out.append(',');
            comma = true;
            out.append("{\"object\":").append(item.getObjectId()).append(",\"template\":").append(item.getItemId())
                    .append(",\"count\":").append(item.getCount()).append(",\"slot\":")
                    .append(item.isEquipped() ? item.getLocationSlot() : -1).append('}');
        }
        out.append("],\"skills\":[");
        comma = false;
        for (var skill : p.getAllSkills()) {
            if (skill.getId() != 3 && skill.getId() != 257) continue;
            if (comma) out.append(',');
            comma = true;
            out.append("{\"id\":").append(skill.getId()).append(",\"level\":").append(skill.getLevel()).append('}');
        }
        return out.append("]}").toString();
    }
}
