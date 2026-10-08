package main

import (
	"bufio"
	"errors"
	"fmt"
	"log"
	"os"
	"sort"
	"sync"
	"time"
)

const workerCount = 3

type Task struct {
	ID    int
	Value int
}

type Result struct {
	TaskID int
	Status string
	Detail string
}

func (r Result) String() string {
	return fmt.Sprintf("task=%d status=%s %s", r.TaskID, r.Status, r.Detail)
}

// TaskQueue uses a channel as a concurrency-safe shared task queue.
type TaskQueue struct {
	tasks chan Task
}

func NewTaskQueue(capacity int) *TaskQueue {
	return &TaskQueue{tasks: make(chan Task, capacity)}
}

func (q *TaskQueue) AddTask(task Task)    { q.tasks <- task }
func (q *TaskQueue) GetTask() <-chan Task { return q.tasks }
func (q *TaskQueue) Close()               { close(q.tasks) }

type ResultStore struct {
	mu      sync.Mutex
	results []Result
}

func (s *ResultStore) Add(result Result) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.results = append(s.results, result)
}

func (s *ResultStore) Snapshot() []Result {
	s.mu.Lock()
	defer s.mu.Unlock()
	copyOfResults := append([]Result(nil), s.results...)
	sort.Slice(copyOfResults, func(i, j int) bool {
		return copyOfResults[i].TaskID < copyOfResults[j].TaskID
	})
	return copyOfResults
}

func processTask(task Task) (Result, error) {
	if task.Value < 0 {
		return Result{}, errors.New("value must be nonnegative")
	}
	time.Sleep(time.Duration(40+(task.ID%3)*20) * time.Millisecond)
	return Result{TaskID: task.ID, Status: "OK",
		Detail: fmt.Sprintf("input=%d squared=%d", task.Value, task.Value*task.Value)}, nil
}

func worker(id int, tasks <-chan Task, store *ResultStore, wg *sync.WaitGroup) {
	defer wg.Done()
	log.Printf("Worker-%d started", id)
	defer log.Printf("Worker-%d stopped", id)
	for task := range tasks {
		result, err := processTask(task)
		if err != nil {
			store.Add(Result{TaskID: task.ID, Status: "ERROR", Detail: "message=" + err.Error()})
			log.Printf("Worker-%d error on task %d: %v", id, task.ID, err)
			continue
		}
		store.Add(result)
		log.Printf("Worker-%d completed task %d", id, task.ID)
	}
}

func writeResults(path string, results []Result) (err error) {
	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create output: %w", err)
	}
	defer func() {
		if closeErr := file.Close(); closeErr != nil && err == nil {
			err = fmt.Errorf("close output: %w", closeErr)
		}
	}()
	writer := bufio.NewWriter(file)
	defer func() {
		if flushErr := writer.Flush(); flushErr != nil && err == nil {
			err = fmt.Errorf("flush output: %w", flushErr)
		}
	}()
	for _, result := range results {
		if _, err = fmt.Fprintln(writer, result.String()); err != nil {
			return fmt.Errorf("write result: %w", err)
		}
	}
	return nil
}

func main() {
	log.SetFlags(log.Ltime | log.Lmicroseconds)
	queue := NewTaskQueue(10)
	store := &ResultStore{}
	var wg sync.WaitGroup

	for id := 1; id <= workerCount; id++ {
		wg.Add(1)
		go worker(id, queue.GetTask(), store, &wg)
	}

	values := []int{4, 7, 10, 13, 16, -3, 22, 25, 28, 31}
	for index, value := range values {
		queue.AddTask(Task{ID: index + 1, Value: value})
	}
	queue.Close()
	wg.Wait()

	results := store.Snapshot()
	if err := writeResults("results.txt", results); err != nil {
		log.Printf("ERROR: could not save results: %v", err)
		return
	}
	successful := 0
	for _, result := range results {
		if result.Status == "OK" {
			successful++
		}
	}
	log.Printf("Saved %d results to results.txt", len(results))
	log.Printf("Summary: submitted=%d recorded=%d successful=%d failed=%d",
		len(values), len(results), successful, len(results)-successful)
}
