package edu.wylie.crs.integration;

import edu.wylie.crs.dto.OfferingVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 遗留课程目录 Stub。权威字段与前端 Mock 本学期班次对齐，供本地 {@code course_offering} 缓存写入。
 * 本系统不得回写遗留库。
 */
@Component
public class CourseCatalogClientStub implements CourseCatalogClient {

    @Override
    public List<OfferingVO> listCurrentTermOfferings(String term) {
        return List.of(
                offering("CS-301", "软件工程", "计算机科学", "Morgan 博士",
                        List.of("周一", "周三"), 540, 630, "科学楼 204", 8, List.of()),
                offering("MATH-214", "离散数学", "数学", "Lin 教授",
                        List.of("周二", "周四"), 660, 750, "B12 教室", 10, List.of()),
                offering("BUS-110", "管理学原理", "商学", "Patel 博士",
                        List.of("周五"), 780, 930, "西楼 103", 4, List.of()),
                offering("CS-401", "高级数据库", "计算机科学", "Chen 博士",
                        List.of("周一", "周三"), 540, 630, "科学楼 301", 2, List.of("CS-301")),
                offering("PHYS-150", "大学物理", "自然科学", "Kim 教授",
                        List.of("周一", "周四"), 780, 870, "实验楼 105", 6, List.of("PHYS-201")),
                offering("HIST-101", "世界历史", "人文", "Lee 博士",
                        List.of("周三"), 840, 930, "东楼 210", 5, List.of()),
                offering("ART-120", "艺术鉴赏", "艺术", "Dai 博士",
                        List.of("周二"), 930, 1020, "艺术楼 101", 2, List.of()),
                offering("CS-201", "数据结构", "计算机科学", "Chen 博士",
                        List.of("周二", "周四"), 480, 570, "科学楼 206", 3, List.of("CS-101")),
                offering("CS-350", "操作系统", "计算机科学", "Morgan 博士",
                        List.of("周五"), 540, 720, "科学楼 208", 1, List.of("CS-210")),
                offering("MATH-301", "线性代数", "数学", "Lin 教授",
                        List.of("周一", "周三"), 660, 750, "数学楼 201", 5, List.of()),
                offering("ENG-201", "科技写作", "人文", "Lee 博士",
                        List.of("周四"), 540, 630, "东楼 108", 3, List.of()),
                offering("CHEM-101", "普通化学", "自然科学", "Kim 教授",
                        List.of("周二", "周四"), 780, 870, "实验楼 201", 4, List.of()),
                offering("ECON-210", "微观经济学", "商学", "Patel 博士",
                        List.of("周三"), 660, 750, "西楼 201", 7, List.of("BUS-101"))
        );
    }

    private volatile boolean available = true;

    @Override
    public boolean isAvailable() {
        return available;
    }

    /** 测试用：模拟遗留目录不可达 */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    private static OfferingVO offering(
            String id,
            String title,
            String dept,
            String professor,
            List<String> days,
            int start,
            int end,
            String room,
            int seatsTaken,
            List<String> prerequisites
    ) {
        return new OfferingVO(
                id, id, title, dept, professor, days, start, end, room,
                10, seatsTaken, prerequisites, null, false
        );
    }
}
