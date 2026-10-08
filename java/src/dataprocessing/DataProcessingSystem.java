package dataprocessing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DataProcessingSystem {
    private static final Logger LOGGER = Logger.getLogger(DataProcessingSystem.class.getName());
    private static final int WORKER_COUNT = 3;

    private DataProcessingSystem() { }

    public static void main(String[] args) {
        TaskQueue queue = new TaskQueue();
        List<Result> results = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);

        for (int workerId = 1; workerId <= WORKER_COUNT; workerId++) {
            int id = workerId;
            workers.submit(() -> runWorker(id, queue, results));
        }

        int[] values = {4, 7, 10, 13, 16, -3, 22, 25, 28, 31};
        for (int i = 0; i < values.length; i++) {
            queue.addTask(new Task(i + 1, values[i]));
        }
        queue.close();
        workers.shutdown();

        try {
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warning("Workers exceeded the timeout; requesting interruption");
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            LOGGER.log(Level.SEVERE, "Main thread interrupted while awaiting workers", exception);
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }

        List<Result> ordered;
        synchronized (results) {
            ordered = results.stream().sorted(Comparator.comparingInt(Result::taskId)).toList();
        }
        try {
            writeResults(Path.of("results.txt"), ordered);
            LOGGER.info(() -> "Saved " + ordered.size() + " results to results.txt");
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Could not write results.txt", exception);
        }
        LOGGER.info(() -> "Summary: submitted=" + values.length
                + " recorded=" + ordered.size()
                + " successful=" + ordered.stream().filter(r -> r.status().equals("OK")).count()
                + " failed=" + ordered.stream().filter(r -> r.status().equals("ERROR")).count());
    }

    private static void runWorker(int workerId, TaskQueue queue, List<Result> results) {
        LOGGER.info(() -> "Worker-" + workerId + " started");
        try {
            Task task;
            while ((task = queue.getTask()) != null) {
                try {
                    Result result = processTask(task);
                    results.add(result);
                    LOGGER.info("Worker-" + workerId + " completed task " + task.id());
                } catch (IllegalArgumentException exception) {
                    results.add(new Result(task.id(), "ERROR", "message=" + exception.getMessage()));
                    LOGGER.warning("Worker-" + workerId + " error on task " + task.id()
                            + ": " + exception.getMessage());
                }
            }
        } catch (InterruptedException exception) {
            LOGGER.log(Level.WARNING, "Worker-" + workerId + " interrupted", exception);
            Thread.currentThread().interrupt();
        } finally {
            LOGGER.info(() -> "Worker-" + workerId + " stopped");
        }
    }

    private static Result processTask(Task task) throws InterruptedException {
        if (task.value() < 0) {
            throw new IllegalArgumentException("value must be nonnegative");
        }
        Thread.sleep(40L + (task.id() % 3) * 20L);
        long squared = (long) task.value() * task.value();
        return new Result(task.id(), "OK", "input=" + task.value() + " squared=" + squared);
    }

    private static void writeResults(Path output, List<Result> results) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            for (Result result : results) {
                writer.write(result.toString());
                writer.newLine();
            }
        }
    }
}

