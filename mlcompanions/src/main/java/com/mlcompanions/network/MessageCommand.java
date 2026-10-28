package com.mlcompanions.network;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.mlcompanions.MLCompanions;
import com.mlcompanions.Speech;
import com.mlcompanions.entity.EntityCompanion;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client -> server: a button in the companion menu was pressed. */
public class MessageCommand implements IMessage {

    public static final int FOLLOW = 0, STAY = 1, CHOP = 2, GUARD = 3, INVENTORY = 4, GIVE_LOOT = 5;

    private int entityId;
    private int action;

    public MessageCommand() {}

    public MessageCommand(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeByte(this.action);
    }

    private static final ConcurrentLinkedQueue<Object[]> QUEUE = new ConcurrentLinkedQueue<Object[]>();

    public static class Handler implements IMessageHandler<MessageCommand, IMessage> {

        @Override
        public IMessage onMessage(MessageCommand message, MessageContext ctx) {
            // handled on the server thread (see processQueue)
            QUEUE.add(new Object[] { ctx.getServerHandler().playerEntity, message });
            return null;
        }
    }

    /** Called from the server tick. */
    public static void processQueue() {
        Object[] e;
        while ((e = QUEUE.poll()) != null) {
            handle((EntityPlayerMP) e[0], (MessageCommand) e[1]);
        }
    }

    private static void handle(EntityPlayerMP player, MessageCommand m) {
        Entity e = player.worldObj.getEntityByID(m.entityId);
        if (!(e instanceof EntityCompanion)) return;
        EntityCompanion c = (EntityCompanion) e;
        if (!c.isOwner(player) || c.getDistanceSqToEntity(player) > 10 * 10) return;
        switch (m.action) {
            case FOLLOW:
                c.setMode(EntityCompanion.MODE_FOLLOW);
                Speech.say(c, "mode_follow", true);
                break;
            case STAY:
                c.setMode(EntityCompanion.MODE_STAY);
                Speech.say(c, "mode_stay", true);
                break;
            case CHOP:
                c.setMode(EntityCompanion.MODE_CHOP);
                Speech.say(c, "mode_chop", true);
                break;
            case GUARD:
                c.setMode(EntityCompanion.MODE_GUARD);
                Speech.say(c, "mode_guard", true);
                break;
            case INVENTORY:
                player.openGui(MLCompanions.instance, 0, player.worldObj, c.getEntityId(), 0, 0);
                break;
            case GIVE_LOOT:
                c.giveLoot();
                break;
            default:
                break;
        }
    }
}
