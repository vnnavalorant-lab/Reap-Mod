package com.reap;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Salva a progressão de todos os jogadores no mundo. */
public class ReapState extends PersistentState {
    private final Map<UUID, ReapData> map = new HashMap<>();

    public ReapData get(UUID id) {
        return map.computeIfAbsent(id, k -> new ReapData());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, ReapData> e : map.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", e.getKey());
            e.getValue().write(c);
            list.add(c);
        }
        nbt.put("players", list);
        return nbt;
    }

    public static ReapState fromNbt(NbtCompound nbt) {
        ReapState s = new ReapState();
        NbtList list = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            s.map.put(c.getUuid("id"), ReapData.read(c));
        }
        return s;
    }

    public static ReapState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ReapState::fromNbt, ReapState::new, "reap");
    }
}
