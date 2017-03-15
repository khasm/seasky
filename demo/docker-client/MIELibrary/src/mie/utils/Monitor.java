package mie.utils;

public class Monitor {

	private boolean ready;
	private final Object lock;
	private int nThreads;
	private int waitingThreads;
	private boolean synced;

	public Monitor(int nThreads){
		ready = false;
		lock = new Object();
		this.nThreads = nThreads;
		waitingThreads = 0;
		synced = false;
	}

	public int getNThreads() {
		return nThreads;
	}

	public void waitForAll() {
		synced = false;
		synchronized(lock){
			if(waitingThreads == nThreads-1){
				synced = true;
				lock.notifyAll();
				System.out.println("waitForAll finished");
			}
			else{
				waitingThreads++;
				System.out.println("Waiting for " + (nThreads - waitingThreads));
				while(waitingThreads < nThreads && !synced){
					try{
						lock.wait();
					}
					catch(InterruptedException e){
						e.printStackTrace();
					}
				}
				System.out.println("Stopped waiting");
				waitingThreads--;
			}
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
		}
	}
}