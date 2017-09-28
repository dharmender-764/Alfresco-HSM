package com.iprosonic.hsm.integration.docusign.action;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionServiceException;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.lang.StringUtils;

import com.iprosonic.hsm.integration.docusign.service.DocuSignService;
import com.iprosonic.hsm.integration.model.HSMUser;
import com.itextpdf.text.pdf.PdfReader;

public class DocuSignActionExecuter extends ActionExecuterAbstractBase {

	private static final String PARAM_PARTITION_NAME = "partitionName";

	private static final String PARAM_PARTITION_PASSWORD = "partitionPassword";

	private static final String PARAM_CERT_LABEL = "certLabel";

	private static final String PARAM_PAGE_NOS = "pageNos";
	
	private static final String PARAM_SIGN_POSITION = "signPosition";

	private static final String IPRO_NS = "http://www.iprosonic.com/model/content/1.0";

	private QName ASPECT_DOCU_SIGN = QName.createQName(IPRO_NS, "docuSign");

	private QName PROP_DIGITALLY_SIGNED = QName.createQName(IPRO_NS, "digitallySigned");

	private DocuSignService docuSignService;
	
	private NodeService nodeService;
	
	private ContentService contentService;

	private CheckOutCheckInService checkOutCheckInService;
	
	private AuthenticationService authenticationService;
	
