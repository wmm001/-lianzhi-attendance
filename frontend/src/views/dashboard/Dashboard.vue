<template>
<div class="dashboard" v-loading="loading">
  <section class="hero"><img :src="hnuLogo" alt="" class="hero-watermark"/><div><p>{{ greeting }}，{{ user.userInfo?.realName }}</p><h2>{{ student?'今天也要记得按时签到':'今天的教学考勤情况一目了然' }}</h2><span>{{ dateText }} · 湖南大学智慧校园</span></div><el-button type="primary" size="large" @click="go(student&&stats.activeSessions?.[0]?checkinTarget(stats.activeSessions[0].id):student?'/face/attendance':'/courses')"><el-icon><Camera v-if="student"/><VideoPlay v-else/></el-icon>{{student?'立即签到':'发起签到'}}</el-button></section>
  <div class="metrics" :class="{teacher:!student}"><article v-for="c in cards" :key="c.label" :style="{'--color':c.color,'--soft':c.soft}"><i><el-icon><component :is="c.icon"/></el-icon></i><div><strong>{{c.value}}</strong><b>{{c.label}}</b><small>{{c.note}}</small></div></article></div>

  <template v-if="student">
    <div class="grid upper">
      <el-card><template #header><Header title="今日课程" :sub="`${stats.todayCourses?.length||0} 门课程`" action="全部课程" @action="go('/courses')"/></template>
        <div v-if="stats.todayCourses?.length" class="list"><div v-for="c in stats.todayCourses" :key="c.id" class="course"><time><b>{{shortTime(c.startTime)}}</b><span>{{shortTime(c.endTime)}}</span></time><div><b>{{c.courseName}}</b><span>{{c.classroom||'教室待定'}} · {{c.courseCode}}</span></div><el-tag v-if="c.attendanceStatus" :type="statusType(c.attendanceStatus)">{{statusText(c.attendanceStatus)}}</el-tag><el-button v-else-if="c.session?.active" type="primary" @click="go(checkinTarget(c.id))">去签到</el-button><el-tag v-else type="info">暂未开始</el-tag></div></div>
        <el-empty v-else description="今天没有课程，轻松一下吧" :image-size="70"/>
      </el-card>
      <el-card class="live-card"><template #header><Header title="签到状态" sub="实时查看当前签到"/></template>
        <div v-if="stats.activeSessions?.length" class="live"><label>正在签到</label><h3>{{stats.activeSessions[0].courseName}}</h3><p>{{stats.activeSessions[0].classroom||'教室待定'}}</p><div>请在 {{dateTime(stats.activeSessions[0].session?.endsAt)}} 前完成</div><el-button type="primary" size="large" @click="go(checkinTarget(stats.activeSessions[0].id))">立即人脸签到</el-button></div>
        <div v-else class="empty"><el-icon><CircleCheck/></el-icon><h3>当前没有进行中的签到</h3><p>签到开始后，这里会显示快捷入口</p></div>
      </el-card>
    </div>
    <div class="grid lower">
      <el-card><template #header><Header title="最近考勤" sub="最近 3 条记录" action="查看全部" @action="go('/attendance')"/></template>
        <div v-if="stats.recentRecords?.length" class="list"><div v-for="r in stats.recentRecords" :key="r.id" class="record"><time>{{compactDate(r.attendance_date)}}</time><div><b>{{r.course_name}}</b><span>{{r.check_in_time?'签到 '+dateTime(r.check_in_time):'无签到时间'}}</span></div><el-tag :type="statusType(r.status)">{{statusText(r.status)}}</el-tag></div></div><el-empty v-else description="暂无考勤记录" :image-size="64"/>
      </el-card>
      <el-card><template #header><Header title="近期出勤趋势" sub="近 14 天" :action="rate(stats.rate)"/></template><div ref="chartEl" class="chart"/></el-card>
    </div>
  </template>

  <div v-else class="grid teacher-main">
    <el-card><template #header><Header title="正在进行的签到" sub="实时签到进度"/></template>
      <div v-if="stats.activeSessions?.length" class="sessions"><div v-for="s in stats.activeSessions" :key="s.id"><header><div><h3>{{s.courseName}}</h3><p>{{s.classroom||'教室待定'}} · {{dateTime(s.endsAt)}} 结束</p></div><strong>{{s.checkedIn}}/{{s.studentCount}}</strong></header><el-progress :percentage="s.progress" :stroke-width="12"/></div></div>
      <div v-else class="teacher-empty"><el-icon><Timer/></el-icon><div><h3>当前没有进行中的签到</h3><p>从课程页面选择课程并发起签到</p></div><el-button type="primary" @click="go('/courses')">去发起</el-button></div>
    </el-card>
    <el-card><template #header><Header title="待办事项" sub="需要关注的考勤信息"/></template>
      <button class="todo" @click="go('/attendance/leaves')"><i class="yellow"><el-icon><Bell/></el-icon></i><div><b>请假审批</b><span>等待处理的学生申请</span></div><strong>{{stats.pendingLeaves||0}}</strong></button>
      <button class="todo" @click="go('/attendance/statistics')"><i class="red"><el-icon><Warning/></el-icon></i><div><b>异常学生</b><span>出勤率低或存在缺勤</span></div><strong>{{stats.abnormalStudents||0}}</strong></button>
      <button class="todo" @click="go('/attendance/statistics')"><i class="blue"><el-icon><TrendCharts/></el-icon></i><div><b>统计分析</b><span>查看课程与班级趋势</span></div><el-icon><ArrowRight/></el-icon></button>
    </el-card>
  </div>
