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
  System.out.println("Scheduler tests passed");
 }
}
