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
import com.navercorp.batch.domain.CommonImageInfo;
import com.navercorp.batch.domain.ImageInfoVO;
import com.navercorp.batch.mapper.ImageMapper;
import com.navercorp.batch.service.ImageHandlerMySQL;

@Component
@StepScope
public class SetMySQLInitTasklet implements Tasklet{
	@Autowired
	ImageHandlerMySQL imageHanderMySQL;
	@Autowired
	ImageMapper imageMapper;
	
	private static final Logger log = LoggerFactory.getLogger(SetMySQLInitTasklet.class);

	/*
	 * Func : MySQL에 저장된 PanoramaImage가 모두 비어있을 경우 PanoramaAPI를 호출하여 MySQL에 저장하는 함수
	 *        CaptchaServer가 새로 시작되어 MySQL에 PanoramaImage가 비어있거나, 너무 많이 사용하여 아예 비어있을때 동작하며 이는 해당 Tasklet이 동작한 뒤에 setMySQLRefillTasklet이 한번 더 동작하여
	 *        PanoramaAPI 호출 실패한 부분까지 채우기 위해서 InitTasklet을 별도로 만듬  
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		int mySQLTotalPanoramaImagePoolSize = 0;	// var : MySQL에 저장된 모든 PanoramaImage의 개수를 저장하는 변수
		try {
			// MySQL의 모든 PanoramaImage의 개수를 select하여 변수에 저장하는 부분
			mySQLTotalPanoramaImagePoolSize = imageMapper.selectImageInfoCnt();
			// 모든 PanoramaImage의 개수가 0보다 클때 프로세스가 진행된다
			if(mySQLTotalPanoramaImagePoolSize <= 0) {
				// MySQL의 용도(answer,exam)와 타입(school, bridge..)에 대한 기본키와 용도명 또는 타입명을 HashMap에 저장하는 메서드를 미리 호출하여 멀티쓰레드가 동작하기 전에 미리 HashMap 값을 채워놓는다
				imageHanderMySQL.setAllTypeList();
				// 정답 PanoramaImagePool을 생성하기 위해 멀티쓰레드를 동작시키는 함수를 호출하는 부분
				setMySQLPanoramaImagePool(CommonImageInfo.maxBackupAnswerPanoramaImagePoolSize, CommonImageInfo.answerPanoramaImageWidth, CommonImageInfo.answerPanoramaImageHeight, CommonImageInfo.answerString);
				// 보기 PanoramaImagePool을 생성하기 위해 멀티쓰레드를 동작시키는 함수를 호출하는 부분
				setMySQLPanoramaImagePool(CommonImageInfo.maxBackupExamPanoramaImagePoolSize, CommonImageInfo.examPanoramaImageWidth, CommonImageInfo.examPanoramaImageHeight, CommonImageInfo.examString);
			}	
		}catch(Exception e) {
			log.error("[MySQLInit-execute] UserMessage  : MySQL에 저장된 PanoramaImage의 개수를 가져올때 에러발생");
			log.error("[MySQLInit-execute] SystemMessage: {}", e.getMessage());
			log.error("[MySQLInit-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		long end = System.currentTimeMillis();
		log.info("runtime: " + (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	}
	
	/*
	 * Func : 각 타입별로 MySQL에 PanoramaImagePool을 생성하기 위해 멀티쓰레드를 동작시키는 함수로 ImageHandlerMySQL Service에서 리턴한 PanoramaImage에 대한 정보를 ImageInfoVO 리스트를 MySQL에 저장한다
	 */
	public void setMySQLPanoramaImagePool(int panoramaImagePoolSize, int width, int height, String purpose) throws Exception{
		List<ImageInfoVO> panoramaImagePoolInfoList = null;		// var : Service단에서 리턴받은 PanoramaImagePool에 대한 정보를 가지고 있는 ImageInfoVO 리스트를 저장하는 변수 
		try {
			panoramaImagePoolInfoList = new ArrayList<ImageInfoVO>();
			// 멀티쓰레드를 통해 각 타입별로 PanoramaImagePool을 ImageInfoVO 객체형식으로 리턴받는 부분
			List<CompletableFuture<List<ImageInfoVO>>> completableFutureList = new ArrayList<>();
			for(String type : CommonImageInfo.typeList) {
				CompletableFuture<List<ImageInfoVO>> future = CompletableFuture.supplyAsync(() -> {
					try {
						return imageHanderMySQL.getPanoramaImagePoolInfoList(type, panoramaImagePoolSize, width, height, purpose);
					}catch (Exception e) {return null;}
				});
				completableFutureList.add(future);
			}
			// allOf() 메서드를 통해 모든 Thread가 끝날때까지 기다리는 부분
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutureList.toArray(new CompletableFuture[completableFutureList.size()]));
			allFutures.join();
			// 모든 Thread의 결과값인 ImageInfoVO 리스트를 Null인 객체를 제외하고 리스트에 추가하는 부분
			for(CompletableFuture<List<ImageInfoVO>> completableFuture : completableFutureList) {
				if(completableFuture.get() != null) panoramaImagePoolInfoList.addAll(completableFuture.get());
			}
			// 리턴받은 PanoramaImagePool의 크기가 0보다 클 경우 MySQL에 저장한다
			if(panoramaImagePoolInfoList.size() > 0) {
				imageMapper.insertImageInfoList(panoramaImagePoolInfoList);
			}
		}catch(Exception e) {
			log.error("[setMySQLPanoramaImagePool-Init] UserMessage  : 각 타입마다 MySQL의 PanoramaImagePool을 생성하기 위해 ImageHandlerMySQL Service를 멀티쓰레드로 호출하는 도중 에러발생");
			log.error("[setMySQLPanoramaImagePool-Init] SystemMessage: {}", e.getMessage());
			log.error("[setMySQLPanoramaImagePool-Init] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
	}
}
