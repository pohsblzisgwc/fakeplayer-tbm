package net.minecraft.client.multiplayer;
import java.util.*;
public class ClientPacketListener {
 public final Set<String> online=new HashSet<>(); public final List<String> sent=new ArrayList<>();
 public PlayerInfo getPlayerInfoIgnoreCase(String name) {return online.contains(name) ? new PlayerInfo() : null;}
 public void sendCommand(String s) {sent.add(s);}
}
