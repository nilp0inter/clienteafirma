package es.gob.afirma.standalone.signdetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aowagie.text.exceptions.BadPasswordException;
import com.aowagie.text.pdf.AcroFields;
import com.aowagie.text.pdf.PdfDictionary;
import com.aowagie.text.pdf.PdfName;
import com.aowagie.text.pdf.PdfPKCS7;
import com.aowagie.text.pdf.PdfReader;
import com.aowagie.text.pdf.PdfString;

import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.util.tree.AOTreeModel;
import es.gob.afirma.core.util.tree.AOTreeNode;
import es.gob.afirma.signers.pades.AOPDFSigner;
import es.gob.afirma.signvalidation.SignValidity;
import es.gob.afirma.signvalidation.ValidatePdfSignature;

public class PDFSignAnalyzer implements SignAnalyzer {

	static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	List <SignDetails> signDetailsList;
	final AOTreeNode signersTree = new AOTreeNode("Datos"); //$NON-NLS-1$

	private static final String PDF = "PDF"; //$NON-NLS-1$s


	public PDFSignAnalyzer(final byte [] data) throws Exception {
    	try {
    		this.signDetailsList = new ArrayList<SignDetails>();
    		createSignDetails(data);
    	}
    	catch (final Exception e) {
    		throw new AOInvalidFormatException("No se ha podido cargar el documento XML de firmas", e); //$NON-NLS-1$
    	}
	}

	@Override
	public AOTreeModel getSignersTree() {
		return new AOTreeModel(this.signersTree);
	}

	@Override
	public List<SignDetails> getAllSignDetails() {
		return this.signDetailsList;
	}

	@Override
	public String getSignFormat() {
		return PDF;
	}

	@Override
	public String getDataLocation() {
        return null;
	}

	private void createSignDetails(final byte [] data) throws Exception {
    	try {

        	PdfReader pdfReader = null;
        	try {
        		pdfReader = new PdfReader(data);
        	}
        	catch (final BadPasswordException e) {
        		LOGGER.info(
    				"El PDF necesita contrasena. Se devolvera el arbol vacio: " + e //$NON-NLS-1$
    			);
        		throw e;
        	}
        	catch (final Exception e) {
        		LOGGER.severe("No se ha podido leer el PDF, se devolvera un arbol vacio: " + e); //$NON-NLS-1$
        		throw e;
        	}

        	AcroFields af = null;
        	try {
        		af = pdfReader.getAcroFields();
        	}
        	catch (final Exception e) {
        		LOGGER.severe("No se ha podido obtener la informacion de los firmantes del PDF, se devolvera un arbol vacio: " + e); //$NON-NLS-1$
        	}

        	for (final String signatureName : af.getSignatureNames()) {

        		// Comprobamos si es una firma o un sello
        		final PdfDictionary pdfDictionary = af.getSignatureDictionary(signatureName);
        		if (AOPDFSigner.PDFNAME_ETSI_RFC3161.equals(pdfDictionary.get(PdfName.SUBFILTER)) || AOPDFSigner.PDFNAME_DOCTIMESTAMP.equals(pdfDictionary.get(PdfName.SUBFILTER))) {
        			// Ignoramos los sellos
        			continue;
        		}

        		final PAdESSignDetails signDetails = buildSignDetails(signatureName, data, pdfDictionary, af);
        		//final List<SignValidity> validity = ValidatePdfSignature.validate(data);
    			//signDetails.setValidityResult(validity);
    			this.signDetailsList.add(signDetails);

        	}

    	}
    	catch (final Exception e) {
    		throw new AOInvalidFormatException("No se ha podido cargar el documento XML de firmas", e); //$NON-NLS-1$
    	}
	}

	private PAdESSignDetails buildSignDetails(final String signName, final byte[] documentData,
			final PdfDictionary signPdfDictionary, final AcroFields af) throws Exception {

		final PAdESSignDetails padesSignDetails = new PAdESSignDetails();
		final String format = SignatureFormatDetectorPades.resolvePDFFormat(documentData);
		padesSignDetails.setFormat(format);

		PdfPKCS7 pkcs7 = null;
		try {
			pkcs7 = af.verifySignature(signName);
		} catch (final Exception e) {
			LOGGER.log(Level.SEVERE, "El PDF contiene una firma corrupta o con un formato desconocido (" + //$NON-NLS-1$
					signName + ")", //$NON-NLS-1$
					e);
			throw e;
		}

		// Obtenemos el algoritmo de firma
		final String digestAlgorithm = pkcs7.getDigestAlgorithm();
		if (digestAlgorithm != null) {
			padesSignDetails.setAlgorithm(digestAlgorithm);
		}

		// Obtenemos el firmante y lo agregamos al arbol
		this.signersTree.add(new AOTreeNode(AOUtil.getCN(pkcs7.getSigningCertificate())));

		final CertificateDetails certDetails = new CertificateDetails(pkcs7.getSigningCertificate());
		final List <CertificateDetails> certDetailsList = new ArrayList<CertificateDetails>();
		certDetailsList.add(certDetails);
		padesSignDetails.setSigners(certDetailsList);

		final Map<String, String> metadataMap = new HashMap<String, String>();
		final PdfString reason = signPdfDictionary.getAsString(PdfName.REASON);
		if (reason != null) {
			metadataMap.put(SignDetailsFormatter.SIGN_REASON_METADATA, reason.toString());
		}
		final PdfString location = signPdfDictionary.getAsString(PdfName.LOCATION);
		if (location != null) {
			metadataMap.put(SignDetailsFormatter.LOCATION_METADATA, location.toString());
		}
		final PdfString contactInfo = signPdfDictionary.getAsString(PdfName.CONTACTINFO);
		if (contactInfo != null) {
			metadataMap.put(SignDetailsFormatter.CONTACT_INFO_METADATA, contactInfo.toString());
		}
		padesSignDetails.setMetadata(metadataMap);

		//Validamos la firma
		final List<SignValidity> listValidity = ValidatePdfSignature.validateSign(signName, af);
		padesSignDetails.setValidityResult(listValidity);

		//TODO: Obtener datos de politica

		return padesSignDetails;
	}

}
