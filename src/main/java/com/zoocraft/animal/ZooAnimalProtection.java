package com.zoocraft.animal;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/**
 * Exhibit animals inside a zoo are never culled. Vanilla despawning (fish, squid, many modded creatures, and every
 * mob in peaceful) is denied, and the animal is marked persistent, which is saved with it: it unloads with its chunk
 * and comes back when a player is near again. The census marks every animal it counts the same way. Entity-cramming
 * damage is cancelled too, so a crowded pen doesn't thin itself out.
 *
 * <p>The mark stays if the animal later leaves the zoo, like a name tag does.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class ZooAnimalProtection {
    private ZooAnimalProtection() {
    }

    /** Fires for every non-persistent mob every tick, so the zoo lookup (a chunk index) comes before the tag checks. */
    @SubscribeEvent
    static void onDespawnCheck(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        if (mob.isPersistenceRequired() || !inZoo(mob)) {
            return;
        }
        mob.setPersistenceRequired();
        event.setResult(MobDespawnEvent.Result.DENY);
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.CRAMMING) && event.getEntity() instanceof Mob mob && inZoo(mob)) {
            event.setCanceled(true);
        }
    }

    /** Marks an animal the census counted. */
    static void keep(Mob mob) {
        if (!mob.isPersistenceRequired()) {
            mob.setPersistenceRequired();
        }
    }

    private static boolean inZoo(Mob mob) {
        return mob.level().getServer() != null
                && ZooManager.get(mob.level().getServer()).zooAt(mob.level(), mob.blockPosition()).isPresent()
                && AnimalRecognition.isExhibitAnimal(mob);
    }
}
