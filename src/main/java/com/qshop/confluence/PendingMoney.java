package com.qshop.confluence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 离线玩家的绑定货币账面。
 *
 * <p>Confluence 的钱是物品，玩家离线时没有实体可以增删钱币，
 * 所以出售箱这类"离线收益"先记在这里，玩家登录时再以钱币形式补发。</p>
 */
public class PendingMoney extends SavedData {

    private static final String DATA_NAME = "qshop_confluence_pending";

    private final Map<UUID, Long> pending = new HashMap<>();

    public static PendingMoney get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PendingMoney::new, PendingMoney::load, null), DATA_NAME);
    }

    public static PendingMoney load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingMoney data = new PendingMoney();
        CompoundTag entries = tag.getCompound("pending");
        for (String key : entries.getAllKeys()) {
            try {
                long value = entries.getLong(key);
                if (value != 0L) {
                    data.pending.put(UUID.fromString(key), value);
                }
            } catch (IllegalArgumentException ignored) {
                // 损坏的键直接跳过，不要因为一条脏数据让存档读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : pending.entrySet()) {
            if (entry.getValue() != null && entry.getValue() != 0L) {
                entries.putLong(entry.getKey().toString(), entry.getValue());
            }
        }
        tag.put("pending", entries);
        return tag;
    }

    public long peek(UUID playerId) {
        Long value = pending.get(playerId);
        return value == null ? 0L : value;
    }

    public void set(UUID playerId, long amount) {
        long clamped = Math.max(0L, amount);
        if (clamped == 0L) {
            if (pending.remove(playerId) != null) {
                setDirty();
            }
            return;
        }
        if (!Long.valueOf(clamped).equals(pending.put(playerId, clamped))) {
            setDirty();
        }
    }

    public void add(UUID playerId, long amount) {
        set(playerId, peek(playerId) + amount);
    }

    /** 取出并清空，返回取出前的账面金额。 */
    public long take(UUID playerId) {
        Long value = pending.remove(playerId);
        if (value == null || value == 0L) {
            return 0L;
        }
        setDirty();
        return value;
    }
}
