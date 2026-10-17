package fr.roza.eldenflaskscompat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent.Complete;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent.Pre;
import net.blay09.mods.waystones.api.EntityTeleportResult;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;

import java.util.List;

@Mod(FlasksWaystonesCompat.MODID)
public class FlasksWaystonesCompat {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String MODID = "eldenflaskscompat";

    private static final ResourceLocation COMBAT_EFFECT =
        ResourceLocation.fromNamespaceAndPath("in_combat", "combat");

    private static final ResourceLocation HEALING_FLASK =
        ResourceLocation.fromNamespaceAndPath("elden_flasks", "healing_flask");

    private static final ResourceLocation MAX_USES =
        ResourceLocation.fromNamespaceAndPath("elden_flasks", "flask_max_uses");

    private static final ResourceLocation CURRENT_USES =
        ResourceLocation.fromNamespaceAndPath("elden_flasks", "flask_current_uses");

    private static final ResourceLocation CURRENT_KILLS =
        ResourceLocation.fromNamespaceAndPath("elden_flasks", "recharge_kill_current");

    public FlasksWaystonesCompat() {
        NeoForge.EVENT_BUS.addListener(this::onTeleportPre);
        NeoForge.EVENT_BUS.addListener(this::onTeleportComplete);
    }

    /**
     * Refuse une téléportation Waystones si le joueur est en combat.
     */
    private void onTeleportPre(Pre event) {
        if (!(event.getContext().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var combatEffect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
            BuiltInRegistries.MOB_EFFECT.get(
                ResourceLocation.fromNamespaceAndPath("in_combat", "combat")
            )
        );

        if (player.hasEffect(combatEffect)) {
            event.setCanceled(true);
        }
    }

    /**
     * Recharge les fioles uniquement après une téléportation réussie.
     */
    private void onTeleportComplete(Complete event) {
        if (event.getPrimaryResult().filter(EntityTeleportResult::isSuccessful).isEmpty()) {
            return;
        }

        if (!(event.getContext().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Inventaire principal et barre de raccourcis.
        rechargeFlasks(player.getInventory().items);

        // Main secondaire.
        rechargeFlasks(player.getInventory().offhand);

        // Synchronise les changements avec le client.
        player.inventoryMenu.broadcastChanges();
    }

    private void rechargeFlasks(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            rechargeFlask(stack);
        }
    }

    private void rechargeFlask(ItemStack stack) {
        if (stack.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(HEALING_FLASK)) {
            return;
        }

        Integer maxUses = getIntegerComponent(stack, MAX_USES);

        if (maxUses == null) {
            return;
        }

        setIntegerComponent(stack, CURRENT_USES, maxUses);
        setIntegerComponent(stack, CURRENT_KILLS, 0);
    }

    private Integer getIntegerComponent(
            ItemStack stack,
            ResourceLocation componentId) {

        DataComponentType<?> component =
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);

        if (component == null) {
            return null;
        }

        Object value = stack.get(component);
        return value instanceof Integer number ? number : null;
    }

    private void setIntegerComponent(
            ItemStack stack,
            ResourceLocation componentId,
            int value) {

        DataComponentType<?> component =
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);

        if (component != null) {
            setIntegerComponentUnchecked(stack, component, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void setIntegerComponentUnchecked(
            ItemStack stack,
            DataComponentType<?> component,
            int value) {

        stack.set((DataComponentType<Integer>) component, value);
    }
}