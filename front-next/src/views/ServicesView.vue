<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { api } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import ServiceCard from '../components/ServiceCard.vue'
import type { Category, ServiceItem } from '../types/domain'

const categories = ref<Category[]>([])
const services = ref<ServiceItem[]>([])
const selectedCategory = ref<number | null>(null)
const keyword = ref('')
const sort = ref('')
const loading = ref(true)
const error = ref('')

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return services.value.filter((item) => {
    const categoryMatch = !selectedCategory.value || item.categoryId === selectedCategory.value
    const keywordMatch = !query || [item.name, item.subtitle, item.description, item.categoryName]
      .some((value) => value?.toLowerCase().includes(query))
    return categoryMatch && keywordMatch
  })
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [categoryData, serviceData] = await Promise.all([
      api.categories(),
      api.services(sort.value ? { sort: sort.value } : undefined),
    ])
    categories.value = categoryData
    services.value = serviceData
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '项目加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <header class="page-heading">
      <div>
        <span class="eyebrow">CARE CATALOG</span>
        <h1>护理项目</h1>
        <p>项目价格、护理时长、介绍与上下架状态均由管理端统一维护。</p>
      </div>
      <router-link class="button button-primary" to="/booking">选择项目并预约</router-link>
    </header>

    <section class="catalog-toolbar" aria-label="筛选护理项目">
      <div class="search-box">
        <Search />
        <input v-model="keyword" type="search" placeholder="搜索项目名称或护理说明" aria-label="搜索项目" />
      </div>
      <select v-model="sort" class="form-control sort-select" aria-label="项目排序" @change="load">
        <option value="">默认排序</option>
        <option value="clicknum">浏览热度</option>
        <option value="storeupnum">收藏热度</option>
      </select>
    </section>

    <div class="category-tabs" aria-label="项目分类">
      <button :class="{ active: selectedCategory === null }" type="button" @click="selectedCategory = null">
        全部项目
      </button>
      <button
        v-for="category in categories"
        :key="category.id"
        :class="{ active: selectedCategory === category.id }"
        type="button"
        @click="selectedCategory = category.id"
      >
        {{ category.name }}
      </button>
    </div>

    <section class="catalog-results">
      <div class="result-count">共 {{ filtered.length }} 个可预约项目</div>
      <div v-if="error" class="notice">{{ error }}</div>
      <LoadingState v-else-if="loading" class="loading-grid" label="护理项目加载中" :count="6" />
      <div v-else-if="filtered.length" class="service-grid">
        <ServiceCard v-for="service in filtered" :key="service.id" :service="service" />
      </div>
      <EmptyState
        v-else
        title="没有匹配的护理项目"
        description="请清除搜索词或切换其他分类。"
      />
    </section>
  </div>
</template>

<style scoped>
.catalog-toolbar {
  display: grid;
  grid-template-columns: 1fr 180px;
  gap: 12px;
  margin-top: 38px;
}

.search-box {
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
}

.search-box svg {
  width: 17px;
  color: var(--text-muted);
}

.search-box input {
  flex: 1;
  min-width: 0;
  border: 0;
  color: var(--text);
  background: transparent;
  outline: 0;
}

.category-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 18px 0 4px;
}

.category-tabs button {
  flex: 0 0 auto;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--text-soft);
  background: transparent;
  cursor: pointer;
}

.category-tabs button.active {
  border-color: rgba(201, 106, 136, 0.52);
  color: var(--rose-strong);
  background: rgba(168, 79, 100, 0.08);
}

.catalog-results {
  padding: 30px 0 76px;
}

.result-count {
  margin-bottom: 16px;
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 640px) {
  .catalog-toolbar {
    grid-template-columns: 1fr;
  }

  .sort-select {
    width: 100%;
  }
}
</style>
