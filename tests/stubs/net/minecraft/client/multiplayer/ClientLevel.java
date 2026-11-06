package net.minecraft.client.multiplayer;
import java.util.*; import net.minecraft.client.player.AbstractClientPlayer; import net.minecraft.resources.ResourceKey; import net.minecraft.world.level.Level; import net.minecraft.world.level.BlockGetter; import net.minecraft.core.BlockPos; import net.minecraft.world.level.block.state.BlockState;
public class ClientLevel implements BlockGetter {
 public ResourceKey<Level> key=new ResourceKey<>(); public List<AbstractClientPlayer> visible=new ArrayList<>();
 public boolean loaded=true; public Map<Integer,Integer> blocks=new HashMap<>(Map.of(64,1));
 public ResourceKey<Level> dimension(){return key;} public List<AbstractClientPlayer> players(){return visible;}
 public int getMinY(){return 0;} public int getMaxY(){return 319;} public boolean hasChunk(int x,int z){return loaded;}
 public BlockState getBlockState(BlockPos pos){return new BlockState(blocks.getOrDefault(pos.y,0));}
}
