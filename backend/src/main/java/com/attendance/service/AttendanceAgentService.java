package com.attendance.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** General model chat with role-scoped local attendance facts when the question concerns this system. */
@Service
public class AttendanceAgentService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M月d日");
    private final JdbcTemplate jdbc;
    private final EnterpriseAttendanceService attendance;
    private final DeepSeekAgentClient deepSeek;

    public AttendanceAgentService(JdbcTemplate jdbc, EnterpriseAttendanceService attendance, DeepSeekAgentClient deepSeek) {
        this.jdbc = jdbc;
        this.attendance = attendance;
        this.deepSeek = deepSeek;
    }

    public Map<String, Object> chat(long userId, String role, String message,
                                    String previousIntent, String previousOffice) {
        boolean manager = "ADMIN".equals(role) || "TEACHER".equals(role);
        String question = message.replaceAll("\\s+", "");
        String officeCode = officeCode(question);
        boolean followUp = question.matches("^(那|再看|换成)?(今天|昨天|前天|本周|上周|本月|上月)(呢|的|情况)?[？?]?$") ||
                question.matches("^(那|再看|换成)?(东风校区|第二生活园区|生活园区|主场所|分场所)(呢|的)?[？?]?$");
        if (officeCode.isEmpty() && followUp && ("HQ".equals(previousOffice) || "BRANCH".equals(previousOffice))) {
            officeCode = previousOffice;
        }
        String intent = intent(question);
        if ("unknown".equals(intent) && followUp && List.of("summary", "status", "records", "office").contains(previousIntent)) {
            intent = previousIntent;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "offline");
        result.put("intent", intent);
        result.put("officeCode", officeCode);
        List<String> tools = new ArrayList<>();
        String reply;
        if ("office".equals(intent)) {
            reply = officeAnswer(officeCode, tools);
        } else if ("help".equals(intent)) {
            reply = helpAnswer(question, manager);
        } else if ("summary".equals(intent) || "status".equals(intent) || "records".equals(intent)) {
            LocalDate[] dates = period(question);
            if (dates == null) {
                LocalDate today = LocalDate.now();
                dates = "records".equals(intent) ? new LocalDate[]{today.minusDays(6), today} :
                        new LocalDate[]{today, today};
            }
            Long officeId = officeId(officeCode, tools);
            reply = manager ? managerAnswer(intent, officeId, dates, tools) :
                    personalAnswer(userId, intent, officeId, dates, tools);
            result.put("period", dates[0] + "/" + dates[1]);
        } else {
            reply = "DeepSeek 暂时不可用，请稍后再试。考勤数据仍可通过本地查询查看。";
        }
        boolean generalQuestion = "unknown".equals(intent);
        java.util.Optional<String> modelReply = deepSeek.explain(message, generalQuestion ? "" : reply);
        if (modelReply.isPresent()) {
            result.put("mode", "deepseek");
            reply = generalQuestion ? modelReply.get() : reply + "\n\nAI 解读：" + modelReply.get();
        }
        result.put("reply", reply);
        result.put("toolsUsed", tools);
        result.put("suggestions", manager ? List.of("今天各场所签到情况", "第二生活园区在哪里", "解释 Java 接口是什么") :
                List.of("我今天签到了吗", "怎么用人脸签到", "解释 Java 接口是什么"));
        return result;
    }

    private String intent(String q) {
        // Route only clear questions about this attendance system; general knowledge goes straight to the model.
        if (containsAny(q, "原理", "概念", "定义", "算法", "代码", "如何实现", "优缺点", "历史", "法律", "政策")) return "unknown";
        boolean attendanceTerm = containsAny(q, "考勤", "签到", "签退", "打卡", "迟到", "出勤", "工时", "补签", "在岗");
        boolean officeTerm = containsAny(q, "办公场所", "生活园区", "东风校区", "主场所", "分场所");
        if (containsAny(q, "怎么", "如何", "流程", "方法", "帮助", "教程", "不会", "失败", "无法") &&
                containsAny(q, "人脸签到", "录入人脸", "扫码签到", "二维码签到", "签退", "摄像头")) return "help";
        if (officeTerm && containsAny(q, "哪里", "在哪", "地址", "位置", "有哪些", "什么场所") && !attendanceTerm) return "office";
        if (attendanceTerm && containsAny(q, "明细", "记录", "最近", "哪几天", "哪天")) return "records";
        if (attendanceTerm && containsAny(q, "了吗", "有没有", "是否", "签到了", "签退了吗")) return "status";
        if (attendanceTerm && containsAny(q, "我", "我的", "我们", "今天", "今日", "昨天", "本周", "上周", "本月", "上月", "各场所", "第二生活园区", "东风校区")) return "summary";
        return "unknown";
    }

    private String officeCode(String q) {
        if (containsAny(q, "第二生活园区", "生活园区", "分场所", "分部")) return "BRANCH";
        if (containsAny(q, "东风校区", "主场所", "总部")) return "HQ";
        return "";
    }

    private LocalDate[] period(String q) {
        LocalDate today = LocalDate.now();
        if (q.contains("昨天")) return new LocalDate[]{today.minusDays(1), today.minusDays(1)};
        if (q.contains("前天")) return new LocalDate[]{today.minusDays(2), today.minusDays(2)};
        if (q.contains("本周") || q.contains("这周")) return new LocalDate[]{today.with(DayOfWeek.MONDAY), today};
        if (q.contains("上周")) {
            LocalDate start = today.with(DayOfWeek.MONDAY).minusWeeks(1);
            return new LocalDate[]{start, start.plusDays(6)};
        }
        if (q.contains("本月") || q.contains("这个月")) return new LocalDate[]{today.withDayOfMonth(1), today};
        if (q.contains("上月")) {
            LocalDate last = today.minusMonths(1);
            return new LocalDate[]{last.withDayOfMonth(1), last.withDayOfMonth(last.lengthOfMonth())};
        }
        if (q.contains("今天") || q.contains("今日")) return new LocalDate[]{today, today};
        return null;
    }

    private Long officeId(String code, List<String> tools) {
        if (code.isEmpty()) return null;
        tools.add("office.lookup");
        List<Long> ids = jdbc.query("SELECT id FROM office WHERE code=? AND status=1",
                (rs, index) -> rs.getLong(1), code);
        return ids.isEmpty() ? -1L : ids.get(0);
    }

    private String officeAnswer(String code, List<String> tools) {
        tools.add("office.list");
        String sql = "SELECT code,name,address FROM office WHERE status=1" + (code.isEmpty() ? "" : " AND code=?") + " ORDER BY id";
        List<String> lines = code.isEmpty() ? jdbc.query(sql, (rs, row) -> rs.getString("name") + "：" + rs.getString("address")) :
                jdbc.query(sql, (rs, row) -> rs.getString("name") + "：" + rs.getString("address"), code);
        return lines.isEmpty() ? "没有找到启用中的对应场所。" : "当前场所信息：\n" + String.join("\n", lines) +
                "\n精确入口坐标尚未核实，可在“办公场所”中由管理员补充。";
    }

    private String helpAnswer(String q, boolean manager) {
        if (containsAny(q, "人脸", "摄像头")) return "人脸签到：先在“人脸考勤”录入本人照片；之后正对摄像头，点击“拍照识别并签到”。当天可多次签到，每次都保留明细。";
        if (containsAny(q, "二维码", "扫码")) return "扫码签到：管理员在“办公场所”打开对应场所的动态签到码；员工用手机扫码、登录并确认签到。手机和电脑需能互相访问。";
        if (q.contains("签退")) return "签退：当天签到成功后，在工作台点击“签退”。“我的考勤”会显示签退时间和工时。";
        if (manager) return "可问“今天各场所签到情况”“本月迟到多少次”“第二生活园区在哪里”。管理员还能在“办公场所”生成二维码。";
        return "可问“我今天签到了吗”“我本月考勤情况”“我的最近考勤记录”。人脸与扫码签到入口在左侧菜单。";
    }

    private String personalAnswer(long userId, String intent, Long officeId, LocalDate[] dates, List<String> tools) {
        tools.add("attendance.myRecords");
        List<Map<String, Object>> records = attendance.records(userId, officeId, dates[0], dates[1]);
        String label = periodLabel(dates);
        if ("status".equals(intent) && dates[0].equals(dates[1])) {
            if (records.isEmpty()) return label + "暂无你的签到记录。";
            Map<String, Object> record = records.get(0);
            return label + "已签到，场所：" + record.get("officeName") + "；签到时间：" + formatTime(record.get("checkInTime")) +
                    "；方式：" + method(record) + "；状态：" + status(record) + "；" +
                    (record.get("checkOutTime") == null ? "尚未签退。" : "签退时间：" + formatTime(record.get("checkOutTime")) + "。");
        }
        if ("records".equals(intent)) {
            if (records.isEmpty()) return label + "暂无你的考勤记录。";
            List<String> lines = records.stream().limit(5).map(r -> r.get("workDate") + " " + r.get("officeName") + " " +
                    formatTime(r.get("checkInTime")) + "签到（" + method(r) + "，" + status(r) + "）").toList();
            return label + "的考勤记录（最多显示 5 条）：\n" + String.join("\n", lines);
        }
        Map<Object, Map<String, Object>> firstByDay = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            firstByDay.merge(record.get("workDate"), record, (existing, candidate) ->
                    ((java.time.LocalDateTime) candidate.get("checkInTime")).isBefore(
                            (java.time.LocalDateTime) existing.get("checkInTime")) ? candidate : existing);
        }
        long late = firstByDay.values().stream().filter(r -> "LATE".equals(r.get("status"))).count();
        long checkedOut = records.stream().filter(r -> r.get("checkOutTime") != null)
                .map(r -> r.get("workDate")).distinct().count();
        long minutes = records.stream().mapToLong(r -> r.get("workMinutes") instanceof Number ? ((Number) r.get("workMinutes")).longValue() : 0).sum();
        return label + "你签到 " + firstByDay.size() + " 天（共 " + records.size() + " 次），迟到 " + late + " 天，已签退 " + checkedOut +
                " 天；已完成工时 " + minutes / 60 + " 小时 " + minutes % 60 + " 分钟。";
    }

    private String managerAnswer(String intent, Long officeId, LocalDate[] dates, List<String> tools) {
        tools.add("attendance.aggregate");
        String where = " WHERE a.work_date>=? AND a.work_date<=?" + (officeId == null ? "" : " AND a.office_id=?");
        Object[] args = officeId == null ? new Object[]{dates[0], dates[1]} : new Object[]{dates[0], dates[1], officeId};
        Map<String, Object> totals = jdbc.queryForMap("SELECT COUNT(*) AS total,COUNT(DISTINCT a.user_id) AS people " +
                "FROM work_attendance a" + where, args);
        int total = number(totals.get("total"));
        int late = jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance a" + where +
                " AND a.id=(SELECT MIN(x.id) FROM work_attendance x WHERE x.user_id=a.user_id AND x.work_date=a.work_date)" +
                " AND a.status='LATE'", Integer.class, args);
        int checkedOut = jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance a" + where +
                " AND a.id=(SELECT MAX(x.id) FROM work_attendance x WHERE x.user_id=a.user_id AND x.work_date=a.work_date)" +
                " AND a.check_out_time IS NOT NULL", Integer.class, args);
        String label = periodLabel(dates) + (officeId == null ? "全部场所" : officeId == -1 ? "指定场所" : officeName(officeId, tools));
        if ("records".equals(intent)) {
            tools.add("attendance.recentRecords");
            List<Map<String, Object>> rows = attendance.records(null, officeId, dates[0], dates[1]);
            if (rows.isEmpty()) return label + "暂无考勤记录。";
            List<String> lines = rows.stream().limit(5).map(r -> r.get("workDate") + " " + r.get("employeeName") +
                    "（" + r.get("officeName") + "）" + formatTime(r.get("checkInTime")) + " " + status(r)).toList();
            return label + "共 " + total + " 条记录，最近 5 条：\n" + String.join("\n", lines);
        }
        if (dates[0].equals(dates[1])) {
            tools.add("office.assignedEmployees");
            String staffSql = "SELECT COUNT(*) FROM employee_office eo JOIN user u ON u.id=eo.user_id " +
                    "WHERE u.role='STUDENT' AND u.status=1" + (officeId == null ? "" : " AND eo.office_id=?");
            int staff = officeId == null ? jdbc.queryForObject(staffSql, Integer.class) :
                    jdbc.queryForObject(staffSql, Integer.class, officeId);
            String answer = label + "：应到 " + staff + " 人，已签到 " + number(totals.get("people")) +
                    " 人，未签到 " + Math.max(0, staff - number(totals.get("people"))) + " 人；迟到 " + late +
                    " 人，已签退 " + checkedOut + " 人。";
            if (officeId == null) answer += "\n可继续问“东风校区今天签到情况”或“第二生活园区今天签到情况”。";
            return answer;
        }
        return label + "：签到记录 " + total + " 条，涉及 " + number(totals.get("people")) +
                " 人，迟到 " + late + " 次，已签退 " + checkedOut + " 次。";
    }

    private String officeName(Long id, List<String> tools) {
        tools.add("office.lookup");
        List<String> names = jdbc.query("SELECT name FROM office WHERE id=?", (rs, row) -> rs.getString(1), id);
        return names.isEmpty() ? "指定场所" : names.get(0);
    }

    private String periodLabel(LocalDate[] dates) {
        return dates[0].equals(dates[1]) ? dates[0].format(DATE) :
                dates[0].format(DATE) + "至" + dates[1].format(DATE);
    }

    private String formatTime(Object value) {
        String s = String.valueOf(value);
        return s.length() >= 16 ? s.substring(11, 16) : s;
    }

    private String method(Map<String, Object> record) {
        return "FACE".equals(record.get("method")) ? "人脸" : "二维码";
    }

    private String status(Map<String, Object> record) {
        return "LATE".equals(record.get("status")) ? "迟到" : "正常";
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
}
