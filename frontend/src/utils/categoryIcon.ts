import type { TransactionType } from '@/types'

export interface CategoryVisual {
  icon: string
  /** 图标底色(柔和色块) */
  bg: string
  /** 图标前景色,用于占比条等场景 */
  color: string
}

interface CategoryRule {
  keywords: string[]
  visual: CategoryVisual
}

/**
 * 分类名 → emoji 图标 + 柔和底色。
 * 关键词按「随手记」167 个分类的常见命名整理,父分类名与子分类名拼接后匹配,
 * 越靠前的规则优先级越高。
 */
const rules: CategoryRule[] = [
  // —— 餐饮食品 ——
  { keywords: ['早餐', '早午晚餐', '午餐', '晚餐', '餐饮', '下馆子', '外卖'], visual: { icon: '🍜', bg: '#FDEAD7', color: '#E07B2A' } },
  { keywords: ['水果', '零食'], visual: { icon: '🍓', bg: '#FDE3E0', color: '#D85A50' } },
  { keywords: ['烟酒茶', '烟酒', '咖啡', '饮料'], visual: { icon: '🍵', bg: '#E5EFD8', color: '#6B8E3A' } },
  { keywords: ['食品', '买菜', '柴米油盐', '食材'], visual: { icon: '🥬', bg: '#E1F0DD', color: '#4D9152' } },
  // —— 衣饰美容 ——
  { keywords: ['衣服', '鞋帽', '衣饰', '服饰'], visual: { icon: '👕', bg: '#DCEDF7', color: '#3D88B8' } },
  { keywords: ['化妆', '饰品', '美容', '美发', '护肤'], visual: { icon: '💄', bg: '#FBE0EC', color: '#C8568F' } },
  // —— 居住生活 ——
  { keywords: ['房租', '房贷', '物业', '居家', '住房'], visual: { icon: '🏠', bg: '#FCE9D4', color: '#C97F2E' } },
  { keywords: ['水电', '煤气', '水费', '电费', '燃气'], visual: { icon: '💡', bg: '#FCF3CF', color: '#C0982B' } },
  { keywords: ['维修', '家具', '家居', '日用品', '日常用品'], visual: { icon: '🧺', bg: '#EDE7DB', color: '#8F8268' } },
  // —— 交通通讯 ——
  { keywords: ['公交', '地铁', '交通'], visual: { icon: '🚇', bg: '#D9EEF1', color: '#3E96A3' } },
  { keywords: ['打车', '租车', '出租车'], visual: { icon: '🚕', bg: '#FCF0CE', color: '#C09A24' } },
  { keywords: ['私家车', '加油', '停车', '车辆', '行车'], visual: { icon: '🚗', bg: '#DEE9FA', color: '#4A78C4' } },
  { keywords: ['火车', '机票', '飞机'], visual: { icon: '✈️', bg: '#DCEBF8', color: '#4187C2' } },
  { keywords: ['手机', '话费', '上网', '网费', '通讯', '快递', '邮寄'], visual: { icon: '📱', bg: '#E4E3F6', color: '#6B63B5' } },
  // —— 休闲娱乐 ——
  { keywords: ['运动', '健身'], visual: { icon: '🏸', bg: '#DFF2E4', color: '#3E9B62' } },
  { keywords: ['旅游', '度假', '景点'], visual: { icon: '🏖️', bg: '#D7F0EE', color: '#37968E' } },
  { keywords: ['电影', '演出', '休闲', '玩乐', '娱乐', '游戏'], visual: { icon: '🎮', bg: '#E9E2F8', color: '#7C5FBF' } },
  { keywords: ['宠物'], visual: { icon: '🐱', bg: '#F7E8D7', color: '#B07C3F' } },
  { keywords: ['腐败', '聚会', '请客'], visual: { icon: '🍻', bg: '#FBEBCB', color: '#BD8E29' } },
  // —— 学习进修 ——
  { keywords: ['书报', '书籍', '杂志'], visual: { icon: '📚', bg: '#E3ECF9', color: '#4C77BB' } },
  { keywords: ['培训', '进修', '学习', '学费', '教育'], visual: { icon: '🎓', bg: '#E0E8F8', color: '#4A6FBE' } },
  // —— 人情往来 ——
  { keywords: ['送礼', '礼金', '红包', '人情'], visual: { icon: '🧧', bg: '#FBDFDA', color: '#C8483A' } },
  { keywords: ['孝敬', '家长', '抚养', '育儿', '宝宝', '孩子'], visual: { icon: '👨‍👩‍👧', bg: '#FBE7D4', color: '#C07B33' } },
  { keywords: ['公益', '捐赠', '慈善'], visual: { icon: '💝', bg: '#FBE2E8', color: '#C25578' } },
  // —— 医疗保健 ——
  { keywords: ['药品', '医疗', '治疗', '看病', '挂号'], visual: { icon: '💊', bg: '#DFF0EC', color: '#3D9479' } },
  { keywords: ['保健', '体检'], visual: { icon: '🩺', bg: '#DDEFF4', color: '#3D8FA8' } },
  // —— 金融保险 ——
  { keywords: ['保险'], visual: { icon: '🛡️', bg: '#E2E9F2', color: '#56789E' } },
  { keywords: ['利息支出', '手续费', '税', '罚款', '亏损'], visual: { icon: '🧾', bg: '#EFE5DC', color: '#9A7250' } },
  // —— 收入 ——
  { keywords: ['工资', '薪'], visual: { icon: '💼', bg: '#DFF0E4', color: '#368653' } },
  { keywords: ['奖金', '绩效', '年终'], visual: { icon: '🏆', bg: '#FBEFCD', color: '#B98F22' } },
  { keywords: ['投资', '理财', '基金', '股票', '分红'], visual: { icon: '📈', bg: '#DEF0E9', color: '#33927A' } },
  { keywords: ['利息收入', '利息'], visual: { icon: '🪙', bg: '#FBEDCB', color: '#B58F2B' } },
  { keywords: ['兼职', '外快', '副业'], visual: { icon: '🛠️', bg: '#E8E6F5', color: '#6E63AE' } },
  { keywords: ['中奖', '意外'], visual: { icon: '🎉', bg: '#FBE3DB', color: '#C95F3D' } },
  { keywords: ['退款', '报销', '返现'], visual: { icon: '↩️', bg: '#DFEDF6', color: '#4485AE' } },
  // —— 兜底大类 ——
  { keywords: ['购物'], visual: { icon: '🛍️', bg: '#FBE4DA', color: '#C66239' } },
  { keywords: ['收入'], visual: { icon: '💰', bg: '#F8EBC9', color: '#AE8C2C' } },
  { keywords: ['杂项', '其他'], visual: { icon: '🧩', bg: '#ECE7DE', color: '#8C8270' } }
]

const fallbackExpense: CategoryVisual = { icon: '🧾', bg: '#F0E9DC', color: '#94815F' }
const fallbackIncome: CategoryVisual = { icon: '💰', bg: '#F8EBC9', color: '#AE8C2C' }

export function categoryVisual(
  categoryName?: string | null,
  parentName?: string | null,
  type?: TransactionType | null
): CategoryVisual {
  const haystack = `${parentName || ''}${categoryName || ''}`
  if (haystack) {
    for (const rule of rules) {
      if (rule.keywords.some(keyword => haystack.includes(keyword))) {
        return rule.visual
      }
    }
  }
  return type === 'INCOME' ? fallbackIncome : fallbackExpense
}
