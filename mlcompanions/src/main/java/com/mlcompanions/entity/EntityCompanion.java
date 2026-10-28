package com.mlcompanions.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mlcompanions.CompanionManager;
import com.mlcompanions.MLCompanions;
import com.mlcompanions.ModConfig;
import com.mlcompanions.Speech;
import com.mlcompanions.ai.AIChopTrees;
import com.mlcompanions.ai.AIFleeCreeper;
import com.mlcompanions.ai.AIFollowOwner;
import com.mlcompanions.ai.AIPickupItems;
import com.mlcompanions.ai.AIPlay;
import com.mlcompanions.ai.AIRetreat;
import com.mlcompanions.ai.AITargetEnemies;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public abstract class EntityCompanion extends EntityCreature {

    public static final int MODE_FOLLOW = 0;
    public static final int MODE_STAY = 1;
    public static final int MODE_CHOP = 2;
    public static final int MODE_GUARD = 3;

    private static final int DW_MODE = 16;
    private static final int DW_OWNER = 17;
    private static final int DW_FLAGS = 18;
    private static final int DW_BUBBLE = 19;
    private static final int FLAG_KNOCKED = 1;
    private static final int FLAG_LAGGING = 2;

    public final InventoryBasic inventory;

    // speech state (server)
    public long lastSpeakTick = -100000L;
    public String lastPhrase = null;
    public final Map<String, Long> categoryTicks = new HashMap<String, Long>();

    // state (server)
    public int homeX, homeY, homeZ;
    public long generation = 0;
    public boolean retreating = false;
    public boolean chopping = false;
    public boolean playing = false;
    public int ownerIdleTicks = 0;
    public int pickupBlockedTicks = 0;
    public final List<int[]> pendingReplants = new ArrayList<int[]>();
    public EntityCompanion playFightWith = null;
    private EntityPlayer cachedOwner;
    private int ownerCheckTicks = 0;
    private EntityCompanion cachedPartner;
    private int partnerCheckTicks = 0;
    private int knockTicks = 0;
    private int lagTicks = 0;
    private int bubbleTicks = 0;
    private int eatCooldown = 0;
    private int regenTicks = 0;
    private int envTicks = 0;
    private int chatterTicks = 0;
    private int pirateCooldown = 0;
    private int noSeatCooldown = 0;
    private boolean envInit = false;
    private boolean wasNight, wasRain, wasThunder, ownerWasRiding;
    private double lastOwnerX, lastOwnerZ;
    private boolean checkedGeneration = false;

    public EntityCompanion(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.inventory = new InventoryBasic(getDisplayName(), true, 27);
        this.getNavigator().setAvoidsWater(false);
        this.getNavigator().setCanSwim(true);
        this.getNavigator().setBreakDoors(false);
        this.getNavigator().setEnterDoors(true);
        this.experienceValue = 0;
        this.setEquipmentDropChance(0, 2.0F);
        for (int i = 1; i < 5; i++) this.setEquipmentDropChance(i, 2.0F);

        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new AIFleeCreeper(this));
        this.tasks.addTask(2, new AIRetreat(this));
        this.tasks.addTask(3, new EntityAIAttackOnCollide(this, 1.25D, true));
        this.tasks.addTask(4, new AIFollowOwner(this));
        this.tasks.addTask(5, new AIChopTrees(this));
        this.tasks.addTask(6, new AIPickupItems(this));
        this.tasks.addTask(7, new AIPlay(this));
        this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(9, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new AITargetEnemies(this));

        this.setCustomNameTag(getDisplayName());
        this.setAlwaysRenderNameTag(true);
        this.chatterTicks = 20 * (ModConfig.chatterMinSeconds + this.rand.nextInt(Math.max(1, ModConfig.chatterMaxSeconds - ModConfig.chatterMinSeconds + 1)));
    }

    // ---------------------------------------------------------------- character

    /** "Мистик" / "Лаггер" */
    public abstract String getDisplayName();

    /** key in the phrases file: "mystic" / "lagger" */
    public abstract String getPhraseKey();

    public abstract boolean isLagger();

    /** health fraction below which the companion retreats */
    public abstract float getRetreatThreshold();

    public abstract double getTargetRange();

    public abstract float getReplantChance();

    public abstract float getForgetTopChance();

    /** ticks between broken logs */
    public abstract int getChopDelay();

    // ---------------------------------------------------------------- setup

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(DW_MODE, Byte.valueOf((byte) 0));
        this.dataWatcher.addObject(DW_OWNER, "");
        this.dataWatcher.addObject(DW_FLAGS, Byte.valueOf((byte) 0));
        this.dataWatcher.addObject(DW_BUBBLE, "");
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage);
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(ModConfig.maxHealth);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(ModConfig.moveSpeed);
        this.getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(32.0D);
        this.getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(ModConfig.baseDamage);
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean allowLeashing() {
        return false;
    }

    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return "game.player.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "game.player.die";
    }

    // ---------------------------------------------------------------- data watcher state

    public int getMode() {
        return this.dataWatcher.getWatchableObjectByte(DW_MODE);
    }

    public void setMode(int mode) {
        this.dataWatcher.updateObject(DW_MODE, Byte.valueOf((byte) mode));
        this.homeX = MathHelper.floor_double(this.posX);
        this.homeY = MathHelper.floor_double(this.posY);
        this.homeZ = MathHelper.floor_double(this.posZ);
        this.getNavigator().clearPathEntity();
    }

    public String getOwnerId() {
        return this.dataWatcher.getWatchableObjectString(DW_OWNER);
    }

    public void setOwner(EntityPlayer player) {
        this.dataWatcher.updateObject(DW_OWNER, player.getUniqueID().toString());
        this.cachedOwner = player;
    }

    public boolean isOwner(EntityPlayer player) {
        return player != null && player.getUniqueID().toString().equals(getOwnerId());
    }

    private boolean getMLFlag(int flag) {
        return (this.dataWatcher.getWatchableObjectByte(DW_FLAGS) & flag) != 0;
    }

    private void setMLFlag(int flag, boolean value) {
        int b = this.dataWatcher.getWatchableObjectByte(DW_FLAGS);
        b = value ? (b | flag) : (b & ~flag);
        this.dataWatcher.updateObject(DW_FLAGS, Byte.valueOf((byte) b));
    }

    public boolean isKnockedOut() {
        return getMLFlag(FLAG_KNOCKED);
    }

    public boolean isLagging() {
        return getMLFlag(FLAG_LAGGING);
    }

    public String getBubble() {
        return this.dataWatcher.getWatchableObjectString(DW_BUBBLE);
    }

    public void showBubble(String text) {
        if (text.length() > 60) text = text.substring(0, 57) + "...";
        this.dataWatcher.updateObject(DW_BUBBLE, text);
        this.bubbleTicks = 80;
    }

    /** True if the companion is busy (fighting, retreating, knocked out). */
    public boolean isBusy() {
        return getAttackTarget() != null || retreating || isKnockedOut() || isLagging();
    }

    // ---------------------------------------------------------------- owner / partner

    /** Owner if online (any dimension on the server; same world on the client). */
    public EntityPlayer getOwner() {
        String id = getOwnerId();
        if (id.isEmpty()) return null;
        if (this.cachedOwner != null && !this.cachedOwner.isDead && this.ownerCheckTicks > 0) {
            return this.cachedOwner;
        }
        this.ownerCheckTicks = 20;
        this.cachedOwner = null;
        List<?> players;
        if (this.worldObj.isRemote) {
            players = this.worldObj.playerEntities;
        } else {
            players = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
        }
        for (Object o : players) {
            EntityPlayer p = (EntityPlayer) o;
            if (id.equals(p.getUniqueID().toString())) {
                this.cachedOwner = p;
                break;
            }
        }
        return this.cachedOwner;
    }

    /** Owner only if in the same world. */
    public EntityPlayer getOwnerHere() {
        EntityPlayer p = getOwner();
        return p != null && p.worldObj == this.worldObj ? p : null;
    }

    /** The other companion of the same owner nearby. */
    public EntityCompanion findPartner() {
        if (this.partnerCheckTicks > 0 && (this.cachedPartner == null || !this.cachedPartner.isDead)) {
            return this.cachedPartner;
        }
        this.partnerCheckTicks = 40;
        this.cachedPartner = null;
        List<?> list = this.worldObj.getEntitiesWithinAABB(EntityCompanion.class, this.boundingBox.expand(32.0D, 16.0D, 32.0D));
        for (Object o : list) {
            EntityCompanion c = (EntityCompanion) o;
            if (c != this && c.isLagger() != this.isLagger() && c.getOwnerId().equals(this.getOwnerId())) {
                this.cachedPartner = c;
                break;
            }
        }
        return this.cachedPartner;
    }

    /** The companion that handles shared events (weather, chatter) for the pair. */
    private boolean isEventLeader() {
        EntityCompanion p = findPartner();
        return p == null || this.getEntityId() < p.getEntityId();
    }

    private EntityCompanion randomOfPair() {
        EntityCompanion p = findPartner();
        if (p == null || p.isKnockedOut()) return this;
        return this.rand.nextBoolean() ? this : p;
    }

    public boolean teleportNear(EntityPlayer target) {
        int bx = MathHelper.floor_double(target.posX);
        int by = MathHelper.floor_double(target.boundingBox.minY);
        int bz = MathHelper.floor_double(target.posZ);
        for (int i = 0; i < 20; i++) {
            int x = bx + this.rand.nextInt(7) - 3;
            int z = bz + this.rand.nextInt(7) - 3;
            if (Math.abs(x - bx) < 2 && Math.abs(z - bz) < 2) continue;
            for (int dy = 1; dy >= -2; dy--) {
                int y = by + dy;
                boolean ground = World.doesBlockHaveSolidTopSurface(this.worldObj, x, y - 1, z) && this.worldObj.isAirBlock(x, y, z);
                boolean water = this.worldObj.getBlock(x, y - 1, z).getMaterial() == Material.water && this.worldObj.isAirBlock(x, y, z);
                if ((ground || water) && this.worldObj.isAirBlock(x, y + 1, z)) {
                    this.setLocationAndAngles(x + 0.5D, y, z + 0.5D, this.rotationYaw, this.rotationPitch);
                    this.getNavigator().clearPathEntity();
                    this.fallDistance = 0;
                    return true;
                }
            }
        }
        if (target.ridingEntity == null && target.onGround) {
            this.setLocationAndAngles(target.posX, target.posY, target.posZ, this.rotationYaw, this.rotationPitch);
            this.getNavigator().clearPathEntity();
            this.fallDistance = 0;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- update

    @Override
    public void onLivingUpdate() {
        this.updateArmSwingProgress();
        super.onLivingUpdate();
        if (this.worldObj.isRemote) return;

        if (this.ownerCheckTicks > 0) this.ownerCheckTicks--;
        if (this.partnerCheckTicks > 0) this.partnerCheckTicks--;
        if (this.pickupBlockedTicks > 0) this.pickupBlockedTicks--;
        if (this.eatCooldown > 0) this.eatCooldown--;

        if (this.bubbleTicks > 0 && --this.bubbleTicks == 0) {
            this.dataWatcher.updateObject(DW_BUBBLE, "");
        }

        // knocked out
        if (isKnockedOut()) {
            this.motionX = 0;
            this.motionZ = 0;
            this.getNavigator().clearPathEntity();
            if (--this.knockTicks <= 0) {
                setMLFlag(FLAG_KNOCKED, false);
                this.setHealth(this.getMaxHealth() / 2.0F);
                Speech.say(this, "revived", true);
            }
            return;
        }

        // Lagger's "lag"
        if (isLagging()) {
            this.motionX = 0;
            this.motionZ = 0;
            this.getNavigator().clearPathEntity();
            if (--this.lagTicks <= 0) {
                setMLFlag(FLAG_LAGGING, false);
                double yaw = Math.toRadians(this.rotationYaw);
                double nx = this.posX - Math.sin(yaw) * 1.5D;
                double nz = this.posZ + Math.cos(yaw) * 1.5D;
                int bx = MathHelper.floor_double(nx), by = MathHelper.floor_double(this.posY), bz = MathHelper.floor_double(nz);
                if (this.worldObj.isAirBlock(bx, by, bz) && this.worldObj.isAirBlock(bx, by + 1, bz)) {
                    this.setPosition(nx, this.posY, nz);
                }
            }
            return;
        }
        if (isLagger() && ModConfig.enableLaggerGlitch && getAttackTarget() == null && this.rand.nextInt(2400) == 0) {
            setMLFlag(FLAG_LAGGING, true);
            this.lagTicks = 25 + this.rand.nextInt(20);
            Speech.sayOrDialog(this, "lag", false, 0.3F);
        }

        // regeneration out of combat
        if (getAttackTarget() == null && this.getHealth() < this.getMaxHealth() && ++this.regenTicks >= 100) {
            this.regenTicks = 0;
            this.heal(1.0F);
        }
        // eat food from inventory
        if (this.eatCooldown == 0 && this.getHealth() < this.getMaxHealth() - 6.0F && (getAttackTarget() == null || this.retreating)) {
            eatFromInventory();
        }

        // plant saplings for trees chopped earlier
        if (!this.pendingReplants.isEmpty() && this.ticksExisted % 40 == 0) {
            Iterator<int[]> it = this.pendingReplants.iterator();
            while (it.hasNext()) {
                int[] p = it.next();
                if (this.getDistanceSq(p[0], p[1], p[2]) > 32 * 32 || AIChopTrees.replant(this, p[0], p[1], p[2])) it.remove();
            }
        }

        // fell into the void
        if (this.posY < -5.0D) {
            EntityPlayer o = getOwnerHere();
            if (o != null) teleportNear(o);
        }

        EntityPlayer owner = getOwner();
        if (owner == null) return;

        if (!this.checkedGeneration || this.ticksExisted % 100 == 0) {
            this.checkedGeneration = true;
            if (CompanionManager.getGeneration(owner, this) != this.generation) {
                // an older copy of this companion: the owner summoned a new one
                this.setDead();
                return;
            }
        }

        if (owner.worldObj != this.worldObj) {
            if (getMode() == MODE_FOLLOW && owner instanceof EntityPlayerMP) {
                CompanionManager.transferTo(this, (EntityPlayerMP) owner);
            }
            return;
        }

        if (this.ticksExisted % 100 == 0) CompanionManager.saveSnapshot(this, owner);

        // owner idle tracking (for auto tree chopping)
        double mdx = owner.posX - this.lastOwnerX, mdz = owner.posZ - this.lastOwnerZ;
        if (mdx * mdx + mdz * mdz < 0.01D) {
            this.ownerIdleTicks++;
        } else {
            this.ownerIdleTicks = 0;
        }
        this.lastOwnerX = owner.posX;
        this.lastOwnerZ = owner.posZ;

        updateBoat(owner);

        if (++this.envTicks >= 40) {
            this.envTicks = 0;
            if (isEventLeader()) updateEnvironment(owner);
        }
    }

    private void updateBoat(EntityPlayer owner) {
        // boats carry one rider, so companions swim after the owner and teleport when left behind
        if (this.ridingEntity instanceof EntityBoat && owner.ridingEntity == null) this.mountEntity(null);
        if (this.noSeatCooldown > 0) this.noSeatCooldown--;
        if (owner.ridingEntity != null && this.ridingEntity == null && getMode() == MODE_FOLLOW && this.noSeatCooldown == 0
            && this.getDistanceSqToEntity(owner) > 20 * 20) {
            this.noSeatCooldown = 20 * 120;
            Speech.say(this, "no_seat", false);
        }
    }

    private void updateEnvironment(EntityPlayer owner) {
        boolean night = !this.worldObj.isDaytime();
        boolean rain = this.worldObj.isRaining();
        boolean thunder = this.worldObj.isThundering();
        boolean riding = owner.ridingEntity != null;
        if (this.envInit && this.worldObj.provider.dimensionId == 0) {
            if (night != this.wasNight) Speech.sayOrDialog(randomOfPair(), night ? "night" : "day", false, 0.35F);
            else if (thunder && !this.wasThunder) Speech.say(randomOfPair(), "thunder", false);
            else if (rain && !this.wasRain) Speech.sayOrDialog(randomOfPair(), "rain", false, 0.3F);
        }
        if (this.envInit && riding && !this.ownerWasRiding) Speech.sayOrDialog(randomOfPair(), "boat", false, 0.3F);
        this.envInit = true;
        this.wasNight = night;
        this.wasRain = rain;
        this.wasThunder = thunder;
        this.ownerWasRiding = riding;

        // pirates and ships from other mods
        if (this.pirateCooldown > 0) this.pirateCooldown -= 40;
        else {
            List<?> near = this.worldObj.getEntitiesWithinAABB(Entity.class, this.boundingBox.expand(24.0D, 12.0D, 24.0D));
            for (Object o : near) {
                Entity e = (Entity) o;
                if (e instanceof EntityPlayer || e instanceof EntityCompanion) continue;
                String name = EntityList.getEntityString(e);
                if (name == null) continue;
                name = name.toLowerCase();
                if (name.contains("pirate") || name.contains("ship")) {
                    this.pirateCooldown = 20 * 180;
                    Speech.say(randomOfPair(), "pirate", true);
                    break;
                }
            }
        }

        // random chatter
        if (ModConfig.enableChatter) {
            this.chatterTicks -= 40;
            if (this.chatterTicks <= 0) {
                this.chatterTicks = 20 * (ModConfig.chatterMinSeconds
                    + this.rand.nextInt(Math.max(1, ModConfig.chatterMaxSeconds - ModConfig.chatterMinSeconds + 1)));
                EntityCompanion speaker = randomOfPair();
                if (!speaker.isBusy()) Speech.sayOrDialog(speaker, "idle", true, 0.35F);
            }
        }
    }

    // ---------------------------------------------------------------- combat & damage

    @Override
    public boolean attackEntityAsMob(Entity target) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        this.swingItem();
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        if (hit && target instanceof EntityLivingBase) {
            double yaw = Math.toRadians(this.rotationYaw);
            target.addVelocity(-Math.sin(yaw) * 0.3D, 0.1D, Math.cos(yaw) * 0.3D);
        }
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isEntityInvulnerable()) return false;
        if (this.worldObj.isRemote) return false;
        if (source == DamageSource.outOfWorld) {
            EntityPlayer o = getOwnerHere();
            if (o != null && teleportNear(o)) return false;
            return super.attackEntityFrom(source, amount);
        }
        if (isKnockedOut()) return false;
        if (ModConfig.immortal) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof EntityCompanion) return false;
        boolean byOwner = attacker instanceof EntityPlayer && isOwner((EntityPlayer) attacker);
        if (byOwner) {
            amount = Math.min(amount, 1.0F);
            Speech.say(this, "hit_by_owner", false);
        }
        if (!ModConfig.trueDeath && amount >= this.getHealth()) {
            knockOut();
            return true;
        }
        boolean result = super.attackEntityFrom(source, amount);
        if (result) {
            if (byOwner) {
                this.setRevengeTarget(null);
            } else if (this.getHealth() < this.getMaxHealth() * getRetreatThreshold()) {
                Speech.say(this, "low_hp", false);
            } else if (attacker != null) {
                Speech.say(this, "hurt", false);
            }
        }
        return result;
    }

    private void knockOut() {
        this.setHealth(1.0F);
        setMLFlag(FLAG_KNOCKED, true);
        this.knockTicks = 100;
        this.setAttackTarget(null);
        this.setRevengeTarget(null);
        this.retreating = false;
        this.getNavigator().clearPathEntity();
        if (this.ridingEntity != null) this.mountEntity(null);
        this.playSound("game.player.hurt", 1.0F, 0.8F);
        this.lastSpeakTick = -100000L;
        Speech.say(this, "knocked", true);
    }

    @Override
    protected boolean isMovementBlocked() {
        return super.isMovementBlocked() || isKnockedOut() || isLagging();
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!this.worldObj.isRemote) {
            dropInventory(true);
            EntityPlayer owner = getOwner();
            if (owner != null) {
                CompanionManager.clearSnapshot(owner, this);
                owner.addChatMessage(new ChatComponentText("§c" + getDisplayName() + " погиб. Призови снова: /companions summon"));
            }
        }
        super.onDeath(source);
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {}

    @Override
    @SideOnly(Side.CLIENT)
    public void handleHealthUpdate(byte id) {
        if (id == 7) {
            for (int i = 0; i < 7; i++) {
                this.worldObj.spawnParticle("heart", this.posX + (this.rand.nextFloat() * 2F - 1F) * 0.6D, this.posY + 0.5D + this.rand.nextFloat() * 1.5D,
                    this.posZ + (this.rand.nextFloat() * 2F - 1F) * 0.6D, 0, 0.1D, 0);
            }
        } else {
            super.handleHealthUpdate(id);
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected boolean interact(EntityPlayer player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (getOwnerId().isEmpty() && !this.worldObj.isRemote) {
            setOwner(player);
            CompanionManager.adopt(player, this);
        }
        if (!isOwner(player)) {
            if (!this.worldObj.isRemote) {
                player.addChatMessage(new ChatComponentText(getDisplayName() + " слушается только своего хозяина."));
            }
            return true;
        }
        if (player.isSneaking()) {
            if (this.worldObj.isRemote) MLCompanions.proxy.openMenu(this);
            return true;
        }
        if (this.worldObj.isRemote) return true;
        if (held != null && held.getItem() instanceof ItemFood) {
            feed(((ItemFood) held.getItem()).func_150905_g(held));
            if (!player.capabilities.isCreativeMode) {
                held.stackSize--;
                if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            return true;
        }
        if (held != null && (held.getItem() instanceof ItemSword || held.getItem() instanceof ItemAxe)) {
            ItemStack old = this.getHeldItem();
            if (old != null) {
                ItemStack left = addToInventory(old);
                if (left != null) this.entityDropItem(left, 0.5F);
            }
            this.setCurrentItemOrArmor(0, held.copy());
            if (!player.capabilities.isCreativeMode) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            Speech.say(this, "new_weapon", true);
            return true;
        }
        Speech.say(this, "greet", true);
        return true;
    }

    public void feed(int foodPoints) {
        this.heal(Math.max(2, foodPoints));
        this.worldObj.setEntityState(this, (byte) 7);
        this.playSound("random.eat", 0.6F, 1.0F);
        Speech.say(this, "fed", true);
    }

    private void eatFromInventory() {
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemFood) {
                this.heal(Math.max(2, ((ItemFood) s.getItem()).func_150905_g(s)));
                this.playSound("random.eat", 0.6F, 1.0F);
                this.inventory.decrStackSize(i, 1);
                this.eatCooldown = 60;
                return;
            }
        }
        this.eatCooldown = 100;
    }

    // ---------------------------------------------------------------- inventory

    /** @return what did not fit, or null */
    public ItemStack addToInventory(ItemStack stack) {
        if (stack == null) return null;
        stack = stack.copy();
        for (int i = 0; i < this.inventory.getSizeInventory() && stack.stackSize > 0; i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s != null && s.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(s, stack) && s.isStackable()) {
                int max = Math.min(s.getMaxStackSize(), this.inventory.getInventoryStackLimit());
                int move = Math.min(max - s.stackSize, stack.stackSize);
                if (move > 0) {
                    s.stackSize += move;
                    stack.stackSize -= move;
                }
            }
        }
        for (int i = 0; i < this.inventory.getSizeInventory() && stack.stackSize > 0; i++) {
            if (this.inventory.getStackInSlot(i) == null) {
                this.inventory.setInventorySlotContents(i, stack);
                return null;
            }
        }
        this.inventory.markDirty();
        return stack.stackSize > 0 ? stack : null;
    }

    public boolean isInventoryFull() {
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            if (this.inventory.getStackInSlot(i) == null) return false;
        }
        return true;
    }

    /** Swap the held item with the first axe in the inventory. Returns true if holding an axe now. */
    public boolean equipAxe() {
        ItemStack held = this.getHeldItem();
        if (held != null && held.getItem() instanceof ItemAxe) return true;
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemAxe) {
                this.inventory.setInventorySlotContents(i, held);
                this.setCurrentItemOrArmor(0, s);
                return true;
            }
        }
        return false;
    }

    /** Swap the held item with the first sword in the inventory (after chopping). */
    public void equipSword() {
        ItemStack held = this.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSword) return;
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemSword) {
                this.inventory.setInventorySlotContents(i, held);
                this.setCurrentItemOrArmor(0, s);
                return;
            }
        }
    }

    /** Drop everything except weapons and food (or everything if all=true). */
    public void dropInventory(boolean all) {
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s == null) continue;
            if (!all && (s.getItem() instanceof ItemFood || s.getItem() instanceof ItemSword || s.getItem() instanceof ItemAxe)) continue;
            this.entityDropItem(s, 0.5F);
            this.inventory.setInventorySlotContents(i, null);
        }
    }

    public void giveLoot() {
        dropInventory(false);
        this.pickupBlockedTicks = 20 * 15;
        Speech.say(this, "give_loot", true);
    }

    // ---------------------------------------------------------------- save / load

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("MLOwner", getOwnerId());
        tag.setByte("MLMode", (byte) getMode());
        tag.setInteger("MLHomeX", this.homeX);
        tag.setInteger("MLHomeY", this.homeY);
        tag.setInteger("MLHomeZ", this.homeZ);
        tag.setLong("MLGen", this.generation);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
            ItemStack s = this.inventory.getStackInSlot(i);
            if (s != null) {
                NBTTagCompound t = new NBTTagCompound();
                t.setByte("Slot", (byte) i);
                s.writeToNBT(t);
                list.appendTag(t);
            }
        }
        tag.setTag("MLInventory", list);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.dataWatcher.updateObject(DW_OWNER, tag.getString("MLOwner"));
        this.dataWatcher.updateObject(DW_MODE, Byte.valueOf(tag.getByte("MLMode")));
        this.homeX = tag.getInteger("MLHomeX");
        this.homeY = tag.getInteger("MLHomeY");
        this.homeZ = tag.getInteger("MLHomeZ");
        this.generation = tag.getLong("MLGen");
        for (int i = 0; i < this.inventory.getSizeInventory(); i++) this.inventory.setInventorySlotContents(i, null);
        NBTTagList list = tag.getTagList("MLInventory", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < this.inventory.getSizeInventory()) {
                this.inventory.setInventorySlotContents(slot, ItemStack.loadItemStackFromNBT(t));
            }
        }
        this.setCustomNameTag(getDisplayName());
        this.setAlwaysRenderNameTag(true);
    }
}
