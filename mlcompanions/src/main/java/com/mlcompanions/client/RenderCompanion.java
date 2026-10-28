package com.mlcompanions.client;

import org.lwjgl.opengl.GL11;

import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/** Player-shaped model with the companion's skin, a speech bubble and a "lying" pose when knocked out. */
public class RenderCompanion extends RenderBiped {

    private final ResourceLocation texture;

    public RenderCompanion(String skin) {
        super(new ModelBiped(0.0F), 0.5F);
        this.texture = new ResourceLocation("mlcompanions", "textures/entity/" + skin + ".png");
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return this.texture;
    }

    @Override
    protected void rotateCorpse(EntityLivingBase entity, float age, float yaw, float partialTicks) {
        super.rotateCorpse(entity, age, yaw, partialTicks);
        if (entity instanceof EntityCompanion && ((EntityCompanion) entity).isKnockedOut()) {
            GL11.glTranslatef(0.0F, 0.1F, 0.0F);
            GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-0.9F, 0.0F, 0.0F);
        }
    }

    @Override
    protected void passSpecialRender(EntityLivingBase entity, double x, double y, double z) {
        super.passSpecialRender(entity, x, y, z);
        if (entity instanceof EntityCompanion) {
            String text = ((EntityCompanion) entity).getBubble();
            if (text != null && !text.isEmpty()) {
                this.func_147906_a(entity, "§f" + text, x, y + 0.3D, z, 32);
            }
        }
    }
}
