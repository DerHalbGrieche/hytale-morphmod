package xyz.derhalbgrieche.morphmod;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.derhalbgrieche.morphmod.ui.MorphGui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MorphCommand extends AbstractCommand {
    private final Main main;

    public MorphCommand(Main main) {
        super("morph", "Morph commands");
        this.main = main;
        setAllowsExtraArguments(true);
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
        Player player = ctx.senderAs(Player.class);

        if (player.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Add world to polling if not present (e.g. if start missed it)
        synchronized (main.pollingWorlds) {
            if (main.pollingWorlds.add(player.getWorld())) {
                System.out.println("[MorphMod] Added world via command: " + player.getWorld().getName());
            }
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        player.getWorld().execute(() -> {
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
            openUI(player);
            return;
        }

        String cmd = args[start];
        if (cmd.equalsIgnoreCase("help") || cmd.equalsIgnoreCase("--help")) {
            ctx.sendMessage(Message.raw("Usage: /morph [ui|list|unmorph|unlock|id]"));
            return;
        }

        UUID uuid = main.getPlayerUUID(player);

        if (cmd.equals("list")) {
            Set<String> morphs = main.unlockedMorphs.getOrDefault(uuid, Collections.emptySet());
            ctx.sendMessage(Message.raw("Morphs: " + String.join(", ", morphs)));
        } else if (cmd.equals("ui")) {
            openUI(player);
        } else if (cmd.equals("unmorph")) {
            main.unmorphPlayer(player);
        } else if (cmd.equals("test") && args.length > start + 1) {
             String id = args[start + 1];
             if (apply(player, id)) ctx.sendMessage(Message.raw("Applied " + id));
             else ctx.sendMessage(Message.raw("Failed to apply " + id));
        } else if (cmd.equals("debug")) {
             try {
                 Ref<EntityStore> ref = player.getReference();
                 boolean hasModel = ref.getStore().getComponent(ref, ModelComponent.getComponentType()) != null;
                 boolean hasSkin = ref.getStore().getComponent(ref, PlayerSkinComponent.getComponentType()) != null;
                 ctx.sendMessage(Message.raw("Has ModelComponent: " + hasModel));
                 ctx.sendMessage(Message.raw("Has PlayerSkinComponent: " + hasSkin));
                 
                 ctx.sendMessage(Message.raw("Skin Methods:"));
                 for (java.lang.reflect.Method m : PlayerSkinComponent.class.getDeclaredMethods()) {
                     ctx.sendMessage(Message.raw("- " + m.getName()));
                 }

             } catch (Exception e) {
                 ctx.sendMessage(Message.raw("Debug fail: " + e.getMessage()));
             }
        } else if (cmd.equals("unlock") && args.length > start + 1) {
            if (!player.hasPermission("morph.unlock")) {
                ctx.sendMessage(Message.raw("You do not have permission to use this command."));
                return;
            }
            String id = args[start + 1];
            main.unlockedMorphs.computeIfAbsent(uuid, k -> new HashSet<>()).add(id);
            ctx.sendMessage(Message.raw("Unlocked " + id));
            main.saveData();
        } else {
            Set<String> morphs = main.unlockedMorphs.getOrDefault(uuid, Collections.emptySet());
            if (morphs.contains(cmd)) {
                if (apply(player, cmd)) ctx.sendMessage(Message.raw("Morphed into " + cmd));
                else ctx.sendMessage(Message.raw("Failed."));
            } else {
                ctx.sendMessage(Message.raw("Not unlocked: " + cmd));
            }
        }
    }

    private void openUI(Player player) {
        UUID uuid = main.getPlayerUUID(player);
        Set<String> morphs = main.unlockedMorphs.getOrDefault(uuid, Collections.emptySet());
        List<String> morphList = new ArrayList<>(morphs);
        try {
            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            
            if (playerRef != null) {
               player.getPageManager().openCustomPage(ref, store, new MorphGui(main, playerRef, morphList));
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(Message.raw("Failed to open UI."));
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
