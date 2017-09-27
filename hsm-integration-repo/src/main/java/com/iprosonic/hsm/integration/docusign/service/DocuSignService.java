package com.iprosonic.hsm.integration.docusign.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.iprosonic.hsm.integration.model.HSMUser;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfDate;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfPKCS7;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignature;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfString;
import com.safenetinc.luna.LunaSlotManager;
import com.safenetinc.luna.provider.LunaProvider;

public class DocuSignService {

	public static LunaSlotManager hsmConnection = LunaSlotManager.getInstance();

	public static void main(String[] args) {
		File pdfDocument = new File("C:/Users/Public/P2P2MUKTA.pdf");
		HSMUser hsmUser = new HSMUser("dhsi", "par1", "userpin", "YashKey");
		new DocuSignService().signDocument(pdfDocument, hsmUser, Arrays.asList(new Integer[] {1}), "bottommiddle");
	}
	
	public File signDocument(File pdfDocument, HSMUser hsmUser, List<Integer> pagesToSign, String signPosition) {
		PrivateKey priv = null;
		Certificate[] chain = null;
		String certLabel = hsmUser.getCertLabel();
		String partitionName = hsmUser.getPartitionName();
		try {
			if (Security.getProvider("LunaProvider") == null) {
				Security.insertProviderAt(new com.safenetinc.luna.provider.LunaProvider(), 2);
			}
	
			Security.addProvider(new LunaProvider());
			hsmConnection.login(partitionName, hsmUser.getPartitionPassword());
	
			KeyStore ks = KeyStore.getInstance("Luna");
			ks.load(null, hsmUser.getPartitionPassword().toCharArray());
	
			priv = (PrivateKey) ks.getKey(certLabel, null);
			chain = ks.getCertificateChain(certLabel);
		} catch (Exception e) {
			System.err.println("Unable to load digital signature from keystore with certLabel: " + certLabel + " and partitioname: " + partitionName +", skipping pdf signing");
			return null;
		}
		
		File signedPdfDocument = null;
		String fileExtension = FilenameUtils.getExtension(pdfDocument.getName());
		for (Integer pageNo : pagesToSign) {
			signedPdfDocument = new File(pdfDocument.getAbsolutePath().replace("." + fileExtension, "_" + pageNo + ".pdf"));
			try {
				PdfReader reader = new PdfReader(pdfDocument.getAbsolutePath());
				FileOutputStream fout = new FileOutputStream(signedPdfDocument);
				PdfStamper stamper = PdfStamper.createSignature(reader, fout, '\0', null, true);
				PdfSignatureAppearance sap = stamper.getSignatureAppearance();
				sap.setReason("Document Digitally signed");
				sap.setLocation("India");
				Calendar cal = Calendar.getInstance();
				Date date = new Date();
				cal.setTime(date);
				sap.setSignDate(cal);
				
				sap.setVisibleSignature(getSignRect(reader, pageNo, signPosition), pageNo, "signature on page: " + pageNo);
	
				sap.setCrypto(priv, chain, null, PdfSignatureAppearance.WINCER_SIGNED);
	
				PdfSignature dic = new PdfSignature(PdfName.ADOBE_PPKLITE, new PdfName("adbe.pkcs7.detached"));
				dic.setReason(sap.getReason());
				dic.setLocation(sap.getLocation());
				dic.setContact(sap.getContact());
				dic.setDate(new PdfDate(sap.getSignDate()));
				sap.setCryptoDictionary(dic);
	
				int contentEstimated = 15000;
				HashMap<PdfName, Integer> exc = new HashMap<PdfName, Integer>();
				exc.put(PdfName.CONTENTS, new Integer(contentEstimated * 2 + 2));
				sap.preClose(exc);
	
				PdfPKCS7 sgn = new PdfPKCS7(priv, chain, null, "SHA1", null, false);
				InputStream data = sap.getRangeStream();
				MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
				byte buf[] = new byte[8192];
				int n;
				while ((n = data.read(buf)) > 0) {
					messageDigest.update(buf, 0, n);
				}
				byte hash[] = messageDigest.digest();
				cal = Calendar.getInstance();
	
				byte sh[] = sgn.getAuthenticatedAttributeBytes(hash, cal, null);
				sgn.update(sh, 0, sh.length);
	
				byte[] encodedSig = sgn.getEncodedPKCS7(hash, cal, null, null);
	
				if (contentEstimated + 2 < encodedSig.length)
					throw new Exception();
	
				byte[] paddedSig = new byte[contentEstimated];
				System.arraycopy(encodedSig, 0, paddedSig, 0, encodedSig.length);
	
				PdfDictionary dic2 = new PdfDictionary();
				dic2.put(PdfName.CONTENTS, new PdfString(paddedSig).setHexWriting(true));
				sap.close(dic2);
			} catch (Exception e) {
				System.err.println("Error while signing the document: " + pdfDocument.getName());
				e.printStackTrace();
			}
			pdfDocument = signedPdfDocument;
		}
		return signedPdfDocument;
	}
	
	private Rectangle getSignRect(PdfReader reader, Integer pageNo, String position) {
		Rectangle cropBox = reader.getCropBox(1);
		float width = 60;
	    float height = 60;
	    float pageWidth = cropBox.getWidth();
	    Rectangle rectangle = null;
	    
	    if ("topleft".equalsIgnoreCase(position)) {
	    	// Top left
	    	rectangle = new Rectangle(cropBox.getLeft(50), cropBox.getTop(50),
	                              cropBox.getLeft(50 + width), cropBox.getTop(50 + height));
	    } else if ("topmiddle".equalsIgnoreCase(position)) {
	    	// Top middle
	    	float llx = (pageWidth/2) - (width/2);
	    	rectangle = new Rectangle(cropBox.getLeft(llx), cropBox.getTop(50),
	                              cropBox.getLeft(llx + width), cropBox.getTop(50 + height));
	    } else if ("topright".equalsIgnoreCase(position)) {
	    	// Top right
	    	rectangle = new Rectangle(cropBox.getRight(width + 50), cropBox.getTop(50),
	                              cropBox.getRight(50), cropBox.getTop(50 + height));
	    } else if ("bottomleft".equalsIgnoreCase(position)) {
	    	// Bottom left
	    	rectangle = new Rectangle(cropBox.getLeft(50), cropBox.getBottom(50),
	                              cropBox.getLeft(50 + width), cropBox.getBottom(50 + height));
	    } else if ("bottommiddle".equalsIgnoreCase(position)) {
	    	// Bottom middle
	    	float llx = (pageWidth/2) - (width/2);
	    	rectangle = new Rectangle(cropBox.getLeft(llx), cropBox.getBottom(50),
	                              cropBox.getLeft(llx + width), cropBox.getBottom(50 + height));
	    } else {
	    	// Default to bottom right
	    	rectangle = new Rectangle(cropBox.getRight(50 + width), cropBox.getBottom(50),
	                              cropBox.getRight(50), cropBox.getBottom(50 + height));
	    }
		return rectangle;
	}

}