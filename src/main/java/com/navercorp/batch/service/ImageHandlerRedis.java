package com.navercorp.batch.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
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
import com.navercorp.batch.config.RedisConfig;
import com.navercorp.batch.domain.CommonImageInfo;
import com.navercorp.batch.domain.ImageInfoVO;
import com.navercorp.batch.mapper.ImageMapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Service
public class ImageHandlerRedis {
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageMapper imageMapper;
	@Autowired
	ApiCall apiCall;
	
	private static final Logger log = LoggerFactory.getLogger(ImageHandlerRedis.class);
	
	/*
	 * Func : 파라미터로 전달받은 type의 bzstNo, panoTypeCd에 대한 PanoramaImage를 멀티쓰레드로 가져와 바로 Redis Server에 저장하는 함수
	 */
	public void setPanoramaImagePoolInfoList(String imageType, int limit, int width, int height, String imagePurpose) throws Exception{
		jedis = RedisConfig.getJedis();
		String panoramaImageKey = "";													// var : 파라미터로 전달받은 type과 limit와 함께 PanoramaAPI를 호출하여 리턴받은 bzstNo, panoTypeCd를 문자열 형식으로 저장하는 변수
		JsonParser jsonParser = new JsonParser();										// var : 문자열형식의 bzstNo와 panoTypeCd를 JsonPasing한 값을 저장하는 변수
		JsonArray jsonArray = null;														// var : JsonParsing한 결과값을 JsonArray형식으로 저장하는 변수
		try {
			// 파라미터로 전달받은 type과 limit와 함께 PanoramaAPI를 호출하여 리턴받은 bzstNo, panoTypeCd를 문자열 형식으로 저장하는 부분
			panoramaImageKey = apiCall.getPanoramaImageIDList(imageType, limit);
			// 전달받은 문자열을 JsonArray형식으로 파싱하는 부분
			jsonArray = (JsonArray)jsonParser.parse(panoramaImageKey);
			// 멀티쓰레드를 통해 전달받은 bzstNo, panoTypeCd만큼 BufferedImage를 리턴받고 바로 Redis에 이미지 정보들을 저장하는 부분
			List<CompletableFuture<?>> completableFutureList = new ArrayList<>();
			for(JsonElement jsonElement : jsonArray) {
				CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
					try {
						JsonObject jsonObj = (JsonObject)jsonElement;
						// JsonObject에서 bzstNo의 값을 정수형 변수에 저장하는 부분
						int bzstNo = jsonObj.get(CommonImageInfo.bzstNoKey).getAsInt();
						// JsonObject에서 panoTypeCd의 값을 문자열 변수에 저장하는 부분
						String panoTypeCd = jsonObj.get(CommonImageInfo.panoTypeCdKey).getAsString();
						// bzstNo, panoTypeCd와 함께 PanoramaAPI를 호출하여 BufferedImage를 리턴받는 부분
						BufferedImage panoramaImage = apiCall.getPanoramaImage(bzstNo, panoTypeCd, width, height);
						// 리턴받은 BufferedImage의 정보를 Redis에 바로 저장하는 부분
						setPanoramaImagePoolInfo(panoramaImage, imageType, bzstNo, panoTypeCd, imagePurpose);
						return null;
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
		}catch(Exception e) {
			log.error("[Redis-getPanoramaImagePoolInfoList] UserMessage  : Redis에 저장하기 위한 PanoramaImage가 정상적으로 호출되지 않음");
			log.error("[Redis-getPanoramaImagePoolInfoList] SystemMessage: {}", e.getMessage());
			log.error("[Redis-getPanoramaImagePoolInfoList] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
	
	/*
	 * Func(sync) : PanoramaAPI롤 호출하여 리턴받은 BufferedImage와 정보들을 Redis Server에 바로 저장하는 함수
	 *  			DB에 접속해야하는 부분에서 Thread간의 문맥전환이 일어나면 하나의 thread가 timeout 시간 이상으로 기다릴 수 있으므로 syncronized 키워드를 통해 임계영역으로 만든다
	 */
	synchronized public void setPanoramaImagePoolInfo(BufferedImage panoramaImage, String imageType, int bzstNo, String panoTypeCd, String imagePurpose) throws Exception{
		ByteArrayOutputStream output = null;					// var : BufferedImage를 Base64로 변환하기 위해 ByteArray형식의 OutputStream 객체를 저장하는 변수
		byte[] imageByteArray = null;							// var : BufferedImage의 byte 배열을 저장하는 변수
		String b64 = "";										// var : BufferedImage의 Base64문자열을 저장하는 변수
		String panoramaImageKey = "";							// var : panoTypeCd와 bzstNo를 통해 PanoramaImage의 키를 만들고 이 값을 저장하는 변수
		int checkDuplicate = 0;									// var : PanoramaAPI를 호출하여 리턴받은 값이 이미 Redis에 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		int checkUnused = 0;									// var : PanoramaAPI를 호출하여 리턴받은 값이 이미 MySQL의 삭제된 이미지들을 저장하는 unused_info Table 저장되어 있는지 확인하고 그 결과값을 저장하는 변수
		String panoramaImagePoolKey = "";						// var : PanoramaImagePool의 키값을 저장하는 변수
		Pipeline pipeLine = null;
		try {
			output = new ByteArrayOutputStream();
			// PanoramaImage를 ByteArrayOutputStream객체에 쓰는 부분
			ImageIO.write(panoramaImage, CommonImageInfo.imageFormatName, output);
			// ByteArrayOutputStream에 쓰인 값을 byte배열 형식으로 변수에 저장하는 부분
			imageByteArray = output.toByteArray();
			output.close();
			// byte배열의 값을 인코딩하여 Base64문자열로 만들고 이를 변수에 저장하는 부분
			b64 = Base64.getEncoder().encodeToString(imageByteArray);
			
			// bzstNo와 panoTypeCd값을 36진수로 변환하여 panoramaImageKey를 만드는 부분 (Redis에 저장되는 데이터의 크기를 최대한 줄이기 위한 방법)
			panoramaImageKey = Long.toString(Integer.parseInt(panoTypeCd), 36) + "_" + Long.toString(bzstNo, 36);
			// panoramaImageKey들을 리스트 형식으로 저장할 panoramaImagePool의 키값을 만드는 부분 
			// (용도는 앞의 한글자만 사용하고, 타입은 앞의 두글자만 사용하여 만든다 [answer_school --> a_sc], Redis에 저장되는 데이터의 크기를 최대한 줄이기 위한 방법)
			panoramaImagePoolKey = imagePurpose.substring(0, 1) + "_" + imageType.substring(0, 2);					
			
			// panoramaImageKey가 Redis에 중복되는지를 확인하는 부분
			checkDuplicate = jedis.lrem(panoramaImagePoolKey, 0, panoramaImageKey).intValue();
			// panoramaImageKey가 MySQL의 삭제된 이미지들을 저장하는 unused_info Table에 저장되어 있는지를 확인하는 부분
			checkUnused = imageMapper.selectCheckUnusedImage(panoramaImageKey);
			
			pipeLine = jedis.pipelined();
			if(checkDuplicate > 0) {
				pipeLine.lpush(panoramaImagePoolKey, panoramaImageKey);
				log.warn("[Redis-setPanoramaImagePoolInfo] Duplicated PanoramaImage\t: "+ panoramaImageKey);
			}else if(checkUnused > 0){
				log.warn("[Redis-setPanoramaImagePoolInfo] Deleted PanoramaImage\t: "+ panoramaImageKey);
			}else {
				// 정상적인 PanoramaImage라면 이미지에 대한 정보들을 Redis에 저장하며 panoramaImagePool에 panoramaImageKey를 추가하고, panoramaImageKey의 base64값을 추가한다
				pipeLine.lpush(panoramaImagePoolKey, panoramaImageKey);
				pipeLine.hset(panoramaImageKey, CommonImageInfo.base64Field, b64);
			}
			pipeLine.sync();
		}catch(Exception e) {
			log.error("[Redis-setPanoramaImagePoolInfo] UserMessage  : PanoramaImage의 정보를 Redis에 정상적으로 저장하지 못함");
			log.error("[Redis-setPanoramaImagePoolInfo] SystemMessage: {}", e.getMessage());
			log.error("[Redis-setPanoramaImagePoolInfo] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
}
