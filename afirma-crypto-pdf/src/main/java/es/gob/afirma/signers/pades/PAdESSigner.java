package es.gob.afirma.signers.pades;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.Jpeg;
import com.lowagie.text.Rectangle;
import com.lowagie.text.exceptions.BadPasswordException;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDeveloperExtension;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;

import es.gob.afirma.core.AOCancelledOperationException;
import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AdESPolicy;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.signers.cades.GenCAdESEPESSignedData;
import es.gob.afirma.signers.pkcs7.AOAlgorithmID;
import es.gob.afirma.signers.pkcs7.P7ContentSignerParameters;
import es.gob.afirma.signers.tsp.pkcs7.CMSTimestamper;
import es.gob.afirma.signers.tsp.pkcs7.CMSTimestamper.TsaRequestExtension;

final class PAdESSigner {

    private static final int CSIZE = 8000;

	private static final String PDF_OID = "1.2.826.0.1089.1.5"; //$NON-NLS-1$

    private static final Logger LOGGER = Logger.getLogger("es.gob.afirma");  //$NON-NLS-1$

	private static final String PDF_DESC = "Documento en formato PDF"; //$NON-NLS-1$

    /** Referencia a la &uacute;ltima p&aacute;gina del documento PDF. */
    public static final int LAST_PAGE = -666;

    /** Devuelve la posici&oacute;n de la p&aacute;gina en donde debe agregarse
     * la firma. La medida de posicionamiento es el p&iacute;xel y se cuenta en
     * el eje horizontal de izquierda a derecha y en el vertical de abajo a
     * arriba. */
    private static Rectangle getSignaturePositionOnPage(final Properties extraParams) {
    	if (extraParams.getProperty("signaturePositionOnPageLowerLeftX") != null && //$NON-NLS-1$
    		extraParams.getProperty("signaturePositionOnPageLowerLeftY") != null && //$NON-NLS-1$
			extraParams.getProperty("signaturePositionOnPageUpperRightX") != null && //$NON-NLS-1$
			extraParams.getProperty("signaturePositionOnPageUpperRightY") != null //$NON-NLS-1$
		) {
	        try {
	            return new Rectangle(Integer.parseInt(extraParams.getProperty("signaturePositionOnPageLowerLeftX")), //$NON-NLS-1$
	                                 Integer.parseInt(extraParams.getProperty("signaturePositionOnPageLowerLeftY")), //$NON-NLS-1$
	                                 Integer.parseInt(extraParams.getProperty("signaturePositionOnPageUpperRightX")), //$NON-NLS-1$
	                                 Integer.parseInt(extraParams.getProperty("signaturePositionOnPageUpperRightY")) //$NON-NLS-1$
	            );
	        }
	        catch (final Exception e) {
	        	LOGGER.severe("Se ha indicado una posicion de firma invalida: " + e); //$NON-NLS-1$
	        }
    	}
    	return null;
    }

    private static com.lowagie.text.Image getRubricImage(final String imagebase64Encoded) {
    	if (imagebase64Encoded == null || "".equals(imagebase64Encoded)) { //$NON-NLS-1$
    		return null;
    	}
    	final byte[] image;
    	try {
			image = Base64.decode(imagebase64Encoded);
		}
    	catch (final Exception e) {
    		LOGGER.severe("Se ha proporcionado una imagen de rubrica que no esta codificada en Base64: " + e); //$NON-NLS-1$
			return null;
		}
    	try {
			return new Jpeg(image);
		}
    	catch (final Exception e) {
    		LOGGER.severe("Se ha proporcionado una imagen de rubrica que no esta codificada en JPEG: " + e); //$NON-NLS-1$
			return null;
		}
    }

