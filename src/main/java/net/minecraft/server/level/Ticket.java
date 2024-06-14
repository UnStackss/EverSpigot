package net.minecraft.server.level;

import java.util.Objects;

public final class Ticket<T> implements Comparable<Ticket<?>>, ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket<T> { // Paper - rewrite chunk system
    private final TicketType<T> type;
    private final int ticketLevel;
    public final T key;
    // Paper start - rewrite chunk system
    private long removeDelay;

    @Override
    public final long moonrise$getRemoveDelay() {
        return this.removeDelay;
    }

    @Override
    public final void moonrise$setRemoveDelay(final long removeDelay) {
        this.removeDelay = removeDelay;
    }
    // Paper end - rewerite chunk system

    public Ticket(TicketType<T> type, int level, T argument) { // Paper - public
        this.type = type;
        this.ticketLevel = level;
        this.key = argument;
    }

    @Override
    public int compareTo(Ticket<?> ticket) {
        int i = Integer.compare(this.ticketLevel, ticket.ticketLevel);
        if (i != 0) {
            return i;
        } else {
            int j = Integer.compare(System.identityHashCode(this.type), System.identityHashCode(ticket.type));
            return j != 0 ? j : this.type.getComparator().compare(this.key, (T)ticket.key);
        }
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof Ticket<?> ticket
                && this.ticketLevel == ticket.ticketLevel
                && Objects.equals(this.type, ticket.type)
                && Objects.equals(this.key, ticket.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.ticketLevel, this.key);
    }

    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] to die in " + this.removeDelay; // Paper - rewrite chunk system
    }

    public TicketType<T> getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    protected void setCreatedTick(long tickCreated) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    protected boolean timedOut(long currentTick) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }
}
