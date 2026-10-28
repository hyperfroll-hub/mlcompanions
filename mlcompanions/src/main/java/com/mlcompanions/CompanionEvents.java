package com.mlcompanions;

import java.util.List;

import com.mlcompanions.entity.EntityCompanion;
import com.mlcompanions.network.MessageCommand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Reacts to things that happen around the owner. Registered on both the Forge and the FML event bus. */
public class CompanionEvents {

    private static EntityCompanion pick(List<EntityCompanion> list, EntityPlayer player) {
        if (list.isEmpty()) return null;
        return list.get(player.getRNG().nextInt(list.size()));
    }

    private static EntityCompanion pickLagger(List<EntityCompanion> list) {
        for (EntityCompanion c : list) if (c.isLagger()) return c;
        return null;
    }

    private static EntityCompanion pickMystic(List<EntityCompanion> list) {
        for (EntityCompanion c : list) if (!c.isLagger()) return c;
        return null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Speech.tick();
        MessageCommand.processQueue();
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        Entity attacker = event.source.getEntity();
        if (attacker == null || attacker instanceof EntityCompanion) return;
        Speech.say(pick(CompanionManager.nearby(player, 24), player), "owner_hurt", false);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        if (event.entityLiving instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.entityLiving;
            for (EntityCompanion c : CompanionManager.nearby(player, 32)) {
                c.lastSpeakTick = -100000L;
                Speech.say(c, "owner_death", true);
            }
            return;
        }
        Entity killer = event.source.getEntity();
        if (killer instanceof EntityCompanion) {
            EntityCompanion c = (EntityCompanion) killer;
            if (event.entityLiving.getMaxHealth() >= 100) Speech.say(c, "boss", true);
            else Speech.sayOrDialog(c, "kill", false, 0.15F);
        } else if (killer instanceof EntityPlayer && event.entityLiving.getMaxHealth() >= 100) {
            EntityPlayer player = (EntityPlayer) killer;
            for (EntityCompanion c : CompanionManager.nearby(player, 48)) {
                Speech.say(c, "boss", true);
                c.getJumpHelper().setJumping();
            }
        }
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityPlayer) || event.distance < 10) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (player.capabilities.isFlying || player.capabilities.isCreativeMode) return;
        List<EntityCompanion> list = CompanionManager.nearby(player, 24);
        EntityCompanion m = pickMystic(list);
        Speech.say(m != null ? m : pick(list, player), "fall", false);
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.world.isRemote || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.world.getBlock(event.x, event.y, event.z) instanceof BlockChest) {
            List<EntityCompanion> list = CompanionManager.nearby(event.entityPlayer, 12);
            EntityCompanion l = pickLagger(list);
            if (l != null) {
                l.getNavigator().tryMoveToXYZ(event.x + 0.5D, event.y, event.z + 0.5D, 1.3D);
                Speech.say(l, "chest", false);
            } else {
                Speech.say(pick(list, event.entityPlayer), "chest", false);
            }
        }
    }

    private static boolean isTreasure(ItemStack s) {
        if (s == null) return false;
        Item i = s.getItem();
        return i == Items.diamond || i == Items.emerald || i == Items.gold_ingot || i == Items.nether_star
            || i == Item.getItemFromBlock(Blocks.diamond_ore) || i == Item.getItemFromBlock(Blocks.emerald_ore)
            || i == Item.getItemFromBlock(Blocks.gold_ore) || i == Item.getItemFromBlock(Blocks.diamond_block)
            || i == Item.getItemFromBlock(Blocks.gold_block) || i == Item.getItemFromBlock(Blocks.emerald_block);
    }

    @SubscribeEvent
    public void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.player.worldObj.isRemote || event.pickedUp == null || !isTreasure(event.pickedUp.getEntityItem())) return;
        List<EntityCompanion> list = CompanionManager.nearby(event.player, 24);
        EntityCompanion l = pickLagger(list);
        EntityCompanion c = l != null ? l : pick(list, event.player);
        if (c != null) {
            c.getJumpHelper().setJumping();
            Speech.sayOrDialog(c, "treasure", false, 0.3F);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        for (EntityCompanion c : CompanionManager.findLoaded(player)) {
            if (c.getMode() != EntityCompanion.MODE_FOLLOW) continue;
            if (c.worldObj == player.worldObj) c.teleportNear(player);
            else CompanionManager.transferTo(c, player);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        for (EntityCompanion c : CompanionManager.findLoaded(player)) {
            if (c.getMode() == EntityCompanion.MODE_FOLLOW && c.worldObj != player.worldObj && !c.isKnockedOut()) {
                CompanionManager.transferTo(c, player);
            }
        }
    }
}
