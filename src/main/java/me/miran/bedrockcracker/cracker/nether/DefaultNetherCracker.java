package me.miran.bedrockcracker.cracker.nether;

import me.miran.bedrockcracker.BedrockCracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class DefaultNetherCracker extends AbstractNetherCracker{

    // each thread writes its progress to its own stride of this array so the
    // hot loop never touches a shared cache line (a shared atomic counter here
    // made the whole search several times slower)
    private static final int PROGRESS_STRIDE = 8;

    @Override
    protected @NotNull List<Long> getSeedCandidates(Test[] testArr) {
        List<Long> results = Collections.synchronizedList(new ArrayList<>());
        int threadCount = (int) (Runtime.getRuntime().availableProcessors()*0.75);
        threadCount = Math.max(1, threadCount);


        long limit = 1L<<36;
        long chunkSize = limit / threadCount;

        CountDownLatch latch = new CountDownLatch(threadCount);
        long[] progress = new long[threadCount * PROGRESS_STRIDE];

        BedrockCracker.sendChatMessage("§7Using §dCPU §7cracker §8(" + threadCount + " threads, this can take a while)");

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            long start = t * chunkSize;
            long end;

            if (t == threadCount - 1) { // Last thread handles remaining work
                end = limit;
            } else {
                end = (t + 1) * chunkSize;
            }

            new Thread(() -> {
                long done = 0;

                for (long i = start; i < end; i++) {
                    runChecks(testArr, i << 12, 12, results);
                    done++;

                    if ((done & 4095) == 0) {
                        progress[threadIndex * PROGRESS_STRIDE] = done;
                    }
                }

                progress[threadIndex * PROGRESS_STRIDE] = done;
                latch.countDown();
            }).start();

        }

        // report progress every ~10 seconds until all threads are done
        try {
            while (!latch.await(10, TimeUnit.SECONDS)) {
                long done = 0;
                for (int t = 0; t < threadCount; t++) {
                    done += progress[t * PROGRESS_STRIDE];
                }

                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                long eta = done > 0 ? (elapsed * (limit - done)) / done : -1;

                BedrockCracker.sendChatMessage("§7CPU cracker working... §8(" + (done * 100 / limit) + "%, "
                        + results.size() + " candidate(s), " + elapsed + "s elapsed"
                        + (eta >= 0 ? ", ~" + eta + "s left" : "") + ")");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return results;
    }


}
