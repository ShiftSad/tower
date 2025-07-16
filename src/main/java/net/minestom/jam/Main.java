package net.minestom.jam;

import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import net.minestom.jam.instance.BlockHandlers;
import net.minestom.jam.instance.Lobby;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.TaskSchedule;

public class Main {
    public static void main(String[] args) throws Exception {
        LibraryInfo info = new LibraryInfo(
                new DirectoryPath("linux/x86-64/com/github/stephengold"),
                "bulletjme", DirectoryPath.USER_DIR);
        NativeBinaryLoader loader = new NativeBinaryLoader(info);
        NativeDynamicLibrary[] libraries = new NativeDynamicLibrary[]{
//                new NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
//                new NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
                new NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
//                new NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
//                new NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
                new NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
        };
        loader.registerNativeLibraries(libraries).initPlatformLibrary();
        loader.loadLibrary(LoadingCriterion.INCREMENTAL_LOADING);

        System.setProperty("minestom.tps", "60");

        MinecraftServer minecraftServer = MinecraftServer.init();

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
                .executionType(ExecutionType.TICK_END)
                .repeat(TaskSchedule.tick(1))
                .schedule();

        minecraftServer.start("0.0.0.0", 25565);
    }
}