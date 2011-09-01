/*
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un applet de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010 Ministerio de la Presidencia, Gobierno de Espana
 * Este fichero se distribuye bajo licencia GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana.  Si se   distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */
package es.gob.afirma.ui.wizardUtils;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import es.gob.afirma.ui.utils.JAccessibilityDialogWizard;
import es.gob.afirma.ui.utils.Messages;

/**
 * Clase para generar la parte inferior de la ventana con la botonera
 */
public class BotoneraInferior extends JPanel {

	private static final long serialVersionUID = 1L;
	private Dimension dimensiones = new Dimension(603, 47);
	private List<JDialogWizard> ventanas;
	private Integer posicion;

	
	public List<JDialogWizard> getVentanas() {
		return this.ventanas;
	}
	
	/**
	 * Genera una botonera con la configuracion predefinida
	 * @param ventanas	Listado que contiene todas las ventanas en orden de aparicion
	 * @param posicion	Numero de la pagina
	 */
	public BotoneraInferior(List<JDialogWizard> ventanas, Integer posicion) {
		this.ventanas = ventanas;
		this.posicion = posicion;
		initParamenters();
	}
	
	/**
	 * Genera una botonera con unas dimensiones dadas
	 * @param dimensiones	Dimensiones de la botonera
	 */
	public BotoneraInferior(Dimension dimensiones) {
		this.dimensiones = dimensiones;
		initParamenters();
	}

	/**
	 * Inicializacion de parametros
	 */
	private void initParamenters() {
		// Configuracion del panel
    	setBorder(BorderFactory.createEtchedBorder());
        setPreferredSize(dimensiones);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 1, 1));

        // Definicion de botones
        final JButton maximizar = new JButton();
        final JButton anterior = new JButton();
        final JButton siguiente = new JButton();
        final JButton cancelar = new JButton();
        final JButton finalizar = new JButton();
        
        //Boton maximizar
        maximizar.setText(Messages.getString("Wizard.maximizar"));
        maximizar.setName("maximizar");
        maximizar.setMnemonic(KeyEvent.VK_M);
        maximizar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				maximizarActionPerformed();
			}
		});
        add(maximizar);
        
        //Espacio entre botones
		Panel panelVacio = new Panel();
		panelVacio.setPreferredSize(new Dimension(150, 10));
		add(panelVacio);		
		
    	// Boton anterior
        Integer paginas = ventanas.size() - 1;
        if (posicion.equals(0) || paginas.equals(posicion))
        	anterior.setEnabled(false);
        else {
        	 anterior.setMnemonic(KeyEvent.VK_A); //Mnem�nico para el bot�n de anterior
        	anterior.setEnabled(true);
        }
        anterior.setText(Messages.getString("Wizard.anterior")); // NOI18N
        anterior.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                anteriorActionPerformed(anterior, siguiente, finalizar);
            }
        });
       
        add(anterior);
        
        // Boton siguiente
        if (ventanas.size() == 1 || paginas.equals(posicion))
        	siguiente.setVisible(false);
        else {
        	siguiente.setMnemonic(KeyEvent.VK_S); //Mnem�nico para el bot�n de siguiente
        	siguiente.setVisible(true);
        }
        siguiente.setText(Messages.getString("Wizard.siguiente")); // NOI18N
        siguiente.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                siguienteActionPerformed(anterior, siguiente, finalizar);
            }
        });
        add(siguiente);

        // Espacio entre botones
		panelVacio = new Panel();
		panelVacio.setSize(new Dimension(20, 10));
		add(panelVacio);
        
        // Boton cancelar
		if (paginas.equals(posicion))
			cancelar.setVisible(false);
        else {
        	cancelar.setMnemonic(KeyEvent.VK_C); //Mnem�nico para el bot�n de cancelar
        	cancelar.setVisible(true);
        }
        cancelar.setText(Messages.getString("Wizard.cancelar")); // NOI18N
        cancelar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
            	for (JDialogWizard ventana : ventanas)
            		ventana.dispose();
            }
        });
        add(cancelar);

        // Boton finalizar
        if (ventanas.size() == 1 || paginas.equals(posicion)) {
        	 finalizar.setMnemonic(KeyEvent.VK_F); //Mnem�nico para el bot�n de finalizar
        	finalizar.setVisible(true);
        } else 
        	finalizar.setVisible(false);

        finalizar.setText(Messages.getString("Wizard.finalizar")); // NOI18N
        finalizar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
            	for (JDialogWizard ventana : ventanas)
            		ventana.dispose();
            }
        });
        add(finalizar);
	}

	/**
	 * Muestra el dialogo siguiente
	 * @param finalizar Boton finalizar
	 * @param siguiente Boton siguiente
	 * @param anterior 	Boton anterior
	 */
	protected void siguienteActionPerformed(JButton anterior, JButton siguiente, JButton finalizar) {
		Integer indice = posicion + 1;
		
		//mantenemos el tamaño y posición de la ventana acutual en la ventana siguiente
		ventanas.get(posicion+1).setBounds(ventanas.get(posicion).getX(), ventanas.get(posicion).getY(), ventanas.get(posicion).getWidth(), ventanas.get(posicion).getHeight());
		
		ventanas.get(indice).setVisibleAndHide(true, ventanas.get(posicion));
	}

	/**
	 * Muestra el dialogo anterior
	 * @param finalizar Boton finalizar
	 * @param siguiente Boton siguiente
	 * @param anterior 	Boton anterior
	 */
	protected void anteriorActionPerformed(JButton anterior, JButton siguiente, 
			JButton finalizar) {
		// Nos movemos al indice anterior
		Integer indice = posicion - 1;
		
		//ventanas.get(posicion).dispose();
		//mantenemos el tamaño y posición de la ventana acutual en la ventana anterior
		ventanas.get(posicion-1).setBounds(ventanas.get(posicion).getX(), ventanas.get(posicion).getY(), ventanas.get(posicion).getWidth(), ventanas.get(posicion).getHeight());
		
		ventanas.get(indice).setVisibleAndHide(true, ventanas.get(posicion));
	}
	
	/**
	 * Cambia el tamaño de la ventana al tamaño máximo de pantalla menos el tamaño de la barra de tareas de windows
	 */
	public void maximizarActionPerformed(){
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		JAccessibilityDialogWizard j = getJAccessibilityDialogWizard(this);
		j.setBounds(0,0,(int)screenSize.getWidth(), (int)screenSize.getHeight()-35);
		
	}
	
	/**
	 * Busca el JAccessibilityDialogWizard padre de un componente.
	 * @param component El componente.
	 * @return El JAccessibilityDialogWizard buscado.
	 */
	public static JAccessibilityDialogWizard getJAccessibilityDialogWizard(Component component)
	{
		JAccessibilityDialogWizard  resultingJAccessibilityDialogWizard = null;
		while (component != null && resultingJAccessibilityDialogWizard == null)
		{
	        if (component instanceof JAccessibilityDialogWizard){
	        	resultingJAccessibilityDialogWizard = (JAccessibilityDialogWizard)component;
	        }
	        else{
	        	component = component.getParent();
	        }
		 }
		 return resultingJAccessibilityDialogWizard;
	 }
}
