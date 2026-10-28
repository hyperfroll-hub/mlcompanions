package com.mlcompanions.entity;

import net.minecraft.world.World;

/** Лаггер: funny and chaotic, rushes into fights, runs away when hurt, sometimes "lags". */
public class EntityLagger extends EntityCompanion {

    public EntityLagger(World world) {
        super(world);
    }

    @Override
    public String getDisplayName() {
        return "Лаггер";
    }

    @Override
    public String getPhraseKey() {
        return "lagger";
    }

    @Override
    public boolean isLagger() {
        return true;
    }

    @Override
    public float getRetreatThreshold() {
        return 0.4F;
    }

    @Override
    public double getTargetRange() {
        return 20.0D;
    }

    @Override
    public float getReplantChance() {
        return 0.5F;
    }

    @Override
    public float getForgetTopChance() {
        return 0.3F;
    }

    @Override
    public int getChopDelay() {
        return 14;
    }
}
