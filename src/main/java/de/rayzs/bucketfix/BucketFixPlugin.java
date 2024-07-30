package de.rayzs.bucketfix;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import io.netty.channel.*;
import java.util.Map;
import org.bukkit.*;

public class BucketFixPlugin extends JavaPlugin implements Listener {

    private final Map<Player, Channel> CHANNELS = new ConcurrentHashMap<>();
    private final String HANDLER_NAME = "bucketfix-handler";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getOnlinePlayers().forEach(this::injectPlayer);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(this::uninjectPlayer);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if(!injectPlayer(player) && player.isOnline())
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if(!player.isOnline()) return;
                if(!injectPlayer(player)) player.kickPlayer("[BucketFix] Failed to get channel!");
            }, 20);
    }

    private void uninjectPlayer(Player player) {
        if(!CHANNELS.containsKey(player)) return;
        final Channel channel = CHANNELS.get(player);

        channel.eventLoop().submit(() -> {
            final ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.names().contains(HANDLER_NAME))
                pipeline.remove(HANDLER_NAME);
        });

        CHANNELS.remove(player);
    }

    private boolean injectPlayer(Player player) {
        if(!player.isOnline()) return false;

        final Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        if(channel == null) return false;

        CHANNELS.put(player, channel);
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new CustomChannelHandler(player));
        return true;
    }

    private void changeMode(Channel channel, boolean interact) {
        PacketPlayOutGameStateChange packet = new PacketPlayOutGameStateChange(3, interact ? 0 : 2);
        channel.writeAndFlush(packet);
    }

    private class CustomChannelHandler extends ChannelDuplexHandler {

        private final Player player;

        public CustomChannelHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext channelHandlerContext, Object packetObj) throws Exception {
            if (packetObj instanceof PacketPlayInBlockPlace) {
                final PacketPlayInBlockPlace packet = (PacketPlayInBlockPlace) packetObj;
                final String itemName = packet.getItemStack() == null ? "AIR" : packet.getItemStack().getName();
                final Channel channel = channelHandlerContext.channel();

                if (itemName.endsWith("Bucket")) {
                    if(player.getGameMode() == GameMode.SURVIVAL) {
                        changeMode(channel, false);
                        changeMode(channel, true);
                    }
                }
            }

            super.channelRead(channelHandlerContext, packetObj);
        }
    }
}
