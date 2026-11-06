package dev.faketbm;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Method;
public final class SchedulerTest {
 static void check(boolean x, String msg){ if(!x) throw new AssertionError(msg); }
 static void ticks(int n) throws Exception { Method m=FakeTbm.class.getDeclaredMethod("onTick",Minecraft.class); m.setAccessible(true); while(n-->0)m.invoke(null,Minecraft.instance); }
 public static void main(String[] args) throws Exception {
  var mc=Minecraft.instance; var c=mc.connection;
  check(FakeTbm.command(null,"area -1 0 0 0 64")==1,"area");
  check(FakeTbm.command(null,"config TBM_ 2 3")==1,"config");
  check(FakeTbm.command(null,"start")==1,"start"); ticks(1);
  check(c.sent.get(0).equals("player TBM_1 spawn at -0.5 65 0.5"),"negative coordinate");
  c.online.add("TBM_1"); ticks(20); ticks(20); ticks(20); ticks(20);
  check(c.sent.contains("player TBM_1 attack continuous"),"attack");
  check(c.sent.stream().anyMatch(s->s.startsWith("player TBM_2 spawn")),"second slot started");
  c.online.remove("TBM_1"); ticks(120);
  check(c.sent.stream().filter(s->s.startsWith("player TBM_1 spawn")).count()>=2,"offline retries same slot");
  check(FakeTbm.running,"offline does not stop");
  ticks(500);
  check(c.sent.stream().filter(s->s.startsWith("player TBM_2 spawn")).count()>=2,"spawn timeout retry");
  check(FakeTbm.running,"spawn timeout does not stop");
  FakeTbm.running=false; c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"area 0 0 0 0 64"); FakeTbm.command(null,"config TBM_ 1 30"); FakeTbm.command(null,"start");
  ticks(1); c.online.add("TBM_1"); ticks(40);
  var entity=new net.minecraft.client.player.AbstractClientPlayer(); entity.name="TBM_1"; entity.x=8; entity.z=0.5; mc.level.visible.add(entity);
  ticks(120);
  check(c.sent.contains("player TBM_1 kill"),"visible horizontal drift triggers cleanup");
  ticks(420);
  check(c.sent.stream().filter(s->s.equals("player TBM_1 kill")).count()>=2,"kill timeout retry");
  check(FakeTbm.running,"kill timeout does not stop");
  c.online.remove("TBM_1"); ticks(120);
  check(c.sent.stream().filter(s->s.startsWith("player TBM_1 spawn")).count()>=2,"drift retries same block after cleanup");
  FakeTbm.running=false; c.online.clear(); c.sent.clear(); mc.level.visible.clear();
  check(FakeTbm.command(null,"interval 0")==0 && FakeTbm.interval==20,"reject interval below one tick");
  check(FakeTbm.command(null,"interval 1")==1,"one-tick interval");
  FakeTbm.command(null,"area 0 0 1 0 64"); FakeTbm.command(null,"config TBM_ 2 30"); FakeTbm.command(null,"start");
  ticks(1); c.online.add("TBM_1"); ticks(1); ticks(1); ticks(1);
  check(c.sent.size()==4 && c.sent.get(1).endsWith("sneak") && c.sent.get(2).endsWith("look down") && c.sent.get(3).endsWith("attack continuous"),"sneak before attack at one command per tick");
  check(FakeTbm.command(null,"interval 7")==1,"change interval while running");
  ticks(6); check(c.sent.size()==4,"new interval prevents early command");
  ticks(1); check(c.sent.size()==5 && c.sent.get(4).startsWith("player TBM_2 spawn"),"new interval applied");
  check(FakeTbm.command(null,"interval 1201")==0 && FakeTbm.interval==7,"reject out-of-range interval");
  // Consecutive spawn timeouts add 5, then 10 seconds after the 20-second observation window.
  FakeTbm.running=false; c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"area 0 0 0 0 64"); FakeTbm.command(null,"config TBM_ 1 30"); FakeTbm.command(null,"interval 1"); FakeTbm.command(null,"start");
  ticks(1); ticks(400); ticks(99); check(c.sent.size()==1,"no spawn before first backoff");
  ticks(1); check(c.sent.size()==2,"first spawn retry after 100 ticks");
  ticks(400); ticks(199); check(c.sent.size()==2,"second spawn backoff doubles");
  ticks(1); check(c.sent.size()==3,"second spawn retry after 200 ticks");
  // A late spawn during backoff is adopted rather than retried or forgotten.
  ticks(400); c.online.add("TBM_1"); ticks(2);
  check(c.sent.size()==4 && c.sent.get(3).endsWith("sneak"),"late spawn adopted");
  // Consecutive deaths use the same growing per-position backoff.
  FakeTbm.running=false; c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"start"); ticks(1); c.online.add("TBM_1"); ticks(2);
  c.online.remove("TBM_1"); ticks(1); ticks(99); check(c.sent.size()==3,"death first backoff");
  ticks(1); check(c.sent.size()==4,"first death retry after 100 ticks");
  c.online.add("TBM_1"); ticks(2); c.online.remove("TBM_1"); ticks(1);
  ticks(199); check(c.sent.size()==6,"death second backoff doubles");
  ticks(1); check(c.sent.size()==7,"second death retry after 200 ticks");
  // Cleanup retries wait 20, then 40 seconds, capped at 300 seconds.
  FakeTbm.running=false; c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"config TBM_ 1 1"); FakeTbm.command(null,"start");
  ticks(1); c.online.add("TBM_1"); ticks(3); ticks(20);
  check(c.sent.size()==5 && c.sent.get(4).endsWith("kill"),"initial kill");
  ticks(399); check(c.sent.size()==5,"first cleanup delay"); ticks(1); check(c.sent.size()==6,"first cleanup retry");
  ticks(799); check(c.sent.size()==6,"second cleanup delay doubles"); ticks(1); check(c.sent.size()==7,"second cleanup retry");
  Method backoff=FakeTbm.class.getDeclaredMethod("backoff",long.class,int.class); backoff.setAccessible(true);
  check((long)backoff.invoke(null,100L,6)==6000L && (long)backoff.invoke(null,400L,6)==6000L,"backoff cap");
  // Search only through air below configured Y; do not descend into a cave.
  FakeTbm.running=false; c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"area 0 0 0 0 64");
  Method floor=FakeTbm.class.getDeclaredMethod("findFloor",net.minecraft.client.multiplayer.ClientLevel.class,int.class,int.class); floor.setAccessible(true);
  mc.level.blocks.clear(); mc.level.blocks.put(60,1); mc.level.blocks.put(10,1);
  check(floor.invoke(null,mc.level,0,0).equals(60),"first floor below Y wins over deeper cave");
  mc.level.blocks.put(62,2);
  check(floor.invoke(null,mc.level,0,0)==null,"non-air obstacle prevents search into cave");
  mc.level.blocks.remove(62); mc.level.blocks.put(64,1); mc.level.blocks.put(65,1);
  check(floor.invoke(null,mc.level,0,0)==null,"blocked headroom rejected");
  mc.level.blocks.remove(64); mc.level.blocks.remove(65); mc.level.loaded=false;
  FakeTbm.command(null,"config TBM_ 1 30"); FakeTbm.command(null,"interval 1"); FakeTbm.command(null,"start"); ticks(1);
  check(c.sent.isEmpty() && FakeTbm.running,"unloaded chunk waits without spawn");
  mc.level.loaded=true; ticks(99); check(c.sent.isEmpty(),"terrain retry waits"); ticks(1);
  check(c.sent.size()==1 && c.sent.get(0).equals("player TBM_1 spawn at 0.5 61 0.5"),"loaded terrain spawns above detected floor");
  c.online.add("TBM_1"); ticks(1); check(c.sent.get(1).equals("player TBM_1 sneak"),"sneak immediately after join");
  c.online.remove("TBM_1"); ticks(1); mc.level.blocks.remove(60); mc.level.blocks.put(55,1); ticks(100);
  check(c.sent.get(c.sent.size()-1).equals("player TBM_1 spawn at 0.5 56 0.5"),"retry rechecks current floor");
  // A visible bot standing on bottom-layer bedrock is cleaned up, then its name is reused.
  FakeTbm.running=false; c.online.clear(); c.sent.clear(); mc.level.visible.clear(); mc.level.blocks.clear(); mc.level.blocks.put(64,1); mc.level.blocks.put(0,3);
  FakeTbm.command(null,"area 0 0 1 0 64"); FakeTbm.command(null,"config TBM_ 1 30"); FakeTbm.command(null,"interval 1"); FakeTbm.command(null,"start");
  ticks(1); c.online.add("TBM_1");
  var bottom=new net.minecraft.client.player.AbstractClientPlayer(); bottom.name="TBM_1"; bottom.x=0.5; bottom.y=1; bottom.z=0.5; mc.level.visible.add(bottom);
  ticks(1); check(c.sent.size()==2 && c.sent.get(1).equals("player TBM_1 kill"),"bedrock triggers immediate cleanup");
  c.online.remove("TBM_1"); ticks(1);
  check(c.sent.size()==3 && c.sent.get(2).equals("player TBM_1 spawn at 1.5 65 0.5"),"bedrock completion reuses name for next position");
  // Ordinary blocks and bedrock above the world's bottom layers are not a match.
  Method atBedrock=FakeTbm.class.getDeclaredMethod("onBedrock",net.minecraft.client.multiplayer.ClientLevel.class,net.minecraft.client.player.AbstractClientPlayer.class); atBedrock.setAccessible(true);
  mc.level.blocks.put(0,1); check(!(boolean)atBedrock.invoke(null,mc.level,bottom),"ordinary bottom block ignored");
  bottom.y=101; mc.level.blocks.put(100,3); check(!(boolean)atBedrock.invoke(null,mc.level,bottom),"elevated bedrock ignored");
  bottom.y=1; mc.level.blocks.put(0,3); mc.level.loaded=false; check(!(boolean)atBedrock.invoke(null,mc.level,bottom),"unloaded chunk not interpreted as bedrock");
  // Progress survives a new store instance; world, dimension and search height stay isolated.
  var dir=java.nio.file.Files.createTempDirectory("progress-test-");
  var store=new ProgressStore(dir,"world-A","overworld");
  store.mark(4,64,-2); store.save(); store=new ProgressStore(dir,"world-A","overworld");
  check(store.contains(4,64,-2) && store.count(0,8,-3,0,64)==1,"progress saved and reloaded");
  check(!store.contains(4,63,-2),"search Y isolated");
  check(!new ProgressStore(dir,"world-B","overworld").contains(4,64,-2),"world isolated");
  check(!new ProgressStore(dir,"world-A","the_nether").contains(4,64,-2),"dimension isolated");
  store.clear(0,8,-3,0,64); store.save(); check(!new ProgressStore(dir,"world-A","overworld").contains(4,64,-2),"clear persists");
  // Restart the task: its first coordinate was completed on bedrock and must be skipped.
  FakeTbm.running=false; mc.level.loaded=true; mc.level.visible.clear(); c.online.clear(); c.sent.clear();
  FakeTbm.command(null,"area 0 0 1 0 64"); check(FakeTbm.completedCount==1,"progress bar loads completion");
  check(FakeTbm.command(null,"start")==1,"restart task"); ticks(1);
  check(c.sent.size()==1 && c.sent.get(0).contains("1.5 65 0.5"),"restart skips completed coordinate");
  // A different server cannot resume an old connection; the original can.
  mc.connection=new net.minecraft.client.multiplayer.ClientPacketListener(); mc.server.ip="another.invalid"; ticks(3);
  check(FakeTbm.running && mc.connection.sent.isEmpty(),"disconnect pauses without commands");
  check(FakeTbm.command(null,"resume")==0,"wrong server rejected");
  mc.server.ip="test.invalid"; check(FakeTbm.command(null,"resume")==1,"same server resumes");
  // Timer expiry is not completion: after cleanup, the column is scheduled again.
  FakeTbm.running=false; mc.level.visible.clear(); mc.connection.online.clear(); mc.connection.sent.clear(); var resumed=mc.connection;
  FakeTbm.command(null,"area 5 0 5 0 64"); FakeTbm.command(null,"config TBM_ 1 1"); FakeTbm.command(null,"interval 1"); FakeTbm.command(null,"start");
  ticks(1); resumed.online.add("TBM_1"); ticks(3); ticks(20);
  check(resumed.sent.get(resumed.sent.size()-1).endsWith("kill") && FakeTbm.completedCount==0,"timer expiry does not mark complete");
  resumed.online.remove("TBM_1"); ticks(1); ticks(99);
  check(resumed.sent.stream().filter(v->v.contains(" spawn at ")).count()==1,"round waits before retry");
  ticks(1); check(resumed.sent.stream().filter(v->v.contains(" spawn at ")).count()==2,"unconfirmed column continues next round");
  // Settings and progress can be restored after a client restart.
  var settingsDir=java.nio.file.Files.createTempDirectory("settings-test-");
  var settingsStore=new SettingsStore(settingsDir);
  var savedSettings=new SettingsStore.Settings(true,-4,5,-3,6,62,"TBM_",4,55,3);
  settingsStore.save(savedSettings); check(savedSettings.equals(new SettingsStore(settingsDir).load()),"settings round trip");
  java.nio.file.Files.writeString(settingsDir.resolve("fake-player-tbm.properties"),"broken=\\uGGGG");
  boolean invalidSettings=false; try { settingsStore.load(); } catch (java.io.IOException e) { invalidSettings=true; }
  check(invalidSettings,"invalid settings reported");
  var blocked=java.nio.file.Files.createTempFile("progress-blocked-", ".tmp");
  var unsaved=new ProgressStore(blocked,"world-A","overworld"); unsaved.mark(1,64,1);
  boolean failedSave=false; try { unsaved.save(); } catch (java.io.IOException e) { failedSave=true; }
  check(failedSave && unsaved.dirty() && unsaved.contains(1,64,1),"failed save preserves pending progress");
  FakeTbm.running=false; FakeTbm.command(null,"area 0 0 0 0 64");
  FakeTbm.command(null,"config BOT_ 2 31"); FakeTbm.command(null,"interval 3");
  var loadedFlag=FakeTbm.class.getDeclaredField("settingsLoaded"); loadedFlag.setAccessible(true); loadedFlag.setBoolean(null,false);
  FakeTbm.areaSet=false; FakeTbm.count=99; FakeTbm.prefix="wrong"; FakeTbm.completedCount=0;
  FakeTbm.command(null,"help");
  check(FakeTbm.areaSet && FakeTbm.count==2 && FakeTbm.prefix.equals("BOT_") && FakeTbm.interval==3 && FakeTbm.completedCount==1,"restart restores settings and progress");
  // A completely finished area ends without issuing commands, even if names are occupied.
  resumed.sent.clear(); resumed.online.add("BOT_1"); resumed.online.add("BOT_2");
  FakeTbm.command(null,"start"); ticks(1);
  check(!FakeTbm.running && resumed.sent.isEmpty(),"completed area skips occupied names");
  check(FakeTbm.command(null,"clear")==1 && FakeTbm.completedCount==0,"explicit area progress clear");
  FakeTbm.command(null,"area 0 0 0 0 64"); check(FakeTbm.completedCount==0,"cleared progress stays cleared");
  // A bot displaced into another column cannot mark the assigned column complete.
  FakeTbm.running=false; resumed.online.clear(); resumed.sent.clear(); mc.level.visible.clear(); mc.server.ip="fresh.invalid";
  mc.level.blocks.clear(); mc.level.blocks.put(64,1); mc.level.blocks.put(0,3);
  FakeTbm.command(null,"area 0 0 1 0 64"); FakeTbm.command(null,"config TBM_ 1 1"); FakeTbm.command(null,"interval 1"); FakeTbm.command(null,"start");
  ticks(1); resumed.online.add("TBM_1"); bottom.name="TBM_1"; bottom.x=1.5; bottom.y=1; bottom.z=0.5; mc.level.visible.add(bottom);
  ticks(1); check(FakeTbm.completedCount==0 && resumed.sent.get(resumed.sent.size()-1).endsWith("kill"),"displaced bedrock does not mark assigned column");
  resumed.online.remove("TBM_1"); ticks(1); ticks(100);
  check(resumed.sent.get(resumed.sent.size()-1).contains("0.5 65 0.5"),"displaced column retried");
  // Both confirmed columns complete the task and survive a fresh store reload.
  bottom.x=0.5; resumed.online.add("TBM_1"); ticks(1); check(FakeTbm.completedCount==1,"first assigned column confirmed");
  resumed.online.remove("TBM_1"); ticks(1); bottom.x=1.5; resumed.online.add("TBM_1"); ticks(1);
  check(FakeTbm.completedCount==2,"second assigned column confirmed");
  resumed.online.remove("TBM_1"); ticks(1); check(!FakeTbm.running,"all completed stops after cleanup");
  FakeTbm.refreshProgress(); check(FakeTbm.completedCount==2,"final progress restored from disk");
  System.out.println("Scheduler tests passed");
 }
}
