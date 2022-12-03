package com.navercorp.batch.mapper;

import java.util.List;
import com.navercorp.batch.domain.*;

public interface ImageMapper {
	// 모든 Image Type(school..) 리스트 select
	public List<ImageTypeVO> selectImageTypeList() throws Exception;
	// 모든 Image 사용용도 Type(answer, exam) 리스트 select
	public List<ImageTypeVO> selectImageUsageTypeList() throws Exception;
	// Image정보 insert시 List 데이터를 받아서 insert
	public void insertImageInfoList(List<ImageInfoVO> mysqlPanoramaImagePoolInfoList) throws Exception;
	// image id로 중복확인 select
	public int selectDuplicateImage(String imageKey) throws Exception;
	// image_info TBL의 row count를 select
	public int selectImageInfoCnt() throws Exception;
	// Redis에서 RDB에 가져간 갯수만큼 image_info TBL에서 delete
	public void deleteIsUsedImageInfo() throws Exception;
	// RDB에 저장된 데이터의 갯수를 각 타입별로 select
	public List<ImageAllTypeCntVO> selectImageAllTypeList() throws Exception;
	// unused_info TBL에 사용하지 않는 Image insert
	public void insertUnusedImageList(List<ImageInfoVO> imageUnusedInfo) throws Exception;
	// image ID가 사용하지 않는 이미지인지 체크하기 위한 select
	public int selectCheckUnusedImage(String imageKey) throws Exception;
}
