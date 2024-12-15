package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;

public class ComparatorBlock extends DiodeBlock implements EntityBlock {
    public static final MapCodec<ComparatorBlock> CODEC = simpleCodec(ComparatorBlock::new);
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    @Override
    public MapCodec<ComparatorBlock> codec() {
        return CODEC;
    }

    public ComparatorBlock(BlockBehaviour.Properties p_51857_) {
        super(p_51857_);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, Boolean.valueOf(false)).setValue(MODE, ComparatorMode.COMPARE)
        );
    }

    @Override
    protected int getDelay(BlockState pState) {
        return 2;
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState, LevelAccessor pLevel, BlockPos pPos, BlockPos pNeighborPos) {
        return pDirection == Direction.DOWN && !this.canSurviveOn(pLevel, pNeighborPos, pNeighborState)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(pState, pDirection, pNeighborState, pLevel, pPos, pNeighborPos);
    }

    @Override
    protected int getOutputSignal(BlockGetter pLevel, BlockPos pPos, BlockState pState) {
        BlockEntity blockentity = pLevel.getBlockEntity(pPos);
        return blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockentity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.getInputSignal(pLevel, pPos, pState);
        if (i == 0) {
            return 0;
        } else {
            int j = this.getAlternateSignal(pLevel, pPos, pState);
            if (j > i) {
                return 0;
            } else {
                return pState.getValue(MODE) == ComparatorMode.SUBTRACT ? i - j : i;
            }
        }
    }

    @Override
    protected boolean shouldTurnOn(Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.getInputSignal(pLevel, pPos, pState);
        if (i == 0) {
            return false;
        } else {
            int j = this.getAlternateSignal(pLevel, pPos, pState);
            return i > j ? true : i == j && pState.getValue(MODE) == ComparatorMode.COMPARE;
        }
    }

    @Override
    protected int getInputSignal(Level pLevel, BlockPos pPos, BlockState pState) {
        int i = super.getInputSignal(pLevel, pPos, pState);
        Direction direction = pState.getValue(FACING);
        BlockPos blockpos = pPos.relative(direction);
        BlockState blockstate = pLevel.getBlockState(blockpos);
        if (blockstate.hasAnalogOutputSignal()) {
            i = blockstate.getAnalogOutputSignal(pLevel, blockpos);
        } else if (i < 15 && blockstate.isRedstoneConductor(pLevel, blockpos)) {
            blockpos = blockpos.relative(direction);
            blockstate = pLevel.getBlockState(blockpos);
            ItemFrame itemframe = this.getItemFrame(pLevel, direction, blockpos);
            int j = Math.max(
                itemframe == null ? Integer.MIN_VALUE : itemframe.getAnalogOutput(),
                blockstate.hasAnalogOutputSignal() ? blockstate.getAnalogOutputSignal(pLevel, blockpos) : Integer.MIN_VALUE
            );
            if (j != Integer.MIN_VALUE) {
                i = j;
            }
        }

        return i;
    }

    @Nullable
    private ItemFrame getItemFrame(Level pLevel, Direction pFacing, BlockPos pPos) {
        List<ItemFrame> list = pLevel.getEntitiesOfClass(
            ItemFrame.class,
            new AABB(
                (double)pPos.getX(),
                (double)pPos.getY(),
                (double)pPos.getZ(),
                (double)(pPos.getX() + 1),
                (double)(pPos.getY() + 1),
                (double)(pPos.getZ() + 1)
            ),
            p_327257_ -> p_327257_ != null && p_327257_.getDirection() == pFacing
        );
        return list.size() == 1 ? list.get(0) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHitResult) {
        if (!pPlayer.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            pState = pState.cycle(MODE);
            float f = pState.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;
            pLevel.playSound(pPlayer, pPos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, f);
            pLevel.setBlock(pPos, pState, 2);
            this.refreshOutputState(pLevel, pPos, pState);
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }
    }

    @Override
    protected void checkTickOnNeighbor(Level pLevel, BlockPos pPos, BlockState pState) {
        if (!pLevel.getBlockTicks().willTickThisTick(pPos, this)) {
            int i = this.calculateOutputSignal(pLevel, pPos, pState);
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            int j = blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockentity).getOutputSignal() : 0;
            if (i != j || pState.getValue(POWERED) != this.shouldTurnOn(pLevel, pPos, pState)) {
                TickPriority tickpriority = this.shouldPrioritize(pLevel, pPos, pState) ? TickPriority.HIGH : TickPriority.NORMAL;
                pLevel.scheduleTick(pPos, this, 2, tickpriority);
            }
        }
    }

    private void refreshOutputState(Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.calculateOutputSignal(pLevel, pPos, pState);
        BlockEntity blockentity = pLevel.getBlockEntity(pPos);
        int j = 0;
        if (blockentity instanceof ComparatorBlockEntity comparatorblockentity) {
            j = comparatorblockentity.getOutputSignal();
            comparatorblockentity.setOutputSignal(i);
        }

        if (j != i || pState.getValue(MODE) == ComparatorMode.COMPARE) {
            boolean flag1 = this.shouldTurnOn(pLevel, pPos, pState);
            boolean flag = pState.getValue(POWERED);
            if (flag && !flag1) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, Boolean.valueOf(false)), 2);
            } else if (!flag && flag1) {
                pLevel.setBlock(pPos, pState.setValue(POWERED, Boolean.valueOf(true)), 2);
            }

            this.updateNeighborsInFront(pLevel, pPos, pState);
        }
    }

    @Override
    protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        this.refreshOutputState(pLevel, pPos, pState);
    }

    @Override
    protected boolean triggerEvent(BlockState pState, Level pLevel, BlockPos pPos, int pId, int pParam) {
        super.triggerEvent(pState, pLevel, pPos, pId, pParam);
        BlockEntity blockentity = pLevel.getBlockEntity(pPos);
        return blockentity != null && blockentity.triggerEvent(pId, pParam);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new ComparatorBlockEntity(pPos, pState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, MODE, POWERED);
    }

    @Override
    public boolean getWeakChanges(BlockState state, net.minecraft.world.level.LevelReader world, BlockPos pos) {
       return state.is(Blocks.COMPARATOR);
    }

    @Override
    public void onNeighborChange(BlockState state, net.minecraft.world.level.LevelReader world, BlockPos pos, BlockPos neighbor) {
       if (pos.getY() == neighbor.getY() && world instanceof Level level) {
           level.neighborChanged(state, pos, world.getBlockState(neighbor).getBlock(), neighbor, false);
       }
    }
}