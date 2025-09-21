package orders.api.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredTaskScope;

public class ExecutorMain {

    public static void main(String[] args) throws Exception {
        runWithExecutor();

       // runWithStructuredScope();
    }


    /*
     * NOTE: About Executor / ExecutorService
     *
     * - An Executor is a higher-level replacement for manually creating threads.
     *   Instead of doing `new Thread(runnable).start()`, you submit tasks to an Executor.
     *
     * - Executors manage how tasks are scheduled and run (on platform threads or virtual threads).
     *
     * - In this example we use `Executors.newVirtualThreadPerTaskExecutor()`:
     *      • Each submitted task runs in its own lightweight virtual thread (Project Loom).
     *      • The Executor handles creating, starting, and cleaning up those threads.
     *      • When used in a try-with-resources block, the Executor shuts down automatically
     *        after all tasks are finished.
     *
     * - Submitting tasks:
     *      Future<String> task = executor.submit(ExecutorMain::write1);
     *   The executor returns a Future, which lets you:
     *      • `get()` the result (blocking until finished),
     *      • `get(timeout, unit)` with a deadline,
     *      • `cancel(true)` if you want to stop it.
     *
     * - Why Executors are useful:
     *      • You don’t manually manage threads.
     *      • You get pooling, scheduling, and error handling features.
     *      • With Loom, using virtual threads makes tasks scale well without OS thread overhead.
     */

    private static void runWithExecutor() throws Exception {
        System.out.println("Running with Executors...");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("Starting tasks...");

            Future<String> task1 = executor.submit(ExecutorMain::write1);
            Future<String> task2 = executor.submit(ExecutorMain::write2);

            System.out.println("Waiting for results...");

            String result1 = task1.get();
            System.out.println(STR."Got first result: \{result1}");

            String result2 = task2.get();
            System.out.println(STR."Got second result: \{result2}");

            System.out.println(STR."Final result: \{result1} \{result2}");
        }
    }


    /*
     * NOTE: About StructuredTaskScope (JDK 21 structured concurrency)
     *
     * - Structured concurrency groups related tasks under a common "scope".
     *   Instead of managing multiple Futures yourself, the scope controls them together.
     *
     * - You create a scope (e.g. ShutdownOnFailure), fork tasks, then wait for all:
     *      try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
     *          var task1 = scope.fork(() -> ...);
     *          var task2 = scope.fork(() -> ...);
     *          scope.join();           // wait for all tasks
     *          scope.throwIfFailed();  // propagate any exceptions
     *      }
     *
     * - Benefits:
     *      • All tasks start together and are managed as a unit.
     *      • If one fails (in ShutdownOnFailure), the others are cancelled automatically.
     *      • Lifecycle is bound to the try-with-resources block — no leaks.
     *
     * - Getting results:
     *      • After `join()`, each subtask has a state (SUCCESS / FAILED / CANCELLED).
     *      • You can call `resultNow()` safely (it throws if the subtask failed).
     *
     * - Timeouts:
     *      • Instead of `join()`, you can use `joinUntil(Instant deadline)`.
     *      • This waits only until a certain time, and cancels unfinished tasks.
     *
     * - Why it’s useful:
     *      • Makes concurrent code easier to reason about.
     *      • Follows the same structured programming principle as try/catch/finally.
     *      • Prevents "stray tasks" from outliving their scope.
     */


    private static void runWithStructuredScope() throws Exception {
        System.out.println("Running with Structured Scope...");
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> task1 = scope.fork(ExecutorMain::write1);
            StructuredTaskScope.Subtask<String> task2 = scope.fork(ExecutorMain::write2);

            scope.joinUntil(Instant.now().plus(Duration.ofSeconds(3)));
            scope.throwIfFailed();

            String result1 = task1.get();

            System.out.println(STR."Got first result: \{result1}");

            String result2 = task2.get();
            System.out.println(STR."Got second result: \{result2}");

            System.out.println(STR."Final result: \{result1} \{result2}");
        }
    }

    static String write1() throws Exception {
        Thread.sleep(2000);
        return "Hello from Task1";
    }

    static String write2() throws Exception {
        Thread.sleep(5000);
        return "World from Task2";
    }
}
