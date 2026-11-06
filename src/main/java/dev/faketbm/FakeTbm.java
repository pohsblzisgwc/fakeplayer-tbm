package dev.faketbm;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.io.IOException;
import net.minecraft.world.level.storage.LevelResource;

public final class FakeTbm implements ClientModInitializer {
    static int minX, maxX, minZ, maxZ, y, count = 3, seconds = 30, interval = 20;
    static String prefix = "TBM_";
    static boolean areaSet, running, stopping;
    private static boolean openScreen, settingsLoaded, settingsWritable=true;
    private static SettingsStore settings;
    static String lastMessage = "准备就绪";
    static long total, next, completedCount;
    private static ProgressStore progress;
    private static String worldId;
    private static long tick, nextCommand, lastCommand, nextSweepAt;
    private static Object dimension;
    private static ClientPacketListener connection;
    private static Slot[] slots = new Slot[0];
    private enum Phase { FREE, RETRY, TERRAIN, SPAWNING, SNEAK, LOOK, ATTACK, WORKING, KILLING }
    private static final class Slot {
        final String name; Phase phase = Phase.FREE; long since, retryAt; int x,z, failures, killRetries, terrainFailures; boolean complete;
        Slot(String name) { this.name = name; }
    }
    @Override public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
            LiteralArgumentBuilder.<FabricClientCommandSource>literal("faketbm")
                .executes(c -> command(c.getSource(), "gui"))
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("args", StringArgumentType.greedyString())
                    .executes(c -> command(c.getSource(), StringArgumentType.getString(c, "args"))))));
        ClientTickEvents.END_CLIENT_TICK.register(FakeTbm::onTick);
    }
    private static void message(String s) {
        lastMessage = s;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[假人盾构机] " + s));
    }
    static int command(FabricClientCommandSource src, String raw) {
        ensureSettings(Minecraft.getInstance());
        String[] a = raw.trim().split("\\s+");
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "help" -> message("gui | area x1 z1 x2 z2 y | config 前缀 数量 秒 | interval tick数 | start | stop | resume | status | clear");
                case "gui" -> openScreen=true;
                case "area" -> {
                    if (running) throw new IllegalArgumentException("运行中不能修改区域");
                    if (a.length != 6) throw new IllegalArgumentException("用法：area x1 z1 x2 z2 y");
                    int x1 = Integer.parseInt(a[1]), z1 = Integer.parseInt(a[2]), x2 = Integer.parseInt(a[3]), z2 = Integer.parseInt(a[4]);
                    int newY = Integer.parseInt(a[5]);
                    if (Math.abs((long)x1)>30000000 || Math.abs((long)x2)>30000000 || Math.abs((long)z1)>30000000 || Math.abs((long)z2)>30000000 || newY < -2048 || newY > 2047) throw new IllegalArgumentException("坐标超出范围");
                    y=newY; minX=Math.min(x1,x2); maxX=Math.max(x1,x2); minZ=Math.min(z1,z2); maxZ=Math.max(z1,z2);
                    total=((long)maxX-minX+1)*((long)maxZ-minZ+1); areaSet=true;
                    refreshProgress();
                    message("区域已设置，共 " + total + " 个位置，已确认完成 " + completedCount);
                    saveSettings();
                }
                case "config" -> {
                    if (running) throw new IllegalArgumentException("运行中不能修改配置");
                    if (a.length != 4) throw new IllegalArgumentException("用法：config 前缀 数量 秒");
                    int n=Integer.parseInt(a[2]), s=Integer.parseInt(a[3]);
                    if (n<1 || n>100 || s<1 || s>86400 || !a[1].matches("[A-Za-z0-9_]+") || a[1].length()+Integer.toString(n).length()>16) throw new IllegalArgumentException("前缀只能为英文、数字或下划线，完整名字不超过16字符；数量1-100，秒数1-86400");
                    prefix=a[1]; count=n; seconds=s;
                    message("名字 " + prefix + "1 至 " + prefix + count + "，每个位置 " + seconds + " 秒");
                    saveSettings();
                }
                case "interval" -> {
                    if (a.length != 2) throw new IllegalArgumentException("用法：interval tick数（1–1200）");
                    int value=Integer.parseInt(a[1]);
                    if (value<1 || value>1200) throw new IllegalArgumentException("命令间隔须为 1–1200 tick");
                    interval=value;
                    if (running) nextCommand=Math.max(tick, lastCommand+interval);
                    message("命令间隔已设为 " + interval + " tick；服务器可能限制命令频率");
                    saveSettings();
                }
                case "start" -> start(Minecraft.getInstance());
                case "resume" -> {
                    Minecraft mc=Minecraft.getInstance();
                    if (!running || mc.getConnection()==null || mc.level==null) throw new IllegalArgumentException("没有可恢复的任务或尚未进入世界");
                    if (mc.getConnection()==connection) throw new IllegalArgumentException("任务已经连接，切回原维度即可继续");
                    if (!worldId.equals(worldId(mc))) throw new IllegalArgumentException("当前不是原服务器或世界，无法恢复任务");
                    connection=mc.getConnection();
                    message("已重新连接任务");
                }
                case "stop" -> { if (!running) message("当前没有任务"); else { stopping=true; message("正在停止，等待已召唤的假人下线"); } }
                case "status" -> message(running ? "已确认完成 " + completedCount + "/" + total + "，已分配 " + next + "，" + (Minecraft.getInstance().getConnection()!=connection ? "等待手动恢复连接" : stopping ? "正在停止" : "运行中") : "当前没有运行中的任务");
                case "clear" -> {
                    if (running) throw new IllegalArgumentException("请先停止任务再清除进度");
                    if (!areaSet) throw new IllegalArgumentException("请先设置要清除的区域");
                    Minecraft mc=Minecraft.getInstance();
                    if (mc.level==null) throw new IllegalArgumentException("请先进入世界");
                    ProgressStore store=openProgress(mc);
                    store.clear(minX,maxX,minZ,maxZ,y);
                    try { store.save(); } catch (IOException e) { throw new IllegalArgumentException("无法写入进度文件，清除未保存"); }
                    progress=store; completedCount=0; message("已清除当前世界、维度、区域和搜索高度的完成记录");
                }
                default -> throw new IllegalArgumentException("未知子命令，输入 /faketbm 查看用法");
            }
        } catch (NumberFormatException e) { message("数字格式不正确"); return 0; }
          catch (IllegalArgumentException e) { message(e.getMessage()); return 0; }
        return 1;
    }
    private static void start(Minecraft mc) {
        if (running) throw new IllegalArgumentException("任务已在运行");
        if (!areaSet) throw new IllegalArgumentException("请先设置区域");
        if (mc.level==null || mc.getConnection()==null) throw new IllegalArgumentException("请先进入世界");
        if (y<mc.level.getMinY() || y>mc.level.getMaxY()) throw new IllegalArgumentException("目标 Y 超出当前维度的高度范围");
        String id=worldId(mc);
        ProgressStore loaded=openProgress(mc);
        Slot[] newSlots=new Slot[count];
        for (int i=0;i<count;i++) {
            newSlots[i]=new Slot(prefix+(i+1));

        }
        slots=newSlots; connection=mc.getConnection(); dimension=mc.level.dimension(); worldId=id; progress=loaded;
        completedCount=progress.count(minX,maxX,minZ,maxZ,y); next=0; tick=0; nextCommand=0; lastCommand=0; nextSweepAt=0; running=true; stopping=false;
        message("开始处理 " + total + " 个位置；请保持在线。被占用的名字将等待释放");
    }
    private static void send(String cmd) { connection.sendCommand(cmd); lastCommand=tick; nextCommand=tick+interval; }
    private static void onTick(Minecraft mc) {
        ensureSettings(mc);
        if (openScreen) { openScreen=false; mc.setScreenAndShow(new TbmScreen()); }
        if (!running) return;
        // Never issue commands to a different connection after a disconnect or server switch.
        if (mc.getConnection()!=connection || mc.level==null) return;
        tick++;
        if (progress.dirty() && tick%200==0) saveProgress();
        boolean sameDimension=mc.level.dimension().equals(dimension);
        if (tick<nextCommand) return;
        for (Slot s:slots) {
            boolean online=connection.getPlayerInfoIgnoreCase(s.name)!=null;
            if (s.phase==Phase.SPAWNING) {
                if (online) { s.phase=Phase.SNEAK; }
                else if (tick-s.since>=400) {
                    if (stopping) s.phase=Phase.FREE;
                    else { scheduleRetry(s); message(s.name+" 召唤超时，将退避后重试；请检查服务端反馈"); }
                }
            }
            if (s.phase==Phase.KILLING) {
                if (!online) {
                    if (s.complete || stopping) s.phase=Phase.FREE;
                    else scheduleRetry(s);
                } else if (tick-s.since>=backoff(400,s.killRetries)) {
                    send("player "+s.name+" kill"); s.since=tick; s.killRetries=Math.min(s.killRetries+1, 6);
                    message(s.name+" 仍在线，已退避后重试清理"); return;
                }
            }
            if (s.phase==Phase.SNEAK || s.phase==Phase.LOOK || s.phase==Phase.ATTACK || s.phase==Phase.WORKING) {
                if (!online) {
                    if (stopping) s.phase=Phase.FREE; else scheduleRetry(s);
                    message(s.name+" 已离线，将退避后重试当前位置"); continue;
                }
                if (sameDimension) {
                    // The tab list confirms presence; entity coordinates exist only in tracking range.
                    var entity=mc.level.players().stream().filter(p -> p.getScoreboardName().equalsIgnoreCase(s.name)).findFirst();
                    if (entity.isPresent() && onBedrock(mc.level,entity.get())) {
                        boolean inColumn=(int)Math.floor(entity.get().getX())==s.x && (int)Math.floor(entity.get().getZ())==s.z;
                        if (inColumn && progress.mark(s.x,y,s.z)) { completedCount++; saveProgress(); }
                        s.complete=inColumn; send("player "+s.name+" kill"); s.phase=Phase.KILLING; s.since=tick; s.killRetries=0;
                        message(s.name+(inColumn ? " 已到达目标列底部基岩，正在清理" : " 在其他列到达基岩，将重试目标列")); return;
                    }
                    if (entity.isPresent() && s.phase==Phase.WORKING && tick-s.since>=100
                            && (Math.abs(entity.get().getX()-(s.x+0.5))>1.5 || Math.abs(entity.get().getZ()-(s.z+0.5))>1.5)) {
                        s.complete=false; send("player "+s.name+" kill"); s.phase=Phase.KILLING; s.since=tick; s.killRetries=0;
                        message(s.name+" 偏离目标，将重新处理当前位置"); return;
                    }
                }
                if (stopping || (s.phase==Phase.WORKING && tick-s.since>=seconds*20L)) {
                    s.complete=!stopping; send("player "+s.name+" kill"); s.phase=Phase.KILLING; s.since=tick; s.killRetries=0; return;
                }
                if (s.phase==Phase.SNEAK) { send("player "+s.name+" sneak"); s.phase=Phase.LOOK; return; }
                if (s.phase==Phase.LOOK) { send("player "+s.name+" look down"); s.phase=Phase.ATTACK; return; }
                if (s.phase==Phase.ATTACK) { send("player "+s.name+" attack continuous"); s.phase=Phase.WORKING; s.since=tick; return; }
            }
            if (s.phase==Phase.RETRY) {
                // A spawn may arrive after its timeout; keep tracking it instead of losing it.
                if (online) s.phase=Phase.SNEAK;
                else if (stopping) s.phase=Phase.FREE;
                else if (sameDimension && tick>=s.retryAt) { s.phase=Phase.TERRAIN; s.retryAt=tick; }
            }
            if (s.phase==Phase.TERRAIN) {
                if (stopping) s.phase=Phase.FREE;
                else if (sameDimension && tick>=s.retryAt && trySpawn(mc.level,s)) return;
            }
        }
        if (sameDimension && !stopping && tick>=nextSweepAt) {
            int skipBudget=1024;
            for (Slot s:slots) if (s.phase==Phase.FREE && next<total) {
                long width=(long)maxX-minX+1;
                while (next<total && skipBudget>0) {
                    s.x=(int)(minX+next%width); s.z=(int)(minZ+next/width);
                    if (progress.contains(s.x,y,s.z)) { next++; skipBudget--; continue; }
                    if (connection.getPlayerInfoIgnoreCase(s.name)!=null) break;
                    next++;
                    s.failures=0; s.terrainFailures=0; s.phase=Phase.TERRAIN; s.retryAt=tick;
                    if (trySpawn(mc.level,s)) return;
                    break;
                }
                if (skipBudget==0) break;
            }
        }
        boolean idle=true; for (Slot s:slots) if (s.phase!=Phase.FREE) idle=false;
        if (idle && stopping) {
            if (progress.dirty()) saveProgress();
            if (!progress.dirty()) { running=false; message("任务已停止"); }
        }
        else if (idle && next==total) {
            if (completedCount==total) {
                if (progress.dirty()) saveProgress();
                if (!progress.dirty()) { running=false; message("区域已全部确认到达基岩："+completedCount+"/"+total); }
            } else {
                next=0; nextSweepAt=tick+100;
                message("本轮结束，已完成 "+completedCount+"/"+total+"；稍后继续未完成位置");
            }
        }
    }
    private static void ensureSettings(Minecraft mc) {
        if (settingsLoaded) return;
        settingsLoaded=true; settings=new SettingsStore(mc.gameDirectory.toPath().resolve("config"));
        try {
            var saved=settings.load();
            if (saved==null) return;
            areaSet=saved.areaSet(); minX=saved.minX(); maxX=saved.maxX(); minZ=saved.minZ(); maxZ=saved.maxZ(); y=saved.y();
            prefix=saved.prefix(); count=saved.count(); seconds=saved.seconds(); interval=saved.interval();
            total=areaSet ? ((long)maxX-minX+1)*((long)maxZ-minZ+1) : 0;
            refreshProgress();
        } catch (IOException e) { settingsWritable=false; message("设置文件读取失败，本次不会覆盖原文件"); }
    }
    private static void saveSettings() {
        if (!settingsWritable) { message("设置文件读取失败，本次不会覆盖原文件"); return; }
        try { settings.save(new SettingsStore.Settings(areaSet,minX,maxX,minZ,maxZ,y,prefix,count,seconds,interval)); }
        catch (IOException e) { message("设置保存失败，请检查配置目录"); }
    }
    static void refreshProgress() {
        if (running || !areaSet) return;
        Minecraft mc=Minecraft.getInstance();
        completedCount=0;
        if (mc.level==null || mc.getConnection()==null) return;
        try { ProgressStore store=openProgress(mc); completedCount=store.count(minX,maxX,minZ,maxZ,y); }
        catch (IllegalArgumentException e) { lastMessage=e.getMessage(); }
    }
    private static String worldId(Minecraft mc) {
        if (mc.getSingleplayerServer()!=null)
            return "single:"+mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (mc.getCurrentServer()!=null && mc.getCurrentServer().ip!=null && !mc.getCurrentServer().ip.isBlank())
            return "server:"+mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        throw new IllegalArgumentException("无法确定当前世界，不能安全保存进度");
    }
    private static ProgressStore openProgress(Minecraft mc) {
        try { return new ProgressStore(mc.gameDirectory.toPath().resolve("config").resolve("fake-player-tbm-progress"),worldId(mc),mc.level.dimension().toString()); }
        catch (IOException e) { throw new IllegalArgumentException("无法读取进度文件，请检查配置目录"); }
    }
    private static void saveProgress() {
        try { progress.save(); }
        catch (IOException e) { message("进度写入失败，暂存于内存并将在 10 秒后重试"); }
    }
    private static boolean onBedrock(ClientLevel level, AbstractClientPlayer player) {
        int x=(int)Math.floor(player.getX()), z=(int)Math.floor(player.getZ());
        int below=(int)Math.floor(player.getY()-0.01);
        if (below<level.getMinY() || below>level.getMinY()+4 || !level.hasChunk(x >> 4,z >> 4)) return false;
        return level.getBlockState(new BlockPos(x,below,z)).getBlock()==Blocks.BEDROCK;
    }
    private static long backoff(long base, int failures) {
        return Math.min(6000L, base << Math.min(failures, 6));
    }
    private static void scheduleRetry(Slot s) {
        s.phase=Phase.RETRY; s.retryAt=tick+backoff(100,s.failures);
        s.failures=Math.min(s.failures+1, 6);
    }
    private static boolean trySpawn(ClientLevel level, Slot s) {
        if (connection.getPlayerInfoIgnoreCase(s.name)!=null) {
            s.retryAt=tick+backoff(100,s.terrainFailures);
            s.terrainFailures=Math.min(s.terrainFailures+1,6);
            message("名字 "+s.name+" 已在线，等待名字释放");
            return false;
        }
        Integer floor=findFloor(level, s.x, s.z);
        if (floor==null) {
            s.retryAt=tick+backoff(100,s.terrainFailures);
            s.terrainFailures=Math.min(s.terrainFailures+1,6);
            message("位置 "+s.x+", "+s.z+" 未加载或没有安全落脚点，等待后重试");
            return false;
        }
        s.terrainFailures=0;
        send("player "+s.name+" spawn at "+(s.x+0.5)+" "+(floor+1)+" "+(s.z+0.5));
        s.phase=Phase.SPAWNING; s.since=tick; s.complete=false;
        return true;
    }
    // Search downward through air only; never descend through a block into a cave.
    private static Integer findFloor(ClientLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        for (int h=y;h>=level.getMinY();h--) {
            BlockPos floor=new BlockPos(x,h,z);
            var state=level.getBlockState(floor);
            if (state.isAir()) continue;
            if (state.isCollisionShapeFullBlock(level,floor)
                    && level.getBlockState(new BlockPos(x,h+1,z)).isAir()
                    && level.getBlockState(new BlockPos(x,h+2,z)).isAir()) return h;
            return null;
        }
        return null;
    }
}
