package com.mlcompanions;

import java.util.List;

import com.mlcompanions.entity.EntityCompanion;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** /companions summon | home | dismiss | follow | stay */
public class CommandCompanions extends CommandBase {

    @Override
    public String getCommandName() {
        return "companions";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/companions <summon|home|follow|stay|dismiss>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "summon", "home", "follow", "stay", "dismiss");
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String sub = args.length > 0 ? args[0].toLowerCase() : "summon";
        if (sub.equals("summon") || sub.equals("home")) {
            CompanionManager.summonBoth(player);
            player.addChatMessage(new ChatComponentText("§eМистик и Лаггер рядом с тобой."));
        } else if (sub.equals("follow") || sub.equals("stay")) {
            int mode = sub.equals("follow") ? EntityCompanion.MODE_FOLLOW : EntityCompanion.MODE_STAY;
            int n = 0;
            for (EntityCompanion c : CompanionManager.nearby(player, 32)) {
                c.setMode(mode);
                Speech.say(c, mode == EntityCompanion.MODE_FOLLOW ? "mode_follow" : "mode_stay", true);
                n++;
            }
            if (n == 0) player.addChatMessage(new ChatComponentText("§cРядом нет твоих компаньонов."));
        } else if (sub.equals("dismiss")) {
            int n = CompanionManager.dismiss(player);
            player.addChatMessage(new ChatComponentText("§eКомпаньоны отпущены (" + n + "). Их вещи выпали на землю."));
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }
}
