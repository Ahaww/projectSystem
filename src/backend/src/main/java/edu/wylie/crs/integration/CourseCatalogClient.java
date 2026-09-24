package edu.wylie.crs.integration;

import edu.wylie.crs.dto.OfferingVO;

import java.util.List;

/**
 * 遗留课程目录（Ingres / DEC VAX）只读访问。
 * 新系统不得回写该库；可用中间层缓存满足延迟要求。
 */
public interface CourseCatalogClient {

    List<OfferingVO> listCurrentTermOfferings(String term);

    boolean isAvailable();
}