</div>
</template>

<script setup>
import {computed,defineComponent,h,markRaw,nextTick,onMounted,onUnmounted,ref} from 'vue'
import {useRouter} from 'vue-router';import {useUserStore} from '@/stores/user';import {getAttendanceSummary} from '@/api/attendance'
import {init,use} from 'echarts/core';import {LineChart} from 'echarts/charts';import {GridComponent,TooltipComponent} from 'echarts/components';import {CanvasRenderer} from 'echarts/renderers'
import {Calendar,Camera,CircleCheck,TrendCharts,Bell,Warning,Timer,VideoPlay,ArrowRight,UserFilled} from '@element-plus/icons-vue';use([LineChart,GridComponent,TooltipComponent,CanvasRenderer])
import hnuLogo from '@/assets/brand/hnu-logo.png'
const Header=defineComponent({props:{title:String,sub:String,action:String},emits:['action'],setup(p,{emit}){return()=>h('div',{class:'title'},[h('div',[h('b',p.title),h('span',p.sub)]),p.action?h('button',{onClick:()=>emit('action')},p.action):null])}})
const router=useRouter(),user=useUserStore(),stats=ref({}),loading=ref(false),chartEl=ref(null);let chart,observer
const student=computed(()=>user.isStudent),now=new Date(),greeting=now.getHours()<12?'早上好':now.getHours()<18?'下午好':'晚上好',dateText=now.toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'long'})
const rate=v=>v==null?'—':`${v}%`,cards=computed(()=>student.value?[
 {label:'今日课程',value:stats.value.todayCourses?.length||0,note:stats.value.activeSessions?.length?'有课程正在签到':'查看今日安排',icon:markRaw(Calendar),color:'#b12f35',soft:'#fbefef'},
 {label:'本月迟到',value:stats.value.monthLate||0,note:'保持准时到课',icon:markRaw(Timer),color:'#dc9200',soft:'#fffaeb'},
 {label:'本月缺勤 / 请假',value:`${stats.value.monthAbsent||0} / ${stats.value.monthLeave||0}`,note:'缺勤与请假次数',icon:markRaw(Warning),color:'#d92d20',soft:'#fff1f1'},
 {label:'本月出勤率',value:rate(stats.value.rate),note:'已批准请假不计入',icon:markRaw(TrendCharts),color:'#7f56d9',soft:'#f4f3ff'}]:[
 {label:'今日课程',value:stats.value.todayCourseCount||0,note:'今天的教学安排',icon:markRaw(Calendar),color:'#b12f35',soft:'#fbefef'},
 {label:'进行中签到',value:stats.value.activeSessions?.length||0,note:'实时查看签到进度',icon:markRaw(VideoPlay),color:'#039855',soft:'#ecfdf3'},
 {label:'待审批请假',value:stats.value.pendingLeaves||0,note:'等待教师处理',icon:markRaw(Bell),color:'#dc9200',soft:'#fffaeb'},
 {label:'本月平均出勤率',value:rate(stats.value.monthRate),note:'所授课程综合统计',icon:markRaw(TrendCharts),color:'#7f56d9',soft:'#f4f3ff'},
 {label:'异常学生',value:stats.value.abnormalStudents||0,note:'出勤率低或有缺勤',icon:markRaw(UserFilled),color:'#d92d20',soft:'#fff1f1'}])
