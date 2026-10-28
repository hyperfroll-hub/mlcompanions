package com.mlcompanions.client;

import com.mlcompanions.MLCompanions;
import com.mlcompanions.entity.EntityCompanion;
import com.mlcompanions.network.MessageCommand;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Shift + right click menu. */
public class GuiCompanionMenu extends GuiScreen {

    private static final String[] LABELS = { "Следовать", "Стоять здесь", "Рубить лес", "Охранять точку", "Открыть инвентарь",
        "Отдать лут" };
    private static final String[] MODES = { "следует", "стоит", "рубит лес", "охраняет" };

    private final EntityCompanion companion;

    public GuiCompanionMenu(EntityCompanion companion) {
        this.companion = companion;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.buttonList.clear();
        int top = this.height / 2 - 70;
        for (int i = 0; i < LABELS.length; i++) {
            this.buttonList.add(new GuiButton(i, this.width / 2 - 75, top + 24 + i * 22, 150, 20, LABELS[i]));
        }
        this.buttonList.add(new GuiButton(100, this.width / 2 - 75, top + 24 + LABELS.length * 22 + 6, 150, 20, "Закрыть"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < LABELS.length) {
            MLCompanions.network.sendToServer(new MessageCommand(this.companion.getEntityId(), button.id));
        }
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int top = this.height / 2 - 70;
        int mode = this.companion.getMode();
        String state = mode >= 0 && mode < MODES.length ? MODES[mode] : "";
        int hp = (int) Math.ceil(this.companion.getHealth());
        int max = (int) Math.ceil(this.companion.getMaxHealth());
        this.drawCenteredString(this.fontRendererObj, (this.companion.isLagger() ? "§a" : "§b") + this.companion.getDisplayName(),
            this.width / 2, top, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, "Сейчас: " + state + "   HP: " + hp + "/" + max, this.width / 2, top + 11, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        if (this.companion.isDead || this.mc.thePlayer == null || this.mc.thePlayer.getDistanceSqToEntity(this.companion) > 12 * 12) {
            this.mc.displayGuiScreen(null);
        }
    }
}
