package demo.virtualthreads;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadDemo implements CommandLineRunner {

    @Value("${demo.threadType:virtual}")
    private String threadType;

    @Value("${demo.numThreads:10000}")
    private int numThreads;

    @Value("${demo.showThreads:false}")
    private boolean showThreads;

    @Override
    public void run(String... args) throws Exception {
        System.out.printf("Starting demo with %d threads using '%s'%n", numThreads, threadType);

        // Initial metrics
        printMetrics("Before starting threads");

        switch (threadType.toLowerCase()) {
            case "platform" -> runWithPlatformThreads(numThreads);
            case "virtual" -> runWithVirtualThreads(numThreads);
            default -> System.out.println("Invalid demo.threadType. Use 'platform' or 'virtual'.");
        }

        // Metrics after completion
        printMetrics("After threads completed");
    }

    private void runWithPlatformThreads(int numThreads) throws InterruptedException {
        Thread[] threads = new Thread[numThreads];
        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(this::simulateWork);
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        System.out.printf("Platform threads: Completed %d threads in %d ms%n", numThreads, end - start);
    }

    private void runWithVirtualThreads(int numThreads) throws InterruptedException {
        Thread[] threads = new Thread[numThreads];
        long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            threads[i] = Thread.ofVirtual().start(this::simulateWork);
        }

        for (Thread t : threads) {
            t.join();
        }

        long end = System.currentTimeMillis();
        System.out.printf("Virtual threads: Completed %d threads in %d ms%n", numThreads, end - start);
    }

    private void simulateWork() {
        try {
            // Simulate small blocking operation
            int delay = ThreadLocalRandom.current().nextInt(1, 10);
            Thread.sleep(delay);

            // Optionally print thread info
            if (showThreads) {
                System.out.println(Thread.currentThread() + " (virtual=" + Thread.currentThread().isVirtual() + ")");
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
}
