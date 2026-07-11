/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program;
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */

package com.comphenix.protocol.utility;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Detects the presence of the Via* family of plugins (ViaVersion, ViaBackwards, ViaRewind) at runtime, and exposes
 * the protocol version a client actually claims to be speaking, as reported by ViaVersion.
 * <p>
 * This exists because ProtocolLib's {@link MinecraftProtocolVersion} always reflects the <em>server's</em> version,
 * while a player connecting through ViaVersion may be speaking an entirely different wire protocol. Packet listeners
 * that need to branch on the client's real protocol version (rather than the server's) should prefer
 * {@link #getPlayerProtocolVersion(Player)} over assuming the server version applies.
 * <p>
 * All lookups are done reflectively - ProtocolLib does not depend on ViaVersion at compile time, and this class is
 * always safe to call whether or not any Via* plugin is installed.
 *
 * @author ProtocolLib contributors
 */
public final class ViaVersionSupport {

    private static final boolean VIA_VERSION_PRESENT = Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
    private static final boolean VIA_BACKWARDS_PRESENT = Bukkit.getPluginManager().getPlugin("ViaBackwards") != null;
    private static final boolean VIA_REWIND_PRESENT = Bukkit.getPluginManager().getPlugin("ViaRewind") != null;

    private static volatile Method getPlayerVersionMethod;
    private static volatile Object viaApiInstance;
    private static volatile boolean initialized;

    private ViaVersionSupport() {
        // Static utility class
    }

    /**
     * @return {@code true} if the ViaVersion plugin is currently loaded on this server.
     */
    public static boolean isViaVersionPresent() {
        return VIA_VERSION_PRESENT;
    }

    /**
     * @return {@code true} if the ViaBackwards plugin is currently loaded on this server.
     */
    public static boolean isViaBackwardsPresent() {
        return VIA_BACKWARDS_PRESENT;
    }

    /**
     * @return {@code true} if the ViaRewind plugin is currently loaded on this server.
     */
    public static boolean isViaRewindPresent() {
        return VIA_REWIND_PRESENT;
    }

    /**
     * @return {@code true} if any Via* protocol translation plugin is currently loaded on this server.
     */
    public static boolean isAnyViaPluginPresent() {
        return VIA_VERSION_PRESENT || VIA_BACKWARDS_PRESENT || VIA_REWIND_PRESENT;
    }

    /**
     * Retrieves the protocol version a given player's client actually claims to be speaking, as reported by
     * ViaVersion's API. Falls back to {@link MinecraftProtocolVersion#getCurrentVersion()} (the server's own
     * protocol version) if ViaVersion is not installed, or if the lookup fails for any reason.
     *
     * @param player the player to check
     * @return the client's effective protocol version
     */
    public static int getPlayerProtocolVersion(Player player) {
        if (!VIA_VERSION_PRESENT) {
            return MinecraftProtocolVersion.getCurrentVersion();
        }

        try {
            ensureInitialized();
            if (getPlayerVersionMethod != null && viaApiInstance != null) {
                Object result = getPlayerVersionMethod.invoke(viaApiInstance, (Object) player.getUniqueId());
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to the server version below - ViaVersion's internal API may have changed.
        }

        return MinecraftProtocolVersion.getCurrentVersion();
    }

    /**
     * Retrieves the protocol version a given player's client actually claims to be speaking, looked up by UUID
     * rather than a live {@link Player} instance. Useful when handling packets for a player that may already have
     * disconnected. See {@link #getPlayerProtocolVersion(Player)} for details.
     *
     * @param playerId the UUID of the player to check
     * @return the client's effective protocol version
     */
    public static int getPlayerProtocolVersion(UUID playerId) {
        if (!VIA_VERSION_PRESENT) {
            return MinecraftProtocolVersion.getCurrentVersion();
        }

        try {
            ensureInitialized();
            if (getPlayerVersionMethod != null && viaApiInstance != null) {
                Object result = getPlayerVersionMethod.invoke(viaApiInstance, (Object) playerId);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to the server version below.
        }

        return MinecraftProtocolVersion.getCurrentVersion();
    }

    /**
     * Lazily resolves ViaVersion's public API via reflection (com.viaversion.viaversion.api.Via#getAPI()). This is
     * deferred until first use so that class loading does not fail or slow down startup on servers without
     * ViaVersion installed.
     */
    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (ViaVersionSupport.class) {
            if (initialized) {
                return;
            }

            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                Method getApiMethod = viaClass.getMethod("getAPI");
                Object api = getApiMethod.invoke(null);

                getPlayerVersionMethod = api.getClass().getMethod("getPlayerVersion", UUID.class);
                viaApiInstance = api;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // ViaVersion's API package moved at some point (com.viaversion vs us.myles.viaversion). Try the
                // legacy package as a fallback before giving up.
                try {
                    Class<?> legacyViaClass = Class.forName("us.myles.viaversion.api.Via");
                    Method getApiMethod = legacyViaClass.getMethod("getAPI");
                    Object api = getApiMethod.invoke(null);

                    getPlayerVersionMethod = api.getClass().getMethod("getPlayerVersion", UUID.class);
                    viaApiInstance = api;
                } catch (ReflectiveOperationException | RuntimeException fallbackFailure) {
                    getPlayerVersionMethod = null;
                    viaApiInstance = null;
                }
            } finally {
                initialized = true;
            }
        }
    }
}
