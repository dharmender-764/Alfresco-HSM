package com.iprosonic.hsm.docusign.evaluator;

import org.alfresco.web.evaluator.BaseEvaluator;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

public class DocuSignEvaluator extends BaseEvaluator {

	@Override
	public boolean evaluate(JSONObject jsonObject) {
		try {
			boolean isPdfDocument = false;
			Object content = getProperty(jsonObject, "cm:content");
			if (content != null) {
				String contentStr = content.toString();
				String mimetypeStr = contentStr.substring(contentStr.indexOf("mimetype=" + 10));
				String mimetype = mimetypeStr.substring(0, mimetypeStr.indexOf("|"));
				
				if (!StringUtils.isEmpty(mimetype)) {
					isPdfDocument = "application/pdf".equalsIgnoreCase(mimetype);
				}
			}
			
			if (!isPdfDocument) {
				String docName = getProperty(jsonObject, "cm:name").toString();
				isPdfDocument = docName.toLowerCase().endsWith(".pdf");
			}

			if (isPdfDocument) {
				Object digitallySigned = getProperty(jsonObject, "ipm:digitallySigned");
				if (digitallySigned != null && Boolean.valueOf(digitallySigned.toString())) {
					return true;
				}
			}
        } catch (Exception err) {
            throw new RuntimeException("JSONException whilst running action evaluator: " + err.getMessage());
        }
		return false;
	}

}
