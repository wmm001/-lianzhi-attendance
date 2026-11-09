package com.attendance.service;

import com.attendance.entity.StudentGroup;
import com.attendance.entity.StudentGroupMember;
import com.attendance.mapper.StudentGroupMapper;
import com.attendance.mapper.StudentGroupMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StudentGroupAccessService {
    @Autowired private StudentGroupMapper groupMapper;
    @Autowired private StudentGroupMemberMapper memberMapper;

    public boolean isLeader(Long userId) {
        return userId != null && groupMapper.selectCount(new LambdaQueryWrapper<StudentGroup>()
                .eq(StudentGroup::getLeaderId, userId)) > 0;
    }

    public boolean canLeaderManage(Long leaderId, Long memberId) {
        return canLeaderManage(leaderId, memberId, null);
    }

    public boolean canLeaderManage(Long leaderId, Long memberId, Long courseId) {
        if (leaderId == null || memberId == null) return false;
        for (StudentGroup group : groupMapper.selectList(new LambdaQueryWrapper<StudentGroup>().eq(StudentGroup::getLeaderId, leaderId))) {
            if (courseId != null && !courseId.equals(group.getCourseId())) continue;
            if (memberMapper.selectCount(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId, group.getId()).eq(StudentGroupMember::getStudentId, memberId)) > 0) return true;
        }
        return false;
    }
}
