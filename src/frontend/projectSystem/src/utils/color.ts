/**
 * 课程配色：在"课程全集"内为每门课分配一个稳定且互不重复的颜色。
 * - 稳定：同一课程 id 的颜色不随选课变化、不随页面变化（按 id 字典序固定位次）
 * - 不重复：课程总数不超过色板数量时，同屏不同课程颜色一定不同
 * - 超出色板数量时退化为 hash 取色（极端兜底）
 */
export const COURSE_COLORS = ['blue', 'orange', 'green', 'purple', 'red', 'cyan', 'amber', 'pink'] as const;
export type CourseColor = (typeof COURSE_COLORS)[number];

function hashIndex(id: string): number {
  let h = 0;
  for (let k = 0; k < id.length; k++) h = (h * 31 + id.charCodeAt(k)) >>> 0;
  return h % COURSE_COLORS.length;
}

/**
 * 依据课程全集构造 colorOf(id)。
 * @param allCourseIds 当前上下文中的全部课程 id（如课程目录/可选任教列表）
 */
export function makeCourseColorOf(allCourseIds: string[]): (id: string) => CourseColor {
  const order = Array.from(new Set(allCourseIds)).sort();
  const index = new Map<string, number>();
  order.forEach((id, i) => index.set(id, i));
  return (id: string) => COURSE_COLORS[(index.get(id) ?? hashIndex(id)) % COURSE_COLORS.length];
}
