package com.navercorp.batch.tasklet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import com.navercorp.batch.domain.ImageInfoVO;
import com.navercorp.batch.mapper.ImageMapper;
import redis.clients.jedis.Jedis;

@Component
@StepScope
public class SetDeleteUnusedTasklet implements Tasklet{
	@Autowired
	private RedisConfig RedisConfig;
	private Jedis jedis = new Jedis();
	@Autowired
	ImageMapper imageMapper;
	
	private static final Logger log = LoggerFactory.getLogger(SetDeleteUnusedTasklet.class);
	
	/*
	 * Func : issuedCnt에 의해 Captcha Server에서 삭제된 CaptchaImage들을 MySQL의 unused_info 테이블에 저장하고, 
	 *        MySQL 내의 isUsed필드가 '1'인 row들을 모두 삭제한다 (isUsed=1인 row는 Redis에서 PanoramaImage가 부족하여 가져다 쓴 row라는 의미)
	 */
	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		long start = System.currentTimeMillis();
		
		List<ImageInfoVO> deletedAnswerPanoramaImageInfoList = null;			// var : 삭제된 CaptchaImage에서 AnswerPanoramaImage의 정보들을 리스트 형식으로 저장하는 변수
		List<String> deletedCaptchaImagePool = null;							// var : 삭제된 CaptchaImage들의 리스트를 저장하는 변수
		String deletedAnswerPanoramaImageKeyArray[] = new String[2];			// var : 삭제된 CaptchaImage의 AnswerPanoramaImage를 split하여 bzstNo와 panoTypeCd를 배열형식으로 저장하는 변수
		String deletedAnswerPanoramaImageKey = "";								// var : 삭제된 CaptchaImage의 AnswerPanoramaImage 키값을 저장하는 변수
		ImageInfoVO deleteAnswerPanoramaImageInfo = null;						// var : 삭제된 CaptchaImage에서 AnswerPanoramaImage의 정보를 저장하는 변수
		try {
			// Redis가 shutdown 되어 있는지를 확인하는 부분
			if(CommonImageInfo.isAvailableRedis) {
				jedis = RedisConfig.getJedis();
				deletedAnswerPanoramaImageInfoList = new ArrayList<ImageInfoVO>();
				// Redis에 저장된 삭제된 CaptchaImage들의 리스트를 저장하는 부분
				deletedCaptchaImagePool = jedis.lrange(CommonImageInfo.deletedCaptchaImagePoolKey, 0, -1);
				
				// 삭제된 CaptchaImage들의 리스트 크기가 0보다 클때, 즉 삭제된 CaptchaImage가 있을때만 동작하는 부분
				if(deletedCaptchaImagePool.size() > 0) {
					// 삭제된 CaptchaImage 리스트를 반복문을 돌려 answerPanoramaImage정보만 추출하고 리스트형식으로 저장하는 부분
					for(String deletedCaptchaImageKey : deletedCaptchaImagePool) {		
						deleteAnswerPanoramaImageInfo = new ImageInfoVO();
						// 삭제된 CaptchaImage를 split하여 정답PanoramaImage의 키를 추출하는 부분 (1_123+2_234+3_345(캡차이미지) --> [0]: 1_123(정답이미지), [1]: 2_234(보기이미지1), [2]: 3_345(보기이미지2))
						deletedAnswerPanoramaImageKey = deletedCaptchaImageKey.split("\\-")[0];
						// 정답 PanoramaImage를 split하여 bzstNo와 panoTypeCd로 나누고 이를 문자열 배열에 저장하는 부분
						deletedAnswerPanoramaImageKeyArray = deletedAnswerPanoramaImageKey.split("_");
						
						// bzstNo값을 36진수에서 10진수로 변환하고 ImageInfoVO 객체에 저장하는 부분
						deleteAnswerPanoramaImageInfo.setBzstNo((int)Long.parseLong(deletedAnswerPanoramaImageKeyArray[0], 36));
						// panoTypeCd값을 36진수에서 10진수로 변환하고 ImageInfoVO 객체에 저장하는 부분
						deleteAnswerPanoramaImageInfo.setPanoTypeCd(String.valueOf((int)Long.parseLong(deletedAnswerPanoramaImageKeyArray[1], 36)));
					
						// 삭제된 CaptchaImage의 AnswerPanoramaImage에 대한 bzstNo와 panoTypeCd값이 저장된 ImageInfoVO 객체를 리스트에 저장하는 부분 
						deletedAnswerPanoramaImageInfoList.add(deleteAnswerPanoramaImageInfo);
					}
					// 삭제된 CaptchaImage의 AnswerPanoramaImage에 대한 ImageInfoVO 객체들의 리스트를 MySQL에 unused_info 테이블에 insert하는 부분
					imageMapper.insertUnusedImageList(deletedAnswerPanoramaImageInfoList);
					// Redis에 저장된 삭제된 CaptchaImage들의 리스트를 모두 MySQL의 unused_info 테이블로 이동시켰으므로 Redis의 삭제된 CaptchaImage들의 리스트는 모두 삭제한다.
					jedis.del(CommonImageInfo.deletedCaptchaImagePoolKey);
				}
				// MySQL의 isUsed=1인 row를 모두 지우는 부분으로 Redis에서 MySQL의 PanoramaImage를 가져갈때 isUsed=1로 update하고, Batch때 이를 삭제시킨다.
				imageMapper.deleteIsUsedImageInfo();
			}else {
				// Redis가 shutdown되어 있을때는 FAIL을 리턴하고 끝낸다
				log.error("[DeleteUnused-execute] Redis 접속불가");
				contribution.setExitStatus(ExitStatus.FAILED);
			}
		}catch(Exception e) {
			log.error("[DeleteUnused-execute] UserMessage  : Redis의 DeletedList(IssuedCnt에 의해 삭제된 캡차이미지 리스트)에 저장된 정보를 MySQL의 unused_info Table에 insert하는 도중 에러발생");
			log.error("[DeleteUnused-execute] SystemMessage: {}", e.getMessage());
			log.error("[DeleteUnused-execute] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
		}
		long end = System.currentTimeMillis();
		log.info("runtime: " + (end-start)/1000.0);
		
		return RepeatStatus.FINISHED;
	} 
}
