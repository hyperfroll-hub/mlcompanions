package com.mlcompanions.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mlcompanions.ModConfig;
import com.mlcompanions.Speech;
import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Finds a natural tree nearby and chops it log by log, then replants a sapling. */
public class AIChopTrees extends EntityAIBase {

    private final EntityCompanion c;
    private final List<int[]> logs = new ArrayList<int[]>();
    private final Map<Long, Long> blacklist = new HashMap<Long, Long>();
    private int index;
    private int[] base;
    private int scanCooldown;
    private int pathTimer;
    private int pathFails;
    private int chopTimer;
    private int ticks;
    private boolean rolledForget;
    private boolean done;
    private int fullWarnCooldown;

    public AIChopTrees(EntityCompanion c) {
        this.c = c;
        this.setMutexBits(3);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    private boolean modeAllows() {
        int mode = this.c.getMode();
        if (mode == EntityCompanion.MODE_CHOP) return true;
        if (mode != EntityCompanion.MODE_FOLLOW) return false;
        EntityPlayer owner = this.c.getOwnerHere();
        return owner != null && this.c.ownerIdleTicks > 300 && this.c.getDistanceSqToEntity(owner) < 14 * 14;
    }

    @Override
    public boolean shouldExecute() {
        if (!ModConfig.enableChopping || this.c.isBusy() || this.c.ridingEntity != null) return false;
        if (this.fullWarnCooldown > 0) this.fullWarnCooldown--;
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = 40;
        if (!modeAllows()) return false;
        if (this.c.isInventoryFull()) {
            if (this.fullWarnCooldown == 0) {
                this.fullWarnCooldown = 20 * 60;
                Speech.say(this.c, "inventory_full", true);
            }
            return false;
        }
        return findTree();
    }

    private boolean findTree() {
        World w = this.c.worldObj;
        long now = w.getTotalWorldTime();
        boolean chopMode = this.c.getMode() == EntityCompanion.MODE_CHOP;
        int cx = chopMode ? this.c.homeX : MathHelper.floor_double(this.c.posX);
        int cy = chopMode ? this.c.homeY : MathHelper.floor_double(this.c.posY);
        int cz = chopMode ? this.c.homeZ : MathHelper.floor_double(this.c.posZ);
        int r = chopMode ? 16 : 10;
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - 3; y <= cy + 6; y++) {
                    if (!isLog(w, x, y, z) || isLog(w, x, y - 1, z)) continue;
                    Long bl = this.blacklist.get(key(x, y, z));
                    if (bl != null && now - bl < 20 * 120) continue;
                    double d = this.c.getDistanceSq(x + 0.5D, y, z + 0.5D);
                    if (d < bestDist) {
                        bestDist = d;
                        best = new int[] { x, y, z };
                    }
                }
            }
        }
        if (best == null) return false;
        if (!collectTree(w, best)) {
            this.blacklist.put(key(best[0], best[1], best[2]), now);
            if (this.blacklist.size() > 200) this.blacklist.clear();
            return false;
        }
        this.base = best;
        return true;
    }

    private static boolean isLog(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b.isWood(w, x, y, z);
    }

    private static boolean isStructure(Block b) {
        if (b == Blocks.planks || b == Blocks.cobblestone || b == Blocks.stonebrick || b == Blocks.wool || b == Blocks.bookshelf
            || b == Blocks.glass || b == Blocks.glass_pane) return true;
        if (b.getMaterial() == Material.glass) return true;
        return b instanceof BlockDoor || b instanceof BlockFence || b instanceof BlockStairs || b instanceof BlockSlab
            || b instanceof BlockChest || b instanceof BlockWorkbench || b instanceof BlockTorch;
    }

    /** Flood-fills connected logs. Rejects trees without leaves and logs that touch player-built blocks. */
    private boolean collectTree(World w, int[] start) {
        this.logs.clear();
        Set<Long> seen = new HashSet<Long>();
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(start);
        seen.add(key(start[0], start[1], start[2]));
        int leaves = 0;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            this.logs.add(p);
            if (this.logs.size() > 96) return false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int x = p[0] + dx, y = p[1] + dy, z = p[2] + dz;
                        if (y < start[1]) continue;
                        Block b = w.getBlock(x, y, z);
                        if (b.isLeaves(w, x, y, z)) {
                            leaves++;
                            continue;
                        }
                        boolean face = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
                        if (face && isStructure(b)) return false;
                        if (b.isWood(w, x, y, z)) {
                            long k = key(x, y, z);
                            if (seen.add(k)) queue.add(new int[] { x, y, z });
                        }
                    }
                }
            }
        }
        if (leaves < 3) return false;
        Collections.sort(this.logs, new Comparator<int[]>() {

            @Override
            public int compare(int[] a, int[] b) {
                return a[1] - b[1];
            }
        });
        return true;
    }

    @Override
    public void startExecuting() {
        this.index = 0;
        this.pathTimer = 0;
        this.pathFails = 0;
        this.chopTimer = 0;
        this.ticks = 0;
        this.rolledForget = false;
        this.done = false;
        this.c.chopping = true;
        this.c.equipAxe();
        Speech.say(this.c, "chop_tree", false);
    }

    @Override
    public boolean continueExecuting() {
        return !this.done && this.base != null && !this.c.isBusy() && this.ticks < 1800 && modeAllows();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        World w = this.c.worldObj;
        double dx = this.base[0] + 0.5D - this.c.posX;
        double dz = this.base[2] + 0.5D - this.c.posZ;
        double dy = this.base[1] - this.c.posY;
        boolean near = dx * dx + dz * dz < 2.7D * 2.7D && Math.abs(dy) < 3;
        if (!near) {
            if (--this.pathTimer <= 0) {
                this.pathTimer = 20;
                if (!this.c.getNavigator().tryMoveToXYZ(this.base[0] + 0.5D, this.base[1], this.base[2] + 0.5D, 1.0D)) {
                    if (++this.pathFails > 5) {
                        this.blacklist.put(key(this.base[0], this.base[1], this.base[2]), w.getTotalWorldTime());
                        this.done = true;
                    }
                }
            }
            return;
        }
        this.c.getNavigator().clearPathEntity();
        while (this.index < this.logs.size()) {
            int[] p = this.logs.get(this.index);
            if (isLog(w, p[0], p[1], p[2])) break;
            this.index++;
        }
        if (this.index >= this.logs.size()) {
            finish();
            return;
        }
        // Lagger sometimes leaves the top of the tree
        int left = this.logs.size() - this.index;
        if (!this.rolledForget && left <= 2 && this.logs.size() >= 4) {
            this.rolledForget = true;
            if (this.c.getRNG().nextFloat() < this.c.getForgetTopChance()) {
                Speech.say(this.c, "forgot_top", true);
                finish();
                return;
            }
        }
        int[] p = this.logs.get(this.index);
        this.c.getLookHelper().setLookPosition(p[0] + 0.5D, p[1] + 0.5D, p[2] + 0.5D, 30.0F, 30.0F);
        if (this.ticks % 6 == 0) this.c.swingItem();
        ItemStack held = this.c.getHeldItem();
        int delay = this.c.getChopDelay();
        if (held != null && held.getItem() instanceof ItemAxe) delay = Math.max(6, delay * 6 / 10);
        if (++this.chopTimer >= delay) {
            this.chopTimer = 0;
            breakLog(w, p[0], p[1], p[2]);
            this.index++;
        }
    }

    private void breakLog(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        List<ItemStack> drops = b.getDrops(w, x, y, z, meta, 0);
        w.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(b) + (meta << 12));
        w.setBlockToAir(x, y, z);
        if (drops == null) return;
        for (ItemStack s : drops) {
            ItemStack left = this.c.addToInventory(s);
            if (left != null) this.c.entityDropItem(left, 0.5F);
        }
        ItemStack held = this.c.getHeldItem();
        if (held != null && held.isItemStackDamageable()) {
            held.damageItem(1, this.c);
            if (held.stackSize <= 0) this.c.setCurrentItemOrArmor(0, null);
        }
    }

    private void finish() {
        this.done = true;
        World w = this.c.worldObj;
        if (this.base != null && this.c.getRNG().nextFloat() < this.c.getReplantChance()) {
            if (!replant(this.c, this.base[0], this.base[1], this.base[2])) {
                // no sapling yet: plant it later, when leaves drop one
                if (this.c.pendingReplants.size() < 8) this.c.pendingReplants.add(new int[] { this.base[0], this.base[1], this.base[2] });
            }
        }
        Speech.say(this.c, "tree_done", false);
    }

    /** @return true if planted (or the spot is no longer plantable) */
    public static boolean replant(EntityCompanion c, int x, int y, int z) {
        World w = c.worldObj;
        if (!w.isAirBlock(x, y, z)) return true;
        Block ground = w.getBlock(x, y - 1, z);
        if (ground != Blocks.grass && ground != Blocks.dirt) return true;
        Item sapling = Item.getItemFromBlock(Blocks.sapling);
        for (int i = 0; i < c.inventory.getSizeInventory(); i++) {
            ItemStack s = c.inventory.getStackInSlot(i);
            if (s != null && s.getItem() == sapling) {
                w.setBlock(x, y, z, Blocks.sapling, s.getItemDamage(), 3);
                w.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "dig.grass", 1.0F, 0.8F);
                c.inventory.decrStackSize(i, 1);
                return true;
            }
        }
        return false;
    }

    @Override
    public void resetTask() {
        this.c.chopping = false;
        this.c.equipSword();
        this.base = null;
        this.logs.clear();
        this.c.getNavigator().clearPathEntity();
    }
}
