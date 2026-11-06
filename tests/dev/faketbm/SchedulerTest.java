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
  c.online.add("TBM_1"); ticks(20); ticks(20); ticks(20);
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
  ticks(1); c.online.add("TBM_1"); ticks(1); ticks(1);
  check(c.sent.size()==3 && c.sent.get(1).endsWith("look down") && c.sent.get(2).endsWith("attack continuous"),"one command per tick");
  check(FakeTbm.command(null,"interval 7")==1,"change interval while running");
  ticks(6); check(c.sent.size()==3,"new interval prevents early command");
  ticks(1); check(c.sent.size()==4 && c.sent.get(3).startsWith("player TBM_2 spawn"),"new interval applied");
  check(FakeTbm.command(null,"interval 1201")==0 && FakeTbm.interval==7,"reject out-of-range interval");
  System.out.println("Scheduler tests passed");
 }
}
