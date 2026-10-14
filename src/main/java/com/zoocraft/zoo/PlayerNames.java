package com.zoocraft.zoo;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Looks up player names and profiles, including offline players the server has seen before. */
public final class PlayerNames {
    private PlayerNames() {
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> cached = server.getProfileCache().get(id);
            if (cached.isPresent()) {
                return cached.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    public static Optional<GameProfile> profile(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name.trim());
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name.trim());
    }
}
