package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.entity.*;
import com.attendance.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.attendance.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import static com.attendance.security.RequestAuthorization.*;

@RestController
@RequestMapping("/groups")
public class StudentGroupController {
    @Autowired private StudentGroupMapper groupMapper;
    @Autowired private StudentGroupMemberMapper memberMapper;
    @Autowired private StudentCourseMapper enrollmentMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private UserMapper userMapper;

    @GetMapping("/mine")
    public Result<?> mine(HttpServletRequest request) {
        requireRole(request,"STUDENT"); Long studentId=userId(request);
        List<Map<String,Object>> result=new ArrayList<>();
        for(StudentGroupMember membership:memberMapper.selectList(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getStudentId,studentId))){
            StudentGroup group=groupMapper.selectById(membership.getGroupId()); if(group!=null)result.add(groupView(group,studentId));
        }
        return Result.success(result);
    }

    @GetMapping("/course/{courseId}")
    public Result<?> courseGroups(@PathVariable Long courseId,HttpServletRequest request){
        requireTeacherCourse(request,courseId); List<Map<String,Object>> result=new ArrayList<>();
        for(StudentGroup group:groupMapper.selectList(new LambdaQueryWrapper<StudentGroup>().eq(StudentGroup::getCourseId,courseId).orderByAsc(StudentGroup::getId)))result.add(groupView(group,userId(request)));
        return Result.success(result);
    }

    @PostMapping
    @Transactional
    public Result<?> create(HttpServletRequest request,@RequestParam Long courseId,@RequestParam String name,@RequestParam Long leaderId){
        requireTeacherCourse(request,courseId); if(name==null||name.trim().isEmpty())throw new BusinessException("请输入小组名称");
        requireEnrolled(courseId,leaderId); ensureNotGrouped(courseId,leaderId,null);
        StudentGroup group=new StudentGroup();group.setCourseId(courseId);group.setGroupName(name.trim());group.setLeaderId(leaderId);group.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(group);addMembership(group.getId(),leaderId);return Result.success("小组创建成功",groupView(group,userId(request)));
    }

    @PostMapping("/{groupId}/members")
    @Transactional
    public Result<?> add(HttpServletRequest request,@PathVariable Long groupId,@RequestParam Long studentId){
        StudentGroup group=requireTeacherGroup(request,groupId);requireEnrolled(group.getCourseId(),studentId);ensureNotGrouped(group.getCourseId(),studentId,groupId);
        if(memberMapper.selectCount(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId,groupId).eq(StudentGroupMember::getStudentId,studentId))==0)addMembership(groupId,studentId);
        return Result.success("成员添加成功",groupView(group,userId(request)));
    }

    @PutMapping("/{groupId}/leader/{studentId}")
    @Transactional
    public Result<?> setLeader(HttpServletRequest request,@PathVariable Long groupId,@PathVariable Long studentId){
        StudentGroup group=requireTeacherGroup(request,groupId);
        if(memberMapper.selectCount(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId,groupId).eq(StudentGroupMember::getStudentId,studentId))==0)throw new BusinessException("该学生不是本组成员");
        group.setLeaderId(studentId);groupMapper.updateById(group);return Result.success("组长已指定",groupView(group,userId(request)));
    }

    @DeleteMapping("/{groupId}/members/{studentId}")
    public Result<?> remove(HttpServletRequest request,@PathVariable Long groupId,@PathVariable Long studentId){
        StudentGroup group=requireTeacherGroup(request,groupId);if(group.getLeaderId().equals(studentId))throw new BusinessException("请先指定其他组长再移除");
        memberMapper.delete(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId,groupId).eq(StudentGroupMember::getStudentId,studentId));return Result.success("成员已移除",null);
    }

    @DeleteMapping("/{groupId}")
    @Transactional
    public Result<?> deleteGroup(HttpServletRequest request,@PathVariable Long groupId){
        requireTeacherGroup(request,groupId);memberMapper.delete(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId,groupId));groupMapper.deleteById(groupId);return Result.success("小组已删除",null);
    }

    private void requireTeacherCourse(HttpServletRequest request,Long courseId){requireRole(request,"TEACHER");Course course=courseMapper.selectById(courseId);if(course==null)throw new BusinessException(404,"课程不存在");if(!userId(request).equals(course.getTeacherId()))throw new BusinessException(403,"只能管理自己课程的小组");}
    private StudentGroup requireTeacherGroup(HttpServletRequest request,Long groupId){StudentGroup group=groupMapper.selectById(groupId);if(group==null)throw new BusinessException(404,"小组不存在");requireTeacherCourse(request,group.getCourseId());return group;}
    private void requireEnrolled(Long courseId,Long studentId){if(enrollmentMapper.selectCount(new LambdaQueryWrapper<StudentCourse>().eq(StudentCourse::getCourseId,courseId).eq(StudentCourse::getStudentId,studentId).eq(StudentCourse::getStatus,1))==0)throw new BusinessException("该学生不在课程名单中");}
    private void ensureNotGrouped(Long courseId,Long studentId,Long allowedGroupId){for(StudentGroupMember membership:memberMapper.selectList(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getStudentId,studentId))){StudentGroup group=groupMapper.selectById(membership.getGroupId());if(group!=null&&courseId.equals(group.getCourseId())&&!Objects.equals(group.getId(),allowedGroupId))throw new BusinessException("该学生已分配到本课程其他小组");}}
    private void addMembership(Long groupId,Long studentId){StudentGroupMember item=new StudentGroupMember();item.setGroupId(groupId);item.setStudentId(studentId);item.setJoinedAt(LocalDateTime.now());memberMapper.insert(item);}
    private Map<String,Object> groupView(StudentGroup group,Long viewerId){Map<String,Object> result=new LinkedHashMap<>();result.put("id",group.getId());result.put("courseId",group.getCourseId());Course course=courseMapper.selectById(group.getCourseId());result.put("courseName",course==null?"":course.getCourseName());result.put("name",group.getGroupName());result.put("leaderId",group.getLeaderId());result.put("isLeader",viewerId.equals(group.getLeaderId()));List<Map<String,Object>> members=new ArrayList<>();for(StudentGroupMember membership:memberMapper.selectList(new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId,group.getId()))){User user=userMapper.selectById(membership.getStudentId());if(user==null)continue;Map<String,Object> row=new LinkedHashMap<>();row.put("id",user.getId());row.put("realName",user.getRealName());row.put("studentId",user.getStudentId());row.put("leader",user.getId().equals(group.getLeaderId()));row.put("faceRegistered",user.getFaceFeatures()!=null);members.add(row);}result.put("members",members);return result;}
}
