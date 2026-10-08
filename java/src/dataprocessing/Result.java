package dataprocessing;

public record Result(int taskId, String status, String detail) {
    @Override
    public String toString() {
        return "task=" + taskId + " status=" + status + " " + detail;
    }
}

