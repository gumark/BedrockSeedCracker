#define MULTIPLY 25214903917L
#define MASK 281474976710655L
#define ID_LIMIT (1L << 30)

// Each work item walks many seeds in a grid-stride loop (far fewer work items
// -> far less scheduling overhead), and the depth-first search over the 12
// unknown bits uses only scalar registers: the "stack" is encoded as the
// current bit `path` plus a `pending` bitset of unexplored sibling branches.
// The previous queue arrays were dynamically indexed, which makes GPU drivers
// (especially Intel) spill them to slow per-thread scratch memory.
__kernel void crack(__global const long* lowBitsPointer, __constant const long* tests, __global long* resultsAll, __global int* resultsIndex, int resultsCapacity) {
    const long lowBits = lowBitsPointer[0];

    for (long id = (long) get_global_id(0); id < ID_LIMIT; id += (long) get_global_size(0)) {
        const long base = (id << 6 | lowBits) << 12;

        long path = 0;   // bits of the 12-bit suffix fixed so far, in their final positions
        int pending = 0; // bit d set -> the second branch of the node at depth d is unexplored
        int depth = 0;   // how many suffix bits are fixed (0..12)

        while (1) {
            const long upperBits = base | path;
            const int unknownBits = 12 - depth;

            const long lowerBitMask = (1L << unknownBits) - 1;
            const long sub = lowerBitMask * MULTIPLY;
            const long hashMask = MASK ^ lowerBitMask;
            const long compare = 225179981368524L - sub;

            // ultimate loop unrolling in action
            if ((((upperBits ^ (tests[0] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[1] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[2] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[3] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[4] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[5] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[6] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[7] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[8] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[9] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[10] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[11] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[12] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[13] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[14] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[15] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[16] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[17] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[18] & hashMask)) * MULTIPLY) & MASK) >= compare &&
                (((upperBits ^ (tests[19] & hashMask)) * MULTIPLY) & MASK) >= compare) {

                if (unknownBits == 0) {
                    // the counter keeps going past the limit so the host can detect overflow,
                    // but never write outside the results buffer
                    int resultIdx = atomic_inc(resultsIndex);
                    if (resultIdx < resultsCapacity) {
                        resultsAll[resultIdx] = upperBits;
                    }
                } else {
                    // both children are worth exploring: remember the second one, descend into the first
                    pending |= 1 << depth;
                    depth++;
                    continue;
                }
            }

            // this node and its whole subtree are done -> jump to the next unexplored branch
            if (pending == 0) break;

            int level = 31 - clz(pending); // deepest pending sibling
            pending &= ~(1 << level);

            long split = 1L << (11 - level);
            path = (path & ~(split - 1)) | split; // keep higher bits, clear deeper ones, take second branch
            depth = level + 1;
        }
    }
}
