package config;

/**
 * Configures behavior of SkinnerDB during startup.
 * 
 * @author immanueltrummer
 *
 */
public class StartupConfig {
	/**
	 * How to select columns on which to create indices at startup.
	 */
	public static final IndexingMode INDEX_CRITERIA = IndexingMode.ALL;
}
