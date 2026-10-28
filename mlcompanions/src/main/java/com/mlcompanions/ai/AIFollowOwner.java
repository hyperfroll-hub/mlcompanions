package com.mlcompanions.ai;

import com.mlcompanions.ModConfig;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/** Follows the owner (FOLLOW mode) or walks back to the home point (STAY / GUARD / CHOP). */
public class AIFollowOwner extends EntityAIBase {

    private final EntityCompanion c;
    private EntityPlayer owner;
    private int timer;
    private int failed;

    public AIFollowOwner(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    private double followDist() {
        return this.c.worldObj.isDaytime() ? ModConfig.followDistance : Math.min(4, ModConfig.followDistance);
    }

    private double homeLimit() {
        switch (this.c.getMode()) {
            case EntityCompanion.MODE_STAY:
                return 3;
            case EntityCompanion.MODE_GUARD:
                return 12;
            case EntityCompanion.MODE_CHOP:
                return 20;
            default:
                return 0;
        }
    }

    private double homeDistSq() {
        return this.c.getDistanceSq(this.c.homeX + 0.5D, this.c.homeY, this.c.homeZ + 0.5D);
    }

    @Override
    public boolean shouldExecute() {
        if (this.c.ridingEntity != null || this.c.isKnockedOut()) return false;
        if (this.c.getMode() == EntityCompanion.MODE_FOLLOW) {
            this.owner = this.c.getOwnerHere();
            if (this.owner == null) return false;
            double d = this.c.getDistanceSqToEntity(this.owner);
            if (d > ModConfig.teleportDistance * ModConfig.teleportDistance) return true;
            if (this.c.getAttackTarget() != null) return false;
            return d > followDist() * followDist();
        }
        if (this.c.getAttackTarget() != null) return false;
        double lim = homeLimit();
        return homeDistSq() > lim * lim;
    }

    @Override
    public void startExecuting() {
        this.timer = 0;
        this.failed = 0;
    }

    @Override
    public boolean continueExecuting() {
        if (this.c.ridingEntity != null || this.c.isKnockedOut()) return false;
        if (this.c.getMode() == EntityCompanion.MODE_FOLLOW) {
            if (this.owner == null || this.owner.isDead || this.owner.worldObj != this.c.worldObj) return false;
            if (this.c.getAttackTarget() != null && this.c.getDistanceSqToEntity(this.owner) < 20 * 20) return false;
            return this.c.getDistanceSqToEntity(this.owner) > 2.5D * 2.5D;
        }
        if (this.c.getAttackTarget() != null) return false;
        return homeDistSq() > 1.5D * 1.5D && this.failed < 6;
    }

    @Override
    public void updateTask() {
        if (this.c.getMode() == EntityCompanion.MODE_FOLLOW) {
            this.c.getLookHelper().setLookPositionWithEntity(this.owner, 10.0F, this.c.getVerticalFaceSpeed());
            if (--this.timer > 0) return;
            this.timer = 10;
            double d = this.c.getDistanceSqToEntity(this.owner);
            boolean far = d > ModConfig.teleportDistance * ModConfig.teleportDistance;
            boolean ok = !far && this.c.getNavigator().tryMoveToEntityLiving(this.owner, d > 12 * 12 ? 1.35D : 1.05D);
            if (!ok) this.failed++;
            else this.failed = 0;
            if (far || (this.failed > 4 && d > 12 * 12)) {
                if (this.owner.onGround || this.owner.isInWater() || this.owner.ridingEntity != null) {
                    this.c.teleportNear(this.owner);
                    this.failed = 0;
                }
            }
        } else {
            if (--this.timer > 0) return;
            this.timer = 20;
            boolean ok = this.c.getNavigator().tryMoveToXYZ(this.c.homeX + 0.5D, this.c.homeY, this.c.homeZ + 0.5D, 1.0D);
            if (!ok) {
                this.failed++;
                if (this.failed >= 6 && homeDistSq() < 64 * 64) {
                    this.c.setPosition(this.c.homeX + 0.5D, this.c.homeY, this.c.homeZ + 0.5D);
                }
            }
        }
    }

    @Override
    public void resetTask() {
        this.owner = null;
        this.c.getNavigator().clearPathEntity();
    }
}
