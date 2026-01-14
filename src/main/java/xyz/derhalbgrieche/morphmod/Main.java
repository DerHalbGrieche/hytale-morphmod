package xyz.derhalbgrieche.morphmod;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Main extends JavaPlugin {

  private Map<UUID, Set<String>> unlockedMorphs = new HashMap<>();
  private Path dataFile;
  private final Set<Ref> processedDeaths = Collections.synchronizedSet(new HashSet<>());
  private final Timer timer = new Timer("MorphMod-Poller", true);
  private final Set<World> pollingWorlds = Collections.synchronizedSet(new HashSet<>());

  public Main(JavaPluginInit init) {
    super(init);
  }

  @Override
  protected void setup() {
    System.out.println("[MorphMod] Setup (Polling V1)");
    getCommandRegistry().registerCommand(new MorphCommand());
    getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);

    try {
      Path dataDir = getDataDirectory();
      if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
      dataFile = dataDir.resolve("morphs.dat");
      loadData();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void start() {
    System.out.println("[MorphMod] Start");
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              synchronized (pollingWorlds) {
                for (World world : pollingWorlds) {
                  world.execute(() -> checkDeaths(world));
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        },
        1000,
        200);
  }

  @Override
  protected void shutdown() {
    timer.cancel();
    saveData();
  }

  private void onPlayerConnect(PlayerConnectEvent event) {
    Player player = event.getPlayer();
    if (player != null) {
      World world = player.getWorld();
      if (world != null && pollingWorlds.add(world)) {
        System.out.println("[MorphMod] Started polling for world: " + world.getName());
      }
    }
  }

  private void checkDeaths(World world) {
    try {
      Store<EntityStore> store = world.getEntityStore().getStore();
      store.forEachChunk(
          (chunk, buffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
              DeathComponent dc = chunk.getComponent(i, DeathComponent.getComponentType());
              if (dc != null) {
                Ref ref = chunk.getReferenceTo(i);
                if (processedDeaths.add(ref)) {
                  handleDeath(dc, chunk, i);
                }
              }
            }
          });
    } catch (Exception e) {
    }
  }

  private void handleDeath(DeathComponent dc, ArchetypeChunk chunk, int index) {
    Damage damage = dc.getDeathInfo();
    if (damage != null && damage.getSource() instanceof Damage.EntitySource) {
      Damage.EntitySource source = (Damage.EntitySource) damage.getSource();
      Ref<EntityStore> killerRef = source.getRef();
      try {
        Player killer = killerRef.getStore().getComponent(killerRef, Player.getComponentType());
        if (killer != null) {
          ModelComponent victimModel =
              (ModelComponent) chunk.getComponent(index, ModelComponent.getComponentType());
          if (victimModel != null && victimModel.getModel() != null) {
            String id = victimModel.getModel().getModelAssetId();
            if (id != null) {
              UUID uuid = getPlayerUUID(killer);
              if (unlockedMorphs.computeIfAbsent(uuid, k -> new HashSet<>()).add(id)) {
                System.out.println("[MorphMod] Unlocked " + id);
                killer.sendMessage(Message.raw("Unlocked: ").insert(Message.raw(id).color("aqua")));
                saveData();
              }
            }
          }
        }
      } catch (Exception e) {
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void loadData() {
    if (!Files.exists(dataFile)) return;
    try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(dataFile))) {
      Object obj = ois.readObject();
      if (obj instanceof Map) unlockedMorphs = (Map<UUID, Set<String>>) obj;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void saveData() {
    if (dataFile == null) return;
    try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(dataFile))) {
      oos.writeObject(unlockedMorphs);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private UUID getPlayerUUID(Player player) {
    try {
      Ref<EntityStore> ref = player.getReference();
      Store<EntityStore> store = ref.getStore();
      PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
      if (playerRef != null) return playerRef.getUuid();
    } catch (Exception e) {
    }
    return player.getUuid();
  }

  public class MorphCommand extends AbstractCommand {
    public MorphCommand() {
      super("morph", "Morph commands");
      setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
      if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
      Player player = ctx.senderAs(Player.class);

      // Add world to polling if not present (e.g. if start missed it)
      synchronized (pollingWorlds) {
        if (pollingWorlds.add(player.getWorld())) {
          System.out.println("[MorphMod] Added world via command: " + player.getWorld().getName());
        }
      }

      CompletableFuture<Void> future = new CompletableFuture<>();
      player
          .getWorld()
          .execute(
              () -> {
                try {
                  handle(ctx, player);
                  future.complete(null);
                } catch (Exception e) {
                  e.printStackTrace();
                  future.completeExceptionally(e);
                }
              });
      return future;
    }

    private void handle(CommandContext ctx, Player player) {
      String input = ctx.getInputString();
      String[] args = input != null ? input.trim().split("\\s+") : new String[0];
      int start = (args.length > 0 && args[0].equalsIgnoreCase("morph")) ? 1 : 0;

      if (args.length <= start) {
        ctx.sendMessage(Message.raw("Usage: /morph <list|unmorph|unlock|id>"));
        return;
      }

      String cmd = args[start];
      UUID uuid = getPlayerUUID(player);

      if (cmd.equals("list")) {
        Set<String> morphs = unlockedMorphs.getOrDefault(uuid, Collections.<String>emptySet());
        ctx.sendMessage(Message.raw("Morphs: " + String.join(", ", morphs)));
      } else if (cmd.equals("unmorph")) {
        if (apply(player, "hytale:main_character")) ctx.sendMessage(Message.raw("Unmorphed."));
        else ctx.sendMessage(Message.raw("Fail."));
      } else if (cmd.equals("unlock") && args.length > start + 1) {
        String id = args[start + 1];
        unlockedMorphs.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
        ctx.sendMessage(Message.raw("Unlocked " + id));
        saveData();
      } else {
        Set<String> morphs = unlockedMorphs.getOrDefault(uuid, Collections.<String>emptySet());
        if (morphs.contains(cmd)) {
          if (apply(player, cmd)) ctx.sendMessage(Message.raw("Morphed into " + cmd));
          else ctx.sendMessage(Message.raw("Failed."));
        } else {
          ctx.sendMessage(Message.raw("Not unlocked: " + cmd));
        }
      }
    }

    private boolean apply(Player player, String id) {
      try {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(id);
        if (asset == null) return false;
        Model model = Model.createUnitScaleModel(asset);
        ModelComponent comp = new ModelComponent(model);
        Ref<EntityStore> ref = player.getReference();
        ref.getStore().putComponent(ref, ModelComponent.getComponentType(), comp);
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
    }
  }
}

