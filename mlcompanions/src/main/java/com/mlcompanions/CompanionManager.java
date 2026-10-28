package com.mlcompanions;

import java.util.ArrayList;
import java.util.List;

import com.mlcompanions.entity.EntityCompanion;
import com.mlcompanions.entity.EntityLagger;
import com.mlcompanions.entity.EntityMystic;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Keeps one Mystic and one Lagger per player.
 * Every companion has a "generation"; the current generation is stored in the player's persistent NBT.
 * A snapshot of the companion is stored there too, so the companion can be recalled with its inventory.
 */
public class CompanionManager {

    private static String key(boolean lagger) {
        return lagger ? "lagger" : "mystic";
    }

    private static NBTTagCompound data(EntityPlayer player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) root.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static long getGeneration(EntityPlayer owner, EntityCompanion c) {
        NBTTagCompound d = data(owner);
        String k = "mlc_gen_" + key(c.isLagger());
        if (!d.hasKey(k)) {
            d.setLong(k, c.generation);
            return c.generation;
        }
        return d.getLong(k);
    }

    private static long nextGeneration(EntityPlayer owner, boolean lagger) {
        NBTTagCompound d = data(owner);
        String k = "mlc_gen_" + key(lagger);
        long g = d.getLong(k) + 1;
        d.setLong(k, g);
        return g;
    }

    public static void adopt(EntityPlayer owner, EntityCompanion c) {
        c.generation = getGeneration(owner, c);
    }

    public static void saveSnapshot(EntityCompanion c, EntityPlayer owner) {
        NBTTagCompound t = new NBTTagCompound();
        c.writeToNBT(t);
        data(owner).setTag("mlc_snap_" + key(c.isLagger()), t);
    }

    public static void clearSnapshot(EntityPlayer owner, EntityCompanion c) {
        data(owner).removeTag("mlc_snap_" + key(c.isLagger()));
    }

    /** All loaded companions of this player (any dimension). */
    public static List<EntityCompanion> findLoaded(EntityPlayer owner) {
        List<EntityCompanion> result = new ArrayList<EntityCompanion>();
        String id = owner.getUniqueID().toString();
        for (WorldServer w : MinecraftServer.getServer().worldServers) {
            if (w == null) continue;
            for (Object o : w.loadedEntityList) {
                if (o instanceof EntityCompanion) {
                    EntityCompanion c = (EntityCompanion) o;
                    if (!c.isDead && id.equals(c.getOwnerId())) result.add(c);
                }
            }
        }
        return result;
    }

    private static EntityCompanion findCurrent(EntityPlayer owner, boolean lagger) {
        for (EntityCompanion c : findLoaded(owner)) {
            if (c.isLagger() == lagger && c.generation == getGeneration(owner, c)) return c;
        }
        return null;
    }

    private static EntityCompanion create(World world, boolean lagger) {
        return lagger ? new EntityLagger(world) : new EntityMystic(world);
    }

    /** Move a companion into the owner's world (other dimension). */
    public static EntityCompanion transferTo(EntityCompanion c, EntityPlayerMP owner) {
        World target = owner.worldObj;
        NBTTagCompound t = new NBTTagCompound();
        c.writeToNBT(t);
        EntityCompanion n = create(target, c.isLagger());
        n.readFromNBT(t);
        n.dimension = owner.dimension;
        n.setLocationAndAngles(owner.posX, owner.posY, owner.posZ, owner.rotationYaw, 0.0F);
        n.setOwner(owner);
        c.setDead();
        target.spawnEntityInWorld(n);
        n.teleportNear(owner);
        int dim = target.provider.dimensionId;
        Speech.say(n, dim == -1 ? "nether" : dim == 1 ? "end" : "overworld", true);
        return n;
    }

    /** Bring a companion to the player: teleports the loaded one, or restores it from the snapshot, or creates a new one. */
    public static EntityCompanion summon(EntityPlayerMP player, boolean lagger, double x, double y, double z) {
        EntityCompanion c = findCurrent(player, lagger);
        if (c != null) {
            if (c.worldObj != player.worldObj) c = transferTo(c, player);
            c.setLocationAndAngles(x, y, z, c.rotationYaw, 0.0F);
            c.getNavigator().clearPathEntity();
            if (c.getMode() != EntityCompanion.MODE_FOLLOW) c.setMode(EntityCompanion.MODE_FOLLOW);
            Speech.say(c, "greet", true);
            return c;
        }
        World world = player.worldObj;
        EntityCompanion n = create(world, lagger);
        NBTTagCompound d = data(player);
        String snapKey = "mlc_snap_" + key(lagger);
        if (d.hasKey(snapKey)) {
            n.readFromNBT(d.getCompoundTag(snapKey));
            n.dimension = player.dimension;
            if (n.getHealth() <= 0) n.setHealth(n.getMaxHealth());
        }
        n.generation = nextGeneration(player, lagger);
        n.setOwner(player);
        n.setLocationAndAngles(x, y, z, player.rotationYaw, 0.0F);
        n.setMode(EntityCompanion.MODE_FOLLOW);
        world.spawnEntityInWorld(n);
        saveSnapshot(n, player);
        Speech.say(n, "greet", true);
        return n;
    }

    /** Summon both companions near the player. */
    public static void summonBoth(EntityPlayerMP player) {
        for (boolean lagger : new boolean[] { false, true }) {
            EntityCompanion c = summon(player, lagger, player.posX, player.posY, player.posZ);
            c.teleportNear(player);
        }
    }

    /** Remove the player's companions; their items drop on the ground. */
    public static int dismiss(EntityPlayerMP player) {
        int n = 0;
        for (EntityCompanion c : findLoaded(player)) {
            c.dropInventory(true);
            if (c.getHeldItem() != null) {
                c.entityDropItem(c.getHeldItem(), 0.5F);
                c.setCurrentItemOrArmor(0, null);
            }
            c.setDead();
            n++;
        }
        for (boolean lagger : new boolean[] { false, true }) {
            nextGeneration(player, lagger);
            data(player).removeTag("mlc_snap_" + key(lagger));
        }
        return n;
    }

    /** Companions near the player (same world). */
    public static List<EntityCompanion> nearby(EntityPlayer player, double range) {
        List<EntityCompanion> result = new ArrayList<EntityCompanion>();
        List<?> list = player.worldObj.getEntitiesWithinAABB(EntityCompanion.class, player.boundingBox.expand(range, range / 2, range));
        for (Object o : list) {
            EntityCompanion c = (EntityCompanion) o;
            if (c.isOwner(player) && !c.isKnockedOut()) result.add(c);
        }
        return result;
    }
}
