package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Node {
    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    public int heapIdx = -1;
    public float g;
    public float h;
    public float f;
    @Nullable
    public Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    public float costMalus;
    public PathType type = PathType.BLOCKED;

    public Node(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = createHash(x, y, z);
    }

    public Node cloneAndMove(int x, int y, int z) {
        Node node = new Node(x, y, z);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(int x, int y, int z) {
        return y & 0xFF | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    public float distanceTo(Node node) {
        float f = (float)(node.x - this.x);
        float g = (float)(node.y - this.y);
        float h = (float)(node.z - this.z);
        return Mth.sqrt(f * f + g * g + h * h);
    }

    public float distanceToXZ(Node node) {
        float f = (float)(node.x - this.x);
        float g = (float)(node.z - this.z);
        return Mth.sqrt(f * f + g * g);
    }

    public float distanceTo(BlockPos pos) {
        float f = (float)(pos.getX() - this.x);
        float g = (float)(pos.getY() - this.y);
        float h = (float)(pos.getZ() - this.z);
        return Mth.sqrt(f * f + g * g + h * h);
    }

    public float distanceToSqr(Node node) {
        float f = (float)(node.x - this.x);
        float g = (float)(node.y - this.y);
        float h = (float)(node.z - this.z);
        return f * f + g * g + h * h;
    }

    public float distanceToSqr(BlockPos pos) {
        float f = (float)(pos.getX() - this.x);
        float g = (float)(pos.getY() - this.y);
        float h = (float)(pos.getZ() - this.z);
        return f * f + g * g + h * h;
    }

    public float distanceManhattan(Node node) {
        float f = (float)Math.abs(node.x - this.x);
        float g = (float)Math.abs(node.y - this.y);
        float h = (float)Math.abs(node.z - this.z);
        return f + g + h;
    }

    public float distanceManhattan(BlockPos pos) {
        float f = (float)Math.abs(pos.getX() - this.x);
        float g = (float)Math.abs(pos.getY() - this.y);
        float h = (float)Math.abs(pos.getZ() - this.z);
        return f + g + h;
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public Vec3 asVec3() {
        return new Vec3((double)this.x, (double)this.y, (double)this.z);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Node node && this.hash == node.hash && this.x == node.x && this.y == node.y && this.z == node.z;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    @Override
    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeFloat(this.walkedDistance);
        buf.writeFloat(this.costMalus);
        buf.writeBoolean(this.closed);
        buf.writeEnum(this.type);
        buf.writeFloat(this.f);
    }

    public static Node createFromStream(FriendlyByteBuf buf) {
        Node node = new Node(buf.readInt(), buf.readInt(), buf.readInt());
        readContents(buf, node);
        return node;
    }

    protected static void readContents(FriendlyByteBuf buf, Node target) {
        target.walkedDistance = buf.readFloat();
        target.costMalus = buf.readFloat();
        target.closed = buf.readBoolean();
        target.type = buf.readEnum(PathType.class);
        target.f = buf.readFloat();
    }
}
