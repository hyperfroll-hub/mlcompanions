package com.mlcompanions;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class ModConfig {

    public static boolean enableCombat = true;
    public static boolean enableChopping = true;
    public static boolean enablePickup = true;
    public static boolean enableChatter = true;
    public static boolean enableBubbles = true;
    public static boolean enableLaggerGlitch = true;
    public static boolean enablePlayFights = true;
    public static boolean trueDeath = false;
    public static boolean immortal = false;

    public static float maxHealth = 40F;
    public static float baseDamage = 3F;
    public static float moveSpeed = 0.3F;
    public static int followDistance = 8;
    public static int teleportDistance = 30;
    public static int chatRadius = 32;
    public static int chatterMinSeconds = 90;
    public static int chatterMaxSeconds = 240;
    public static float phraseChance = 0.6F;
    public static int phraseCooldownSeconds = 12;

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        String g = "general";
        enableCombat = c.getBoolean("enableCombat", g, enableCombat, "Компаньоны дерутся с мобами");
        enableChopping = c.getBoolean("enableChopping", g, enableChopping, "Компаньоны рубят деревья");
        enablePickup = c.getBoolean("enablePickup", g, enablePickup, "Компаньоны подбирают предметы");
        enableChatter = c.getBoolean("enableChatter", g, enableChatter, "Компаньоны болтают");
        enableBubbles = c.getBoolean("enableBubbles", g, enableBubbles, "Показывать реплики облачком над головой");
        enableLaggerGlitch = c.getBoolean("enableLaggerGlitch", g, enableLaggerGlitch, "Лаггер иногда 'лагает'");
        enablePlayFights = c.getBoolean("enablePlayFights", g, enablePlayFights, "Шуточные драки между компаньонами");
        trueDeath = c.getBoolean("trueDeath", g, trueDeath, "Настоящая смерть (иначе компаньон теряет сознание и встаёт)");
        immortal = c.getBoolean("immortal", g, immortal, "Компаньоны не получают урон");
        maxHealth = c.getFloat("maxHealth", g, maxHealth, 1F, 1000F, "Максимальное здоровье");
        baseDamage = c.getFloat("baseDamage", g, baseDamage, 0F, 100F, "Урон без оружия");
        moveSpeed = c.getFloat("moveSpeed", g, moveSpeed, 0.1F, 1F, "Скорость");
        followDistance = c.getInt("followDistance", g, followDistance, 3, 32, "На каком расстоянии догонять игрока");
        teleportDistance = c.getInt("teleportDistance", g, teleportDistance, 10, 128, "С какого расстояния телепортироваться к игроку");
        chatRadius = c.getInt("chatRadius", g, chatRadius, 4, 256, "Радиус, в котором видны фразы");
        chatterMinSeconds = c.getInt("chatterMinSeconds", g, chatterMinSeconds, 10, 3600, "Мин. пауза случайной болтовни (сек)");
        chatterMaxSeconds = c.getInt("chatterMaxSeconds", g, chatterMaxSeconds, 10, 3600, "Макс. пауза случайной болтовни (сек)");
        phraseChance = c.getFloat("phraseChance", g, phraseChance, 0F, 1F, "Шанс фразы на событие");
        phraseCooldownSeconds = c.getInt("phraseCooldownSeconds", g, phraseCooldownSeconds, 0, 600, "Мин. пауза между фразами одного NPC (сек)");
        if (chatterMaxSeconds < chatterMinSeconds) chatterMaxSeconds = chatterMinSeconds;
        if (c.hasChanged()) c.save();
    }
}
