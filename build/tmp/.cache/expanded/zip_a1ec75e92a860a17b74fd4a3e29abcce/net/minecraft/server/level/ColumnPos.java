package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public record ColumnPos(int x, int z) {
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;

    public ChunkPos toChunkPos() {
        return new ChunkPos(SectionPos.blockToSectionCoord(this.x), SectionPos.blockToSectionCoord(this.z));
    }

    public long toLong() {
        return asLong(this.x, this.z);
    }

    public static long asLong(int pX, int pZ) {
        return (long)pX & 4294967295L | ((long)pZ & 4294967295L) << 32;
    }

    public static int getX(long pPos) {
        return (int)(pPos & 4294967295L);
    }

    public static int getZ(long pPos) {
        return (int)(pPos >>> 32 & 4294967295L);
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    @Override
    public int hashCode() {
        return ChunkPos.hash(this.x, this.z);
    }
}