package com.navercorp.batch.domain;

import java.util.Arrays;
import java.util.List;

public class CommonImageInfo {
	// project environment
	public static final String projectEnvironment = "dev";								// var : Captcha Server가 실행되는 환경을 저장하는 변수 (dev, stage, real)
	public static boolean isAvailableRedis = true; 										// var : Redis Server가 현재 사용가능한지 유무를 저장하는 변수

	// Image Info
	public static final int answerPanoramaImageWidth = 100;								// var : 정답이미지 가로길이 변수
	public static final int answerPanoramaImageHeight = 100;							// var : 정답이미지 세로길이 변수
	public static final int examPanoramaImageWidth = 300;								// var : 보기이미지 가로길이 변수
	public static final int examPanoramaImageHeight = 300;								// var : 보기이미지 세로길이 변수
	public static final String typeArray[] = {											// var : 이미지의 타입을 배열형식으로 저장한 변수 (초기화의 편의성을 위해 배열선언)
			"school", "bridge", "crossroad", "apartment", "gas-station", 
			"convenience-store", "fire-station", "subway-station", "daiso"};
	public static final String typeResizedArray[] = {									// var : 이미지 타입명의 앞 두자리만 사용한 타입을 배열형식으로 저장한 변수
			"sc", "br", "cr", "ap", "ga", "co", "fi", "su", "da"};
	public static final List<String> typeList = Arrays.asList(typeArray);				// var : 배열타입으로 선언된 이미지 타입들을 리스트형식으로 저장하는 변수 (사용시의 편의를 위해 리스트 생성)
	public static final List<String> typeResizedList = Arrays.asList(typeResizedArray);	// var : Resized된 이미지타입 배열을 리스트형식으로 저장하는 변수 (사용시의 편의를 위해 리스트 생성)
	
	// ImagePool Header
	public static final String answerPanoramaImagePoolKeyHeader = "a_";					// var : 정답 PanoramaImage들을 저장하고 있는 리스트의 헤더부분을 저장하는 변수
	public static final String examPanoramaImagePoolKeyHeader = "e_";					// var : 보기 PanoramaImage들을 저장하고 있는 리스트의 헤더부분을 저장하는 변수
	public static final String deletedCaptchaImagePoolKey = "dl";						// var : IssuedCnt에 의해 삭제된 CaptchaImage들의 키값을 저장하고 있는 리스트 키값을 저장하는 변수
	
	// TTL
	public static final int apiCallConnectionTimeout = 2000;							// var : PanoramaAPI 호출시 Connection timeout 시간값을 저장하는 변수
	public static final int apiCallReadTimeout = 2000;									// var : PanoraamAPI 호출시 Read timeout 시간값을 저장하는 변수
	
	// PoolSize Info
	public static final int maxAnswerPanoramaImagePoolSize = 10;						// var : Redis에 저장할 정답PanoramaImage의 최대개수를 저장하는 변수
	public static final int maxExamPanoramaimagePoolSize = 10;							// var : Redis에 저장할 보기PanoramaImage의 최대개수를 저장하는 변수
	public static final int maxBackupAnswerPanoramaImagePoolSize = 8;					// var : MySQL에 저장할 정답PanoramaImage의 최대개수를 저장하는 변수
	public static final int maxBackupExamPanoramaImagePoolSize = 8;						// var : MySQL에 저장할 보기PanoramaImage의 최대개수를 저장하는 변수
	
	
	// API Call Key
	public static final String limitKey = "limit";										// var : PanoramaAPI호출시 url Parameter를 만들기 위한 이미지 호출 최대개수를 저장하는 변수 
	public static final String widthKey = "width";										// var : PanoramaAPI호출시 url Parameter를 만들기 위한 가로길이를 저장하는 변수
	public static final String heightKey = "height";									// var : PanoramaAPI호출시 url Parameter를 만들기 위한 세로길이를 저장하는 변수
	public static final String bzstNoKey = "bzstNo";									// var : PanoramaAPI호출시 url Parameter를 만들기 위한 bzstNo값을 저장하는 변수
	public static final String panoTypeCdKey = "panoTypeCd";							// var : PanoramaAPI호출시 url Parameter를 만들기 위한 panoTypeCd값을 저장하는 변수
	
	// Full String
	public static final String imageTypeString = "type";								// var : MySQL에 저장할때 이미지타입(school, bridge..)에 대한 키값을 얻기 위해 용도(answer,exam)와 구분하기 위한 구분문자열을 저장하는 변수  
	public static final String imageUsageTypeString = "usage";							// var : MySQL에 저장할때 용도(answer,exam)에 대한 키값을 얻기 위해 이미지타입(school, bridge..)과 구분하기 위한 구분문자열을 저장하는 변수
	public static final String answerString = "answer";									// var : 정답PanoramaImage의 키값 설정을 위한 변수
	public static final String examString = "exam";										// var : 보기PanoramaImage의 키값 설정을 위한 변수
	
	// ETC
	public static final String base64Field = "b64";										// var : Redis에 PanoramaImage를 저장할때 base64필드 문자열을 저장하는 변수
	public static final String imageFormatName = "jpeg";								// var : BufferedImage를 base64로 변환할때 format문자열을 저장하는 변수 
	public static final int emptyValue = 0;												// var : "0"이라는 초기값 문자열을 저장하는 변수
}
