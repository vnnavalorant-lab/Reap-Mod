package com.reap.client;

import com.reap.ReapData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientData {
    public static final Map<UUID, ReapData> MAP = new ConcurrentHashMap<>();
    public static long levelUpAt = 0;
    public static int levelUpLevel = 0;

    private ClientData() {}
}
