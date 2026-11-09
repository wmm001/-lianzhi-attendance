<template>
  <div class="not-found">
    <h1>404 - 页面未找到</h1>
    <p>抱歉，您访问的页面不存在</p>
    <router-link to="/" class="btn">返回首页</router-link>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  text-align: center;
  padding: 100px 20px;
}

h1 {
  font-size: 48px;
  color: #333;
  margin-bottom: 20px;
}

p {
  font-size: 18px;
  color: #666;
  margin-bottom: 30px;
}

.btn {
  display: inline-block;
  padding: 12px 30px;
  background-color: var(--brand);
  color: white;
  text-decoration: none;
  border-radius: 4px;
}

.btn:hover {
  background-color: #66b1ff;
}
</style>
