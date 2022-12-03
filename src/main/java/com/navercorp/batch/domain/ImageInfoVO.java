package com.navercorp.batch.domain;

public class ImageInfoVO {
	private String imageKey;			// var : 지도API를 호출하여 리턴받은 PanoramaImage의 키값을 저장하는 변수 ( {bzstNo_panoTypeCd} 형식으로 사용 ) 
	private int imageUsageKey;			// var : 지도API를 호출하여 리턴받은 PanoramaImage의 용도(answer, exam) 키값을 저장하는 변수
										//       (용도에 대한 정보는 image_usage_type Table에 저장되어 있으며 해당 Table의 primaryKey를 저장하는 변수)
	private int imageTypeKey;			// var : 지도API를 호출하여 리턴받은 PanoramaImage의 타입(school, bridge..) 키값을 저자하는 변수
										//   	 (타입에 대한 정보는 image_type Table에 저장되어 있으며 해당 Table의 primaryKey를 저장하는 변수)
	private int bzstNo;					// var : 지도API를 호출하여 리턴받은 PanoramaImage의 bzstNo 값을 저장하는 변수 
	private String panoTypeCd;			// var : 지도API를 호출하여 리턴받은 PanoramaImage의 panoTypeCd 값을 저장하는 변수
	private String b64;					// var : 지도API를 호출하여 리턴받은 PanoramaImage의 Base64 정보를 저장하는 변수
	
	/*
	 * < 지도API 사용방식 >
	 *  지도API에 타입(type)과 개수(limit)를 파라미터로 요청하면 PanoramaImage에 대한 bzstNo, panoTypeCd값을 리턴받고,
	 *  bzstNo, panoTypeCd, width, height를 파라미터로 요청하면 해당 PanoramaImage를 리턴받는 형식 
	 */
	
	public String getImageKey() {
		return imageKey;
	}
	public void setImageKey(String imageKey) {
		this.imageKey = imageKey;
	}
	public int getImageUsageKey() {
		return imageUsageKey;
	}
	public void setImageUsageKey(int imageUsageKey) {
		this.imageUsageKey = imageUsageKey;
	}
	public int getImageTypeKey() {
		return imageTypeKey;
	}
	public void setImageTypeKey(int imageTypeKey) {
		this.imageTypeKey = imageTypeKey;
	}
	public int getBzstNo() {
		return bzstNo;
	}
	public void setBzstNo(int bzstNo) {
		this.bzstNo = bzstNo;
	}
	public String getPanoTypeCd() {
		return panoTypeCd;
	}
	public void setPanoTypeCd(String panoTypeCd) {
		this.panoTypeCd = panoTypeCd;
	}
	public String getB64() {
		return b64;
	}
	public void setB64(String b64) {
		this.b64 = b64;
	}
}
