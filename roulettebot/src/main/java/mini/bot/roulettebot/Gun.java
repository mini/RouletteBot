package mini.bot.roulettebot;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Gun {
	public volatile static boolean FORCE = false;
	private static final Random rand = new Random();

	private static final int SIZE = 8;
	private static AtomicInteger pulls = new AtomicInteger();
	private static AtomicInteger fired = new AtomicInteger();
	
	private String guildName;
	private int currentChamber;
	private int bulletChamber;
	
	public Gun(String guildName) {
		this.guildName = guildName;
		insertBullet();
	}
	
	private void insertBullet() {
		synchronized (this) {
			currentChamber = 1;
			bulletChamber = rand.nextInt(SIZE) + 1;
			System.out.println(guildName + ": Placed bullet in " + bulletChamber);
		}
	}
	
	public boolean fire() {
		pulls.getAndIncrement();
		synchronized (this) {
			System.out.println(guildName + ": Fired " + currentChamber + ", bullet in " + bulletChamber);
			if(currentChamber++ == bulletChamber || FORCE) {
				insertBullet();
				fired.getAndIncrement();
				return true;
			} 			
			return false;
		}		
	}	
	
	public static int getPulls() {
		return pulls.get();
	}
	
	public static int getFired() {
		return fired.get();
	}
}
