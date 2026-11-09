package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.exception.BusinessException;
import com.attendance.service.AttendanceAgentService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static com.attendance.security.RequestAuthorization.*;

@RestController
@RequestMapping("/enterprise/agent")
public class EnterpriseAgentController {
    private final AttendanceAgentService agent;

    public EnterpriseAgentController(AttendanceAgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        Object raw = input.get("message");
        if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) {
            throw new BusinessException("请输入问题");
        }
        String message = ((String) raw).trim();
        if (message.length() > 2000) throw new BusinessException("单次问题不能超过 2000 字");
        String previousIntent = input.get("previousIntent") instanceof String ? (String) input.get("previousIntent") : "";
        String previousOffice = input.get("previousOffice") instanceof String ? (String) input.get("previousOffice") : "";
        return Result.success(agent.chat(userId(request), role(request), message, previousIntent, previousOffice));
    }
}
