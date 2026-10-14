package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import com.zoocraft.item.DesignationMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class ZooComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ZooCraft.MOD_ID);

    /** The Zoo Designation Tool's current mode. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DesignationMode>> DESIGNATION_MODE =
            COMPONENTS.registerComponentType("designation_mode",
                    builder -> builder.persistent(DesignationMode.CODEC).networkSynchronized(DesignationMode.STREAM_CODEC));

    private ZooComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
