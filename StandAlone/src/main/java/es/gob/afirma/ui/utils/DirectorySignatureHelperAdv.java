/*
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un applet de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010 Ministerio de la Presidencia, Gobierno de Espana
 * Este fichero se distribuye bajo licencia GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana.  Si se   distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */
package es.gob.afirma.ui.utils;

import java.io.File;

import javax.swing.JDialog;

import es.gob.afirma.core.AOUnsupportedSignFormatException;
import es.gob.afirma.massive.DirectorySignatureHelper;

/**
 * M&oacute;dulo para la ejecuci&oacute;n de firmas y multifirmas de ficheros. Durante el proceso
 * se muestra una barra de progreso que informa de la situaci&oacute;n.
 */
public class DirectorySignatureHelperAdv extends DirectorySignatureHelper {

	/** Componente padre sobre el que se mostrara el di&aacute;logo con la barra de progreso. */
	private JDialog parent = null;
  
	/** Dialogo con la barra de progreso. */
	private ProgressDialog progressDialog = null;
	
	/**
	 * Crea un instancia de la clase con una configuraci&oacute;n y un componente padre asignado.
	 * @param algorithm Algoritmo de firma electr&oacute;nica.
	 * @param format Formato de firma por defecto.
	 * @param mode Modo de firma.
	 * @param parent Componente padre.
	 * @throws AOUnsupportedSignFormatException 
	 */
	public DirectorySignatureHelperAdv(String algorithm, String format, String mode, JDialog parent) throws AOUnsupportedSignFormatException {
		super(algorithm, format, mode);
		this.parent = parent;
	}
	
	@Override
	protected void prepareOperation(File[] files) {
	    this.progressDialog = new ProgressDialog(this.parent, files.length, Messages.getString("Wizard.multifirma.progress.titulo")); //$NON-NLS-1$
	    this.progressDialog.show();
	}
	
	@Override
	protected void disposeOperation() {
	    this.progressDialog.close();
	}
	
	@Override
	protected void preProcessFile(File file) {
	    this.progressDialog.processElement(file.getAbsolutePath());
	}
}
