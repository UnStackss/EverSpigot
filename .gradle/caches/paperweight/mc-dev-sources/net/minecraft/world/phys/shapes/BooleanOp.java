package net.minecraft.world.phys.shapes;

public interface BooleanOp {
    BooleanOp FALSE = (a, b) -> false;
    BooleanOp NOT_OR = (a, b) -> !a && !b;
    BooleanOp ONLY_SECOND = (a, b) -> b && !a;
    BooleanOp NOT_FIRST = (a, b) -> !a;
    BooleanOp ONLY_FIRST = (a, b) -> a && !b;
    BooleanOp NOT_SECOND = (a, b) -> !b;
    BooleanOp NOT_SAME = (a, b) -> a != b;
    BooleanOp NOT_AND = (a, b) -> !a || !b;
    BooleanOp AND = (a, b) -> a && b;
    BooleanOp SAME = (a, b) -> a == b;
    BooleanOp SECOND = (a, b) -> b;
    BooleanOp CAUSES = (a, b) -> !a || b;
    BooleanOp FIRST = (a, b) -> a;
    BooleanOp CAUSED_BY = (a, b) -> a || !b;
    BooleanOp OR = (a, b) -> a || b;
    BooleanOp TRUE = (a, b) -> true;

    boolean apply(boolean a, boolean b);
}
