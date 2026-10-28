package com.mlcompanions.ai;

import java.util.List;

import com.mlcompanions.ModConfig;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Walks to dropped items nearby and puts them into the companion's inventory. */
public class AIPickupItems extends EntityAIBase {

    private final EntityCompanion c;
    private EntityItem item;
    private int ticks;
    private int scan;

    public AIPickupItems(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!ModConfig.enablePickup || this.c.isBusy() || this.c.pickupBlockedTicks > 0 || this.c.ridingEntity != null) return false;
        if (++this.scan < 10) return false;
        this.scan = 0;
        if (this.c.isInventoryFull()) return false;
        EntityPlayer owner = this.c.getOwnerHere();
        if (this.c.getMode() == EntityCompanion.MODE_FOLLOW && (owner == null || this.c.getDistanceSqToEntity(owner) > 12 * 12)) return false;
        double r = this.c.getMode() == EntityCompanion.MODE_STAY ? 4.0D : 8.0D;
        List<?> list = this.c.worldObj.getEntitiesWithinAABB(EntityItem.class, this.c.boundingBox.expand(r, 3.0D, r));
        double best = Double.MAX_VALUE;
        this.item = null;
        for (Object o : list) {
            EntityItem e = (EntityItem) o;
            if (e.isDead || e.delayBeforeCanPickup > 0 || e.getEntityItem() == null) continue;
            // don't grab what the owner just threw
            if (owner != null && e.age < 200 && e.getDistanceSqToEntity(owner) < 5 * 5) continue;
            double d = this.c.getDistanceSqToEntity(e);
            if (d < best) {
                best = d;
                this.item = e;
            }
        }
        return this.item != null;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
    }

    @Override
    public boolean continueExecuting() {
        return this.item != null && !this.item.isDead && this.ticks < 200 && !this.c.isBusy();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.c.getDistanceSqToEntity(this.item) < 1.8D * 1.8D) {
            ItemStack s = this.item.getEntityItem();
            ItemStack left = this.c.addToInventory(s);
            if (left == null) {
                this.item.setDead();
            } else if (left.stackSize != s.stackSize) {
                this.item.setEntityItemStack(left);
            } else {
                this.item = null;
                return;
            }
            this.c.playSound("random.pop", 0.2F, ((this.c.getRNG().nextFloat() - this.c.getRNG().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            this.c.swingItem();
            this.item = null;
            return;
        }
        if (this.ticks % 10 == 1) {
            this.c.getNavigator().tryMoveToXYZ(this.item.posX, this.item.posY, this.item.posZ, 1.1D);
        }
    }

    @Override
    public void resetTask() {
        this.item = null;
    }
}
