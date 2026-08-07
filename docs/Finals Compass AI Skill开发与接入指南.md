# Finals Compass AI Skill 开发与接入指南

## 1. Skill 的定位

Skill 是完成单一、可复用能力的原子模块，不是用户功能入口。用户始终使用自然语言描述学习目标；`LearningTaskRouter` 识别产品任务，Workflow 决定在何时组合哪些 Skill。

新增 Skill 不应修改 Controller、Provider Adapter、`AiProviderGateway` 或核心请求协议，也不应在前端增加 Skill 选择器。

## 2. 创建 Skill

代码放在 `backend/src/main/java/cn/finalscompass/ai/skill/`。当前 V2 接口为声明式契约：

```java
public interface AiSkill {
    String id();
    String category();
    String displayName();
    String description();
    int maxInputLength();
    Set<String> modalities();
    String systemInstruction();
    String outputContract();
    Set<String> allowedTools();
}
```

字段约束：

- `id`：稳定、全局唯一的 kebab-case 标识，发布后不要改名。
- `displayName`：供管理和审计使用，不应暴露为普通用户的功能选择。
- `description`：准确说明单一能力边界。
- `modalities`：声明 `TEXT`、`IMAGE` 等输入类型。
- `systemInstruction`：只描述该能力的执行原则，不能包含 Provider 逻辑。
- `outputContract`：定义可验证的产出结构。
- `allowedTools`：最小工具白名单；未知工具会被 `AiToolLimiter` 拒绝。

例如新增作业分析能力：

```java
@Bean
AiSkill homeworkAnalysisSkill() {
    return new DefaultAiSkill(
        "homework-analysis",
        "LEARNING",
        "作业分析",
        "识别作业覆盖的知识点、难度和完成风险。",
        12000,
        Set.of("TEXT", "IMAGE"),
        "只依据用户提供的作业内容分析，不虚构题目或教师要求。",
        "输出知识点、题型、难度、前置知识、建议顺序和风险项。",
        Set.of()
    );
}
```

## 3. 注册 Skill

推荐在 `AiSkillConfiguration` 中声明 Spring Bean。`AiSkillRegistry` 会自动收集全部 `AiSkill` Bean，并在启动时拒绝重复 ID。

Skill 注册后只表示系统能够发现它；它不会自动成为用户入口。只有 Router 对应的 Workflow 引用了该 ID，能力才会进入产品执行链路。

## 4. 任务映射

先判断新能力是否属于已有 `LearningTaskType`：

- `EXAM_PREPARATION`
- `MATERIAL_ANALYSIS`
- `QUESTION_ASSISTANCE`
- `ANSWER_REVIEW`
- `STUDY_PLANNING`

属于已有任务时，不修改枚举和 Router，只把 Skill 加入相应 Workflow。

只有出现稳定、独立且不能由现有五类表达的产品目标时，才增加任务类型；同时必须补充 Router 规则、Workflow 和测试。Router 是闭集分类器，禁止根据模型输出动态创造任务类型。

## 5. 加入 Workflow

Workflow 决定 Skill 的顺序和所需上下文。例如把作业分析加入考试准备：

```java
return workflow("exam-preparation", LearningTaskType.EXAM_PREPARATION,
    step(1, "material-summary", "COURSE", "MATERIALS"),
    step(2, "homework-analysis", "COURSE"),
    step(3, "exam-focus-analysis", "COURSE", "TEACHER"),
    step(4, "study-plan-generation", "COURSE"));
```

规则：

- 步骤序号严格递增。
- 引用的 Skill 必须已经注册，否则应用启动失败。
- `requiredContext` 只声明真正需要的业务上下文。
- Workflow 不选择 Provider，不读取 API Key，不处理用户权限。
- 最后一个 Skill 的输出契约定义最终学习成果的结构。

## 6. 测试要求

每个新增 Skill 至少包含：

1. Skill 契约测试：ID、输入长度、模态、工具白名单和输出契约。
2. Router 测试：若增加或调整任务识别规则，覆盖正例、冲突优先级和默认回退。
3. Workflow 测试：验证 Skill 顺序、Registry 可发现性和所需上下文。
4. 回归测试：确认 `/api/ai/invoke`、显式旧 Skill 调用和 Provider Gateway 不受影响。

测试不应锁定所有 Skill 的固定数量；应断言行为关系，例如“Workflow 引用的每个 ID 均能从 Registry 解析”。

## 7. 前端与兼容性

新增 Skill 原则上不修改前端。普通用户不应该看到 Skill 名、Workflow 名、Provider 或内部执行过程。

如需快捷入口，只能把自然语言写入输入框，例如“帮我分析这份作业并制定完成计划”，随后仍通过 `/api/ai/invoke` 的自动任务路由执行。

提交前运行：

```bash
cd backend
mvn test
```

并确认前端生产构建通过：

```bash
npm run build
```
