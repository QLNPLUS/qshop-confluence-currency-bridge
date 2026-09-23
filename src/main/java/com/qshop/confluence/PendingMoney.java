package com.qshop.confluence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 旧版本离线补发账面，仅保留用于迁移已有存档。
 *
 * <p>新版本会把总余额写入 QShop 玩家钱包，因此离线存取款直接由 QShop
 * 的 UUID API 持久化。此类只读取旧版本留下的待补发金额。</p>
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
