package com.mlcompanions;

import com.mlcompanions.entity.EntityLagger;
import com.mlcompanions.entity.EntityMystic;
import com.mlcompanions.network.MessageCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = MLCompanions.MODID, name = "Mystic & Lagger Companions", version = "1.0.0", acceptedMinecraftVersions = "[1.7.10]")
public class MLCompanions {

    public static final String MODID = "mlcompanions";

    @Mod.Instance(MODID)
    public static MLCompanions instance;

    @SidedProxy(clientSide = "com.mlcompanions.client.ClientProxy", serverSide = "com.mlcompanions.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;
    public static ItemSummon summonItem;

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override
        public Item getTabIconItem() {
            return summonItem;
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        Phrases.load(event.getModConfigurationDirectory());

        summonItem = new ItemSummon();
        GameRegistry.registerItem(summonItem, "summon");

        EntityRegistry.registerModEntity(EntityMystic.class, "Mystic", 0, this, 80, 3, true);
        EntityRegistry.registerModEntity(EntityLagger.class, "Lagger", 1, this, 80, 3, true);

        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(MessageCommand.Handler.class, MessageCommand.class, 0, Side.SERVER);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        CompanionEvents events = new CompanionEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandCompanions());
    }
}
