<template>
  <div class="agent-page">
    <div class="heading">
      <div><h1>考勤 Agent</h1><p>可以问考勤，也可以问其他问题；考勤数据按登录权限查询。</p></div>
      <el-tag :type="mode==='deepseek'?'success':'info'" effect="plain">{{mode==='deepseek'?'DeepSeek 已连接':'本地查询'}}</el-tag>
    </div>
    <el-card class="chat-card" shadow="never">
      <div ref="conversation" class="conversation" aria-live="polite">
        <div v-for="(entry, index) in messages" :key="index" class="message" :class="entry.from">
          <span class="avatar">{{ entry.from === 'agent' ? '问' : '我' }}</span>
          <div class="bubble"><div class="message-text">{{ entry.text }}</div><small v-if="entry.tools?.length">已查询：{{ toolNames(entry.tools) }}</small></div>
        </div>
        <div v-if="sending" class="message agent"><span class="avatar">问</span><div class="bubble">正在回答…</div></div>
      </div>
      <div class="suggestions"><span>试试这样问</span><el-button v-for="prompt in suggestions" :key="prompt" size="small" plain round :disabled="sending" @click="send(prompt)">{{ prompt }}</el-button></div>
      <form class="composer" @submit.prevent="send()"><el-input v-model="draft" maxlength="2000" show-word-limit placeholder="例如：我本月迟到几次？或解释一下 Java 中的接口" :disabled="sending" /><el-button type="primary" native-type="submit" :loading="sending" :disabled="!draft.trim()">发送</el-button></form>
      <p class="note">普通问题由 DeepSeek 回答，单次生成设有 token 上限；考勤事实先从本地数据库按登录权限查询。员工只能查看自己的考勤。</p>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { askAttendanceAgent } from '@/api/enterprise'

const user = useUserStore()
const draft = ref(''), sending = ref(false), conversation = ref(null)
const mode = ref('offline')
const messages = ref([{ from:'agent', text:'你好！可以问我考勤、场所信息，也可以聊其他问题。涉及你的考勤时，我会先查询系统记录。' }])
const suggestions = ref(user.isStudent ? ['我今天签到了吗', '怎么用人脸签到', '解释 Java 接口是什么'] : ['今天各场所签到情况', '第二生活园区在哪里', '解释 Java 接口是什么'])
let previousIntent = '', previousOffice = ''
const toolLabels = { 'office.lookup':'场所', 'office.list':'场所', 'office.assignedEmployees':'在册人员', 'attendance.myRecords':'我的考勤', 'attendance.aggregate':'考勤汇总', 'attendance.recentRecords':'考勤记录' }
function toolNames(tools) { return [...new Set(tools.map(tool => toolLabels[tool] || tool))].join('、') }
async function scrollBottom() { await nextTick(); if (conversation.value) conversation.value.scrollTop = conversation.value.scrollHeight }
async function send(prompt) {
  const question = typeof prompt === 'string' ? prompt : draft.value.trim()
  if (!question || sending.value) return
  messages.value.push({ from:'self', text:question })
  draft.value = ''
  sending.value = true
  await scrollBottom()
  try {
    const result = await askAttendanceAgent({ message:question, previousIntent, previousOffice })
    mode.value = result.mode || 'offline'
    messages.value.push({ from:'agent', text:result.reply, tools:result.toolsUsed || [] })
    previousIntent = result.intent || ''
    previousOffice = result.officeCode || ''
    if (result.suggestions?.length) suggestions.value = result.suggestions
  } catch (error) {
    messages.value.push({ from:'agent', text:'暂时无法查询，请稍后重试。' })
    ElMessage.error(error?.message || '查询失败')
  } finally { sending.value = false; await scrollBottom() }
}
</script>

<style scoped>
.agent-page{max-width:940px;margin:0 auto;display:grid;gap:18px}.heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.heading h1{font-size:24px;margin:0 0 8px}.heading p{color:#8796a9;font-size:13px;margin:0}.chat-card{border:1px solid #e3eaf4;border-radius:14px}.chat-card :deep(.el-card__body){padding:0}.conversation{height:min(55vh,520px);min-height:330px;overflow-y:auto;padding:26px;display:flex;flex-direction:column;gap:18px;background:linear-gradient(180deg,#f9fbff 0%,#fff 75%)}.message{display:flex;gap:10px;max-width:84%}.message.self{align-self:flex-end;flex-direction:row-reverse}.avatar{width:32px;height:32px;border-radius:11px;background:#e7f1ff;color:#2d6dcc;display:grid;place-items:center;font-weight:700;flex:none}.self .avatar{background:#dfe8f8;color:#466388}.bubble{background:#fff;border:1px solid #e4ebf5;border-radius:12px;padding:11px 14px;color:#25334a;font-size:14px;line-height:1.7;box-shadow:0 2px 8px #1531530a}.self .bubble{background:#e9f2ff;border-color:#dbeaff}.message-text{white-space:pre-wrap;overflow-wrap:anywhere}.bubble small{display:block;border-top:1px solid #edf1f6;color:#8392a8;margin-top:8px;padding-top:6px;font-size:11px}.suggestions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:14px 24px 0}.suggestions span{font-size:12px;color:#8696aa;margin-right:2px}.composer{display:flex;gap:10px;padding:16px 24px}.composer .el-input{flex:1}.note{font-size:11px;color:#91a0b3;line-height:1.5;margin:0;padding:0 24px 20px}@media(max-width:600px){.heading h1{font-size:21px}.conversation{height:48vh;min-height:260px;padding:16px}.message{max-width:95%}.suggestions{padding:12px 15px 0}.composer{padding:12px 15px}.note{padding:0 15px 15px}}
</style>
