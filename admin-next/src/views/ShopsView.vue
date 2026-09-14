<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="environment-label">访问范围</p>
      <h1>当前账号可管理的门店</h1>
      <p>列表由后端根据租户和角色范围返回，前端不能通过修改门店ID越权查看。</p>
    </div>
  </section>

  <section class="content-section">
    <div v-if="auth.shops.length" class="shop-table-wrap">
      <table class="shop-table">
        <thead>
          <tr>
            <th>门店编码</th>
            <th>门店名称</th>
            <th>联系电话</th>
            <th>地址</th>
            <th>时区</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="shop in auth.shops" :key="shop.id">
            <td><code>{{ shop.shopCode }}</code></td>
            <td><strong>{{ shop.name }}</strong></td>
            <td>{{ shop.phone || '未填写' }}</td>
            <td>{{ shop.address || '未填写' }}</td>
            <td>{{ shop.timezone }}</td>
            <td><span class="status-badge status-badge--success">启用</span></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-state">
      <h2>尚未分配可管理门店</h2>
      <p>请让品牌负责人在账号角色中为当前账号分配区域或门店。</p>
    </div>
  </section>
</template>
