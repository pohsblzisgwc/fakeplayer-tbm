package net.minecraft.client;
import net.minecraft.client.multiplayer.*; import net.minecraft.client.player.LocalPlayer; import net.minecraft.client.gui.screens.Screen;
public class Minecraft { public static Minecraft instance=new Minecraft(); public ClientLevel level=new ClientLevel(); public LocalPlayer player=new LocalPlayer(); public ClientPacketListener connection=new ClientPacketListener(); public static Minecraft getInstance(){return instance;} public ClientPacketListener getConnection(){return connection;} public void setScreenAndShow(Screen s){} }
