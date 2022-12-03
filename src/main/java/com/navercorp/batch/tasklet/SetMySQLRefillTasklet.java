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
import com.navercorp.batch.domain.CommonImageInfo;
import com.navercorp.batch.domain.ImageAllTypeCntVO;
import com.navercorp.batch.domain.ImageInfoVO;
import com.navercorp.batch.mapper.ImageMapper;
import com.navercorp.batch.service.ImageHandlerMySQL;

@Component
@StepScope
public class SetMySQLRefillTasklet implements Tasklet{
	@Autowired
	ImageHandlerMySQL imageHanderMySQL;
	@Autowired
	ImageMapper imageMapper;
	
	private static final Logger log = LoggerFactory.getLogger(SetMySQLInitTasklet.class);

	/*
	 * Func : MySQL에 저장된 PanoramaImage의 부족한 개수만큼 PanoramaAPI를 호출하여 MySQL에 저장하는 함수
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		
		Map<String, Integer> emptyAnswerPanoramaImagePoolSizeMap = null;		// var : 정답PanoramaImage의 타입을 key로, 부족한 개수를 value로 저장하는 변수 
		Map<String, Integer> emptyExamPanoramaImagePoolSizeMap = null;			// var : 보기PanoramaImage의 타입을 key로, 부족한 개수를 value로 저장하는 변수
		int emptyAnswerPanoramaImagePoolSize = 0;								// var : 정답PanoramaImagePool 내에 부족한 개수를 저장하는 변수
		int emptyExamPanoramaImagePoolSize = 0;									// var : 보기PanoramaImagePool 내에 부족한 개수를 저장하는 변수
		List<ImageAllTypeCntVO> allTypeList = null;								// var : MySQL에서 모든 용도(answer,exam)와 타입(school, bridge..)에 대한 PanoramaImagePool의 크기를 ImageAllTypeCntVO 리스트로 저장하는 변수 
		try {
			emptyAnswerPanoramaImagePoolSizeMap = new HashMap<String, Integer>();
			emptyExamPanoramaImagePoolSizeMap = new HashMap<String, Integer>();
			// MySQL에서 모든 용도(answer,exam)와 타입(school, bridge..)에 대한 PanoramaImagePool의 크기를 ImageAllTypeCntVO 리스트로 저장하는 부분
			allTypeList = imageMapper.selectImageAllTypeList();
			
			// 반복문을 돌려 용도(answer,exam)에 따라 다른 HashMap 변수에 저장하고 타입(school, bridge..)을 key로 타입에 대한 PanoramaImagePool의 개수를 value로 저장하는 부분  
			// (MySQL의 접속횟수를 줄이기 위해 모든 용도에 대해 한번에 가져와 코드단에서 분리)
			for(ImageAllTypeCntVO type : allTypeList) {
				if(type.getUsageName().equals(CommonImageInfo.answerString)) {
					// 정답PanoramaImage의 경우 정답HashMap변수에 타입(school, bridge..)을 key로 타입에 대한 PanoramaImagePool의 개수를 value로 저장하는 부분
					emptyAnswerPanoramaImagePoolSizeMap.put(type.getTypeName(), type.getPanoramaImagePoolSize());
				}else if(type.getUsageName().equals(CommonImageInfo.examString)) {
					// 보기PanoramaImage의 경우 보기HashMap변수에 타입(school, bridge..)을 key로 타입에 대한 PanoramaImagePool의 개수를 value로 저장하는 부분
					emptyExamPanoramaImagePoolSizeMap.put(type.getTypeName(), type.getPanoramaImagePoolSize());
				}
			}
			
			// 타입에 대한 반복문을 돌려 각 타입별로 PanoramaImagePool을 구성하기에 부족한 개수를 value로 다시 저장하는 부분
			for(String type : CommonImageInfo.typeList) {
				/* 정답 PanoramaImage에 대한 프로세싱 */
				if(emptyAnswerPanoramaImagePoolSizeMap.get(type) == null) {
					// MySQL내에 반복문 순서에 따른 type에 대한 PanoramaImagePool이 아예 비어있어 select되지 않았을때에는 최대PanoramaImagePool크기를 value로 저장하여 PanoramaImage를 가져오도록 하는 부분
					emptyAnswerPanoramaImagePoolSizeMap.put(type, CommonImageInfo.maxAnswerPanoramaImagePoolSize);
				}else {
					// 최대PanoramaImagePool의 크기와 MySQL에 저장된 반복문 순서에 따른 type에 대한 PanoramaImagePool의 크기의 차이를 계산하는 부분 
					emptyAnswerPanoramaImagePoolSize = CommonImageInfo.maxAnswerPanoramaImagePoolSize - emptyAnswerPanoramaImagePoolSizeMap.get(type);
					if(emptyAnswerPanoramaImagePoolSize > CommonImageInfo.emptyValue) {
						// 계산된 값이 '0'보다 큰 경우에는 부족한 개수만큼을 value로 저장하여 PanoramaImage를 가져오도록 하는 부분
						emptyAnswerPanoramaImagePoolSizeMap.put(type, emptyAnswerPanoramaImagePoolSize);
					}else{
						// 계산된 값이 '0'인 경우에는 PanoramaImagePool이 가득 차있다는 의미이므로 해당 type을 HashMap에서 제거하여 PanoramaImage를 호출하지 않도록 하는 부분
						emptyAnswerPanoramaImagePoolSizeMap.remove(type);
					}
				}
				
