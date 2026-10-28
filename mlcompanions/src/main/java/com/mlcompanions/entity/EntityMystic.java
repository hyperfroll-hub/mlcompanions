package com.mlcompanions.entity;

import net.minecraft.world.World;

/** Мистик: calm leader, protects the owner, always replants trees. */
public class EntityMystic extends EntityCompanion {

    public EntityMystic(World world) {
        super(world);
    }

    @Override
    public String getDisplayName() {
        return "Мистик";
    }

    @Override
    public String getPhraseKey() {
        return "mystic";
    }

    @Override
    public boolean isLagger() {
        return false;
    }

    @Override
    public float getRetreatThreshold() {
        return 0.25F;
    }

    @Override
    public double getTargetRange() {
        return 16.0D;
    }

    @Override
    public float getReplantChance() {
        return 1.0F;
    }

    @Override
    public float getForgetTopChance() {
        return 0.0F;
    }

    @Override
    public int getChopDelay() {
        return 20;
    }
}
