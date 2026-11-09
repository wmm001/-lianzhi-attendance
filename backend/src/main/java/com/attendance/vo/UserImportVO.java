package com.attendance.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Excel 批量导入学生/教师的结果。 */
@Data
public class UserImportVO {

    /** 导入的角色：STUDENT / TEACHER */
    private String role;

    /** Excel 数据行数（不含表头和空行） */
    private Integer totalCount;

    /** 校验通过的条数 */
    private Integer successCount;

    /** 校验失败的条数 */
    private Integer failCount;

    /** 是否整批回滚（存在任意失败时整批不入库） */
    private Boolean rolledBack;

    /** 逐条失败原因 */
    private List<Failure> failures = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {
        /** Excel 中的实际行号（表头为第 1 行） */
        private Integer row;
        private String username;
        private String realName;
        private String reason;
    }
}
