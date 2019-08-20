package config;

/**
 * Configuration parameters that influence
 * several of the three processing phases.
 * 
 * @author immanueltrummer
 *
 */
public class GeneralConfig {
	/**
	 * Whether to use in-memory data processing.
	 */
	public static boolean inMemory = true;
	/**
	 * Whether to use in-memory index processing.
	 */
	public static boolean indexInMemory = false;
	/**
	 * The size of cache in bytes
	 */
	public static int cacheSize = 50000000;

}
