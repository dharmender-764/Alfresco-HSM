// create mail action   
var signDocument = actions.create("signDocument");   

signDocument.parameters.partitionName = "par1";   
signDocument.parameters.partitionPassword = "userpin";   
signDocument.parameters.certLabel = "YashKey";   
signDocument.parameters.pageNos = "1";
signDocument.parameters.signPosition = "bottomleft";

signDocument.execute(document);