package com.mlcompanions;

import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.player.EntityPlayer;

public class CommonProxy {

    public void init() {}

    public void openMenu(EntityCompanion c) {}

    public Object getInventoryGui(EntityPlayer player, EntityCompanion c) {
        return null;
    }
}
