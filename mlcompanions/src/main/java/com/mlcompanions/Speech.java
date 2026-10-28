package com.mlcompanions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

/** Sends companion phrases to chat and runs dialogs between the two companions. */
public class Speech {

    private static class Pending {
        final EntityCompanion speaker;
        final String text;
        long dueTick;

        Pending(EntityCompanion speaker, String text, long dueTick) {
            this.speaker = speaker;
            this.text = text;
            this.dueTick = dueTick;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<Pending>();
    private static long serverTicks = 0;

    /**
     * Try to say a phrase of a category.
     *
     * @param important ignore the random chance and category cooldown
     */
    public static boolean say(EntityCompanion c, String category, boolean important) {
        if (c == null || c.worldObj.isRemote || c.isKnockedOut() && !"knocked".equals(category)) return false;
        long now = c.worldObj.getTotalWorldTime();
        if (!important) {
            if (c.getRNG().nextFloat() > ModConfig.phraseChance) return false;
            if (now - c.lastSpeakTick < ModConfig.phraseCooldownSeconds * 20L) return false;
            Long last = c.categoryTicks.get(category);
            if (last != null && now - last < 60 * 20L) return false;
        } else if (now - c.lastSpeakTick < 30) {
            return false;
        }
        String text = Phrases.get(c.getPhraseKey(), category, c.lastPhrase, c.getRNG());
        if (text == null) return false;
        c.categoryTicks.put(category, now);
        speakNow(c, text);
        return true;
    }

    /** Say a phrase, or (with some chance) start a dialog with the partner. */
    public static void sayOrDialog(EntityCompanion c, String category, boolean important, float dialogChance) {
        if (c == null || c.worldObj.isRemote) return;
        EntityCompanion partner = c.findPartner();
        if (partner != null && c.getRNG().nextFloat() < dialogChance && startDialog(category, c, partner)) return;
        say(c, category, important);
    }

    public static boolean startDialog(String trigger, EntityCompanion a, EntityCompanion b) {
        if (a == null || b == null || a.worldObj.isRemote) return false;
        if (a.isKnockedOut() || b.isKnockedOut()) return false;
        long now = a.worldObj.getTotalWorldTime();
        if (now - a.lastSpeakTick < ModConfig.phraseCooldownSeconds * 20L
            || now - b.lastSpeakTick < ModConfig.phraseCooldownSeconds * 20L) return false;
        Phrases.Dialog d = Phrases.getDialog(trigger, a.getRNG());
        if (d == null) return false;
        EntityCompanion mystic = a.isLagger() ? b : a;
        EntityCompanion lagger = a.isLagger() ? a : b;
        long due = serverTicks;
        for (String[] line : d.lines) {
            EntityCompanion who = "lagger".equals(line[0]) ? lagger : mystic;
            PENDING.add(new Pending(who, line[1], due));
            due += 50 + a.getRNG().nextInt(20);
        }
        a.categoryTicks.put(trigger, now);
        b.categoryTicks.put(trigger, now);
        return true;
    }

    public static void speakNow(EntityCompanion c, String text) {
        c.lastSpeakTick = c.worldObj.getTotalWorldTime();
        c.lastPhrase = text;
        String color = c.isLagger() ? "§a" : "§b";
        ChatComponentText msg = new ChatComponentText(color + "<" + c.getDisplayName() + ">§r " + text);
        double r2 = ModConfig.chatRadius * ModConfig.chatRadius;
        for (Object o : c.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.getDistanceSqToEntity(c) <= r2) p.addChatMessage(msg);
        }
        if (ModConfig.enableBubbles) c.showBubble(text);
        c.playSound("random.pop", 0.4F, c.isLagger() ? 1.4F : 0.9F);
    }

    /** Called every server tick. */
    public static void tick() {
        serverTicks++;
        if (PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.speaker.isDead) {
                it.remove();
                continue;
            }
            if (serverTicks >= p.dueTick) {
                it.remove();
                if (!p.speaker.isKnockedOut()) speakNow(p.speaker, p.text);
            }
        }
    }
}
