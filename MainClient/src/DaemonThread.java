
public abstract class DaemonThread implements Runnable{
	
    private boolean active = false;

    private int interval = -1;


    private Thread runner;
    

    public DaemonThread(int intervalSeconds) {

        this.interval = intervalSeconds * 1000;
    }

    
        public void start() {

        active = true;

        if (runner == null && interval > 0) {
            runner = new Thread(this);
            runner.start();
        }
    }


    public void run() {

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        System.out.println("Starting monitoring...");
        while (active) {
            try {
                doInterval();
                Thread.sleep(interval);

            } catch (InterruptedException e) {
            	System.out.println("Daemon thread interrupted "+e);
            }
        }
    }
    
    /**
     * The interval has expired and now it's time to do something.
     */
    protected abstract void doInterval();
    
    
}