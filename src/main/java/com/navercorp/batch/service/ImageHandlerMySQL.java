package com.navercorp.batch.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.navercorp.batch.domain.CommonImageInfo;
import com.navercorp.batch.domain.ImageInfoVO;
import com.navercorp.batch.domain.ImageTypeVO;
import com.navercorp.batch.mapper.ImageMapper;

@Service
public class ImageHandlerMySQL {
	@Autowired
	ImageMapper imageMapper;
	@Autowired
	ApiCall apiCall;
	
	private Map<String, List<ImageTypeVO>> panoramaImageAllTypeMap = null;		// var : 모든 용도(answer, exma)와 타입(school, bridge..)의 기본키와 용도 또는 타입명을 HashMap형식으로 저장하는 변수
	
	private static final Logger log = LoggerFactory.getLogger(ImageHandlerMySQL.class);
	
	/*
	 * Func : 파라미터로 전달받은 type의 bzstNo, panoTypeCd에 대한 PanoramaImage를 멀티쓰레드로 가져와 ImageInfoVO 리스트 객체에 저장하여 리턴하는 함수
	 */
	public List<ImageInfoVO> getPanoramaImagePoolInfoList(String imageType, int limit, int width, int height, String imagePurpose) throws Exception{
		List<ImageInfoVO> panoramaImagePoolInfoList = null;				// var : PanoramaAPI롤 호출하여 리턴받은 정보를 ImageInfoVO 리스트 형식으로 저장하는 변수
		String panoramaImageKey = "";									// var : 파라미터로 전달받은 type과 limit와 함께 PanoramaAPI를 호출하여 리턴받은 bzstNo, panoTypeCd를 문자열 형식으로 저장하는 변수
		JsonParser jsonParser = new JsonParser();						// var : 문자열형식의 bzstNo와 panoTypeCd를 JsonPasing한 값을 저장하는 변수
		JsonArray jsonArray = null;										// var : JsonParsing한 결과값을 JsonArray형식으로 저장하는 변수
		try {
			panoramaImagePoolInfoList = new ArrayList<ImageInfoVO>();
			// 파라미터로 전달받은 type과 limit와 함께 PanoramaAPI를 호출하여 리턴받은 bzstNo, panoTypeCd를 문자열 형식으로 저장하는 부분
			panoramaImageKey = apiCall.getPanoramaImageIDList(imageType, limit);	
			// 전달받은 문자열을 JsonArray형식으로 파싱하는 부분
			jsonArray = (JsonArray)jsonParser.parse(panoramaImageKey);
			// 멀티쓰레드를 통해 전달받은 bzstNo, panoTypeCd만큼 BufferedImage를 리턴받는 부분
			List<CompletableFuture<ImageInfoVO>> completableFutureList = new ArrayList<>();		
			for(JsonElement jsonElement : jsonArray) {
				CompletableFuture<ImageInfoVO> future = CompletableFuture.supplyAsync(() -> {
					try {
						JsonObject jsonObject = (JsonObject)jsonElement;
						// JsonObject에서 bzstNo의 값을 정수형 변수에 저장하는 부분
						int bzstNo = jsonObject.get(CommonImageInfo.bzstNoKey).getAsInt();
						// JsonObject에서 panoTypeCd의 값을 문자열 변수에 저장하는 부분
						String panoTypeCd = jsonObject.get(CommonImageInfo.panoTypeCdKey).getAsString();
						// bzstNo, panoTypeCd와 함께 PanoramaAPI를 호출하여 BufferedImage를 리턴받는 부분
						BufferedImage panoramaImage = apiCall.getPanoramaImage(bzstNo, panoTypeCd, width, height);
						// 리턴받은 BufferedImage의 정보를 ImageInfoVO 객체에 저장하고 이를 리턴하는 부분
						return getPanoramaImagePoolInfo(panoramaImage, imageType, bzstNo, panoTypeCd, imagePurpose);
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
			
			// 모든 Thread의 결과값인 ImageInfoVO 객체를 Null인 객체를 제외하고 리스트에 추가하는 부분
			for(CompletableFuture<ImageInfoVO> completableFuture : completableFutureList) {
				if(completableFuture.get() == null) log.info(imageType + " null occur!");
				else panoramaImagePoolInfoList.add(completableFuture.get());
			}
		}catch(Exception e) {
			log.error("[MySQL-getPanoramaImagePoolInfoList] UserMessage  : MySQL에 저장하기 위한 PanoramaImage가 정상적으로 호출되지 않음");
			log.error("[MySQL-getPanoramaImagePoolInfoList] SystemMessage: {}", e.getMessage());
			log.error("[MySQL-getPanoramaImagePoolInfoList] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return panoramaImagePoolInfoList;
	}
	
	/*
	 * Func(sync) : PanoramaAPI롤 호출하여 리턴받은 BufferedImage와 정보들을 ImageInfoVO 객체에 담아 리턴하는 함수
	 *              DB에 접속해야하는 부분에서 Thread간의 문맥전환이 일어나면 하나의 thread가 timeout 시간 이상으로 기다릴 수 있으므로 syncronized 키워드를 통해 임계영역으로 만든다
	 */
	synchronized public ImageInfoVO getPanoramaImagePoolInfo(BufferedImage panoramaImage, String imageType, int bzstNo, String panoTypeCd, String imagePurpose) throws Exception{
		ByteArrayOutputStream output = null;				// var : BufferedImage를 Base64로 변환하기 위해 ByteArray형식의 OutputStream 객체를 저장하는 변수
		byte[] imageByteArray = null;						// var : BufferedImage의 byte 배열을 저장하는 변수
		String b64 = "";									// var : BufferedImage의 Base64문자열을 저장하는 변수
		String panoramaImageKey = "";						// var : panoTypeCd와 bzstNo를 통해 PanoramaImage의 키를 만들고 이 값을 저장하는 변수
		ImageInfoVO imageInfo = null;						// var : PanoramaImage에 대한 정보를 저장하는 객체변수
		int checkDuplicate = 0;								// var : PanoramaAPI를 호출하여 리턴받은 값이 이미 MySQL에 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		int checkUnused = 0;								// var : PanoramaAPI를 호출하여 리턴받은 값이 이미 MySQL의 삭제된 이미지들을 저장하는 unused_info Table 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		try{
			output = new ByteArrayOutputStream();
			// PanoramaImage를 ByteArrayOutputStream객체에 쓰는 부분
			ImageIO.write(panoramaImage, CommonImageInfo.imageFormatName, output);
			// ByteArrayOutputStream에 쓰인 값을 byte배열 형식으로 변수에 저장하는 부분
			imageByteArray = output.toByteArray();
			output.close();
			// byte배열의 값을 인코딩하여 Base64문자열로 만들고 이를 변수에 저장하는 부분
			b64 = Base64.getEncoder().encodeToString(imageByteArray);
			// panoTypeCd와 bzstNo값을 가지고 panoramaImageKey를 만드는 부분
			panoramaImageKey = panoTypeCd + "_" + String.valueOf(bzstNo);
		
			// panoramaImageKey가 MySQL에 중복되는지를 확인하는 부분
			checkDuplicate = imageMapper.selectDuplicateImage(panoramaImageKey);
			// panoramaImageKey가 MySQL의 삭제된 이미지들을 저장하는 unused_info Table에 저장되어 있는지를 확인하는 부분
			checkUnused = imageMapper.selectCheckUnusedImage(panoramaImageKey);
			if(checkDuplicate > 0) {
				log.warn("[MySQL-getPanoramaImagePoolInfo] Duplicated PanoramaImage\t: "+ panoramaImageKey);
			}else if(checkUnused > 0){
				log.warn("[MySQL-getPanoramaImagePoolInfo] Deleted PanoramaImage\t: "+ panoramaImageKey);
			}else {
				// 정상적인 PanoramaImage라면 이미지에 대한 정보들을 ImageInfoVO 객체에 담아 리턴한다
				imageInfo = new ImageInfoVO();
				imageInfo.setImageKey(panoramaImageKey);
				imageInfo.setImageUsageKey(getPanoramaImageAllTypeKey(CommonImageInfo.imageUsageTypeString, imagePurpose));
				imageInfo.setImageTypeKey(getPanoramaImageAllTypeKey(CommonImageInfo.imageTypeString, imageType));
				imageInfo.setBzstNo(bzstNo);
				imageInfo.setPanoTypeCd(panoTypeCd);
				imageInfo.setB64(b64);
			}
		}catch(Exception e) {
			log.error("[MySQL-getPanoramaImagePoolInfo] UserMessage  : PanoramaImage의 정보를 MySQL에 저장하기 위한 ImageInfoVO객체에 정상적으로 저장하지 못함");
			log.error("[MySQL-getPanoramaImagePoolInfo] SystemMessage: {}", e.getMessage());
			log.error("[MySQL-getPanoramaImagePoolInfo] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return imageInfo; 
	}
	
	/*
	 * Func : PanoramaImage의 용도(answer,exam) 또는 타입(school, bridge..)에 대한 기본키를  panoramaImageAllTypeMap변수에서 검색하여 결과값을 리턴하는 함수
	 */
	public int getPanoramaImageAllTypeKey(String findType, String typeName) throws Exception{
		List<ImageTypeVO> imageTypeList = null;		// var : 용도 또는 타입에 대한 기본키와 용도 또는 타입명을 저장하고 있는 ImageTypeVO 리스트 객체를 저장하는 변수
		int typeKey = 0;							// var : 용도 또는 타입에 대한 기본키를 저장하는 변수
		try {
			// 파라미터로 전달받은 findType(용도인지, 타입인지를 구분해주는 구분자)을 키값으로 가지는 ImageTypeVO 리스트 객체를 변수에 저장하는 부분
			imageTypeList = panoramaImageAllTypeMap.get(findType);
			// ImageTypeVO 리스트 객체를 반복문을 돌려 타입명 또는 용도명에 해당하는 기본키를 찾아서 변수에 저장하고 리턴하는 부분
			for(ImageTypeVO imageType : imageTypeList) {
				if(imageType.getTypeName().equals(typeName)) {
					typeKey = imageType.getTypeKey();
				}
			}
		}catch(Exception e) {
			log.error("[getPanoramaImageAllTypeKey] UserMessage  : " + findType + "에 해당하는 기본키를 panoramaImageAllTypeMap 객체에서 찾는 도중 에러발생");
			log.error("[getPanoramaImageAllTypeKey] SystemMessage: {}", e.getMessage());
			log.error("[getPanoramaImageAllTypeKey] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		return typeKey;
	}
	
	/*
	 * Func : panoramaImageAllTypeMap변수에 모든 용도(answer, exma)와 타입(school, bridge..)의 기본키와 용도 또는 타입명을 HashMap형식으로 저장하는 함수
	 */
	public void setAllTypeList() throws Exception{
		try {
			panoramaImageAllTypeMap = new HashMap<String, List<ImageTypeVO>>();
			// MySQL에서 타입에 대한 정보를 저장하고 있는 image_type 테이블의 모든 row들을 select하여 ImageTypeVO 리스트 형식으로 리턴하고, panoramaImageAllTypeMap변수의 value로 저장하는 부분
			panoramaImageAllTypeMap.put(CommonImageInfo.imageTypeString, imageMapper.selectImageTypeList());
			// MySQL에서 용도에 대한 정보를 저장하고 있는 image_usage_type 테이블의 모든 row들을 select하여 ImageTypeVO 리스트 형식으로 리턴하고, panoramaImageAllTypeMap변수의 value로 저장하는 부분
			panoramaImageAllTypeMap.put(CommonImageInfo.imageUsageTypeString, imageMapper.selectImageUsageTypeList());
		}catch(Exception e) {
			log.error("[setAllTypeList] UserMessage  : " + "panoramaImageAllTypeMap 객체에 MySQL내에 저장된 type과 usageType의 기본키와 타입명을 select하여 저장하는 도중 에러발생");
			log.error("[setAllTypeList] SystemMessage: {}", e.getMessage());
			log.error("[setAllTypeList] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
}
