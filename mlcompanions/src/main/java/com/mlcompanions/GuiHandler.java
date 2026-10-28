package com.mlcompanions;

import com.mlcompanions.entity.EntityCompanion;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.world.World;

/** Companion inventory: a normal 3-row chest screen. x = entity id. */
public class GuiHandler implements IGuiHandler {

    private static EntityCompanion find(World world, int id) {
        Entity e = world.getEntityByID(id);
        return e instanceof EntityCompanion ? (EntityCompanion) e : null;
    }

    @Override
    public Object getServerGuiElement(int guiId, EntityPlayer player, World world, int x, int y, int z) {
        EntityCompanion c = find(world, x);
        if (c == null) return null;
        return new ContainerChest(player.inventory, c.inventory);
    }

    @Override
    public Object getClientGuiElement(int guiId, EntityPlayer player, World world, int x, int y, int z) {
        EntityCompanion c = find(world, x);
        if (c == null) return null;
        return MLCompanions.proxy.getInventoryGui(player, c);
    }
}
