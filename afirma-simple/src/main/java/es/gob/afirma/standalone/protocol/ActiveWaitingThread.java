package es.gob.afirma.standalone.protocol;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Hilo para la solicitud continua de espera a trav&eacute;s del servidor
 * intermedio. Una vez en ejecuci&oacute;n, enviar&aacute; cada cierto
 * tiempo una solicitud de espera al servidor intermedio hasta que el hilo
 * se interrumpa.
 */
public class ActiveWaitingThread extends Thread {

	private static final String WAIT_CONSTANT = "#WAIT"; //$NON-NLS-1$
	private static final long SLEEP_PERIOD = 15000;

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private final String storageServiceUrl;
	private final String transactionId;

	private boolean cancelled = false;

	/**
	 * Construye un hilo que cada cierto tiempo sube al servidor intermedio
	 * un mensaje de espera.
	 * @param storageServiceUrl URL del servicio de guardado del servidor intermedio.
	 * @param transactionId Identificador de transacci&oacute;n.
	 */
	public ActiveWaitingThread(String storageServiceUrl, String transactionId) {
		this.storageServiceUrl = storageServiceUrl;
		this.transactionId = transactionId;
	}

	@Override
	public void run() {

		while (!this.cancelled) {
			LOGGER.info(" ==== Ciclo de hilo");
			try {
				Thread.sleep(SLEEP_PERIOD);
			} catch (final InterruptedException e) {
				LOGGER.warning("No se ha podido esperar para el envio de la peticion de espera: " + e); //$NON-NLS-1$
			}
			synchronized (IntermediateServerUtil.getUniqueSemaphoreInstance()) {
				if (!this.cancelled) {
					LOGGER.info(" ==== El hilo NO esta interrumpido\n");
					try {
						IntermediateServerUtil.sendData(WAIT_CONSTANT, this.storageServiceUrl, this.transactionId);
					} catch (final IOException e) {
						LOGGER.warning("No se ha podido enviar la peticion de espera: " + e); //$NON-NLS-1$
					}
				}
				else {
					LOGGER.info(" ==== El hilo SI esta interrumpido\n");
				}
			}
		}
	}

	@Override
	public void interrupt() {
		this.cancelled = true;
		super.interrupt();
	}
}
