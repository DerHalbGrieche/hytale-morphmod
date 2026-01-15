package xyz.derhalbgrieche.morphmod.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.derhalbgrieche.morphmod.Main;

import javax.annotation.Nonnull;
import java.util.List;

public class MorphGui extends InteractiveCustomUIPage<MorphGui.MorphData> {

    private final Main main;
    private final List<String> morphs;

    public MorphGui(Main main, PlayerRef playerRef, List<String> morphs) {
        super(playerRef, CustomPageLifetime.CanDismiss, MorphData.CODEC);
        this.main = main;
        this.morphs = morphs;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        // Load the main GUI layout
        uiCommandBuilder.append("Pages/MorphMod/MorphGui.ui");

        // Clear existing list if any
        uiCommandBuilder.clear("#MorphGrid");
        
        // Populate the list
        for (int i = 0; i < morphs.size(); i++) {
            String morphId = morphs.get(i);
            
            // Append the entry layout to the grid
            uiCommandBuilder.append("#MorphGrid", "Pages/MorphMod/MorphEntry.ui");
            
            String basePath = "#MorphGrid[" + i + "]";
            uiCommandBuilder.set(basePath + " #MorphName.Text", morphId);
            
            // Add click event to the whole entry group
            uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, 
                basePath, 
                EventData.of("MorphId", morphId), 
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull MorphData data) {
        if (data.morphId != null) {
            applyMorph(ref, data.morphId);
            this.close(); 
        }
    }

    private void applyMorph(Ref<EntityStore> playerRef, String id) {
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(id);
            if (asset != null) {
                Model model = Model.createUnitScaleModel(asset);
                ModelComponent comp = new ModelComponent(model);
                playerRef.getStore().putComponent(playerRef, ModelComponent.getComponentType(), comp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class MorphData {
        public static final BuilderCodec<MorphData> CODEC = BuilderCodec.builder(MorphData.class, MorphData::new)
                .addField(new KeyedCodec<>("MorphId", Codec.STRING), (d, v) -> d.morphId = v, d -> d.morphId)
                .build();

        public String morphId;
    }
}
