<template>
  <div class="overview" v-loading="loading">
    <section class="hero"><div><p class="eyebrow">{{ manager ? '企业考勤管理中心' : '员工工作台' }}</p><h1>{{ greeting }}，{{ user.userInfo?.realName || '欢迎回来' }}</h1><p>{{ manager ? '实时掌握各办公场所的签到情况。' : '完成签到、查看记录，让每天的工作有据可查。' }}</p></div><div class="hero-actions"><el-button v-if="manager" type="primary" @click="router.push('/offices')">生成场所二维码</el-button><el-button v-else type="primary" @click="router.push('/face')">人脸签到</el-button><el-button v-if="!manager" plain @click="router.push('/qr-checkin')">二维码签到</el-button></div></section>
    <div class="metrics"><article v-for="item in cards" :key="item.label"><span :style="{ background:item.soft, color:item.color }"><el-icon><component :is="item.icon" /></el-icon></span><div><small>{{ item.label }}</small><strong>{{ item.value }}</strong><p>{{ item.note }}</p></div></article></div>
    <div class="columns"><el-card><template #header><div class="card-head"><b>近七天签到趋势</b><small>实时汇总</small></div></template><div ref="chartEl" class="trend"></div></el-card>
      <el-card v-if="manager"><template #header><div class="card-head"><b>今日场所分布</b><small>当前在岗 {{ data.todayOnSite ?? 0 }} · 已签退 {{ data.todayCheckedOut ?? 0 }}</small></div></template><div v-if="data.byOffice?.length" class="office-list"><div v-for="office in data.byOffice" :key="office.name"><span>{{ office.name }}</span><el-progress :percentage="percent(office.count)" :show-text="false" /><b>{{ office.count }} 人</b><small>已签退 {{ office.checkedOut }} 人</small></div></div><el-empty v-else description="暂无场所数据" /></el-card>
      <el-card v-else><template #header><div class="card-head"><b>我的考勤</b><small>{{ me.officeName || '尚未分配场所' }}</small></div></template><div class="my-status"><span :class="data.myToday ? 'ok' : 'pending'">{{ data.myToday ? `今日已签到 ${data.todayCheckInCount || 1} 次` : '今日待签到' }}</span><h3>{{ data.myToday?.checkInTime?.replace('T',' ').slice(11,16) || '— : —' }}</h3><p>{{ data.myToday ? `${data.myToday.officeName} · 最近一次${data.myToday.method === 'FACE' ? '人脸识别' : '二维码'}签到` : '请选择人脸或二维码方式完成签到' }}</p><el-button v-if="data.myToday && !data.myToday.checkOutTime" plain @click="finish">签退</el-button><p v-if="data.myToday?.checkOutTime">已于 {{ data.myToday.checkOutTime.replace('T',' ').slice(11,16) }} 签退</p></div></el-card>
    </div>
    <el-card v-if="manager"><template #header><div class="card-head"><b>今日签到时段</b><small>按小时统计，便于查看到岗高峰</small></div></template><div ref="hourlyEl" class="hourly-chart"></div></el-card>
    <el-card v-if="manager"><template #header><div class="card-head"><b>各场所近七天签到趋势</b><small>同一员工一天多次签到只计一人</small></div></template><div ref="officeTrendEl" class="office-trend-chart"></div></el-card>
    <el-card v-if="manager"><template #header><div class="card-head"><b>近三十天场所签到热力图</b><small>按场所与首次签到小时汇总</small></div></template><div ref="officeHeatmapEl" class="office-heatmap-chart"></div></el-card>
    <el-card v-if="manager"><template #header><div class="card-head"><b>近三十天员工工时</b><small>已签退记录累计</small></div></template><el-table :data="data.workHours || []" stripe empty-text="暂无已签退记录"><el-table-column prop="employeeName" label="员工" min-width="120" /><el-table-column prop="employeeNumber" label="工号" min-width="130" /><el-table-column prop="days" label="出勤天数" width="110" /><el-table-column prop="sessions" label="签到记录" width="110" /><el-table-column label="累计工时" min-width="120"><template #default="scope">{{ workTime(scope.row.minutes) }}</template></el-table-column></el-table></el-card>
    <el-card v-if="manager"><template #header><div class="card-head"><b>下一工作日缺勤趋势</b><small>预测日期 {{ prediction.targetDate || '—' }}</small></div></template><p class="prediction-note">{{ prediction.method }}</p><el-table :data="prediction.employees || []" stripe empty-text="暂无员工数据"><el-table-column prop="employeeName" label="员工" min-width="110" /><el-table-column prop="officeName" label="场所" min-width="180" /><el-table-column label="历史工作日" width="115"><template #default="scope">{{ scope.row.attendedWorkdays }}/{{ scope.row.observedWorkdays }}</template></el-table-column><el-table-column label="估计缺勤率" width="120"><template #default="scope">{{ scope.row.estimatedAbsencePercent==null?'样本不足':`${scope.row.estimatedAbsencePercent}%` }}</template></el-table-column><el-table-column label="趋势" width="110"><template #default="scope"><el-tag :type="riskType(scope.row.risk)">{{ riskName(scope.row.risk) }}</el-tag></template></el-table-column></el-table></el-card>
    <section class="quick"><b>快捷入口</b><div><router-link v-for="item in shortcuts" :key="item.path" :to="item.path"><el-icon><component :is="item.icon" /></el-icon><span>{{item.label}}</span><i>→</i></router-link></div></section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { User, Location, CircleCheck, Camera, Grid, Document, DataAnalysis } from '@element-plus/icons-vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, HeatmapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard, getMe, getAbsencePredictions, checkOut } from '@/api/enterprise'

