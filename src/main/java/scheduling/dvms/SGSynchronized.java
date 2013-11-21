package scheduling.dvms;


public class SGSynchronized {
	//private Sem sem; 
	boolean locked; // There is no way to get the value of the semaphore directly
	
	SGSynchronized(){
		//sem = new Sem(0);
		locked=false; 
	}
	
	public void lock(){
		//try {
			//this.sem.acquire();

			locked=true; 
		//} catch (InterruptedException e) {
		//	locked=false; 
		//	e.printStackTrace();
		//}
	}
	public void unlock(){
		if(locked){
		//	this.sem.release();
			locked=false;
		}
	}
}