	public void setDocuSignService(DocuSignService docuSignService) {
		this.docuSignService = docuSignService;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void setCheckOutCheckInService(CheckOutCheckInService checkOutCheckInService) {
		this.checkOutCheckInService = checkOutCheckInService;
	}

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@Override
	protected void executeImpl(Action action, NodeRef nodeRef) {
		System.out.println("Sign document action param values: " + action.getParameterValues());
		String partitionName = (String) action.getParameterValue(PARAM_PARTITION_NAME);
		String partitionPwd = (String) action.getParameterValue(PARAM_PARTITION_PASSWORD);
		String certLabel = (String) action.getParameterValue(PARAM_CERT_LABEL);
		if (StringUtils.isEmpty(partitionName) || StringUtils.isEmpty(partitionPwd) || StringUtils.isEmpty(certLabel)) {
			System.err.println("Invalid request, Please provide all the required details");
			throw new ActionServiceException("Invalid request, Please provide all the required details to sign document");
		}
		String pageNos = (String) action.getParameterValue(PARAM_PAGE_NOS);
		String signPosition = (String) action.getParameterValue(PARAM_SIGN_POSITION);
		
		String username = authenticationService.getCurrentUserName();
		HSMUser hsmUser = new HSMUser(username, partitionName, partitionPwd, certLabel);
		
		if (nodeService.exists(nodeRef)) {
			boolean alreadySigned = false;
			/*if (nodeService.hasAspect(nodeRef, ASPECT_DOCU_SIGN)) {
				Serializable digitallySignedValue = nodeService.getProperty(nodeRef, PROP_DIGITALLY_SIGNED);
				if (digitallySignedValue != null) {
					System.out.println("Document already signed: " + digitallySignedValue.toString());
					alreadySigned = Boolean.valueOf(digitallySignedValue.toString());
				}
			}*/
			
			if (alreadySigned) {
				System.out.println("node: " + nodeRef + " already signed, can not sign it again!");
			} else {
				System.out.println("Reading pdf node: " + nodeRef + " to alfresco tmp file");
				String documentName = nodeService.getProperty(nodeRef, ContentModel.PROP_NAME).toString();
				String alfTempDir = TempFileProvider.getTempDir().getAbsolutePath();
				File pdfDocument = new File(alfTempDir + File.separator + System.currentTimeMillis() + File.separator + documentName);
				pdfDocument.getParentFile().mkdirs();
				ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
				contentReader.getContent(pdfDocument);
				System.out.println("Reading pdf node: " + nodeRef + " to alfresco tmp file: " + pdfDocument + " success!");

				List<Integer> pageNumbersToSign = getPageNumbersToSign(pageNos, pdfDocument);
				File signPdfDocument = docuSignService.signDocument(pdfDocument, hsmUser, pageNumbersToSign, signPosition);
				
				if (signPdfDocument != null) {
					System.out.println("pdf node: " + nodeRef + " signed successful with certlabel: " + certLabel);
					NodeRef workingCopyNodeRef = checkOutCheckInService.checkout(nodeRef);
					
					Map<String, Serializable> versionProps = new HashMap<>();
					versionProps.put(VersionModel.PROP_DESCRIPTION, "Document digitally signed");
					versionProps.put(VersionModel.PROP_VERSION_TYPE, VersionType.MAJOR);
					
					try {
						System.out.println("writing signed pdf to node: " + nodeRef + " started with certlabel: " + certLabel);
						ContentWriter contentWriter = contentService.getWriter(workingCopyNodeRef, ContentModel.PROP_CONTENT, true);
						contentWriter.putContent(signPdfDocument);
						NodeRef checkinNodeRef = checkOutCheckInService.checkin(workingCopyNodeRef, versionProps);
						
						Map<QName, Serializable> aspectProperties = new HashMap<>();
						aspectProperties.put(PROP_DIGITALLY_SIGNED, true);
						nodeService.addAspect(checkinNodeRef, ASPECT_DOCU_SIGN, aspectProperties);
						System.out.println("writing signed pdf to node: " + nodeRef + " success with certlabel: " + certLabel);
					} catch (Exception e) {
						e.printStackTrace();
						System.err.println("Error putting signed pdf cotent back to node: " + nodeRef + " for certlabel: " + certLabel);
						checkOutCheckInService.cancelCheckout(workingCopyNodeRef);
					}
				}
			}
		} else {
			System.out.println("Can not sign document, node: " + nodeRef + " does not exist.");
		}
	}

	private List<Integer> getPageNumbersToSign(String pageNos, File pdfDocument) {
		List<Integer> pageNumbersToSign = new ArrayList<>();
		try {
			if (StringUtils.isEmpty(pageNos)) {
				System.out.println("pageNos is empty, will sign only first page");
				pageNumbersToSign.add(1);
			} else if ("all".equalsIgnoreCase(pageNos)) {
				try {
					PdfReader reader = new PdfReader(pdfDocument.getAbsolutePath());
					int numberOfPages = reader.getNumberOfPages();
					
					System.out.println("pageNos is all, will sign 1 to " + numberOfPages + " pages");
					for (int i = 1; i <= numberOfPages; i++) {
						pageNumbersToSign.add(i);
					}
					reader.close();
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Error reading no of pages from pdf document: " + pdfDocument + " for signing all pages");
				}
			} else {
				for (String pageNoRange : pageNos.split(",")) {
					pageNoRange = pageNoRange.trim();
					if (pageNoRange.indexOf("-") != -1) {
						String start = pageNoRange.split("-")[0].trim();
						Integer startPage = Integer.parseInt(start);
						
						String end = pageNoRange.split("-")[1].trim();
						Integer endPage = Integer.parseInt(end);
						if (startPage > endPage) {
							throw new Exception("Start page can not be greater than end page in page range: " + pageNoRange);
						}
						
						for (Integer pageNo = startPage; pageNo <= endPage; pageNo++) {
							pageNumbersToSign.add(pageNo);
						}
					} else {
						Integer pageNo = Integer.parseInt(pageNoRange);
						pageNumbersToSign.add(pageNo);
					}
				}
				System.out.println("pageNos is: " + pageNos + ", will sign " + pageNumbersToSign + " pages");
			}
		} catch (Exception e) {
			System.err.println("Invalid request, Please provide all the required details: " + e.getMessage());
			throw new ActionServiceException("Invalid request, Incorrect page no to sign: " + e.getMessage());
		}
		return pageNumbersToSign;
	}

	@Override
	protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
		for (String s : new String[]{PARAM_PARTITION_NAME, PARAM_PARTITION_PASSWORD, PARAM_CERT_LABEL, PARAM_PAGE_NOS, PARAM_SIGN_POSITION}) {
            paramList.add(new ParameterDefinitionImpl(s, DataTypeDefinition.TEXT, true, getParamDisplayLabel(s)));
        }
	}

}
