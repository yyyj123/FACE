<template>
  <nav class="breadcrumb" aria-label="面包屑导航">
    <el-breadcrumb :separator="separator">
      <el-breadcrumb-item>首页</el-breadcrumb-item>
      <el-breadcrumb-item v-for="(item, index) in breadcrumbItem" :key="index">{{item.name}}</el-breadcrumb-item>
    </el-breadcrumb>
  </nav>
</template>

<script>
  export default {
    name: 'Breadcrumb',
    props: {
      separator: {
        type: String,
        default: '/'
      },
      breadcrumbItem: {
        type: Array,
        default: () => []
      }
    }
  }
</script>

<style lang='css' scoped>
  .breadcrumb {
    width: min(100%, 1280px);
    min-height: 44px;
    margin: 24px auto 32px;
    padding: 0 16px;
    border-bottom: 1px solid var(--oc-border);
    background: transparent;
  }
  .breadcrumb /deep/ .el-breadcrumb {
    min-height: 44px;
    color: var(--oc-text-muted);
    line-height: 44px;
  }
  .breadcrumb /deep/ .el-breadcrumb__inner,
  .breadcrumb /deep/ .el-breadcrumb__separator {
    color: var(--oc-text-muted);
  }
  .breadcrumb /deep/ .el-breadcrumb__item:last-child .el-breadcrumb__inner {
    color: var(--oc-gold);
  }
</style>
