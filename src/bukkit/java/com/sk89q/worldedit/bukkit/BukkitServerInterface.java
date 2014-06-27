/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit;

import com.sk89q.bukkit.util.CommandInfo;
import com.sk89q.bukkit.util.CommandRegistration;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.minecraft.util.commands.CommandsManager;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Preference;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

public class BukkitServerInterface extends ServerInterface {
    public Server server;
    public WorldEditPlugin plugin;
    private CommandRegistration dynamicCommands;
    private BukkitBiomeTypes biomes;
    private boolean hookingEvents;

    public BukkitServerInterface(WorldEditPlugin plugin, Server server) {
        this.plugin = plugin;
        this.server = server;
        this.biomes = new BukkitBiomeTypes();
        dynamicCommands = new CommandRegistration(plugin);
    }

    boolean isHookingEvents() {
        return hookingEvents;
    }

    @Override
    public int resolveItem(String name) {
        Material mat = Material.matchMaterial(name);
        return mat == null ? 0 : mat.getId();
    }

    @Override
    public boolean isValidMobType(String type) {
        final EntityType entityType = EntityType.fromName(type);
        return entityType != null && entityType.isAlive();
    }

    @Override
    public void reload() {
        plugin.loadConfiguration();
    }

    @Override
    public BiomeTypes getBiomes() {
        return biomes;
    }

    @Override
    public int schedule(long delay, long period, Runnable task) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, period);
    }

    @Override
    public List<LocalWorld> getWorlds() {
        List<World> worlds = server.getWorlds();
        List<LocalWorld> ret = new ArrayList<LocalWorld>(worlds.size());

        for (World world : worlds) {
            ret.add(BukkitUtil.getLocalWorld(world));
        }

        return ret;
    }

    @Nullable
    @Override
    public Player matchPlayer(Player player) {
        if (player instanceof BukkitPlayer) {
            return player;
        } else {
            org.bukkit.entity.Player bukkitPlayer = server.getPlayerExact(player.getName());
            return bukkitPlayer != null ? new BukkitPlayer(plugin, this, bukkitPlayer) : null;
        }
    }

    @Nullable
    @Override
    public com.sk89q.worldedit.world.World matchWorld(com.sk89q.worldedit.world.World world) {
        if (world instanceof BukkitWorld) {
            return world;
        } else {
            World bukkitWorld = server.getWorld(world.getName());
            return bukkitWorld != null ? new BukkitWorld(bukkitWorld) : null;
        }
    }

    @Override
    public void onCommandRegistration(List<Command> commands, CommandsManager<LocalPlayer> manager) {
        List<CommandInfo> toRegister = new ArrayList<CommandInfo>();
        for (Command command : commands) {
            List<String> permissions = null;
            Method cmdMethod = manager.getMethods().get(null).get(command.aliases()[0]);
            Map<String, Method> childMethods = manager.getMethods().get(cmdMethod);

            if (cmdMethod != null && cmdMethod.isAnnotationPresent(CommandPermissions.class)) {
                permissions = Arrays.asList(cmdMethod.getAnnotation(CommandPermissions.class).value());
            } else if (cmdMethod != null && childMethods != null && childMethods.size() > 0) {
                permissions = new ArrayList<String>();
                for (Method m : childMethods.values()) {
                    if (m.isAnnotationPresent(CommandPermissions.class)) {
                        permissions.addAll(Arrays.asList(m.getAnnotation(CommandPermissions.class).value()));
                    }
                }
            }

            toRegister.add(new CommandInfo(command.usage(), command.desc(), command.aliases(), commands, permissions == null ? null : permissions.toArray(new String[permissions.size()])));
        }

        dynamicCommands.register(toRegister);
    }

    @Override
    public void registerGameHooks() {
        hookingEvents = true;
    }

    @Override
    public LocalConfiguration getConfiguration() {
        return plugin.getLocalConfiguration();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getPlatformName() {
        return "Bukkit-Official";
    }

    @Override
    public String getPlatformVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public Map<Capability, Preference> getCapabilities() {
        Map<Capability, Preference> capabilities = new EnumMap<Capability, Preference>(Capability.class);
        capabilities.put(Capability.CONFIGURATION, Preference.NORMAL);
        capabilities.put(Capability.GAME_HOOKS, Preference.PREFERRED);
        capabilities.put(Capability.PERMISSIONS, Preference.PREFERRED);
        capabilities.put(Capability.USER_COMMANDS, Preference.PREFERRED);
        capabilities.put(Capability.WORLD_EDITING, Preference.PREFER_OTHERS);
        return capabilities;
    }

    public void unregisterCommands() {
        dynamicCommands.unregisterCommands();
    }
}