const statuses={PRESENT:['正常','success'],LATE:['迟到','warning'],ABSENT:['缺勤','danger'],LEAVE:['请假','info']},statusText=v=>statuses[v]?.[0]||v||'未签到',statusType=v=>statuses[v]?.[1]||'info'
const shortTime=v=>String(v||'').slice(0,5),compactDate=v=>{const d=new Date(v);return `${d.getMonth()+1}月${d.getDate()}日`},dateTime=v=>v?String(v).replace('T',' ').slice(5,16):'—',go=p=>router.push(p),checkinTarget=id=>({path:'/face/attendance',query:{courseId:id}})
function render(){if(!student.value||!chartEl.value)return;if(!chart)chart=init(chartEl.value);const rows=stats.value.trend||[];chart.setOption({grid:{left:38,right:15,top:20,bottom:28},tooltip:{trigger:'axis'},xAxis:{type:'category',boundaryGap:false,data:rows.map(r=>compactDate(r.attendance_date)),axisTick:{show:false}},yAxis:{min:0,max:100,axisLabel:{formatter:'{value}%'},splitLine:{lineStyle:{color:'#f0e8e5'}}},series:[{type:'line',smooth:true,connectNulls:true,symbolSize:7,data:rows.map(r=>r.rate),lineStyle:{width:3,color:'#b12f35'},itemStyle:{color:'#b12f35'},areaStyle:{color:'rgba(177,47,53,.12)'}}]},true)}
async function load(){loading.value=true;try{stats.value=await getAttendanceSummary();await nextTick();render()}finally{loading.value=false}}
onMounted(()=>{load();observer=new ResizeObserver(()=>chart?.resize());if(chartEl.value)observer.observe(chartEl.value)});onUnmounted(()=>{observer?.disconnect();chart?.dispose()})
</script>