				/* 보기 PanoramaImage에 대한 프로세싱 */
				if(emptyExamPanoramaImagePoolSizeMap.get(type) == null) {
					// MySQL내에 반복문 순서에 따른 type에 대한 PanoramaImagePool이 아예 비어있어 select되지 않았을때에는 최대PanoramaImagePool크기를 value로 저장하여 PanoramaImage를 가져오도록 하는 부분
					emptyExamPanoramaImagePoolSizeMap.put(type, CommonImageInfo.maxExamPanoramaimagePoolSize);
				}else {
					// 최대PanoramaImagePool의 크기와 MySQL에 저장된 반복문 순서에 따른 type에 대한 PanoramaImagePool의 크기의 차이를 계산하는 부분
					emptyExamPanoramaImagePoolSize = CommonImageInfo.maxExamPanoramaimagePoolSize - emptyExamPanoramaImagePoolSizeMap.get(type);
					if(emptyExamPanoramaImagePoolSize > CommonImageInfo.emptyValue) {
						// 계산된 값이 '0'보다 큰 경우에는 부족한 개수만큼을 value로 저장하여 PanoramaImage를 가져오도록 하는 부분
						emptyExamPanoramaImagePoolSizeMap.put(type, emptyExamPanoramaImagePoolSize);
					}else {
						// 계산된 값이 '0'인 경우에는 PanoramaImagePool이 가득 차있다는 의미이므로 해당 type을 HashMap에서 제거하여 PanoramaImage를 호출하지 않도록 하는 부분
						emptyExamPanoramaImagePoolSizeMap.remove(type);
					}
				}
			}
			// PanoramaImagePool의 부족한 개수가 있는 경우에만 아래의 프로세스 실행 (모든 PanoramaImagePool이 가득 차 있는 경우에는 프로세스를 진행시키지 않는다)
			if(emptyAnswerPanoramaImagePoolSizeMap.size() > 0 || emptyExamPanoramaImagePoolSizeMap.size() > 0) {
				// MySQL의 용도(answer,exam)와 타입(school, bridge..)에 대한 기본키와 용도명 또는 타입명을 HashMap에 저장하는 메서드를 미리 호출하여 멀티쓰레드가 동작하기 전에 미리 HashMap 값을 채워놓는다
				imageHanderMySQL.setAllTypeList();
				if(emptyAnswerPanoramaImagePoolSizeMap.size() > 0) {
					// 정답 PanoramaImagePool을 부족한 만큼 PanoramaAPI를 호출하여 채우기 위해 멀티쓰레드를 동작시키는 함수를 호출하는 부분
					setRefillMySQLPanoramaImagePool(emptyAnswerPanoramaImagePoolSizeMap, CommonImageInfo.answerPanoramaImageWidth, CommonImageInfo.answerPanoramaImageHeight, CommonImageInfo.answerString);
				}
				if(emptyExamPanoramaImagePoolSizeMap.size() > 0) {
					// 보기 PanoramaImagePool을 부족한 만큼 PanoramaAPI를 호출하여 채우기 위해 멀티쓰레드를 동작시키는 함수를 호출하는 부분
					setRefillMySQLPanoramaImagePool(emptyExamPanoramaImagePoolSizeMap, CommonImageInfo.examPanoramaImageWidth, CommonImageInfo.examPanoramaImageHeight, CommonImageInfo.examString);	
				}
			}
		}catch(Exception e) {
			log.error("[MySQLRefill-execute] UserMessage  : MySQL에 저장된 PanoramaImagePool의 개수를 select하여 최대ImagePool보다 부족한 개수를 계산하던 도중 에러발생");
			log.error("[MySQLRefill-execute] SystemMessage: {}", e.getMessage());
			log.error("[MySQLRefill-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		long end = System.currentTimeMillis();
		log.info("runtime: " + (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	}
	
	/*
	 * Func : 각 타입별로 MySQL에 PanoramaImagePool을 부족한 만큼 채우기 위해  멀티쓰레드를 동작시키는 함수로 ImageHandlerMySQL Service에서 리턴한 PanoramaImage에 대한 정보를 ImageInfoVO 리스트를 MySQL에 저장한다 
	 */
	public void setRefillMySQLPanoramaImagePool(Map<String, Integer> emptyAllPanoramaImagePoolSizeMap, int width, int height, String purpose) throws Exception{
		List<ImageInfoVO> mysqlPanoramaImagePoolInfoList = null;
		try {
			mysqlPanoramaImagePoolInfoList = new ArrayList<ImageInfoVO>();
			// 멀티쓰레드를 통해 각 타입별로 PanoramaImagePool을 ImageInfoVO 객체형식으로 리턴받는 부분
			List<CompletableFuture<List<ImageInfoVO>>> completableFutureList = new ArrayList<>();
			for(Map.Entry<String, Integer> entry : emptyAllPanoramaImagePoolSizeMap.entrySet()) {
				CompletableFuture<List<ImageInfoVO>> future = CompletableFuture.supplyAsync(() -> {
					try {
						return imageHanderMySQL.getPanoramaImagePoolInfoList(entry.getKey(), entry.getValue(), width, height, purpose);
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
			// 모든 Thread의 결과값인 ImageInfoVO 리스트를 Null인 객체를 제외하고 리스트에 추가하는 부분
			for(CompletableFuture<List<ImageInfoVO>> completableFuture : completableFutureList) {
				if(completableFuture.get() != null) mysqlPanoramaImagePoolInfoList.addAll(completableFuture.get());
			}
			// 리턴받은 PanoramaImagePool의 크기가 0보다 클 경우 MySQL에 저장한다
			if(mysqlPanoramaImagePoolInfoList.size() != 0) {
				imageMapper.insertImageInfoList(mysqlPanoramaImagePoolInfoList);
			}
		}catch(Exception e){
			log.error("[setMySQLPanoramaImagePool-Refill] UserMessage  : 각 타입마다 최대ImagePool 개수보다 부족한만큼 MySQL의 PanoramaImagePool을 보충하기 위해 ImageHandlerMySQL Service를 멀티쓰레드로 호출하는 도중 에러발생");
			log.error("[setMySQLPanoramaImagePool-Refill] SystemMessage: {}", e.getMessage());
			log.error("[setMySQLPanoramaImagePool-Refill] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
}
