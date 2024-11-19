public class MyTask implements Runnable {
    private String taskName;

    public MyTask(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void run() {
        System.out.println(taskName + " is running in thread " + Thread.currentThread().getName());
        try {
            // Simulate some work with sleep
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println(taskName + " was interrupted.");
        }
        System.out.println(taskName + " completed in thread " + Thread.currentThread().getName());
    }
}
