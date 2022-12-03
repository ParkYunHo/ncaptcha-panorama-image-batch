package com.navercorp.batch.domain;

public class ImageAllTypeCntVO {
	private String usageName;				// var : MySQL에 저장된 PanoramaImage의 용도(answer,exam)명을 저장하는 변수
	private String typeName;				// var : MySQL에 저장된 PanoramaImage의 타입(school, bridge..)명을 저장하는 변수
	private int panoramaImagePoolSize;		// var : MySQL에 저장된 PanoramaImagePool의 개수를 저장하는 변수
	
	public String getUsageName() {
		return usageName;
	}
	public void setUsageName(String usageName) {
		this.usageName = usageName;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	public int getPanoramaImagePoolSize() {
		return panoramaImagePoolSize;
	}
	public void setPanoramaImagePoolSize(int panoramaImagePoolSize) {
		this.panoramaImagePoolSize = panoramaImagePoolSize;
	}
}
