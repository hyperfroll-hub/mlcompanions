package com.mlcompanions;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/** Spawn-egg style item: damage 0 = Mystic, 1 = Lagger. Summons (or calls back) your companion. */
public class ItemSummon extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon overlay;

    public ItemSummon() {
        this.setHasSubtypes(true);
        this.setMaxDamage(0);
        this.setMaxStackSize(1);
        this.setUnlocalizedName("mlcompanions.summon");
        this.setTextureName("spawn_egg");
        this.setCreativeTab(MLCompanions.TAB);
    }

    private static boolean isLagger(ItemStack stack) {
        return stack.getItemDamage() == 1;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return isLagger(stack) ? "item.mlcompanions.summon_lagger" : "item.mlcompanions.summon_mystic";
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (world.isRemote) return true;
        if (!(player instanceof EntityPlayerMP)) return false;
        x += Facing.offsetsXForSide[side];
        y += Facing.offsetsYForSide[side];
        z += Facing.offsetsZForSide[side];
        CompanionManager.summon((EntityPlayerMP) player, isLagger(stack), x + 0.5D, y, z + 0.5D);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
        list.add(new ItemStack(item, 1, 1));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        if (isLagger(stack)) return pass == 0 ? 0x1A1A1A : 0xD42020;
        return pass == 0 ? 0x6B4A2B : 0xE8E0D0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        return pass > 0 ? this.overlay : super.getIconFromDamageForRenderPass(damage, pass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        super.registerIcons(reg);
        this.overlay = reg.registerIcon("spawn_egg_overlay");
    }
}
