package de.fernunihagen.dna.jkn.scalephant.storage.sstable.spatialindex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.jkn.scalephant.ScalephantConfiguration;
import de.fernunihagen.dna.jkn.scalephant.ScalephantConfigurationManager;

public class SpatialIndexFactory {
	
	/**
	 * The cached instance of the factory
	 */
	protected static SpatialIndexer cachedInstance = null;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SpatialIndexFactory.class);

	/**
	 * Get an instance of the configured factory
	 * @return
	 */
	public static SpatialIndexer getInstance() {
		
		if(cachedInstance != null) {
			return cachedInstance;
		}
		
		final ScalephantConfiguration configuration = ScalephantConfigurationManager.getConfiguration();
		final String factoryClass = configuration.getStorageSpatialIndexerFactory();
		
		if("none".equals(factoryClass)) {
			return null;
		}
		
		// Instance the classname
		try {
			final Class<?> classObject = Class.forName(factoryClass);
			
			if(classObject == null) {
				throw new ClassNotFoundException("Unable to locate class: " + factoryClass);
			}
			
			final Object factoryObject = classObject.newInstance();
			
			if(! (factoryObject instanceof SpatialIndexer)) {
				throw new ClassNotFoundException(factoryClass + " is not a instance of SpatialIndexer");
			}
			
			cachedInstance = (SpatialIndexer) factoryObject;
			
			return cachedInstance;
			
		} catch (Exception e) {
			logger.warn("Unable to instance class" + factoryClass, e);
			throw new RuntimeException(e);
		} 
	}
	
	/**
	 * Clear the cached instance
	 */
	public static void clearCache() {
		cachedInstance = null;
	}
	
}
