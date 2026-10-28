package com.mlcompanions.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mlcompanions.Speech;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.Vec3;

/** Runs from a hissing creeper. Lagger sometimes panics as soon as he sees one. */
public class AIFleeCreeper extends EntityAIBase {

    private final EntityCompanion c;
    private EntityCreeper creeper;
    private final Set<Integer> decided = new HashSet<Integer>();
    private int ticks;

    public AIFleeCreeper(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.c.isKnockedOut() || this.c.ticksExisted % 5 != 0) return false;
        List<?> list = this.c.worldObj.getEntitiesWithinAABB(EntityCreeper.class, this.c.boundingBox.expand(7.0D, 3.0D, 7.0D));
        for (Object o : list) {
            EntityCreeper cr = (EntityCreeper) o;
            if (!cr.isEntityAlive()) continue;
            boolean flee = cr.getCreeperState() > 0 && this.c.getDistanceSqToEntity(cr) < 6 * 6;
            if (!flee && this.c.isLagger() && !this.decided.contains(cr.getEntityId())) {
                this.decided.add(cr.getEntityId());
                if (this.decided.size() > 50) this.decided.clear();
                flee = this.c.getRNG().nextFloat() < 0.3F;
            }
            if (flee) {
                this.creeper = cr;
                return true;
            }
        }
        return false;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.c.setAttackTarget(null);
        Speech.say(this.c, "creeper", false);
        move();
    }

    private void move() {
        Vec3 away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.c, 10, 5,
            Vec3.createVectorHelper(this.creeper.posX, this.creeper.posY, this.creeper.posZ));
        if (away != null) this.c.getNavigator().tryMoveToXYZ(away.xCoord, away.yCoord, away.zCoord, 1.5D);
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.c.getNavigator().noPath()) move();
    }

    @Override
    public boolean continueExecuting() {
        return this.creeper != null && this.creeper.isEntityAlive() && this.ticks < 80 && this.c.getDistanceSqToEntity(this.creeper) < 10 * 10;
    }

    @Override
    public void resetTask() {
        this.creeper = null;
    }
}
