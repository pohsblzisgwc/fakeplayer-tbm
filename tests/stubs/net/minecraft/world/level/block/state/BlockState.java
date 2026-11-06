package net.minecraft.world.level.block.state;
import net.minecraft.core.BlockPos; import net.minecraft.world.level.BlockGetter;
public class BlockState { public final int kind; public BlockState(int kind){this.kind=kind;} public boolean isAir(){return kind==0;} public boolean isCollisionShapeFullBlock(BlockGetter level, BlockPos pos){return kind==1;} }
