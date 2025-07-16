package net.minestom.jam;

import net.minestom.jam.instance.BlockHandlers;
import net.minestom.jam.instance.Lobby;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.TaskSchedule;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        // Load native libraries
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch");

        if (osName.contains("linux") && (osArch.equals("amd64") || osArch.equals("x86_64"))) System.load(new File("./libs/natives/bulletjme.dll").getAbsolutePath());
        else if (osName.contains("windows")) System.load(new java.io.File("./libs/natives/bulletjme.dll").getAbsolutePath());
        else throw new UnsupportedOperationException("Unsupported OS: " + osName);

        System.setProperty("minestom.tps", "60");

        MinecraftServer minecraftServer = MinecraftServer.init();
        MojangAuth.init();

        String secret = System.getenv("VELOCITY_SECRET");
        if (secret != null) {
            VelocityProxy.enable(secret);
        }

        BlockHandlers.register(MinecraftServer.getBlockManager());

        Queue.Manager queues = new Queue.Manager();
        Queue.Commands.register(queues, MinecraftServer.getCommandManager());

        var events = MinecraftServer.getGlobalEventHandler();
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();

            event.setSpawningInstance(Lobby.INSTANCE);
            player.setRespawnPoint(Lobby.SPAWN_POINT);
        });

        events.addListener(PlayerDisconnectEvent.class, event -> {
            final Player player = event.getPlayer();

            queues.dequeue(player);

            Game game = player.getTag(Game.GAME);
            if (game != null) game.onDisconnect(player);
        });

        MinecraftServer.getSchedulerManager().buildTask(() -> Game.GAMES.forEach(Game::update))
                .repeat(TaskSchedule.tick(1))
                .schedule();

        minecraftServer.start("0.0.0.0", 25565);
    }
}