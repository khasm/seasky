package mie.utils;

public class Monitor {

	private boolean ready;
	private final Object lock;

	public Monitor(){
		ready = false;
		lock = new Object();
	}

	public void waitForThreads(){
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

	public void ready(){
		synchronized(lock){
			ready = true;
			lock.notifyAll();
		}
	}
}