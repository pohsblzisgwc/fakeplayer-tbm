package net.minecraft.client;
import java.io.File; import java.nio.file.Files; import java.io.IOException; import net.minecraft.client.multiplayer.*; import net.minecraft.client.player.LocalPlayer; import net.minecraft.client.gui.screens.Screen; import net.minecraft.client.server.IntegratedServer;
public class Minecraft {
 public static Minecraft instance=new Minecraft(); public ClientLevel level=new ClientLevel(); public LocalPlayer player=new LocalPlayer(); public ClientPacketListener connection=new ClientPacketListener(); public ServerData server=new ServerData(); public final File gameDirectory;
 public Minecraft(){try{gameDirectory=Files.createTempDirectory("faketbm-test-").toFile();}catch(IOException e){throw new RuntimeException(e);}}
 public static Minecraft getInstance(){return instance;} public ClientPacketListener getConnection(){return connection;} public void setScreenAndShow(Screen s){} public ServerData getCurrentServer(){return server;} public IntegratedServer getSingleplayerServer(){return null;}
}
