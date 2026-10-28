package com.mlcompanions.ai;

import java.util.List;

import com.mlcompanions.ModConfig;
import com.mlcompanions.Speech;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;

/** Chooses who to fight: the owner's attacker, own attacker, the owner's victim, or the nearest monster. */
public class AITargetEnemies extends EntityAIBase {

    private final EntityCompanion c;
    private EntityLivingBase target;
    private EntityLivingBase lastOwnerVictim;
    private int cooldown = 0;

    public AITargetEnemies(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(1);
    }

    private boolean valid(EntityLivingBase e, boolean allowPlayers) {
        if (e == null || !e.isEntityAlive() || e == this.c) return false;
        if (e instanceof EntityCompanion) return false;
        if (e instanceof EntityPlayer) {
            if (!allowPlayers || this.c.isOwner((EntityPlayer) e) || ((EntityPlayer) e).capabilities.isCreativeMode) return false;
        }
        if (e instanceof EntityTameable && ((EntityTameable) e).isTamed()) return false;
        return this.c.getDistanceSqToEntity(e) < 24 * 24;
    }

    private boolean inArea(EntityLivingBase e) {
        int mode = this.c.getMode();
        if (mode == EntityCompanion.MODE_STAY) return e.getDistanceSq(this.c.homeX, this.c.homeY, this.c.homeZ) < 8 * 8;
        if (mode == EntityCompanion.MODE_GUARD) return e.getDistanceSq(this.c.homeX, this.c.homeY, this.c.homeZ) < 14 * 14;
        return true;
    }

    private EntityLivingBase choose() {
        EntityPlayer owner = this.c.getOwnerHere();
        if (owner != null) {
            EntityLivingBase a = owner.getAITarget();
            if (valid(a, true) && inArea(a)) return a;
        }
        EntityLivingBase self = this.c.getAITarget();
        if (valid(self, true)) return self;
        if (owner != null) {
            EntityLivingBase v = owner.getLastAttacker();
            if (v != null && v != this.lastOwnerVictim && owner.ticksExisted - owner.getLastAttackerTime() < 100) {
                this.lastOwnerVictim = v;
                if (valid(v, false) && inArea(v)) return v;
            }
        }
        // nearest monster
        double range = this.c.getTargetRange();
        if (this.c.getMode() == EntityCompanion.MODE_STAY) range = 8;
        List<?> list = this.c.worldObj.getEntitiesWithinAABB(EntityLiving.class, this.c.boundingBox.expand(range, 6.0D, range));
        EntityLivingBase best = null;
        double bestDist = Double.MAX_VALUE;
        for (Object o : list) {
            EntityLiving e = (EntityLiving) o;
            if (!(e instanceof IMob) || !valid(e, false) || !inArea(e)) continue;
            if (owner != null && this.c.getMode() == EntityCompanion.MODE_FOLLOW && e.getDistanceSqToEntity(owner) > 20 * 20) continue;
            if (!this.c.canEntityBeSeen(e)) continue;
            double d = this.c.getDistanceSqToEntity(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    @Override
    public boolean shouldExecute() {
        if (!ModConfig.enableCombat || this.c.retreating || this.c.isKnockedOut()) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = 10;
        this.target = choose();
        return this.target != null;
    }

    @Override
    public void startExecuting() {
        this.c.setAttackTarget(this.target);
        Speech.say(this.c, "combat_start", false);
    }

    @Override
    public boolean continueExecuting() {
        EntityLivingBase t = this.c.getAttackTarget();
        if (t == null || !t.isEntityAlive() || this.c.retreating || !ModConfig.enableCombat) return false;
        if (this.c.getDistanceSqToEntity(t) > 28 * 28) return false;
        if (this.c.getMode() != EntityCompanion.MODE_FOLLOW && t.getDistanceSq(this.c.homeX, this.c.homeY, this.c.homeZ) > 20 * 20) return false;
        return true;
    }

    @Override
    public void resetTask() {
        this.c.setAttackTarget(null);
        this.target = null;
    }
}
