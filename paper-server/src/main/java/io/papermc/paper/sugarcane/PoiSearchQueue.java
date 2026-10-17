package io.papermc.paper.sugarcane;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;

/**
 * Breadth-first queue over the chunk sections of a fixed search box, used by Moonrise's {@code PoiAccess}.
 * <p>
 * {@code PoiAccess} floods outwards from the section holding the search centre. It used to track visited sections
 * in a {@code LongOpenHashSet} and queue every neighbour, including ones outside the box that it then threw away.
 * A villager's 48 block job site search covers 7x7 chunk columns and can span the whole world height, so that
 * meant around 1,200 hash set inserts, plus rehashes, per search. On a server with a thousand villagers this was
 * the largest single cost in the villager brain.
 * <p>
 * The box is known before the search starts, so a bitset over it and a plain array queue do the same job.
 * {@link #enqueueNeighbours} visits neighbours in the same order as the loop it replaces, and sections outside
 * the box were never searched, so sections come out of this queue in exactly the same order as before. Search
 * results, and the order position predicates are called in, are unchanged.
 */
public final class PoiSearchQueue {

    private final int lowerX;
    private final int lowerY;
    private final int lowerZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long[] seen;
    private final long[] queue;
    private int head;
    private int tail;

    public PoiSearchQueue(final int lowerX, final int lowerY, final int lowerZ, final int upperX, final int upperY, final int upperZ) {
        this.lowerX = lowerX;
        this.lowerY = lowerY;
        this.lowerZ = lowerZ;
        this.sizeX = Math.max(0, upperX - lowerX + 1);
        this.sizeY = Math.max(0, upperY - lowerY + 1);
        this.sizeZ = Math.max(0, upperZ - lowerZ + 1);
        // every section is queued at most once
        final int volume = Math.multiplyExact(Math.multiplyExact(this.sizeX, this.sizeY), this.sizeZ);
        this.seen = new long[(volume + (Long.SIZE - 1)) >>> 6];
        this.queue = new long[volume];
    }

    public boolean isEmpty() {
        return this.head == this.tail;
    }

    /**
     * Returns the section key of the next queued section.
     */
    public long dequeue() {
        return this.queue[this.head++];
    }

    /**
     * Queues the section unless it is outside the search box or has been queued before.
     */
    public void enqueue(final int sectionX, final int sectionY, final int sectionZ) {
        final int x = sectionX - this.lowerX;
        final int y = sectionY - this.lowerY;
        final int z = sectionZ - this.lowerZ;
        // the unsigned compare also rejects coordinates below the box
        if (Integer.compareUnsigned(x, this.sizeX) >= 0 || Integer.compareUnsigned(y, this.sizeY) >= 0 || Integer.compareUnsigned(z, this.sizeZ) >= 0) {
            return;
        }

        final int index = (x * this.sizeZ + z) * this.sizeY + y;
        final int word = index >>> 6;
        final long bit = 1L << index;
        if ((this.seen[word] & bit) != 0L) {
            return;
        }
        this.seen[word] |= bit;
        this.queue[this.tail++] = CoordinateUtils.getChunkSectionKey(sectionX, sectionY, sectionZ);
    }

    /**
     * Queues the six face neighbours of a section, in the order {@code PoiAccess} has always visited them.
     */
    public void enqueueNeighbours(final int sectionX, final int sectionY, final int sectionZ) {
        this.enqueue(sectionX, sectionY, sectionZ - 1);
        this.enqueue(sectionX - 1, sectionY, sectionZ);
        this.enqueue(sectionX, sectionY - 1, sectionZ);
        this.enqueue(sectionX, sectionY + 1, sectionZ);
        this.enqueue(sectionX + 1, sectionY, sectionZ);
        this.enqueue(sectionX, sectionY, sectionZ + 1);
    }
}
