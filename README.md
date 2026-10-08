# Concurrent Data Processing System

This repository contains equivalent Java and Go implementations of a worker-pool data processing system. Both programs process each submitted task exactly once, record successful and failed outcomes, log worker lifecycle events, write a shared result file safely, and terminate without deadlock.

## Run the Java implementation

```bash
cd java
./run.sh
```

The Java version uses a `ReentrantLock` and `Condition` in `TaskQueue`, a fixed `ExecutorService`, a synchronized results list, interruption handling, and try-with-resources for output.

## Run the Go implementation

```bash
cd go
go run .
```

The Go version uses a buffered channel as the task queue, goroutines, `sync.WaitGroup`, a mutex-protected results collector, explicit error returns, and `defer` for cleanup.

Both programs create a local `results.txt`. Task 6 intentionally contains invalid input so the error path is visible while the remaining tasks continue.

## Repository link

https://github.com/siddharth2170/data-processing-system
