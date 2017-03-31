package mie.utils;

public class Monitor {

	private boolean ready;
	private final Object lock;
	private int nThreads;
	private int waitingThreads;
	private int waitingTimerThreads; //second variable to avoid concurrency/conflit issues
	private int lastSingleOp;
	private long startTime;
	private long totalTime;
	private boolean synced;

	public Monitor(int nThreads) {
		ready = false;
		lock = new Object();
		this.nThreads = nThreads;
		waitingThreads = 0;
		waitingTimerThreads = 0;
		lastSingleOp = 0;
		startTime = 0;
		totalTime = 0;
		synced = false;
	}

	public int getNThreads() {
		return nThreads;
	}

	public void waitFor(int seconds) {
		long tmpTime = -1;
		synchronized(lock){
			waitingTimerThreads++;
			if(waitingTimerThreads == nThreads){
				tmpTime = System.nanoTime();
				totalTime += tmpTime - startTime;
			}
		}
		try{
			Thread.sleep(seconds*1000);
		}
		catch(IllegalArgumentException | InterruptedException e){
			e.printStackTrace();
		}
		synchronized(lock){
			if(-1 != tmpTime && nThreads == waitingTimerThreads)
				startTime = System.nanoTime();
			waitingTimerThreads--;
		}
	}

	public void waitForAll() {
		synchronized(lock){
			synced = false;
			if(waitingThreads == nThreads-1){
				synced = true;
				lock.notifyAll();
			}
			else{
				waitingThreads++;
				while(waitingThreads < nThreads && !synced){
					try{
						lock.wait();
					}
					catch(InterruptedException e){
						e.printStackTrace();
					}
				}
				waitingThreads--;
			}
		}
	}

	public synchronized boolean canExecute(int nextSingleOp) {
		if(nextSingleOp == lastSingleOp+1){
			lastSingleOp++;
			return true;
		}
		else{
			return false;
		}
	}

	public void ready() {
		synchronized(lock){
			while(!ready){
				try{
					lock.wait();
				}
				catch(InterruptedException e){
					e.printStackTrace();
				}
			}
		}
	}

	public void start() {
		synchronized(lock){
			ready = true;
			lock.notifyAll();
			startTime = System.nanoTime();
		}
	}

	public void end() {
		totalTime += System.nanoTime() - startTime;
	}

	public long getTotalTime() {
		return totalTime;
	}

	@Override
	public String toString() {
		return String.format("Total time: %.6f", getTotalTime()/1000000000f);
	}
}