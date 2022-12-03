package com.navercorp.batch.tasklet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.navercorp.batch.config.RedisConfig;
import com.navercorp.batch.domain.CommonImageInfo;
import com.navercorp.batch.service.ImageHandlerRedis;

import redis.clients.jedis.Jedis;

@Component
@StepScope
public class SetRedisRefillTasklet implements Tasklet{
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageHandlerRedis imageHandlerRedis;
	
	private static final Logger log = LoggerFactory.getLogger(SetRedisRefillTasklet.class);

	/*
	 * Func : Redis에 저장된 PanoramaImage의 부족한 개수만큼 PanoramaAPI를 호출하여 Redis에 저장하는 함수
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		
		Map<String, Integer> emptyAllPanoramaImagePoolSizeMap = null;				// var : 모든 용도와 타입에 대해 부족한 PanoramaImagePool의 개수를 저장할 HashMap 변수
		String answerPanoramaImagePoolKey = "";										// var : 정답PanoramaImagePool의 키값을 저장하는 변수
		String examPanoramaImagePoolKey = "";										// var : 보기PanoramaImagePool의 키값을 저장하는 변수
		int emptyAnswerPanoramaImagePoolSize = 0;									// var : 정답PanoramaImagePool의 부족한 개수을 저장하는 변수
		int emptyExamPanoramaImagePoolSize = 0;										// var : 보기PanoramaImagePool의 부족한 개수을 저장하는 변수
		try {
			if(CommonImageInfo.isAvailableRedis) {
				jedis = RedisConfig.getJedis();
				emptyAllPanoramaImagePoolSizeMap = new HashMap<String, Integer>();
				// 타입에 대한 반복문을 돌려 모든 용도(answer,exam)의 각 타입별로 PanoramaImagePool의 키값을 Key로, PanoramaImagePool을 구성하기에 부족한 개수를 value로 HashMap에 저장하는 부분
				for(String type : CommonImageInfo.typeResizedList) {
					/* 정답 PanoramaImage에 대한 프로세싱 */
					// 반복문 순서에 따른 타입에 대한 정답 PanoramaImagePool의 키값을 변수에 저장하는 부분
					answerPanoramaImagePoolKey = CommonImageInfo.answerPanoramaImagePoolKeyHeader + type;
					// 최대 정답PanoramaImagePool크기보다 부족한 개수를 변수에 저장하는 부분
					emptyAnswerPanoramaImagePoolSize = CommonImageInfo.maxAnswerPanoramaImagePoolSize - jedis.llen(answerPanoramaImagePoolKey).intValue();
					// 부족한 개수가 '0'보다 큰 경우에만 모든 용도와 타입에 대한 부족한 값을 저장하는 HashMap에 ImagePool의 키값을 Key로, 부족한 개수를 value로 저장하여 PanoramaImage를 채우는 부분 
					if(emptyAnswerPanoramaImagePoolSize > 0) emptyAllPanoramaImagePoolSizeMap.put(answerPanoramaImagePoolKey, emptyAnswerPanoramaImagePoolSize);
					
