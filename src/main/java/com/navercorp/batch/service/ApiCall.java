package com.navercorp.batch.service;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.navercorp.batch.domain.CommonImageInfo;

@Service
public class ApiCall {
	@Value("${" + CommonImageInfo.projectEnvironment +".panoramaAPI.panoramaImageIDAPI}")
	private String panoramaImageIDAPI;															// var : PanoramaImage의 bzstNo, PanoTypeCd를 리턴받는 API의 URL을 저장하는 변수
	@Value("${" + CommonImageInfo.projectEnvironment +".panoramaAPI.panoramaImageAPI}")
	private String parnoamaImageAPI;															// var : PanoramaImage의 BufferedImage를 리턴받는 API의 URL을 저장하는 변수
	
	private static final Logger log = LoggerFactory.getLogger(ApiCall.class);

	/*
	 * Func : String 형식의 url와 HashMap형식의 파라미터를 합쳐 URI형식으로 만들어 리턴하는 함수 
	 */
	private URI uriBuild(String httpUrl, Map<String, String> parameterMap) {
		MultiValueMap<String, String> multiParameterMap = null;		
		try {
			multiParameterMap = new LinkedMultiValueMap<>();
			for(Entry<String, String> entry : parameterMap.entrySet()) {
				multiParameterMap.add(entry.getKey(), entry.getValue());
			}
			return UriComponentsBuilder.fromHttpUrl(httpUrl).queryParams(multiParameterMap).build().toUri();
		}catch(Exception e) {
			log.error("[uriBuild] UserMessage  : PanoramaAPI 호출시 Parameter가 정상적으로 URL에 포함되지 않음");
			log.error("[uriBuild] SystemMessage: {}", e.getMessage());
			log.error("[uriBuild] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			return null;
		}
	}
	
	/*
	 * Func : 파라미터로 전달받은 type과 limit를 함께 PanoramaAPI로 호출하여 bzstNo, panoTypeCd를 리턴하는 함수
	 */
	public String getPanoramaImageIDList(String type, int limit) throws Exception {
		Map<String, String> parameterMap = null;
		URI uri = null;
		URL url = null;
		try {
			// 파라미터를 hashMap형식으로 만드는 부분
			parameterMap = new HashMap<>();
			parameterMap.put(CommonImageInfo.limitKey, String.valueOf(limit));
			
			uri = uriBuild(panoramaImageIDAPI+type, parameterMap);
			
			url = new URL(uri.toString());
			// timeout을 설정하는 부분
			url.openConnection().setConnectTimeout(CommonImageInfo.apiCallConnectionTimeout);
			url.openConnection().setReadTimeout(CommonImageInfo.apiCallReadTimeout);
			
			// AutoCloseable 문법을 사용하여 close()를 하지 않아도 해당 객체들이 자동으로 close() 되도록하는 부분
			try (InputStream input = url.openStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.name()))){
				return reader.readLine();
			}
		}catch(Exception e) {
			log.error("[getPanoramaImageIDList] UserMessage  : PanoramaAPI bzstNo, panoTypeCd가 정상적으로 호출되지 않음");
			log.error("[getPanoramaImageIDList] SystemMessage: {}", e.getMessage());
			log.error("[getPanoramaImageIDList] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			return null;
		}
	}
	
	/*
	 * Func : API를 호출하여 전달받은 bzstNo, panoTypeCd를 함께 PanoramaAPI를 호출하여 BufferedImage를 리턴하는 함수
	 */
	public BufferedImage getPanoramaImage(int bzstNo, String panoTypeCd, int width, int height) throws Exception {
		Map<String, String> parameterMap = null;
		URI uri = null;
		URL url = null;
		BufferedImage panoramaImage = null;
		try {
			// 파라미터를 hashMap형식으로 만드는 부분
			parameterMap = new HashMap<>();
			parameterMap.put(CommonImageInfo.widthKey, String.valueOf(width));
			parameterMap.put(CommonImageInfo.heightKey, String.valueOf(height));
			
			uri = uriBuild(parnoamaImageAPI + String.valueOf(bzstNo) + "/" + panoTypeCd, parameterMap);
			
			url = new URL(uri.toString());
			// timeout을 설정하는 부분
			url.openConnection().setConnectTimeout(CommonImageInfo.apiCallConnectionTimeout);
			url.openConnection().setReadTimeout(CommonImageInfo.apiCallReadTimeout);
			
			try(InputStream input = url.openStream()) {
				panoramaImage= ImageIO.read(input);
			}
			return panoramaImage;
		}catch(Exception e) {
			log.error("[getPanoramaImage] UserMessage  : PanoramaAPI Image가 정상적으로 호출되지 않음");
			log.error("[getPanoramaImage] SystemMessage: {}", e.getMessage());
			log.error("[getPanoramaImage] StackTrace   :\n" + Arrays.asList(e.getStackTrace()).toString().replace(",", "\n"));
			return null;
		}
	}
}
