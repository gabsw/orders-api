package orders.api.demo.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadDemo implements CommandLineRunner {

    @Value("${demo.threadType:platform}")
    private String threadType;

    // TODO: Showcase 1M and then 10k
    @Value("${demo.numThreads:10000}")
    private int numThreads;

    @Value("${demo.showThreads:false}")
    private boolean showThreads;

    @Value("${demo.logInterval:1000}")
    private int logInterval;

    @Override
    public void run(String... args) throws Exception {
        System.out.printf("Starting demo with %d threads using '%s'%n", numThreads, threadType);
        printMetrics("Before starting threads");

        switch (threadType.toLowerCase()) {
            case "platform" -> runWithPlatformThreads(numThreads);
            case "virtual" -> runWithVirtualThreads(numThreads);
            default -> System.out.println("Invalid demo.threadType. Use 'platform' or 'virtual'.");
        }

        printMetrics("After threads completed");
    }

    private void runWithPlatformThreads(int numThreads) throws InterruptedException {
        Thread[] threads = new Thread[numThreads];
        long start = System.currentTimeMillis();
        long peakMemory = 0;
        int peakOsThreads = 0;

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(this::simulateWork);
            threads[i].start();

            if (i % logInterval == 0) {
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                int osThreads = countPlatformThreads();
                peakMemory = Math.max(peakMemory, used);
                peakOsThreads = Math.max(peakOsThreads, osThreads);
                System.out.printf("Created %d platform threads, Memory: %.2f MB, OS threads: %d%n",
                        i, used / (1024.0 * 1024.0), osThreads);
            }
        }

        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        long finalUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        peakMemory = Math.max(peakMemory, finalUsed);

        System.out.printf("Platform threads: Completed %d threads in %d ms, Peak memory: %.2f MB, Peak OS threads: %d%n",
                numThreads, end - start, peakMemory / (1024.0 * 1024.0), peakOsThreads);
    }

    private void runWithVirtualThreads(int numThreads) throws InterruptedException {
        Thread[] threads = new Thread[numThreads];
        long start = System.currentTimeMillis();
        long peakMemory = 0;
        int peakOsThreads = 0;

        for (int i = 0; i < numThreads; i++) {
            threads[i] = Thread.ofVirtual().start(this::simulateWork);

            if (i % logInterval == 0) {
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                int osThreads = countPlatformThreads();
                peakMemory = Math.max(peakMemory, used);
                peakOsThreads = Math.max(peakOsThreads, osThreads);
                System.out.printf("Created %d virtual threads, Memory: %.2f MB, OS threads: %d%n",
                        i, used / (1024.0 * 1024.0), osThreads);
            }
        }

        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        long finalUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        peakMemory = Math.max(peakMemory, finalUsed);

        System.out.printf("Virtual threads: Completed %d threads in %d ms, Peak memory: %.2f MB, Peak OS threads: %d%n",
                numThreads, end - start, peakMemory / (1024.0 * 1024.0), peakOsThreads);
    }

    private void simulateWork() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(1, 10);
            Thread.sleep(delay);

            if (showThreads) {
                System.out.printf("%s (virtual=%b)%n",
                        Thread.currentThread(), Thread.currentThread().isVirtual());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printMetrics(String label) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int activeThreads = Thread.activeCount();

        System.out.printf("[%s] Memory used: %.2f MB, Active threads: %d%n",
                label, usedMemory / (1024.0 * 1024.0), activeThreads);
    }

    private int countPlatformThreads() {
        Thread[] threads = new Thread[Thread.activeCount()];
        int count = Thread.enumerate(threads);
        int platformCount = 0;
        for (int i = 0; i < count; i++) {
            if (!threads[i].isVirtual()) {
                platformCount++;
            }
        }
        return platformCount;
    }
}
