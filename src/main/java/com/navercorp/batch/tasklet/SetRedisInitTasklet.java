package com.navercorp.batch.tasklet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import com.navercorp.batch.service.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Component
@StepScope
public class SetRedisInitTasklet implements Tasklet{
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageHandlerRedis imageHandlerRedis;
	
	private static final Logger log = LoggerFactory.getLogger(SetRedisInitTasklet.class);

	/*
	 * Func : Redis에 저장된 PanoramaImage가 모두 비어있을 경우 PanoramaAPI를 호출하여 Redis에 저장하는 함수
	 * 	      CaptchaServer가 새로 시작되어 Redis에 PanoramaImage가 비어있거나, 너무 많이 사용하여 아예 비어있을때 동작하며 이는 해당 Tasklet이 동작한 뒤에 setRedisRefillTasklet이 한번 더 동작하여
	 *        PanoramaAPI 호출 실패한 부분까지 채우기 위해서 InitTasklet을 별도로 만듬  
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		
		int redisTotalPanoramaImagePoolSize = 0;
		Pipeline pipeLine = null;
		List<Object> redisTotalPanoramaImagePoolSizeList = null;
		try {
			// Redis가 shutdown 되어 있는지를 확인하는 부분
			if(CommonImageInfo.isAvailableRedis) {
				jedis = RedisConfig.getJedis();
				pipeLine = jedis.pipelined();
				// 타입(school, bridge..)에 대한 반복문을 돌려 Redis에 저장된 모든 PanoramaImagePool의 크기를 합산하여 저장하는 부분
				for(String type : CommonImageInfo.typeResizedList) {
					pipeLine.llen(CommonImageInfo.answerPanoramaImagePoolKeyHeader + type);
					pipeLine.llen(CommonImageInfo.examPanoramaImagePoolKeyHeader + type);
				}
				redisTotalPanoramaImagePoolSizeList = pipeLine.syncAndReturnAll();
				for(Object redisTotalPanoramaImagePoolSizeItem : redisTotalPanoramaImagePoolSizeList) {
					redisTotalPanoramaImagePoolSize += Integer.parseInt(String.valueOf(redisTotalPanoramaImagePoolSizeItem));
				}
				
				// 모든 PanoramaImage의 개수가 0보다 클때 프로세스가 진행된다
				if(redisTotalPanoramaImagePoolSize == 0) {
					// 멀티쓰레드를 통해 타입별로 모든 정답, 보기 PanoramaImagePool을 RedisServer 내에 저장하는 부분
					List<CompletableFuture<Void>> completableFutureList = new ArrayList<>();
					for(String type : CommonImageInfo.typeList) {
						CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
							try {
								// 모든 정답PanoramaImage(100x100)를 호출하는 부분
								imageHandlerRedis.setPanoramaImagePoolInfoList(type, CommonImageInfo.maxAnswerPanoramaImagePoolSize, CommonImageInfo.answerPanoramaImageWidth, CommonImageInfo.answerPanoramaImageHeight, CommonImageInfo.answerString);
								// 모든 보기PanoramaImage(300x300)를 호출하는 부분
								imageHandlerRedis.setPanoramaImagePoolInfoList(type, CommonImageInfo.maxExamPanoramaimagePoolSize, CommonImageInfo.examPanoramaImageWidth, CommonImageInfo.examPanoramaImageHeight, CommonImageInfo.examString);
								return null;
							}catch (Exception e) {return null;}
						});
						completableFutureList.add(future);
					}
					// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
					CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
					allFutures.join();
					
					// Test Code
					checkAllData();
					//
				}
			}else {
				// Redis가 shutdown되어 있을때는 FAIL을 리턴하고 끝낸다
				log.error("[RedisInit-execute] Redis 접속불가");
				contribution.setExitStatus(ExitStatus.FAILED);
			}
		}catch(Exception e) {
			log.error("[RedisInit-execute] UserMessage\t\t: Redis에 저장된 정답PanoramaImagePool의 크기를 계산하던 도중 에러발생");
			log.error("[RedisInit-execute] SystemMessage\t: {}", e.getMessage());
			log.error("[RedisInit-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		long end = System.currentTimeMillis();
		log.info("runtime: " + (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
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
