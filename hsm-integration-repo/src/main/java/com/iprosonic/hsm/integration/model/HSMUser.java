package com.iprosonic.hsm.integration.model;

public class HSMUser {

	private String username;
	
	private String partitionName;
	
	private String partitionPassword;
	
	private String certLabel;

	public HSMUser() {
		
	}
	
	public HSMUser(String username, String partitionName, String partitionPassword, String certLabel) {
		this.username = username;
		this.partitionName = partitionName;
		this.partitionPassword = partitionPassword;
		this.certLabel = certLabel;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPartitionName() {
		return partitionName;
	}

	public void setPartitionName(String partitionName) {
		this.partitionName = partitionName;
	}

	public String getPartitionPassword() {
		return partitionPassword;
	}

	public void setPartitionPassword(String partitionPassword) {
		this.partitionPassword = partitionPassword;
	}

	public String getCertLabel() {
		return certLabel;
	}

	public void setCertLabel(String certLabel) {
		this.certLabel = certLabel;
	}
	
}
