package com.mlcompanions.client;

import com.mlcompanions.CommonProxy;
import com.mlcompanions.entity.EntityCompanion;
import com.mlcompanions.entity.EntityLagger;
import com.mlcompanions.entity.EntityMystic;

import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.player.EntityPlayer;

public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityMystic.class, new RenderCompanion("mystic"));
        RenderingRegistry.registerEntityRenderingHandler(EntityLagger.class, new RenderCompanion("lagger"));
    }

    @Override
    public void openMenu(EntityCompanion c) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCompanionMenu(c));
    }

    @Override
    public Object getInventoryGui(EntityPlayer player, EntityCompanion c) {
        return new GuiChest(player.inventory, c.inventory);
    }
}
