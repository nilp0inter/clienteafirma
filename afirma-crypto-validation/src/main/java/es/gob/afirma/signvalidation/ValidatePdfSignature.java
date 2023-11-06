/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.signvalidation;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.aowagie.text.pdf.AcroFields;
import com.aowagie.text.pdf.PdfDictionary;
import com.aowagie.text.pdf.PdfName;
import com.aowagie.text.pdf.PdfPKCS7;
import com.aowagie.text.pdf.PdfReader;

import es.gob.afirma.core.RuntimeConfigNeededException;
import es.gob.afirma.signers.pades.common.PdfExtraParams;
import es.gob.afirma.signers.pades.common.PdfFormModifiedException;
import es.gob.afirma.signers.pades.common.SuspectedPSAException;
import es.gob.afirma.signvalidation.SignValidity.SIGN_DETAIL_TYPE;
import es.gob.afirma.signvalidation.SignValidity.VALIDITY_ERROR;

/** Validador de firmas PDF.
 * Se validan los certificados en local revisando si procede las fechas de validez de los certificados.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class ValidatePdfSignature extends SignValider {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final PdfName PDFNAME_ETSI_RFC3161 = new PdfName("ETSI.RFC3161"); //$NON-NLS-1$
	private static final PdfName PDFNAME_DOCTIMESTAMP = new PdfName("DocTimeStamp"); //$NON-NLS-1$

	private static final String DEFAULT_PAGES_TO_CHECK_PSA = "10"; //$NON-NLS-1$

	/**
	 * Valida una firma PDF (PKCS#7/PAdES). En caso de validar los certificados de firma,
	 * s&oacute;lo se validar&aacute; el periodo de caducidad.
     * @param sign PDF firmado.
     * @return Validez de la firma.
     * @throws RuntimeConfigNeededException Cuando en la validaci&oacute;n laxa se puede considerar
     * que podr&iacute;a operarse sobre la firma si se cuenta con m&aacute;s informaci&oacute;n del
     * usuario.
     * @throws IOException Si ocurren problemas relacionados con la lectura del documento, si no se
     * encuentran firmas PDF en el documento o si se requiere m&aacute;s informacion para la validaci&oacute;n.
     */
	@Override
	public List<SignValidity> validate(final byte[] sign) throws RuntimeConfigNeededException, IOException {
		return validate(sign, true);
	}

	/**
	 * Valida una firma PDF (PKCS#7/PAdES). En caso de validar los certificados de firma,
	 * s&oacute;lo se validar&aacute; el periodo de caducidad.
     * @param sign PDF firmado.
     * @param checkCertificates Indica si debe comprobarse la caducidad de los certificados de firma.
     * @return Validez de la firma.
     * @throws RuntimeConfigNeededException Cuando en la validaci&oacute;n laxa se puede considerar
     * que podr&iacute;a operarse sobre la firma si se cuenta con m&aacute;s informaci&oacute;n del
     * usuario.
     * @throws IOException Si ocurren problemas relacionados con la lectura del documento, si no se
     * encuentran firmas PDF en el documento o si se requiere m&aacute;s informacion para la validaci&oacute;n.
     */
	@Override
	public List<SignValidity> validate(final byte[] sign, final boolean checkCertificates) throws RuntimeConfigNeededException, IOException {
		final Properties params = new Properties();
		params.setProperty(PdfExtraParams.CHECK_CERTIFICATES, Boolean.toString(checkCertificates));
		try {
			return validate(sign, params);
		} catch (final RuntimeConfigNeededException e) {
			throw new IOException("No se dispone de la informacion necesaria para completar la validacion", e); //$NON-NLS-1$
		}
	}

	/** Valida una firma PDF (PKCS#7/PAdES). En caso de validar los certificados de firma,
	 * s&oacute;lo se validar&aacute; el periodo de caducidad.
     * @param sign PDF firmado.
     * @param params Par&aacute;metros a tener en cuenta para la validaci&oacute;n.
     * @return Validez de la firma.
     * @throws RuntimeConfigNeededException Cuando en la validaci&oacute;n laxa se puede considerar
     * que podr&iacute;a operarse sobre la firma si se cuenta con m&aacute;s informaci&oacute;n del
     * usuario.
     * @throws IOException Si ocurren problemas relacionados con la lectura del documento
     * o si no se encuentran firmas PDF en el documento. */
	@Override
	public List<SignValidity> validate(final byte[] sign, final Properties params) throws RuntimeConfigNeededException, IOException {

		final List<SignValidity> validityList = new ArrayList<SignValidity>();
		AcroFields af;
		PdfReader reader;
		try {
			reader = new PdfReader(sign);
			af = reader.getAcroFields();
		}
		catch (final Exception e) {
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.NO_SIGN));
			return validityList;
		}
		final List<String> signNames = af.getSignatureNames();

		// Si no hay firmas, no hay nada que comprobar
		if (signNames.size() == 0) {
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.NO_SIGN));
			return validityList;
		}

		boolean algorithmNotSupportedOcurred = false;
		boolean noMatchDataOcurred = false;
		boolean corruptedSignOcurred = false;
		boolean certExpiredOcurred = false;
		boolean certNotValidYetOcurred = false;

		for (final String name : signNames) {

			// Valimamos la firma
			final PdfPKCS7 pk = af.verifySignature(name);

			// Comprobamos que el algoritmo de hash este bien declarado, supliendo asi la flexibilidad de iText que permite
			// cargar firmas que usan algoritmos de firma como algoritmos de hash
			if (pk.getStrictHashAlgorithm() == null && !algorithmNotSupportedOcurred) {
				validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.ALGORITHM_NOT_SUPPORTED));
				algorithmNotSupportedOcurred = true;
			}

    		// Comprobamos si es una firma o un sello
    		final PdfDictionary pdfDictionary = af.getSignatureDictionary(name);

    		// Si no es un sello, comprobamos el PKCS#1
    		if (!PDFNAME_ETSI_RFC3161.equals(pdfDictionary.get(PdfName.SUBFILTER)) && !PDFNAME_DOCTIMESTAMP.equals(pdfDictionary.get(PdfName.SUBFILTER))) {
				try {
					if (!pk.verify() && !noMatchDataOcurred) {
						validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.NO_MATCH_DATA));
						noMatchDataOcurred = true;
					}
				}
				catch (final Exception e) {
					if (!corruptedSignOcurred) {
						LOGGER.warning("Error validando una de las firmas del PDF: " + e); //$NON-NLS-1$
						validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CORRUPTED_SIGN, e));
						corruptedSignOcurred = true;
					}
				}
    		}

    		final boolean checkCertificates = Boolean.parseBoolean(params.getProperty(PdfExtraParams.CHECK_CERTIFICATES, Boolean.TRUE.toString()));

    		if (checkCertificates) {
				final X509Certificate signCert = pk.getSigningCertificate();
				try {
					signCert.checkValidity();
				}
				catch (final CertificateExpiredException e) {
					// Certificado caducado
					if (!certExpiredOcurred) {
						validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CERTIFICATE_EXPIRED, e));
						certExpiredOcurred = true;
					}
				}
				catch (final CertificateNotYetValidException e) {
					// Certificado aun no valido
					if (!certNotValidYetOcurred) {
						validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CERTIFICATE_NOT_VALID_YET, e));
						certNotValidYetOcurred = true;
					}
				}
			}
		}

		// COMPROBACION DE CAMBIOS EN LOS FORMULARIOS PDF

		final String allowSignModifiedFormProp = params.getProperty(PdfExtraParams.ALLOW_SIGN_MODIFIED_FORM);
		final boolean allowSignModifiedForm = Boolean.parseBoolean(allowSignModifiedFormProp);

		// Si se debe comprobar que no haya cambios en los valores de los formularios, lo hacemos
		// si hay mas de una revision, comprobamos si ha habido cambios en campos de formularios
		if (!allowSignModifiedForm && af.getTotalRevisions() > 1) {
			final Map<String, String> errors = DataAnalizerUtil.checkPDFForm(reader);
			if (errors != null && !errors.isEmpty()) {
				// Si no estaba definido un comportamiento concreto, consultaremos al usuario.
				if (allowSignModifiedFormProp == null && isRelaxed()) {
					throw new PdfFormModifiedException("Se han detectado cambios en un formulario posteriores a la primera firma"); //$NON-NLS-1$
				}
				validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.MODIFIED_FORM));
			}
		}

		// COMPROBACION DE PDF SHADOW ATTACK

		final String allowShadowAttackProp = params.getProperty(PdfExtraParams.ALLOW_SHADOW_ATTACK);
		final boolean allowPdfShadowAttack = Boolean.parseBoolean(allowShadowAttackProp);
		final String pagesToCheck =  params.getProperty(PdfExtraParams.PAGES_TO_CHECK_PSA, DEFAULT_PAGES_TO_CHECK_PSA);

		// La comprobacion de PDF Shadow Attack detecta tambien los cambios en los formularios PDF,
		// asi que estos cambios impiden que se pueda hacer una comprobacion realista de esta
		// situacion. Por tanto, si se permiten los cambios en los formularios, se ignorara la
		// validacion de PDF Shadow Attack

		// Por otra parte, si se debe comprobar si se ha producido un PDF Shadow Attack
		// (modificacion de un documento tras la firma), se encuentran varias revisiones
		// en el documento y hay al menos una posterior a la ultima firma (la de la
		// posicion 0), se comprueba si el documento ha sufrido un PSA.
		if (!allowSignModifiedForm && !allowPdfShadowAttack && af.getTotalRevisions() > 1 && af.getRevision(signNames.get(0)) < af.getTotalRevisions()) {
			// La revision firmada mas reciente se encuentra en el primer lugar de la lista, por ello se accede a la posicion 0
			try (final InputStream lastReviewStream = af.extractRevision(signNames.get(0))) {
				SignValidity validity = DataAnalizerUtil.checkPdfShadowAttack(sign, lastReviewStream, pagesToCheck);
				// Si se devolvio informacion de validez, la firma no es completamente valida
				if (validity != null) {
					// Si es una validacion relajada, se comprueba si se debe consultar al usuario y si se
					// cumplen los requisitos para ello
					if (isRelaxed() &&  validity.getValidity() == SignValidity.SIGN_DETAIL_TYPE.PENDING_CONFIRM_BY_USER
							&& allowShadowAttackProp == null) {
						throw new SuspectedPSAException("PDF sospechoso de haber sufrido PDF Shadow Attack"); //$NON-NLS-1$
					}
					// Si habia que consultar y no se cumplen los requisitos,
					// se considera que la firma no es valida
					if (validity.getValidity() == SignValidity.SIGN_DETAIL_TYPE.PENDING_CONFIRM_BY_USER) {
						validity = new SignValidity(SIGN_DETAIL_TYPE.KO, validity.getError());
					}
					validityList.add(validity);
				}
			}
		}

		if (validityList.size() == 0) {
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.OK, null));
		}
		return validityList;
	}


	/** Valida una firma PDF (PKCS#7/PAdES). En caso de validar los certificados de firma,
	 * s&oacute;lo se validar&aacute; el periodo de caducidad.
     * @param signName Nombre de firma.
     * @param params Par&aacute;metros a tener en cuenta para la validaci&oacute;n.
     * @return Lista con las validaciones realizadas en la firma.
     * @throws RuntimeConfigNeededException Cuando en la validaci&oacute;n laxa se puede considerar
     * que podr&iacute;a operarse sobre la firma si se cuenta con m&aacute;s informaci&oacute;n del
     * usuario.
     * @throws IOException Si ocurren problemas relacionados con la lectura del documento
     * o si no se encuentran firmas PDF en el documento. */
	public static List<SignValidity> validateSign(final String signName, final AcroFields signAcrofields)
			throws RuntimeConfigNeededException, IOException {

		final List<SignValidity> validityList = new ArrayList<SignValidity>();
		// Valimamos la firma
		final PdfPKCS7 pk = signAcrofields.verifySignature(signName);

		// Comprobamos que el algoritmo de hash este bien declarado, supliendo asi la
		// flexibilidad de iText que permite
		// cargar firmas que usan algoritmos de firma como algoritmos de hash
		if (pk.getStrictHashAlgorithm() == null) {
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.ALGORITHM_NOT_SUPPORTED));
		}

		// Comprobamos si es una firma o un sello
		final PdfDictionary pdfDictionary = signAcrofields.getSignatureDictionary(signName);

		// Si no es un sello, comprobamos el PKCS#1
		if (!PDFNAME_ETSI_RFC3161.equals(pdfDictionary.get(PdfName.SUBFILTER))
				&& !PDFNAME_DOCTIMESTAMP.equals(pdfDictionary.get(PdfName.SUBFILTER))) {
			try {
				if (!pk.verify()) {
					validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.NO_MATCH_DATA));
				}
			} catch (final Exception e) {
				LOGGER.warning("Error validando una de las firmas del PDF: " + e); //$NON-NLS-1$
				validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CORRUPTED_SIGN, e));
			}
		}

		final X509Certificate signCert = pk.getSigningCertificate();
		try {
			signCert.checkValidity();
		} catch (final CertificateExpiredException e) {
			// Certificado caducado
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CERTIFICATE_EXPIRED, e));
		} catch (final CertificateNotYetValidException e) {
			// Certificado aun no valido
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.KO, VALIDITY_ERROR.CERTIFICATE_NOT_VALID_YET, e));
		}

		if (validityList.size() == 0) {
			validityList.add(new SignValidity(SIGN_DETAIL_TYPE.OK, null));
		}

		return validityList;

	}


}