	static byte[] signPDF(final PrivateKey key,
						  final java.security.cert.Certificate[] certChain,
	                      final byte[] inPDF,
	                      final Properties extraParams,
	                      final String algorithm) throws IOException,
	                                                     AOException,
	                                                     DocumentException,
	                                                     NoSuchAlgorithmException,
	                                                     CertificateException {

		// *********************************************************************************************************************
		// **************** LECTURA PARAMETROS ADICIONALES *********************************************************************
		// *********************************************************************************************************************

		// Imagen de la rubrica
	    final Image rubric = getRubricImage(extraParams.getProperty("signatureRubricImage")); //$NON-NLS-1$

	    // Usar hora y fecha del sistema
	    final boolean useSystemDateTime = Boolean.parseBoolean(extraParams.getProperty("applySystemDate", Boolean.TRUE.toString())); //$NON-NLS-1$

	    // Motivo de la firma
	    final String reason = extraParams.getProperty("signReason"); //$NON-NLS-1$

	    // Nombre del campo de firma preexistente en el PDF a usar
	    final String signatureField = extraParams.getProperty("signatureField"); //$NON-NLS-1$

	    // Lugar de realizacion de la firma
	    final String signatureProductionCity = extraParams.getProperty("signatureProductionCity"); //$NON-NLS-1$

	    // Datos de contacto (correo electronico) del firmante
	    final String signerContact = extraParams.getProperty("signerContact"); //$NON-NLS-1$

	    // Pagina donde situar la firma visible
	    int page = LAST_PAGE;
	    try {
	        page = Integer.parseInt(extraParams.getProperty("signaturePage")); //$NON-NLS-1$
	    }
	    catch (final Exception e) {
	        /* Se deja la pagina tal y como esta */
	    }

	    // Nombre del subfiltro de firma en el diccionario PDF
	    final String signatureSubFilter = extraParams.getProperty("signatureSubFilter"); //$NON-NLS-1$

	    // ******************
	    // ** Adjuntos ******

	    // Contenido a adjuntar (en Base64)
	    final String b64Attachment = extraParams.getProperty("attach"); //$NON-NLS-1$

	    // Nombre que se pondra al fichero adjunto en el PDF
	    final String attachmentFileName = extraParams.getProperty("attachFileName"); //$NON-NLS-1$

	    // Descripcion del adjunto
	    final String attachmentDescription = extraParams.getProperty("attachDescription"); //$NON-NLS-1$

	    // ** Fin Adjuntos **
		// ******************

	    // *****************************
	    // **** Texto firma visible ****

	    // Texto
	    final String layer2Text = extraParams.getProperty("layer2Text"); //$NON-NLS-1$

	    // Tipo de letra
	    int layer2FontFamily;
	    try {
	    	layer2FontFamily = extraParams.getProperty("layer2FontFamily") != null ? //$NON-NLS-1$
    			Integer.parseInt(extraParams.getProperty("layer2FontFamily")) : //$NON-NLS-1$
    				-1;
	    }
	    catch(final Exception e) {
	    	layer2FontFamily = -1;
	    }

	    // Tamano del tipo de letra
	    int layer2FontSize;
	    try {
	    	layer2FontSize = extraParams.getProperty("layer2FontSize") != null ? //$NON-NLS-1$
    			Integer.parseInt(extraParams.getProperty("layer2FontSize")) : //$NON-NLS-1$
    				-1;
	    }
	    catch(final Exception e) {
	    	layer2FontSize = -1;
	    }

	    // Estilo del tipo de letra
	    int layer2FontStyle;
	    try {
	    	layer2FontStyle = extraParams.getProperty("layer2FontStyle") != null ? //$NON-NLS-1$
    			Integer.parseInt(extraParams.getProperty("layer2FontStyle")) : //$NON-NLS-1$
    				-1;
	    }
	    catch(final Exception e) {
	    	layer2FontStyle = -1;
	    }

	    // Color del tipo de letra
	    final String layer2FontColor = extraParams.getProperty("layer2FontColor"); //$NON-NLS-1$

	    // ** Fin texto firma visible **
	    // *****************************

	    // Contrasena del propietario del PDF
	    String ownerPassword = extraParams.getProperty("ownerPassword"); //$NON-NLS-1$

	    // Contrasena del usuario del PDF
	    final String userPassword =  extraParams.getProperty("userPassword"); //$NON-NLS-1$

		// *********************************************************************************************************************
		// **************** FIN LECTURA PARAMETROS ADICIONALES *****************************************************************
		// *********************************************************************************************************************

	    PdfReader pdfReader;
	    try {
	        if (ownerPassword != null) {
	        	pdfReader = new PdfReader(inPDF, ownerPassword.getBytes());
	        }
	        else if (userPassword != null) {
	        	pdfReader = new PdfReader(inPDF, userPassword.getBytes());
	        }
	        else {
	        	pdfReader = new PdfReader(inPDF);
	        }
	    }
	    catch (final BadPasswordException e) {
	        // Comprobamos que el signer esta en modo interactivo, y si no lo
	        // esta no pedimos contrasena por dialogo, principalmente para no interrumpir un firmado por lotes
	        // desatendido
	        if (Boolean.TRUE.toString().equalsIgnoreCase(extraParams.getProperty("headLess"))) { //$NON-NLS-1$
	            throw new BadPdfPasswordException(e);
	        }
	        // La contrasena que nos han proporcionada no es buena o no nos
	        // proporcionaron ninguna
	        ownerPassword = new String(
	    		AOUIFactory.getPassword(
					ownerPassword == null ? PDFMessages.getString("AOPDFSigner.0") : PDFMessages.getString("AOPDFSigner.1"), //$NON-NLS-1$ //$NON-NLS-2$
					null
				)
			);
	        try {
	            pdfReader = new PdfReader(inPDF, ownerPassword.getBytes());
	        }
	        catch (final BadPasswordException e2) {
	            throw new BadPdfPasswordException(e2);
	        }
	    }
	    catch (final IOException e) {
	    	throw new InvalidPdfException(e);
		}

	    if (pdfReader.getCertificationLevel() != PdfSignatureAppearance.NOT_CERTIFIED && !Boolean.parseBoolean(extraParams.getProperty("allowSigningCertifiedPdfs"))) { //$NON-NLS-1$
	    	// Si no permitimos dialogos graficos o directamente hemos indicado que no permitimos firmar PDF certificados lanzamos
	    	// una excepcion
	        if (Boolean.parseBoolean(extraParams.getProperty("headLess")) || "false".equalsIgnoreCase(extraParams.getProperty("allowSigningCertifiedPdfs"))) {  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	        	throw new PdfIsCertifiedException();
	        }
	        // En otro caso, perguntamos al usuario
	        if (AOUIFactory.NO_OPTION == AOUIFactory.showConfirmDialog(
	    		null,
	            PDFMessages.getString("AOPDFSigner.8"), //$NON-NLS-1$
	            PDFMessages.getString("AOPDFSigner.9"), //$NON-NLS-1$
	            AOUIFactory.YES_NO_OPTION,
	            AOUIFactory.WARNING_MESSAGE)
	        ) {
	    		throw new AOCancelledOperationException("El usuario no ha permitido la firma de un PDF certificado"); //$NON-NLS-1$
	        }
	    }

	    // Los derechos van firmados por Adobe, y como desde iText se invalidan
	    // es mejor quitarlos
	    pdfReader.removeUsageRights();

	    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

	    // Activar el atributo de "agregar firma" (cuarto parametro del metodo
	    // "PdfStamper.createSignature") hace que se cree una nueva revision del
	    // documento y evita que las firmas previas queden invalidadas. Sin embargo, este
	    // exige que el PDF no incorpore ningun error, asi que lo mantendremos desactivado
	    // para la primera firma y activado para las subsiguientes. Un error incorporado
	    // en un PDF erroneo puede quedar subsanado en su version firmada, haciendo
	    // posible incorporar nuevas firmas agregando revisiones del documento.
	    final PdfStamper stp;
	    try {
	        stp = PdfStamper.createSignature(
	              pdfReader, // PDF de entrada
	              baos, // Salida
	              '\0', // Mantener version
	              null, // No crear temporal
	              pdfReader.getAcroFields().getSignatureNames().size() > 0 // Si hay mas firmas, creo una revision
	        );
	    }
	    catch(final BadPasswordException e) {
	    	throw new PdfIsPasswordProtectedException(e);
	    }

	    // Aplicamos todos los atributos de firma
	    final PdfSignatureAppearance sap = stp.getSignatureAppearance();
	    stp.setFullCompression();
	    sap.setAcro6Layers(true);

	    // PAdES parte 3 seccion 4.7 - Habilitacion para LTV
	    stp.getWriter().addDeveloperExtension(new PdfDeveloperExtension(
			new PdfName("ESIC"), //$NON-NLS-1$
			PdfWriter.PDF_VERSION_1_7,
			1
		));

	    // Adjuntos
	    if (b64Attachment != null && attachmentFileName != null) {
	    	byte[] attachment = null;
	    	try {
	    		attachment = Base64.decode(b64Attachment);
	    	}
	    	catch(final IOException e) {
	    		LOGGER.warning("Se ha indicado un adjunto, pero no estaba en formato Base64, se ignorara : " + e); //$NON-NLS-1$
	    	}
		    if (attachment != null) {
		    	stp.getWriter().addFileAttachment(attachmentDescription, attachment, null, attachmentFileName);
		    }
	    }

	    // iText antiguo
	    sap.setRender(PdfSignatureAppearance.SignatureRenderDescription);
	    // En iText nuevo seria "sap.setRenderingMode(PdfSignatureAppearance.RenderingMode.NAME_AND_DESCRIPTION);"

	    if (reason != null) {
	        sap.setReason(reason);
	    }
	    if (useSystemDateTime) {
	        sap.setSignDate(new GregorianCalendar());
	    }
	    if (pdfReader.isEncrypted() && (ownerPassword != null || userPassword != null)) {
	        if (Boolean.TRUE.toString().equalsIgnoreCase(extraParams.getProperty("avoidEncryptingSignedPdfs"))) { //$NON-NLS-1$
	            LOGGER.info(
	                "Aunque el PDF original estaba encriptado no se encriptara el PDF firmado (se establecio el indicativo 'avoidEncryptingSignedPdfs')" //$NON-NLS-1$
	            );
	        }
	        else {
	            LOGGER.info(
	                "El PDF original estaba encriptado, se intentara encriptar tambien el PDF firmado" //$NON-NLS-1$
	            );
	            try {
	                stp.setEncryption(
	            		ownerPassword != null ? ownerPassword.getBytes() : null,
	    				userPassword != null ? userPassword.getBytes() : null,
	            		pdfReader.getPermissions(),
	            		pdfReader.getCryptoMode()
	        		);
	            }
	            catch (final DocumentException de) {
	                LOGGER.warning(
	                   "No se ha podido cifrar el PDF destino, se escribira sin contrasena: " + de //$NON-NLS-1$
	                );
	            }
	        }
	    }

	    // Pagina en donde se imprime la firma
	    if (page == LAST_PAGE) {
	        page = pdfReader.getNumberOfPages();
	    }

	    // Posicion de la firma
	    final Rectangle signaturePositionOnPage = getSignaturePositionOnPage(extraParams);
	    if (signaturePositionOnPage != null && signatureField == null) {
	        sap.setVisibleSignature(signaturePositionOnPage, page, null);
	    }
	    else if (signatureField != null) {
	        sap.setVisibleSignature(signatureField);
	    }

	    // Localizacion en donde se produce la firma
	    if (signatureProductionCity != null) {
	        sap.setLocation(signatureProductionCity);
	    }

	    // Contacto del firmante
	    if (signerContact != null) {
	        sap.setContact(signerContact);
	    }

	    // Rubrica de la firma
	    if (rubric != null) {
	        sap.setImage(rubric);
	        sap.setLayer2Text(""); //$NON-NLS-1$
	        sap.setLayer4Text(""); //$NON-NLS-1$
	    }

	    // Texto en las capas
	    else {

	    	// Capa 2
	    	if (layer2Text != null) {

		    	sap.setLayer2Text(layer2Text);

			    final int layer2FontColorR;
			    final int layer2FontColorG;
			    final int layer2FontColorB;
			    if ("black".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 0;
				    layer2FontColorG = 0;
				    layer2FontColorB = 0;
			    }
			    else if ("white".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 255;
				    layer2FontColorG = 255;
				    layer2FontColorB = 255;
			    }
			    else if ("lightGray".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 255;
				    layer2FontColorG = 255;
				    layer2FontColorB = 255;
			    }
			    else if ("lightGray".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 192;
				    layer2FontColorG = 192;
				    layer2FontColorB = 192;
			    }
			    else if ("gray".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 128;
				    layer2FontColorG = 128;
				    layer2FontColorB = 128;
			    }
			    else if ("darkGray".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 64;
				    layer2FontColorG = 64;
				    layer2FontColorB = 64;
			    }
			    else if ("red".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 255;
				    layer2FontColorG = 0;
				    layer2FontColorB = 0;
			    }
			    else if ("pink".equalsIgnoreCase(layer2FontColor)) { //$NON-NLS-1$
			    	layer2FontColorR = 255;
				    layer2FontColorG = 175;
				    layer2FontColorB = 175;
			    }
			    else if (layer2FontColor == null) {
			    	layer2FontColorR = 0;
				    layer2FontColorG = 0;
				    layer2FontColorB = 0;
			    }
			    else {
			    	LOGGER.warning("No se soporta el color '" + layer2FontColor + "' para el texto de la capa 4, se usara negro"); //$NON-NLS-1$ //$NON-NLS-2$
			    	layer2FontColorR = 0;
				    layer2FontColorG = 0;
				    layer2FontColorB = 0;
			    }

		    	sap.setLayer2Font(
	    			new com.lowagie.text.Font(
		    			// Family (COURIER = 0, HELVETICA = 1, TIMES_ROMAN = 2, SYMBOL = 3, ZAPFDINGBATS = 4)
		    			layer2FontFamily == -1 ? 0 : layer2FontFamily,
						// Size (DEFAULTSIZE = 12)
		    			layer2FontSize == -1 ? 12 : layer2FontSize,
						// Style (NORMAL = 0, BOLD = 1, ITALIC = 2, BOLDITALIC = 3, UNDERLINE = 4, STRIKETHRU = 8)
						layer2FontStyle == -1 ? com.lowagie.text.Font.NORMAL : layer2FontStyle,
		    			// Color
		    			new Color(
	    					layer2FontColorR,
	    				    layer2FontColorG,
	    				    layer2FontColorB
						)
					)
				);
	    	}
	    }

	    sap.setCrypto(null, certChain, null, null);

	    final PdfSignature dic = new PdfSignature(
			PdfName.ADOBE_PPKLITE,
			signatureSubFilter != null && !"".equals(signatureSubFilter) ? new PdfName(signatureSubFilter) : PdfName.ADBE_PKCS7_DETACHED //$NON-NLS-1$
		);
	    if (sap.getSignDate() != null) {
	        dic.setDate(new PdfDate(sap.getSignDate()));
	    }
	    dic.setName(PdfPKCS7.getSubjectFields((X509Certificate) certChain[0]).getField("CN")); //$NON-NLS-1$
	    if (sap.getReason() != null) {
	        dic.setReason(sap.getReason());
	    }
	    if (sap.getLocation() != null) {
	        dic.setLocation(sap.getLocation());
	    }
	    if (sap.getContact() != null) {
	        dic.setContact(sap.getContact());
	    }

	    sap.setCryptoDictionary(dic);

	    // Reservamos el espacio necesario en el PDF para insertar la firma
	    final HashMap<PdfName, Integer> exc = new HashMap<PdfName, Integer>();
	    exc.put(PdfName.CONTENTS, Integer.valueOf(CSIZE * 2 + 2));

	    sap.preClose(exc);

	    // ********************************************************************************
	    // **************** CALCULO DEL SIGNED DATA ***************************************
	    // ********************************************************************************

	    // La norma PAdES establece que si el algoritmo de huella digital es SHA1 debe usarse SigningCertificateV2, y en cualquier
	    // otro caso deberia usarse SigningCertificateV2
	    boolean signingCertificateV2;
	    if (extraParams.containsKey("signingCertificateV2")) { //$NON-NLS-1$
	    	signingCertificateV2 = Boolean.parseBoolean(extraParams.getProperty("signingCertificateV2")); //$NON-NLS-1$
	    }
	    else {
	    	signingCertificateV2 = !"SHA1".equals(AOSignConstants.getDigestAlgorithmName(algorithm));	 //$NON-NLS-1$
	    }

		byte[] completeCAdESSignature = GenCAdESEPESSignedData.generateSignedData(
	        new P7ContentSignerParameters(inPDF, algorithm),
	        true, // omitContent
	        new AdESPolicy(extraParams),
	        signingCertificateV2,
	        key,
	        certChain,
	        MessageDigest.getInstance(AOSignConstants.getDigestAlgorithmName(algorithm)).digest(AOUtil.getDataFromInputStream(sap.getRangeStream())),
	        true, // Modo PAdES
	        PDF_OID,
	        extraParams.getProperty("contentDescription") != null ? extraParams.getProperty("contentDescription") : PDF_DESC //$NON-NLS-1$ //$NON-NLS-2$
	    );

	    //***************** SELLO DE TIEMPO ****************
	    final String tsa = extraParams.getProperty("tsaURL"); //$NON-NLS-1$
	    URI tsaURL;
	    if (tsa != null) {
	        try {
	            tsaURL = new URI(tsa);
	        }
	        catch(final Exception e) {
	            LOGGER.warning("Se ha indicado una URL de TSA invalida (" + tsa + "), no se anadira sello de tiempo: " + e); //$NON-NLS-1$ //$NON-NLS-2$
	            tsaURL = null;
	        }
	        if (tsaURL != null) {
	            final String tsaPolicy = extraParams.getProperty("tsaPolicy"); //$NON-NLS-1$
	            if (tsaPolicy == null) {
	                LOGGER.warning("Se ha indicado una URL de TSA pero no una politica, no se anadira sello de tiempo"); //$NON-NLS-1$
	            }
	            else {
	                final String tsaHashAlgorithm = extraParams.getProperty("tsaHashAlgorithm"); //$NON-NLS-1$
	                completeCAdESSignature = new CMSTimestamper(
	                     !Boolean.FALSE.toString().equalsIgnoreCase(extraParams.getProperty("tsaRequireCert")),  //$NON-NLS-1$
	                     tsaPolicy,
	                     tsaURL,
	                     extraParams.getProperty("tsaUsr"),  //$NON-NLS-1$
	                     extraParams.getProperty("tsaPwd"), //$NON-NLS-1$
	                     extraParams.getProperty("tsaExtensionOid") != null && extraParams.getProperty("tsaExtensionValueBase64") != null ? //$NON-NLS-1$ //$NON-NLS-2$
	                		 new TsaRequestExtension[] {
	                    		 new TsaRequestExtension(
	                				 extraParams.getProperty("tsaExtensionOid"), //$NON-NLS-1$
	                				 Boolean.getBoolean(extraParams.getProperty("tsaExtensionCritical", "false")), //$NON-NLS-1$ //$NON-NLS-2$
	                				 Base64.decode(extraParams.getProperty("tsaExtensionValueBase64")) //$NON-NLS-1$
	            				 )
	                         } :
	            			 null
	                 ).addTimestamp(completeCAdESSignature, AOAlgorithmID.getOID(AOSignConstants.getDigestAlgorithmName(tsaHashAlgorithm != null ? tsaHashAlgorithm : "SHA1"))); //$NON-NLS-1$
	            }
	        }

	    }
	    //************** FIN SELLO DE TIEMPO ****************


	    // ********************************************************************************
	    // *************** FIN CALCULO DEL SIGNED DATA ************************************
	    // ********************************************************************************

	    final byte[] outc = new byte[CSIZE];
	    if (outc.length < completeCAdESSignature.length) {
	        throw new AOException(
	          "La firma generada tiene un tamano (" + completeCAdESSignature.length + ") mayor que el permitido (" + outc.length + ")" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	        );
	    }
	    final PdfDictionary dic2 = new PdfDictionary();
	    System.arraycopy(completeCAdESSignature, 0, outc, 0, completeCAdESSignature.length);
	    dic2.put(PdfName.CONTENTS, new PdfString(outc).setHexWriting(true));

	    sap.close(dic2);

	    return baos.toByteArray();
	}

	public static void main(final String[] args) {
		System.out.println(Integer.toString(1 | 2));
		System.out.println(new com.lowagie.text.Font(
    			// Family (COURIER = 0, HELVETICA = 1, TIMES_ROMAN = 2, SYMBOL = 3, ZAPFDINGBATS = 4)
    			0,
				// Size (DEFAULTSIZE = 12)
    			12,
				// Style (NORMAL = 0, BOLD = 1, ITALIC = 2, BOLDITALIC = 3, UNDERLINE = 4)
				com.lowagie.text.Font.NORMAL,
    			// Color
    			Color.BLACK
			).getColor());
	}
}