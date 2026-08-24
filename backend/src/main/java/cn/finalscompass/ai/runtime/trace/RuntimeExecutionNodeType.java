package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时执行节点类型允许使用的固定取值
 * 维护入口：执行链路、状态和审计字段变化时修改这里
 */
public enum RuntimeExecutionNodeType {
  TASK_UNDERSTANDING,
  WORKFLOW_RESOLUTION,
  SKILL_RESOLUTION,
  SKILL,
  MODEL,
  TOOL,
  CONDITION,
  PARALLEL,
  JOIN,
  HUMAN_CONFIRM,
  DOCUMENT,
  AGENT
}