<style scoped>
.dashboard{display:grid;gap:18px;color:var(--text)}.hero{min-height:136px;padding:26px 30px;border-radius:18px;background:linear-gradient(120deg,#721f25,#a92b31 62%,#c1464c);color:#fff;display:flex;align-items:center;justify-content:space-between;box-shadow:0 14px 30px rgba(127,32,38,.27);position:relative;overflow:hidden}.hero>div,.hero>button{position:relative;z-index:1}.hero-watermark{position:absolute;height:180px;width:auto;max-width:none;right:-25px;top:-20px;opacity:.09;filter:grayscale(1) brightness(3)}.hero p,.hero span{margin:0;color:#f4dfe0}.hero h2{margin:5px 0 8px;font-size:25px}.hero button{background:#fff;color:#92262c;border:0}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.metrics.teacher{grid-template-columns:repeat(5,1fr)}.metrics article{background:#fff;border:1px solid #e8dfdc;border-top:3px solid var(--color);border-radius:14px;padding:18px;display:flex;align-items:center;gap:13px;min-width:0}.metrics article>i{width:45px;height:45px;display:grid;place-items:center;flex:none;border-radius:12px;background:var(--soft);color:var(--color);font-size:23px}.metrics strong,.metrics b,.metrics small{display:block}.metrics strong{font-size:25px}.metrics b{font-size:13px;margin-top:4px}.metrics small{color:#988b8d;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.grid{display:grid;gap:16px}.upper,.teacher-main{grid-template-columns:minmax(0,1.55fr) minmax(300px,1fr)}.lower{grid-template-columns:1fr 1fr}.dashboard :deep(.el-card){border-radius:15px;border-color:#e8dfdc}.title{display:flex;justify-content:space-between;align-items:center}.title>div{display:grid;gap:3px}.title span{font-size:12px;color:#988b8d}.dashboard :deep(.title button){border:0;background:none;color:var(--brand);cursor:pointer;font:inherit;padding:0}.list{display:grid}.course,.record{display:flex;align-items:center;gap:15px;padding:13px 0;border-bottom:1px solid #f1eae7}.course:last-child,.record:last-child{border:0}.course>time{width:52px;border-right:2px solid #e9cfd0;text-align:center}.course>time span,.course>div span,.record span,.sessions p{display:block;font-size:12px;color:#988b8d;margin-top:4px}.course>div,.record>div{flex:1}.record>time{width:55px;color:#706668}.live-card{background:linear-gradient(145deg,#fdf7f5,#fff)}.live label{font-size:11px;color:#039855;font-weight:700;letter-spacing:1px}.live h3{font-size:23px;margin:13px 0 4px}.live p{color:#706668}.live>div{padding:11px;background:var(--brand-soft);border-radius:9px;color:#8f282d;font-size:13px;margin:18px 0}.empty{text-align:center;padding:24px;color:#988b8d}.empty>.el-icon{font-size:42px;color:#12b76a}.empty h3{color:#514749;margin:10px 0 4px}.empty p{margin:0}.chart{height:210px}.sessions>div{padding:7px 0 18px}.sessions>div+div{border-top:1px solid #f1eae7;padding-top:18px}.sessions header{display:flex;justify-content:space-between;align-items:center}.sessions h3,.sessions p{margin:0 0 5px}.sessions header>strong{font-size:22px;color:var(--brand)}.teacher-empty{display:flex;align-items:center;gap:15px;padding:32px 5px}.teacher-empty>.el-icon{font-size:38px;color:#988b8d}.teacher-empty>div{flex:1}.teacher-empty h3,.teacher-empty p{margin:3px}.teacher-empty p{font-size:13px;color:#988b8d}.todo{width:100%;display:flex;align-items:center;gap:12px;padding:13px 3px;border:0;border-bottom:1px solid #f1eae7;background:none;text-align:left;cursor:pointer;color:#44393b}.todo:last-child{border:0}.todo>i{width:41px;height:41px;display:grid;place-items:center;border-radius:11px;font-size:19px}.todo>div{display:grid;gap:3px;flex:1}.todo span{font-size:12px;color:#988b8d}.todo>strong{font-size:21px}.yellow{background:#fffaeb;color:#dc6803}.red{background:#fff1f1;color:#d92d20}.blue{background:var(--brand-soft);color:var(--brand)}
@media(max-width:1250px){.metrics.teacher{grid-template-columns:repeat(3,1fr)}}@media(max-width:1000px){.metrics{grid-template-columns:repeat(2,1fr)}.upper,.lower,.teacher-main{grid-template-columns:1fr}}@media(max-width:600px){.hero{padding:20px;gap:14px}.hero h2{font-size:19px}.hero button{min-width:108px}.metrics,.metrics.teacher{grid-template-columns:1fr 1fr;gap:9px}.metrics article{padding:13px 10px;gap:9px}.metrics article>i{width:37px;height:37px;font-size:19px}.metrics strong{font-size:20px}.metrics small{display:none}}
</style>
