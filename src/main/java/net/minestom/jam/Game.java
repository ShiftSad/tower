package net.minestom.jam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.jam.instance.Lobby;
import net.minestom.jam.objects.BlockRigidBody;
import net.minestom.jam.objects.MinecraftPhysicsObject;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerCustomClickEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class Game implements PacketGroupingAudience {

    private static final LinkedList<Pos> spawnPoints = new LinkedList<>(Arrays.asList(
            new Pos(15.5, 5, -74.5, 180, 0),
            new Pos(-28.5, 5, -119.5, -90, 0),
            new Pos(-11.5, 5, -159.5, 0, 0),
            new Pos(43.5, 5, -119.5, 90, 0)
    ));

    /**
     * The game that a player is in.
     */
    public static final Tag<Game> GAME = Tag.Transient("Game");
    public static final @NotNull Set<Game> GAMES = new HashSet<>();

    private static InstanceContainer createGameInstance() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer(
                new AnvilLoader(Path.of("game"))
        );

        instance.setTimeRate(0);
        instance.setTime(6000); // Noon

        return instance;
    }

    private final InstanceContainer instance;
    private final List<Player> players = new ArrayList<>();
    private final AtomicBoolean ending = new AtomicBoolean(false);
    private final MinecraftPhysics minecraftPhysics;

    private long lastUpdate = System.nanoTime();

    public Game(@NotNull Set<UUID> players) {
        this.instance = createGameInstance();
        minecraftPhysics = new MinecraftPhysics(instance);

        for (int i = 0; i < players.size(); i++) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(players.toArray(new UUID[0])[i]);
            if (player == null) continue;

            this.players.add(player);
            player.setTag(GAME, this);
            player.setGameMode(GameMode.CREATIVE);

            player.setInstance(instance, spawnPoints.get(i % spawnPoints.size()));
        }

        instance.eventNode().addListener(PlayerChangeHeldSlotEvent.class, event -> {
            var block = new BlockRigidBody(
                    minecraftPhysics,
                    event.getPlayer().getPosition().asVec(),
                    new Vec(1, 1, 1),
                    1.0f,
                    true,
                    Block.DIAMOND_BLOCK
            );
            minecraftPhysics.addObject(block);
            block.setInstance();
        });

        GAMES.add(this);
    }

    public void onGameEnd() {
        ending.set(true);

        for (Player player : players) {
            player.setInstance(Lobby.INSTANCE, Lobby.SPAWN_POINT);
            player.removeTag(GAME);
        }

        players.clear();
        GAMES.remove(this);
    }

    public void onDisconnect(@NotNull Player player) {
        players.remove(player);

        sendMessage(PLAYER_HAS_LEFT.apply(player.getUsername()));

        // As an example, we end the game if there's one player left
        if (players.size() == 1) {
            onGameEnd();
        }
    }

    /**
     * Method called every tick to update the game state.
     */
    public void update() {
        long diff = System.nanoTime() - lastUpdate;
        float deltaTime = diff / 1_000_000_000f;
        lastUpdate = System.nanoTime();
        minecraftPhysics.update(deltaTime);
    }

    private static final Function<String, Component> PLAYER_HAS_LEFT = username -> Component.textOfChildren(
            Component.text("[!]", NamedTextColor.YELLOW, TextDecoration.BOLD),
            Component.text(" ", NamedTextColor.GRAY),
            Component.text(username, NamedTextColor.GRAY),
            Component.text(" has left the game!", NamedTextColor.GRAY)
    );

    @Override
    public @NotNull @UnmodifiableView Collection<@NotNull Player> getPlayers() {
        return Collections.unmodifiableCollection(players);
    }
}
