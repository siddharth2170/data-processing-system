package dataprocessing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** A blocking, closeable queue implemented with an explicit lock. */
public final class TaskQueue {
    private final Deque<Task> tasks = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition taskAvailable = lock.newCondition();
    private boolean closed;

    public void addTask(Task task) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Cannot add a task after the queue is closed");
            }
            tasks.addLast(task);
            taskAvailable.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Returns null only when the queue is closed and drained. */
    public Task getTask() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (tasks.isEmpty() && !closed) {
                taskAvailable.await();
            }
            return tasks.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            taskAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

