package me.miran.bedrockcracker.cracker.nether;

import me.miran.bedrockcracker.BedrockCracker;
import me.miran.bedrockcracker.cracker.util.jocl.Buffer;
import me.miran.bedrockcracker.cracker.util.jocl.IntBuffer;
import me.miran.bedrockcracker.cracker.util.jocl.LongBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jocl.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.jocl.CL.*;

class GPUNetherCracker extends AbstractNetherCracker implements AutoCloseable {

    private static boolean initialized;
    private static boolean available;

    private static cl_kernel kernel;
    private static cl_command_queue queue;
    private static cl_context context;

    private static void init() throws IOException {
        CL.setExceptionsEnabled(true);

        cl_platform_id[] platforms = new cl_platform_id[1];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[0];

        // FIXME use a better way to choose a GPU device
        cl_device_id[] devices = new cl_device_id[1];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, devices, null);
        cl_device_id device = devices[0];

        context = clCreateContext(null, 1, devices, null, null, null);

        String source = readCrackerSource();
        cl_program program = clCreateProgramWithSource(context, 1, new String[]{source}, null, null);
        clBuildProgram(program, 0, null, null, null, null);

        kernel = clCreateKernel(program, "crack", null);
        queue = clCreateCommandQueueWithProperties(context, device, null, null);
    }

    private static String readCrackerSource() throws IOException {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Identifier sourceId = Identifier.fromNamespaceAndPath("bedrockcracker", "cracking.cl");

        Resource resource = resourceManager.getResourceOrThrow(sourceId);

        StringBuilder source = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                source.append(line).append("\n");
            }
        }

        return source.toString();
    }


    static boolean available() {
        if (initialized) return available;

        initialized = true;

        try {
            init();
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }

        return available;
    }

    @Override
    protected @NotNull List<Long> getSeedCandidates(Test[] testArr) {
        final long[] tests = new long[20];
        for (int j = 0; j < testArr.length; j++) {
            Test test = testArr[j];
            tests[j] = test.hashXor();
        }


        List<Long> output = new ArrayList<>();
        int loopLimit = 1 << 6;
        long startTime = System.currentTimeMillis();

        BedrockCracker.sendChatMessage("§7Using §dGPU §7cracker §8(" + loopLimit + " batches)");

        for (int i = 0; i < loopLimit; i++) {
            // FIXME 16384 is an arbitrary size
            output.addAll(calculateBatch(tests, i, 16384));

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long eta = (i + 1) > 0 ? (elapsed * (loopLimit - i - 1)) / (i + 1) : 0;

            BedrockCracker.sendChatMessage("§7Batch §f" + (i + 1) + "/" + loopLimit + " §7done §8(" + output.size() + " candidate(s), "
                    + elapsed + "s elapsed, ~" + eta + "s left)");
        }

        return output;
    }


    private static List<Long> calculateBatch(long[] testsValue, int lowBitsValue, int resultsArraySize) {
        int capacity = resultsArraySize;

        while (true) {
            LongBuffer lowBits = new LongBuffer(context, new long[]{lowBitsValue});
            LongBuffer tests = new LongBuffer(context, testsValue);
            LongBuffer results = new LongBuffer(context, capacity);
            IntBuffer resultIndex = new IntBuffer(context, 1);

            // clCreateBuffer leaves contents undefined -> the atomic counter MUST start at 0
            resultIndex.write(queue, new int[]{0});

            passArgs(lowBits, tests, results, resultIndex);
            clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{capacity}));

            // the kernel grid-strides over all ids itself, this only controls parallelism
            clEnqueueNDRangeKernel(queue, kernel, 1, null,
                    new long[]{1 << 20}, null, 0, null, null);

            clFinish(queue); // rather call `finish` to make sure the index is the correct one

            int[] indexArr = resultIndex.read(queue);
            int count = indexArr[0];

            if (count > capacity) {
                // the kernel keeps counting past the limit, so `count` is the exact amount needed
                releaseBuffers(lowBits, tests, results, resultIndex);
                capacity = count;
                continue;
            }

            long[] output = results.read(queue, Math.max(count, 0));
            clFinish(queue);

            List<Long> resultsList = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                resultsList.add(output[i]);
            }

            releaseBuffers(lowBits, tests, results, resultIndex);

            return resultsList;
        }
    }

    private static void releaseBuffers(Buffer... buffers) {
        for (Buffer buffer : buffers) {
            buffer.release();
        }
    }

    private static void passArgs(Buffer... buffers) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i].passAsArg(kernel, i);
        }
    }

    @Override
    public void close() {
        clReleaseKernel(kernel);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);
    }
}
