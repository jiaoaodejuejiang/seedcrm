import { computed, reactive, ref, unref, watch } from 'vue'

export const TABLE_PAGE_SIZES = [30, 50]

export function useTablePagination(source, defaultPageSize = 30) {
  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)

  const sourceRows = computed(() => {
    const resolved = typeof source === 'function' ? source() : unref(source)
    return Array.isArray(resolved) ? resolved : []
  })

  const total = computed(() => sourceRows.value.length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

  const rows = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return sourceRows.value.slice(start, start + pageSize.value)
  })

  watch(total, () => {
    if (currentPage.value > pageCount.value) {
      currentPage.value = pageCount.value
    }
  })

  function handleSizeChange(size) {
    pageSize.value = size
    currentPage.value = 1
  }

  function handleCurrentChange(page) {
    currentPage.value = page
  }

  function reset() {
    currentPage.value = 1
  }

  return reactive({
    currentPage,
    pageSize,
    pageSizes: TABLE_PAGE_SIZES,
    total,
    rows,
    handleSizeChange,
    handleCurrentChange,
    reset
  })
}
