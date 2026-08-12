package cn.finalscompass.ai.runtime.trace;

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
