package dev.faketbm;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

final class SettingsStore {
    record Settings(boolean areaSet,int minX,int maxX,int minZ,int maxZ,int y,String prefix,int count,int seconds,int interval) {}
    private final Path path;
    SettingsStore(Path configDir) { path=configDir.resolve("fake-player-tbm.properties"); }
    Settings load() throws IOException {
        if (!Files.exists(path)) return null;
        Properties p=new Properties();
        try (Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { p.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid settings file",e); }
        try {
            String area=p.getProperty("areaSet");
            if (!"true".equals(area) && !"false".equals(area)) throw new IllegalArgumentException();
            int x1=num(p,"minX"), x2=num(p,"maxX"), z1=num(p,"minZ"), z2=num(p,"maxZ"), y=num(p,"y");
            int count=num(p,"count"), seconds=num(p,"seconds"), interval=num(p,"interval");
            String prefix=p.getProperty("prefix");
            if (Math.abs((long)x1)>30000000 || Math.abs((long)x2)>30000000 || Math.abs((long)z1)>30000000 || Math.abs((long)z2)>30000000 || x1>x2 || z1>z2 || y< -2048 || y>2047 || count<1 || count>100 || seconds<1 || seconds>86400 || interval<1 || interval>1200 || prefix==null || !prefix.matches("[A-Za-z0-9_]+") || prefix.length()+Integer.toString(count).length()>16) throw new IllegalArgumentException();
            return new Settings(Boolean.parseBoolean(area),x1,x2,z1,z2,y,prefix,count,seconds,interval);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid settings file",e); }
    }
    void save(Settings s) throws IOException {
        Properties p=new Properties();
        p.setProperty("areaSet",Boolean.toString(s.areaSet)); p.setProperty("minX",Integer.toString(s.minX)); p.setProperty("maxX",Integer.toString(s.maxX));
        p.setProperty("minZ",Integer.toString(s.minZ)); p.setProperty("maxZ",Integer.toString(s.maxZ)); p.setProperty("y",Integer.toString(s.y));
        p.setProperty("prefix",s.prefix); p.setProperty("count",Integer.toString(s.count)); p.setProperty("seconds",Integer.toString(s.seconds)); p.setProperty("interval",Integer.toString(s.interval));
        Files.createDirectories(path.getParent()); Path tmp=Files.createTempFile(path.getParent(),"faketbm-settings-", ".tmp");
        try {
            try (Writer writer=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)) { p.store(writer,"Fake Player TBM settings"); }
            try { Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private static int num(Properties p,String key) { return Integer.parseInt(p.getProperty(key)); }
}
