package de.rayzs.bucketfix;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import io.netty.channel.*;
import org.bukkit.Bukkit;
import java.util.Map;

public class BucketFixPlugin extends JavaPlugin implements Listener {

    private Plugin PLUGIN;

    private final Map<Player, Channel> CHANNELS = new ConcurrentHashMap<>();
    private final String HANDLER_NAME = "bucketfix-handler";

    @Override
    public void onEnable() {
        this.PLUGIN = this;
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

        if(!injectPlayer(player))
            Bukkit.getScheduler().runTaskLater(this, () -> {
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
        final Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        if(channel == null) return false;

        CHANNELS.put(player, channel);
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new CustomChannelHandler(player));
        return true;
    }

    private void updateCube(Player player, BlockPosition blockPosition, int radius) {
        for(int x = -radius; x < radius; x++)
            for(int y = -radius; y < radius; y++)
                for(int z = -radius; z < radius; z++)
                    sendFakeBlock(player, player.getWorld(), blockPosition.getX() + x, blockPosition.getY() + y, blockPosition.getZ() + z);
    }

    private void sendFakeBlock(Player player, org.bukkit.World world, int x, int y, int z) {
        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            EntityPlayer entityPlayer = craftPlayer.getHandle();

            CraftWorld craftWorld =  (CraftWorld) world;
            BlockPosition blockPosition = new BlockPosition(x, y, z);
            PacketPlayOutBlockChange packet = new PacketPlayOutBlockChange(craftWorld.getHandle(), blockPosition);

            entityPlayer.playerConnection.sendPacket(packet);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private class CustomChannelHandler extends ChannelDuplexHandler {

        private final Player player;

        public CustomChannelHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext channelHandlerContext, Object packetObj) throws Exception {
            if (packetObj instanceof PacketPlayInBlockPlace) {
                PacketPlayInBlockPlace packet = (PacketPlayInBlockPlace) packetObj;
                String itemName = packet.getItemStack() == null ? "AIR" : packet.getItemStack().getName();

                if (itemName.endsWith(" Bucket")) {
                    BlockPosition blockPosition = packet.a();
                    Bukkit.getScheduler().scheduleAsyncDelayedTask(PLUGIN, () -> updateCube(player, blockPosition, 5), 1);
                }
            }

            super.channelRead(channelHandlerContext, packetObj);
        }
    }
}
