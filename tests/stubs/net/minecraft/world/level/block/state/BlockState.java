package net.minecraft.world.level.block.state;
import net.minecraft.core.BlockPos; import net.minecraft.world.level.BlockGetter; import net.minecraft.world.level.block.Block; import net.minecraft.world.level.block.Blocks;
public class BlockState { public final int kind; public BlockState(int kind){this.kind=kind;} public boolean isAir(){return kind==0;} public boolean isCollisionShapeFullBlock(BlockGetter level, BlockPos pos){return kind==1 || kind==3;} public Block getBlock(){return kind==3?Blocks.BEDROCK:Blocks.OTHER;} }
