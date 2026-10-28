package com.mlcompanions.ai;

import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.Vec3;

/** Runs away when health is low: Lagger hides behind Mystic, Mystic goes to the owner. */
public class AIRetreat extends EntityAIBase {

    private final EntityCompanion c;
    private int ticks;
    private EntityLivingBase danger;

    public AIRetreat(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.c.isKnockedOut()) return false;
        if (this.c.getHealth() >= this.c.getMaxHealth() * this.c.getRetreatThreshold()) return false;
        this.danger = this.c.getAttackTarget() != null ? this.c.getAttackTarget() : this.c.getAITarget();
        return this.danger != null;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.c.retreating = true;
        this.c.setAttackTarget(null);
    }

    private Entity protector() {
        if (this.c.isLagger()) {
            EntityCompanion p = this.c.findPartner();
            if (p != null && !p.isKnockedOut()) return p;
        }
        return this.c.getOwnerHere();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.ticks % 10 != 1) return;
        Entity p = protector();
        if (p != null && this.c.getDistanceSqToEntity(p) > 4) {
            this.c.getNavigator().tryMoveToXYZ(p.posX, p.posY, p.posZ, 1.45D);
        } else if (p == null && this.danger != null) {
            Vec3 away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.c, 12, 6,
                Vec3.createVectorHelper(this.danger.posX, this.danger.posY, this.danger.posZ));
            if (away != null) this.c.getNavigator().tryMoveToXYZ(away.xCoord, away.yCoord, away.zCoord, 1.45D);
        }
    }

    @Override
    public boolean continueExecuting() {
        return !this.c.isKnockedOut() && this.ticks < 200 && this.c.getHealth() < this.c.getMaxHealth() * (this.c.getRetreatThreshold() + 0.15F);
    }

    @Override
    public void resetTask() {
        this.c.retreating = false;
        this.danger = null;
    }
}
