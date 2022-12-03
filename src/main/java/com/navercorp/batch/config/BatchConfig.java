package com.navercorp.batch.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.navercorp.batch.tasklet.SetDeleteUnusedTasklet;
import com.navercorp.batch.tasklet.SetMySQLInitTasklet;
import com.navercorp.batch.tasklet.SetMySQLRefillTasklet;
import com.navercorp.batch.tasklet.SetRedisInitTasklet;
import com.navercorp.batch.tasklet.SetRedisRefillTasklet;

@Configuration
@EnableBatchProcessing
public class BatchConfig {
	@Autowired
	private JobBuilderFactory jobBuilderFactory;
	@Autowired
	private StepBuilderFactory stepBuilderFactory;
	@Autowired
	private SetRedisInitTasklet setRedisInitTasklet;
	@Autowired
	private SetRedisRefillTasklet setRedisRefillTasklet;
	@Autowired
	private SetMySQLInitTasklet setMySQLInitTasklet;
	@Autowired
	private SetDeleteUnusedTasklet setDeleteUnusedTasklet; 
	@Autowired
	private SetMySQLRefillTasklet setMySQLRefillTasklet;
	
	/* Redis Step */
	// Step : redis에 저장된 PanoramaImagePool이 모두 비어있는 경우 해당 Step을 실행
	public Step redisInitStep() {
		return stepBuilderFactory.get("redisInitStep")
				.tasklet(setRedisInitTasklet)
				.build();
	}
	// Step : redis에 저장된 PanoramaImagePool의 부족한 개수만큼 채우는 Step
	public Step redisRefillStep() {
		return stepBuilderFactory.get("redisRefillStep")
				.tasklet(setRedisRefillTasklet)
				.build();
	}
	
	/* MySQL Step */
	// Step : Redis내에 DeletedList에 있는 모든 CaptchaImage들을 unused_info 테이블로 이동시키고
	//        MySQL내에 isUsed=1인 row들을 모두 삭제하는 Step (isUsed=1인 row는 Redis에서 PanoramaImage가 부족해 가져간 row들을 의미)
	public Step deleteUnusedStep() {
		return stepBuilderFactory.get("deleteUnusedStep")
				.tasklet(setDeleteUnusedTasklet)
				.build();
	}
	// Step : MySQL에 저장된 PanoramaImagePool이 모두 비어있는 경우 해당 Step을 실행
	public Step initMySQLStep() {
		return stepBuilderFactory.get("initMySQLStep")
				.tasklet(setMySQLInitTasklet)
				.build();
	}
	// Step : MySQL에 저장된 PanoramaImagePool의 부족한 개수만큼 채우는 Step
	public Step refillMySQLStep() {
		return stepBuilderFactory.get("refillMySQLStep")
				.tasklet(setMySQLRefillTasklet)
				.build();
	}
	
	@Bean
	public Job baseJob() {
		return jobBuilderFactory.get("baseJob")
				.incrementer(new RunIdIncrementer())
				.start(deleteUnusedStep())	
					.on("FAILED")				// deleteUnusedStep 실패시는 Redis 접속이 되지 않는 경우이므로 이때는 Redis 관련된 Step을 뛰어넘고 MySQL 관련된 Step을 바로 실행시킨다
					.to(initMySQLStep())
					.next(refillMySQLStep())
					.on("*")
					.end()
				.from(deleteUnusedStep())
					.on("*")
					.to(redisInitStep())
					.next(redisRefillStep())
					.next(initMySQLStep())
					.next(refillMySQLStep())
					.on("*")
					.end()
				.end()
				.build();
	}
}