					/* 보기 PanoramaImage에 대한 프로세싱 */
					// 반복문 순서에 따른 타입에 대한 보기 PanoramaImagePool의 키값을 변수에 저장하는 부분
					examPanoramaImagePoolKey = CommonImageInfo.examPanoramaImagePoolKeyHeader + type;
					// 최대 보기PanoramaImagePool크기보다 부족한 개수를 변수에 저장하는 부분
					emptyExamPanoramaImagePoolSize = CommonImageInfo.maxExamPanoramaimagePoolSize - jedis.llen(examPanoramaImagePoolKey).intValue();
					// 부족한 개수가 '0'보다 큰 경우에만 모든 용도와 타입에 대한 부족한 값을 저장하는 HashMap에 ImagePool의 키값을 Key로, 부족한 개수를 value로 저장하여 PanoramaImage를 채우는 부분
					if(emptyExamPanoramaImagePoolSize > 0) emptyAllPanoramaImagePoolSizeMap.put(examPanoramaImagePoolKey, emptyExamPanoramaImagePoolSize);
				}
				
				// 멀티쓰레드를 통해 타입별로 부족한 정답, 보기 PanoramaImagePool을 RedisServer 내에 저장하는 부분
				List<CompletableFuture<Void>> completableFutureList = new ArrayList<>();
				for(Map.Entry<String, Integer> entry : emptyAllPanoramaImagePoolSizeMap.entrySet()) {
					CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
						try {
							if(entry.getKey().contains(CommonImageInfo.answerPanoramaImagePoolKeyHeader)) {
								// 부족한 개수만큼 정답PanoramaImage를 호출하는 부분
								imageHandlerRedis.setPanoramaImagePoolInfoList(getFullTypeName(entry.getKey().replace(CommonImageInfo.answerPanoramaImagePoolKeyHeader, "")), entry.getValue(), CommonImageInfo.answerPanoramaImageWidth, CommonImageInfo.answerPanoramaImageHeight, CommonImageInfo.answerString);
							}else if(entry.getKey().contains(CommonImageInfo.examPanoramaImagePoolKeyHeader)) {
								// 부족한 개수만큼 보기PanoramaImage를 호출하는 부분
								imageHandlerRedis.setPanoramaImagePoolInfoList(getFullTypeName(entry.getKey().replace(CommonImageInfo.examPanoramaImagePoolKeyHeader, "")), entry.getValue(), CommonImageInfo.examPanoramaImageWidth, CommonImageInfo.examPanoramaImageHeight, CommonImageInfo.examString);
							}
							return null;
						}catch (Exception e) {return null;}
					});
					completableFutureList.add(future);
				}
				// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
				CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
				allFutures.join();
				
				// Test Code
				if(emptyAllPanoramaImagePoolSizeMap.size() > 0) {
					checkAllData();
				}else {
					log.info("=======================================");
					log.info("Refill Nothing");
					log.info("=======================================");
				}
				//
			}else {
				log.error("[RedisRefill-execute] Redis 접속불가");
				contribution.setExitStatus(ExitStatus.FAILED);
			}
		}catch(Exception e) {
			log.error("[RedisRefill-execute] UserMessage\t\t: Redis에 저장된 PanoramaImagePool의 용도(answer,exam)별 부족한 개수를 HashMap객체에 저장하던 도중 에러발생");
			log.error("[RedisRefill-execute] SystemMessage\t: {}", e.getMessage());
			log.error("[RedisRefill-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		long end = System.currentTimeMillis();
		log.info("runtime: " + (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	}
	
	/*
	 * Func : Redis에 저장되는 타입명의 크기를 줄이기 위해 앞의 두개 char만을 사용하는데 PanoramaAPI를 호출하기 위하여 앞의 두개 char를 파라미터로 받아 전체 타입명을 리턴하는 함수
	 */
	public String getFullTypeName(String resizedType) throws Exception{
		int typeIndex = 0;					// var : Reszied한 타입명 리스트에서 찾고자하는 타입의 인덱스를 저장하는 변수
		String typeFullString = "";			// var : Resized하지 않은 전체 타입명을 저장하는 변수
		try {
			typeIndex = CommonImageInfo.typeResizedList.indexOf(resizedType);
			typeFullString = CommonImageInfo.typeList.get(typeIndex); 
		}catch(Exception e) {
			log.error("[getFullTypeName] UserMessage\t: 데이터크기를 줄이기 위해 앞의 두자리만 사용하였던 타입의 전체명을 typeResizedList 리스트에서 찾던 도중 에러발생");
			log.error("[getFullTypeName] SystemMessage\t: {}", e.getMessage());
			log.error("[getFullTypeName] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			typeFullString = "";
		}
		return typeFullString;
	}
	
	// Test Code 
	public void checkAllData() throws Exception{
		log.info("=======================================");
		log.info(" < Answer PanoramaImagePool Size > ");
		String imageKey = "";
		for(String type : CommonImageInfo.typeList) {
			imageKey = CommonImageInfo.answerPanoramaImagePoolKeyHeader + type.substring(0, 2);
			log.info(type + "=" + jedis.llen(imageKey));
		}
		log.info(" < Exam PanoramaImagePool Size > ");
		for(String type : CommonImageInfo.typeList) {
			imageKey = CommonImageInfo.examPanoramaImagePoolKeyHeader + type.substring(0, 2);
			log.info(type + "=" + jedis.llen(imageKey).intValue());
		}
		log.info("=======================================");
	}
	//
}
