package net.minecraft.client.multiplayer;
import java.util.*; import net.minecraft.client.player.AbstractClientPlayer; import net.minecraft.resources.ResourceKey; import net.minecraft.world.level.Level;
public class ClientLevel { public ResourceKey<Level> key=new ResourceKey<>(); public List<AbstractClientPlayer> visible=new ArrayList<>(); public ResourceKey<Level> dimension(){return key;} public List<AbstractClientPlayer> players(){return visible;} }