use([BarChart, LineChart, HeatmapChart, GridComponent, TooltipComponent, LegendComponent, VisualMapComponent, CanvasRenderer])
const router = useRouter(), user = useUserStore(), data = ref({}), me = ref({}), prediction = ref({}), loading = ref(false), chartEl = ref(null), hourlyEl = ref(null), officeTrendEl = ref(null), officeHeatmapEl = ref(null)
let chart, hourlyChart, officeTrendChart, officeHeatmapChart, observer
const manager = computed(() => !user.isStudent)
const hour = new Date().getHours(), greeting = hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
const cards = computed(() => manager.value ? [
  { label:'在职员工', value:data.value.totalEmployees ?? '—', note:'已启用员工账号', icon:User, color:'#286de1', soft:'#e8f1ff' },
  { label:'办公场所', value:data.value.totalOffices ?? '—', note:'可用场所总数', icon:Location, color:'#14a27c', soft:'#e4f8f1' },
  { label:'今日已签到', value:data.value.todayCheckedIn ?? '—', note:'实时上报人数', icon:CircleCheck, color:'#6b55df', soft:'#f0edff' },
  { label:'今日已签退', value:data.value.todayCheckedOut ?? '—', note:'已完成签退人数', icon:CircleCheck, color:'#dd8b21', soft:'#fff5e4' }
] : [
  { label:'本月签到', value:data.value.monthCount ?? '—', note:'累计打卡天数', icon:CircleCheck, color:'#286de1', soft:'#e8f1ff' },
  { label:'今日签到次数', value:data.value.todayCheckInCount ?? 0, note:'每次人脸签到都保留记录', icon:CircleCheck, color:'#dd8b21', soft:'#fff5e4' },
  { label:'所属场所', value:me.value.officeName || '未分配', note:'当前办公地点', icon:Location, color:'#14a27c', soft:'#e4f8f1' },
  { label:'人脸状态', value:me.value.faceRegistered ? '已录入' : '待录入', note:'用于摄像头识别', icon:Camera, color:'#6b55df', soft:'#f0edff' }
])
const shortcuts = computed(() => manager.value ? [{path:'/offices',label:'办公场所与二维码',icon:Location},{path:'/employees',label:'员工信息',icon:User},{path:'/records',label:'考勤明细',icon:Document}] : [{path:'/face',label:'人脸考勤',icon:Camera},{path:'/qr-checkin',label:'二维码签到',icon:Grid},{path:'/records',label:'查看记录',icon:Document}])
const percent = count => data.value.todayCheckedIn ? Math.round(count / data.value.todayCheckedIn * 100) : 0
const workTime = minutes => `${Math.floor((minutes || 0) / 60)} 小时 ${String((minutes || 0) % 60).padStart(2, '0')} 分钟`
const riskName = value => ({HIGH:'偏高',MEDIUM:'中等',LOW:'偏低',INSUFFICIENT:'样本不足'}[value] || '样本不足')
const riskType = value => ({HIGH:'danger',MEDIUM:'warning',LOW:'success'}[value] || 'info')
function render() { if (!chartEl.value) return; if (!chart) chart = init(chartEl.value); chart.setOption({ grid:{left:35,right:10,top:25,bottom:25}, tooltip:{trigger:'axis'}, xAxis:{type:'category',data:(data.value.trend||[]).map(x=>x.date.slice(5)),axisTick:{show:false}}, yAxis:{type:'value',minInterval:1,splitLine:{lineStyle:{color:'#eef2f7'}}}, series:[{type:'bar',barWidth:24,itemStyle:{color:'#4389ef',borderRadius:[5,5,0,0]},data:(data.value.trend||[]).map(x=>x.count)}] }); if (hourlyEl.value) { if (!hourlyChart) hourlyChart=init(hourlyEl.value); hourlyChart.setOption({grid:{left:35,right:10,top:20,bottom:35},tooltip:{trigger:'axis'},xAxis:{type:'category',data:(data.value.hourlyTrend||[]).map(x=>x.hour),axisTick:{show:false},axisLabel:{interval:2}},yAxis:{type:'value',minInterval:1,splitLine:{lineStyle:{color:'#eef2f7'}}},series:[{type:'bar',barMaxWidth:23,itemStyle:{color:'#26a69a',borderRadius:[4,4,0,0]},data:(data.value.hourlyTrend||[]).map(x=>x.count)}]}) } if (officeTrendEl.value) { if (!officeTrendChart) officeTrendChart=init(officeTrendEl.value); officeTrendChart.setOption({grid:{left:40,right:20,top:48,bottom:30},legend:{top:4,type:'scroll'},tooltip:{trigger:'axis'},xAxis:{type:'category',data:(data.value.trend||[]).map(x=>x.date.slice(5))},yAxis:{type:'value',minInterval:1,splitLine:{lineStyle:{color:'#eef2f7'}}},series:(data.value.officeTrend||[]).map((office,index)=>({name:office.name,type:'line',smooth:true,symbolSize:7,lineStyle:{width:3},itemStyle:{color:['#4389ef','#26a69a','#e29a35','#865ee3'][index%4]},data:office.counts}))}) } }
function renderHeatmap() {
  if (!officeHeatmapEl.value) return
  if (!officeHeatmapChart) officeHeatmapChart = init(officeHeatmapEl.value)
  const heatmap = data.value.officeHourHeatmap || {}
  const points = heatmap.points || []
  officeHeatmapChart.setOption({
    grid: { left: 135, right: 22, top: 18, bottom: 75 },
    tooltip: { position: 'top', formatter: params => `${(heatmap.offices || [])[params.value[1]] || '场所'}<br/>${String(params.value[0]).padStart(2, '0')}:00 签到 ${params.value[2]} 人次` },
    xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`), splitArea: { show: true }, axisLabel: { interval: 2 } },
    yAxis: { type: 'category', data: (heatmap.offices || []).map(name => name.replace('郑州轻工业大学', '')), splitArea: { show: true }, axisLabel: { width: 110, overflow: 'truncate' } },
    visualMap: { min: 0, max: Math.max(1, ...points.map(point => point[2])), calculable: true, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#eef6ff', '#80b4f4', '#226ac7'] } },
    series: [{ type: 'heatmap', data: points, label: { show: true, formatter: params => params.value[2] || '' }, emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,.3)' } } }]
  })
}
async function load() { loading.value=true; try { const [dashboard,profile,forecast]=await Promise.all([getDashboard(),getMe(),manager.value?getAbsencePredictions():Promise.resolve({})]); data.value=dashboard; me.value=profile; prediction.value=forecast; await nextTick(); render(); renderHeatmap() } finally { loading.value=false } }
async function finish() { await checkOut(); ElMessage.success('签退成功'); await load() }
onMounted(() => { load(); observer = new ResizeObserver(() => { chart?.resize(); hourlyChart?.resize(); officeTrendChart?.resize(); officeHeatmapChart?.resize() }); if (chartEl.value) observer.observe(chartEl.value); if (hourlyEl.value) observer.observe(hourlyEl.value); if (officeTrendEl.value) observer.observe(officeTrendEl.value); if (officeHeatmapEl.value) observer.observe(officeHeatmapEl.value) })
onUnmounted(() => { observer?.disconnect(); chart?.dispose(); hourlyChart?.dispose(); officeTrendChart?.dispose(); officeHeatmapChart?.dispose() })
</script>

<style scoped>
.overview{display:grid;gap:20px}.hero{min-height:170px;border-radius:18px;background:linear-gradient(110deg,#163b70,#1b65ad 68%,#3ca8e6);color:#fff;padding:31px 34px;display:flex;align-items:center;justify-content:space-between;gap:20px;box-shadow:0 14px 28px rgba(21,78,143,.15)}.eyebrow{font-size:11px;letter-spacing:2px;color:#aedaff}.hero h1{font-size:27px;margin:10px 0}.hero p:last-child{color:#cbe5f8}.hero-actions{display:flex;gap:10px}.hero .el-button--primary{background:#fff!important;color:#215ca7!important;border-color:#fff!important}.hero .el-button.is-plain{background:transparent;color:#fff;border-color:#86b9e9}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:15px}.metrics article{background:#fff;border:1px solid #e2e9f3;border-radius:15px;padding:20px;display:flex;gap:15px;min-width:0}.metrics small,.metrics strong,.metrics p{display:block}.metrics small{font-size:12px;color:#8493aa}.metrics strong{font-size:23px;margin:5px 0;color:#17243c;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.metrics p{font-size:11px;color:#9aa6b7;margin:0}.columns{display:grid;grid-template-columns:1.5fr 1fr;gap:18px;min-width:0}.card-head{display:flex;align-items:center;justify-content:space-between}.card-head small{color:#96a3b5}.trend,.hourly-chart,.office-trend-chart{height:260px}.office-list{display:grid;gap:25px;padding:10px 0}.office-list>div{display:grid;grid-template-columns:100px 1fr 42px;align-items:center;gap:12px;font-size:13px}.office-list b{text-align:right}.office-list small{grid-column:2 / 4;color:#9aa6b7;text-align:right;margin-top:-9px}.my-status{text-align:center;padding:13px 0}.my-status>span{font-size:12px;padding:6px 13px;border-radius:20px}.my-status .ok{background:#e4f8ef;color:#13845b}.my-status .pending{background:#fff5e7;color:#b46f1b}.my-status h3{font-size:34px;margin:20px 0 8px}.my-status p{color:#8997aa;font-size:13px;margin:0 0 18px}.quick{background:#fff;border:1px solid #e2e9f3;border-radius:15px;padding:22px}.quick>div{display:grid;grid-template-columns:repeat(3,1fr);gap:13px;margin-top:16px}.quick a{border:1px solid #e5edf8;background:#f8fbff;border-radius:11px;display:flex;align-items:center;gap:10px;padding:15px;color:#36618f}.quick i{margin-left:auto;font-style:normal;color:#a1b4cb}
.office-heatmap-chart{height:310px}
.prediction-note{font-size:12px;color:#8796a9;line-height:1.6;margin:0 0 14px}
@media(max-width:1100px){.metrics{grid-template-columns:repeat(2,1fr)}.columns{grid-template-columns:1fr}}@media(max-width:600px){.hero{padding:22px;display:block}.hero h1{font-size:22px}.hero-actions{margin-top:22px}.metrics{gap:9px}.metrics article{padding:13px;gap:9px}.metrics article>span{width:36px;height:36px;font-size:18px}.metrics strong{font-size:18px}.metrics p{display:none}.quick>div{grid-template-columns:1fr}}
</style>
