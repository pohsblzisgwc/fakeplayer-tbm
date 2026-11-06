package dev.faketbm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Stores only completed coordinates; world and dimension identifiers are hashed into the filename. */
final class ProgressStore {
    record Point(int x, int y, int z) {}
    private final Path path;
    private final Set<Point> completed = new HashSet<>();
    private boolean dirty;
    ProgressStore(Path directory, String world, String dimension) throws IOException {
        path=directory.resolve(hash(world+"\n"+dimension)+".txt");
        if (Files.exists(path)) for (String line:Files.readAllLines(path,StandardCharsets.UTF_8)) {
            String[] v=line.split(",",-1);
            try {
                if (v.length!=3) throw new NumberFormatException();
                completed.add(new Point(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2])));
            } catch (NumberFormatException e) { throw new IOException("Invalid progress file",e); }
        }
    }
    boolean contains(int x,int y,int z) { return completed.contains(new Point(x,y,z)); }
    long count(int minX,int maxX,int minZ,int maxZ,int y) {
        return completed.stream().filter(p -> p.y==y && p.x>=minX && p.x<=maxX && p.z>=minZ && p.z<=maxZ).count();
    }
    boolean mark(int x,int y,int z) { boolean added=completed.add(new Point(x,y,z)); dirty|=added; return added; }
    void clear(int minX,int maxX,int minZ,int maxZ,int y) { dirty|=completed.removeIf(p -> p.y==y && p.x>=minX && p.x<=maxX && p.z>=minZ && p.z<=maxZ); }
    boolean dirty() { return dirty; }
    void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(path.getParent());
        Path tmp=Files.createTempFile(path.getParent(),"progress-", ".tmp");
        try {
            var lines=completed.stream().sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::z).thenComparingInt(Point::x))
                .map(p -> p.x+","+p.y+","+p.z).toList();
            Files.write(tmp,lines,StandardCharsets.UTF_8);
            try { Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
            dirty=false;
        } finally { Files.deleteIfExists(tmp); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
