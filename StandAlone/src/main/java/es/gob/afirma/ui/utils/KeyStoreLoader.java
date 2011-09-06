package es.gob.afirma.ui.utils;

import java.util.Vector;

import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.keystores.common.AOKeyStore;
import es.gob.afirma.keystores.common.KeyStoreConfiguration;

/**
 * Clase para la recuperaci&oacute;n de los distintos almacenes del sistema
 * disponibles para realizar distintas operaciones.
 */
public class KeyStoreLoader {

	/**
	 * Recupera los almacenes compatibles con el sistema y preparados
	 * para contener certificados de firma.
	 * @return Listado de almacenes.
	 */
	public static KeyStoreConfiguration[] getKeyStoresToSign() {
		
		Vector<KeyStoreConfiguration> stores = new Vector<KeyStoreConfiguration>();

		if (Platform.getOS().equals(Platform.OS.WINDOWS)) 
			stores.add(new KeyStoreConfiguration(AOKeyStore.WINDOWS, null, null)); //$NON-NLS-1$ //$NON-NLS-2$
		if (Platform.getOS().equals(Platform.OS.MACOSX)) 
			stores.add(new KeyStoreConfiguration(AOKeyStore.APPLE, null, null)); //$NON-NLS-1$ //$NON-NLS-2$
		
//		try {
//			MozillaKeyStoreUtilities.getSystemNSSLibDir();
			stores.add(new KeyStoreConfiguration(AOKeyStore.MOZ_UNI, null, null));
//		} catch (Exception e) {}
		
		stores.add(new KeyStoreConfiguration(AOKeyStore.PKCS12, null, null));
		
		return stores.toArray(new KeyStoreConfiguration[0]);
	}
	
	/**
	 * Recupera los almacenes compatibles con el sistema y preparados
	 * para contener certificados para envoltura de datos.
	 * @return Listado de almacenes.
	 */
	public static KeyStoreConfiguration[] getKeyStoresToWrap() {
		
		Vector<KeyStoreConfiguration> stores = new Vector<KeyStoreConfiguration>();

		stores.add(new KeyStoreConfiguration(AOKeyStore.SINGLE, null, null));
		stores.add(new KeyStoreConfiguration(AOKeyStore.PKCS12, null, null));
		
		if (Platform.getOS().equals(Platform.OS.WINDOWS)) {
			stores.add(new KeyStoreConfiguration(AOKeyStore.WINADDRESSBOOK, null, null));
		}
		
		return stores.toArray(new KeyStoreConfiguration[0]);
	}
}
