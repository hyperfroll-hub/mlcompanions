package com.mlcompanions.ai;

import com.mlcompanions.ModConfig;
import com.mlcompanions.Speech;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Idle fun while the owner stands still:
 * Lagger runs circles around Mystic, jumps, or starts a play fight;
 * Mystic chases Lagger ("tag"), crouches and looks around, and answers play fights.
 */
public class AIPlay extends EntityAIBase {

    private static final int NONE = 0, CIRCLES = 1, JUMPS = 2, FIGHT = 3, TAG = 4, CROUCH = 5, RESPOND = 6;

    private final EntityCompanion c;
    private EntityCompanion partner;
    private int activity = NONE;
    private int ticks;
    private int duration;
    private int step;
    private int check;

    public AIPlay(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    private boolean ownerIdleNear() {
        EntityPlayer owner = this.c.getOwnerHere();
        return owner != null && this.c.getMode() == EntityCompanion.MODE_FOLLOW && this.c.ownerIdleTicks > 100
            && this.c.getDistanceSqToEntity(owner) < 10 * 10;
    }

    @Override
    public boolean shouldExecute() {
        if (this.c.isBusy() || this.c.chopping || this.c.ridingEntity != null || this.c.isInWater()) return false;
        // Mystic answers Lagger's play fight
        EntityCompanion p = this.c.findPartner();
        if (p != null && p.playing && p.playFightWith == this.c) {
            this.partner = p;
            this.activity = RESPOND;
            return true;
        }
        if (++this.check < 20) return false;
        this.check = 0;
        if (!ownerIdleNear() || this.c.getRNG().nextInt(40) != 0) return false;
        this.partner = p;
        boolean partnerFree = p != null && !p.isBusy() && !p.chopping && !p.playing && p.getDistanceSqToEntity(this.c) < 12 * 12;
        int r = this.c.getRNG().nextInt(10);
        if (this.c.isLagger()) {
            if (partnerFree && ModConfig.enablePlayFights && r < 3) this.activity = FIGHT;
            else if (partnerFree && r < 7) this.activity = CIRCLES;
            else this.activity = JUMPS;
        } else {
            if (partnerFree && r < 4) this.activity = TAG;
            else this.activity = CROUCH;
        }
        return true;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.step = 0;
        this.c.playing = true;
        switch (this.activity) {
            case FIGHT:
                this.duration = 80;
                this.c.playFightWith = this.partner;
                Speech.startDialog("play_fight", this.c, this.partner);
                break;
            case CIRCLES:
                this.duration = 160;
                break;
            case JUMPS:
                this.duration = 50;
                break;
            case TAG:
                this.duration = 120;
                break;
            case CROUCH:
                this.duration = 60;
                this.c.setSneaking(true);
                break;
            case RESPOND:
                this.duration = 100;
                break;
            default:
                this.duration = 0;
        }
    }

    @Override
    public boolean continueExecuting() {
        if (this.ticks >= this.duration || this.c.isBusy() || this.c.ridingEntity != null) return false;
        if (this.activity == RESPOND) return this.partner != null && this.partner.playing && this.partner.playFightWith == this.c;
        if (this.activity == CROUCH || this.activity == JUMPS) return ownerIdleNear();
        return this.partner != null && !this.partner.isDead && !this.partner.isBusy() && ownerIdleNear();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        switch (this.activity) {
            case FIGHT:
            case RESPOND:
                this.c.getLookHelper().setLookPositionWithEntity(this.partner, 30.0F, 30.0F);
                if (this.c.getDistanceSqToEntity(this.partner) > 2.2D * 2.2D) {
                    if (this.ticks % 10 == 1) this.c.getNavigator().tryMoveToEntityLiving(this.partner, 1.1D);
                } else {
                    this.c.getNavigator().clearPathEntity();
                    if (this.c.getRNG().nextInt(this.activity == FIGHT ? 7 : 10) == 0) {
                        this.c.swingItem();
                        this.c.playSound("random.wood_click", 0.3F, 1.5F);
                    }
                    if (this.c.onGround && this.c.getRNG().nextInt(25) == 0) this.c.getJumpHelper().setJumping();
                }
                break;
            case CIRCLES:
                if (this.ticks % 20 == 1) {
                    double a = this.step++ * Math.PI / 3.0D;
                    this.c.getNavigator().tryMoveToXYZ(this.partner.posX + Math.cos(a) * 3.0D, this.partner.posY,
                        this.partner.posZ + Math.sin(a) * 3.0D, 1.3D);
                }
                if (this.c.onGround && this.c.getRNG().nextInt(15) == 0) this.c.getJumpHelper().setJumping();
                break;
            case JUMPS:
                if (this.c.onGround && this.ticks % 12 == 0) this.c.getJumpHelper().setJumping();
                this.c.rotationYawHead += 25.0F;
                break;
            case TAG:
                this.c.getLookHelper().setLookPositionWithEntity(this.partner, 30.0F, 30.0F);
                if (this.ticks % 10 == 1) this.c.getNavigator().tryMoveToEntityLiving(this.partner, 1.25D);
                if (this.c.getDistanceSqToEntity(this.partner) < 2.0D * 2.0D) {
                    this.c.swingItem();
                    // tagged: Lagger runs away
                    double dx = this.partner.posX - this.c.posX, dz = this.partner.posZ - this.c.posZ;
                    double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                    this.partner.getNavigator().tryMoveToXYZ(this.partner.posX + dx / len * 6.0D, this.partner.posY,
                        this.partner.posZ + dz / len * 6.0D, 1.4D);
                    this.ticks = this.duration;
                }
                break;
            case CROUCH:
                if (this.ticks % 20 == 1) {
                    float yaw = this.c.rotationYaw + (this.c.getRNG().nextFloat() - 0.5F) * 120.0F;
                    double rad = Math.toRadians(yaw);
                    this.c.getLookHelper().setLookPosition(this.c.posX - Math.sin(rad) * 5.0D, this.c.posY + this.c.getEyeHeight(),
                        this.c.posZ + Math.cos(rad) * 5.0D, 10.0F, 10.0F);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void resetTask() {
        this.c.playing = false;
        if (this.activity == FIGHT) this.c.playFightWith = null;
        if (this.activity == CROUCH) this.c.setSneaking(false);
        this.c.getNavigator().clearPathEntity();
        this.activity = NONE;
        this.partner = null;
    }
}
