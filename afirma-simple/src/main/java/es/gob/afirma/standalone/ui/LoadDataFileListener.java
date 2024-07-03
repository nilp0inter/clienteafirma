package es.gob.afirma.standalone.ui;

import java.io.File;

/**
 * Clase que atiende peticiones de carga de ficheros.
 */
public interface LoadDataFileListener {

	/**
	 * Solicita la carga de unos ficheros.
	 * @param files Listado de ficheros a cargar.
	 */
	void loadFiles(File[] files, SignOperationConfig signConfig);
}
