<template>
  <div>
    <h2 class="page-title">操作日志</h2>
    <p class="page-desc">
      关键操作写入操作日志，内容包括操作人、操作时间、来源 IP、操作对象、变更前后值与操作结果；日志只增不改，
      按学期归档，可按人员、时间、类型与对象组合检索。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="query.userId" placeholder="操作人" clearable style="width: 150px" @change="search" />
        <el-select v-model="query.operType" placeholder="操作类型" clearable style="width: 130px" @change="search">
          <el-option label="新增" value="01" />
          <el-option label="修改" value="02" />
          <el-option label="删除" value="03" />
          <el-option label="审批" value="04" />
          <el-option label="发布" value="05" />
          <el-option label="导出" value="06" />
        </el-select>
        <el-input v-model="query.operObject" placeholder="操作对象（表名:主键）" clearable style="width: 220px" @change="search" />
        <el-date-picker v-model="startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width: 150px" @change="search" />
        <el-date-picker v-model="endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 150px" @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-table :data="rows" border size="small">
        <el-table-column prop="logId" label="日志编号" width="180" />
        <el-table-column prop="userId" label="操作人" width="120" />
        <el-table-column prop="roleId" label="角色" width="80" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ operTypeName(row.operType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operObject" label="操作对象" min-width="230" show-overflow-tooltip />
        <el-table-column prop="beforeValue" label="变更前" min-width="180" show-overflow-tooltip />
        <el-table-column prop="afterValue" label="变更后" min-width="180" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="来源 IP" width="130" />
        <el-table-column prop="operTime" label="操作时间" width="165" />
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 14px; justify-content: flex-end"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const startDate = ref('')
const endDate = ref('')
const query = reactive({ userId: '', operType: '', operObject: '' })

const operTypeName = (c) => ({ '01': '新增', '02': '修改', '03': '删除', '04': '审批', '05': '发布', '06': '导出' }[c] || c)

async function load() {
  const data = await api.logs({
    page: page.value,
    size,
    userId: query.userId || undefined,
    operType: query.operType || undefined,
    operObject: query.operObject || undefined,
    startDate: startDate.value || undefined,
    endDate: endDate.value || undefined
  })
  rows.value = data.records
  total.value = data.total
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>
